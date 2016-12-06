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
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiKeyword

class JavaSoftKeywordHighlightingPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar) :
  AbstractProjectComponent(project), TextEditorHighlightingPassFactory {

  init {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? =
    if (file is PsiJavaFile && file.languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) JavaSoftKeywordHighlightingPass(file, editor.document)
    else null
}

private class JavaSoftKeywordHighlightingPass(private val file: PsiJavaFile, document: Document) :
  TextEditorHighlightingPass(file.project, document) {

  private val results = mutableListOf<HighlightInfo>()

  override fun doCollectInformation(progress: ProgressIndicator) {
    file.accept(JavaSoftKeywordHighlightingVisitor(results, file.languageLevel))
  }

  override fun doApplyInformationToEditor() {
    if (results.isNotEmpty()) {
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument!!, 0, file.textLength, results, colorsScheme, id)
    }
  }
}

private class JavaSoftKeywordHighlightingVisitor(private val results: MutableList<HighlightInfo>, private val level: LanguageLevel) :
  JavaRecursiveElementVisitor() {

  override fun visitKeyword(keyword: PsiKeyword) {
    if (JavaLexer.isSoftKeyword(keyword.node.chars, level)) {
      val info = HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.JAVA_KEYWORD).range(keyword).create()
      if (info != null) {
        results += info
      }
    }
  }
}