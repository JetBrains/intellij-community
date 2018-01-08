/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.impl

import com.intellij.codeInsight.JavaModuleSystemEx
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.JavaErrorMessages
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.jrt.JrtFileSystem
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.impl.source.PsiJavaModuleReference
import com.intellij.psi.util.PsiUtil

/**
 * Checks package accessibility according to JLS 7 "Packages and Modules".
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html">JLS 7 "Packages and Modules"</a>
 * @see <a href="http://openjdk.java.net/jeps/261">JEP 261: Module System</a>
 */
class JavaPlatformModuleSystem : JavaModuleSystemEx {
  override fun getName() = "Java Platform Module System"

  override fun isAccessible(target: PsiPackage, place: PsiElement) = checkAccess(target, place, quick = true) == null
  override fun isAccessible(target: PsiClass, place: PsiElement) = checkAccess(target, place, quick = true) == null

  override fun checkAccess(target: PsiPackage, place: PsiElement) = checkAccess(target, place, quick = false)
  override fun checkAccess(target: PsiClass, place: PsiElement) = checkAccess(target, place, quick = false)

  private fun checkAccess(target: PsiClass, place: PsiElement, quick: Boolean): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile
    if (useFile != null && PsiUtil.isLanguageLevel9OrHigher(useFile)) {
      val targetFile = target.containingFile
      if (targetFile is PsiClassOwner) {
        return checkAccess(targetFile, useFile, targetFile.packageName, quick)
      }
    }

