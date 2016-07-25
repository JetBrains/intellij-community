/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.util.ExecUtil
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.sun.tools.doclint.DocLint
import java.io.File

class JavadocHtmlLintAnnotator(private val manual: Boolean = false) :
    ExternalAnnotator<JavadocHtmlLintAnnotator.Info, JavadocHtmlLintAnnotator.Result>() {

  data class Info(val file: PsiFile)
  data class Anno(val row: Int, val col: Int, val error: Boolean, val message: String)
  data class Result(val annotations: List<Anno>)

  override fun collectInformation(file: PsiFile): Info? =
      if (isJava8SourceFile(file) && "/**" in file.text && isToolEnabled(file)) Info(file) else null

  override fun doAnnotate(collectedInfo: Info): Result? {
    val file = collectedInfo.file.virtualFile!!
    val copy = createTempFile(collectedInfo.file.text.toByteArray(file.charset))

    try {
      val command = toolCommand(file, collectedInfo.file.project, copy)

      val output = ExecUtil.execAndGetOutput(command)
      if (output.exitCode != 0) {
        val log = Logger.getInstance(JavadocHtmlLintAnnotator::class.java)
        if (log.isDebugEnabled) log.debug("${file}: ${output.exitCode}, ${output.stderr}")
        return null
      }

      val annotations = parse(output.stdoutLines)
      return if (annotations.isNotEmpty()) Result(annotations) else null
    }
    finally {
      FileUtil.delete(copy)
    }
  }

  override fun apply(file: PsiFile, annotationResult: Result, holder: AnnotationHolder) {
    val text = file.text
    val offsets = text.foldIndexed(mutableListOf(0)) { i, offsets, c -> if (c == '\n') offsets += (i + 1); offsets }

    for ((row, col, error, message) in annotationResult.annotations) {
      if (row < offsets.size) {
        val offset = offsets[row] + col
        val element = file.findElementAt(offset)
        if (element != null && PsiTreeUtil.getParentOfType(element, PsiDocComment::class.java) != null) {
          val range = adjust(element, text, offset)
          val description = StringUtil.capitalize(message)
          val annotation = when (error) {
            true -> holder.createErrorAnnotation(range, description)
            false -> holder.createWarningAnnotation(range, description)
          }
          registerFix(annotation)
        }
      }
    }
  }

  //<editor-fold desc="Helpers">

  private val jdk = lazy {
    var jdkHome = File(System.getProperty("java.home"))
    if (jdkHome.name == "jre") jdkHome = jdkHome.parentFile
    JavaSdk.getInstance().createJdk("(internal JDK)", jdkHome.path)
  }

  private val key = lazy { HighlightDisplayKey.find(JavadocHtmlLintInspection.SHORT_NAME) }

  private val lintOptions = "${DocLint.XMSGS_CUSTOM_PREFIX}html/private,accessibility/private"
  private val lintPattern = "^.+:(\\d+):\\s+(error|warning):\\s+(.+)$".toPattern()

  private fun isJava8SourceFile(file: PsiFile) =
      file is PsiJavaFile && file.languageLevel.isAtLeast(LanguageLevel.JDK_1_8) &&
      file.virtualFile != null && ProjectFileIndex.SERVICE.getInstance(file.project).isInSourceContent(file.virtualFile)

  private fun isToolEnabled(file: PsiFile) =
      manual || InspectionProjectProfileManager.getInstance(file.project).currentProfile.isToolEnabled(key.value, file)

  private fun createTempFile(bytes: ByteArray): File {
    val tempFile = FileUtil.createTempFile(File(PathManager.getTempPath()), "javadocHtmlLint", ".java")
    tempFile.writeBytes(bytes)
    return tempFile
  }

  private fun toolCommand(file: VirtualFile, project: Project, copy: File): GeneralCommandLine {
    val parameters = SimpleJavaParameters()

    val jdk = findJdk(file, project)
    parameters.jdk = jdk

    val toolsJar = File("${jdk.homePath}/lib/tools.jar")
    if (toolsJar.exists()) parameters.classPath.add(toolsJar.path)

    parameters.charset = file.charset
    parameters.vmParametersList.addProperty("user.language", "en")
    parameters.mainClass = DocLint::class.qualifiedName
    parameters.programParametersList.add(lintOptions)
    parameters.programParametersList.add(copy.path)

    val cmd = parameters.toCommandLine()
    val exeFile = File(cmd.exePath)
    if (!exeFile.exists()) cmd.exePath = File(exeFile.parentFile.parentFile, "jre/bin/${exeFile.name}").path

    return cmd
  }

  private fun findJdk(file: VirtualFile, project: Project): Sdk {
    val rootManager = ProjectRootManager.getInstance(project)

    val module = rootManager.fileIndex.getModuleForFile(file)
    if (module != null) {
      val sdk = ModuleRootManager.getInstance(module).sdk
      if (isJdk8(sdk)) return sdk!!
    }

    val sdk = rootManager.projectSdk
    if (isJdk8(sdk)) return sdk!!

    return jdk.value
  }

  private fun isJdk8(sdk: Sdk?) =
      sdk != null &&
      sdk.sdkType is JavaSdk && (sdk.sdkType as JavaSdk).isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_8) &&
      JavaSdk.checkForJdk(File(sdk.homePath))

  private fun parse(lines: List<String>): List<Anno> {
    val result = mutableListOf<Anno>()

    val i = lines.iterator()
    while (i.hasNext()) {
      val line = i.next()
      val matcher = lintPattern.matcher(line)
      if (matcher.matches() && i.hasNext() && !i.next().isEmpty() && i.hasNext()) {
        val row = matcher.group(1).toInt() - 1
        val col = i.next().indexOf('^')
        val error = matcher.group(2) == "error"
        val message = matcher.group(3)
        result += Anno(row, col, error, message)
      }
    }

    return result
  }

  private fun adjust(element: PsiElement, text: String, offset: Int): TextRange {
    val range = element.textRange

    if (text[offset] == '<') {
      val right = text.indexOf('>', offset)
      if (right > 0) return TextRange(offset, Integer.min(right + 1, range.endOffset))
    }
    else if (text[offset] == '&') {
      val right = text.indexOf(';', offset)
      if (right > 0) return TextRange(offset, Integer.min(right + 1, range.endOffset))
    }
    else if (text[offset].isLetter() && !text[offset - 1].isLetter()) {
      var right = offset + 1
      while (text[right].isLetter() && right <= range.endOffset) right++
      return TextRange(offset, right)
    }

    return range
  }

  private fun registerFix(annotation: Annotation) =
      annotation.registerFix(EmptyIntentionAction(InspectionsBundle.message("inspection.javadoc.lint.display.name")), null, key.value)

  //</editor-fold>
}