    return null
  }

  private fun checkAccess(target: PsiPackage, place: PsiElement, quick: Boolean): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile
    if (useFile != null && PsiUtil.isLanguageLevel9OrHigher(useFile)) {
      val useVFile = useFile.virtualFile
      if (useVFile != null) {
        val index = ProjectFileIndex.getInstance(useFile.project)
        val module = index.getModuleForFile(useVFile)
        if (module != null) {
          val test = index.isInTestSourceContent(useVFile)
          val dirs = target.getDirectories(module.getModuleWithDependenciesAndLibrariesScope(test))
          if (dirs.isEmpty()) {
            return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("package.not.found", target.qualifiedName))
          }
          val error = checkAccess(dirs[0], useFile, target.qualifiedName, quick)
          return when {
            error == null -> null
            dirs.size == 1 -> error
            dirs.asSequence().drop(1).any { checkAccess(it, useFile, target.qualifiedName, true) == null } -> null
            else -> error
          }
        }
      }
    }

    return null
  }

  private val ERR = ErrorWithFixes("-")

  private fun checkAccess(target: PsiFileSystemItem, place: PsiFileSystemItem, packageName: String, quick: Boolean): ErrorWithFixes? {
    val targetModule = JavaModuleGraphUtil.findDescriptorByElement(target)?.originalElement as PsiJavaModule?
    val useModule = JavaModuleGraphUtil.findDescriptorByElement(place)?.originalElement as PsiJavaModule?

    if (targetModule != null) {
      if (targetModule == useModule) {
        return null
      }

      if (useModule == null && targetModule.containingFile?.virtualFile?.fileSystem !is JrtFileSystem) {
        return null  // a target is not on the mandatory module path
      }

      val targetName = targetModule.name
      val useName = useModule?.name ?: "ALL-UNNAMED"
      val module = place.virtualFile?.let { ProjectFileIndex.getInstance(place.project).getModuleForFile(it) }

      if (!(targetModule is LightJavaModule ||
            JavaModuleGraphUtil.exports(targetModule, packageName, useModule) ||
            module != null && inAddedExports(module, targetName, packageName, useName))) {
        if (quick) return ERR
        val fixes = when {
          packageName.isEmpty() -> emptyList()
          targetModule is PsiCompiledElement && module != null -> listOf(AddExportsOptionFix(module, targetName, packageName, useName))
          targetModule !is PsiCompiledElement && useModule != null -> listOf(AddExportsDirectiveFix(targetModule, packageName, useName))
          else -> emptyList()
        }
        return when (useModule) {
          null -> ErrorWithFixes(JavaErrorMessages.message("module.access.from.unnamed", packageName, targetName), fixes)
          else -> ErrorWithFixes(JavaErrorMessages.message("module.access.from.named", packageName, targetName, useName), fixes)
        }
      }

      if (useModule == null) {
        if (!targetName.startsWith("java.")) return null
        val root = PsiJavaModuleReference.resolve(place, "java.se", false)
        if (root == null || JavaModuleGraphUtil.reads(root, targetModule)) return null
        if (module != null && inAddedModules(module, targetName)) return null
        val fixes = if (quick || module == null) emptyList() else listOf(AddModulesOptionFix(module, targetName))
        return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("module.access.not.in.graph", packageName, targetName), fixes)
      }

      if (!(targetName == PsiJavaModule.JAVA_BASE || JavaModuleGraphUtil.reads(useModule, targetModule))) {
        return when {
          quick -> ERR
          PsiNameHelper.isValidModuleName(targetName, useModule) -> ErrorWithFixes(
            JavaErrorMessages.message("module.access.does.not.read", packageName, targetName, useName),
            listOf(AddRequiresDirectiveFix(useModule, targetName)))
          else -> ErrorWithFixes(JavaErrorMessages.message("module.access.bad.name", packageName, targetName))
        }
      }
    }
    else if (useModule != null) {
      return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("module.access.to.unnamed", packageName, useModule.name))
    }

    return null
  }

  private fun inAddedExports(module: Module, targetName: String, packageName: String, useName: String): Boolean {
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module)
    if (options.isEmpty()) return false
    val prefix = "${targetName}/${packageName}="
    return optionValues(options, "--add-exports")
      .filter { it.startsWith(prefix) }
      .map { it.substring(prefix.length) }
      .flatMap { it.splitToSequence(",") }
      .any { it == useName }
  }

  private fun inAddedModules(module: Module, moduleName: String): Boolean {
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module)
    return optionValues(options, "--add-modules")
      .flatMap { it.splitToSequence(",") }
      .any { it == moduleName || it == "ALL-SYSTEM" }
  }

  private fun optionValues(options: List<String>, name: String) =
    if (options.isEmpty()) emptySequence()
    else {
      var useValue = false
      options.asSequence()
        .map {
          when {
            it == name -> { useValue = true; "" }
            useValue -> { useValue = false; it }
            it.startsWith(name) && it[name.length] == '=' -> it.substring(name.length + 1)
            else -> ""
          }
        }
        .filterNot { it.isEmpty() }
    }


  private abstract class CompilerOptionFix(private val module: Module) : IntentionAction {
    override fun getFamilyName() = "dfd4a2c1-da18-4651-9aa8-d7d31cae10be" // random string; never visible

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = !module.isDisposed

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      if (isAvailable(project, editor, file)) {
        val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module).toMutableList()
        update(options)
        JavaCompilerConfigurationProxy.setAdditionalOptions(module.project, module, options)
        PsiManager.getInstance(project).dropPsiCaches()
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    }

    protected abstract fun update(options: MutableList<String>)

    override fun startInWriteAction() = true
  }

  private class AddExportsOptionFix(module: Module, targetName: String, packageName: String, private val useName: String) : CompilerOptionFix(module) {
    private val qualifier = targetName + '/' + packageName

    override fun getText() = QuickFixBundle.message("add.compiler.option.fix.name", "--add-exports=${qualifier}=${useName}")

    override fun update(options: MutableList<String>) {
      var idx = -1; var candidate = -1; var offset = 0
      for ((i, option) in options.withIndex()) {
        if (option.startsWith("--add-exports")) {
          if (option.length == 13) { candidate = i + 1 ; offset = 0 }
          else if (option[13] == '=') { candidate = i; offset = 14 }
        }
        if (i == candidate && option.startsWith(qualifier, offset)) {
          val qualifierEnd = qualifier.length + offset
          if (option.length == qualifierEnd || option[qualifierEnd] == '=') {
            idx = i
          }
        }
      }
      when {
        idx == -1 -> options += "--add-exports=${qualifier}=${useName}"
        candidate == options.size -> options[idx - 1] = "--add-exports=${qualifier}=${useName}"
        else -> {
          val value = options[idx]
          options[idx] = if (value.endsWith('=') || value.endsWith(',')) value + useName else value + ',' + useName
        }
      }
    }
  }

  private class AddModulesOptionFix(module: Module, private val moduleName: String) : CompilerOptionFix(module) {
    override fun getText() = QuickFixBundle.message("add.compiler.option.fix.name", "--add-modules=${moduleName}")

    override fun update(options: MutableList<String>) {
      var idx = -1
      for ((i, option) in options.withIndex()) {
        if (option.startsWith("--add-modules")) {
          if (option.length == 13) idx = i + 1
          else if (option[13] == '=') idx = i
        }
      }
      when (idx) {
        -1 -> options += "--add-modules=${moduleName}"
        options.size -> options[idx - 1] = "--add-modules=${moduleName}"
        else -> {
          val value = options[idx]
          options[idx] = if (value.endsWith('=') || value.endsWith(',')) value + moduleName else value + ',' + moduleName
        }
      }
    }
  }
}