// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl

import com.intellij.codeInsight.JavaModuleSystemEx
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.NonNls

/**
 * Checks package accessibility according to JLS 7 "Packages and Modules".
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html">JLS 7 "Packages and Modules"</a>
 * @see <a href="http://openjdk.java.net/jeps/261">JEP 261: Module System</a>
 */
class JavaPlatformModuleSystem : JavaModuleSystemEx {
  override fun getName(): String = JavaBundle.message("java.platform.module.system.name")

  override fun isAccessible(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): Boolean =
    checkAccess(targetPackageName, targetFile?.originalFile, place, quick = true) == null

  override fun checkAccess(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): ErrorWithFixes? =
    checkAccess(targetPackageName, targetFile?.originalFile, place, quick = false)

  private fun checkAccess(targetPackageName: String, targetFile: PsiFile?, place: PsiElement, quick: Boolean): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile
    if (useFile != null && PsiUtil.isLanguageLevel9OrHigher(useFile)) {
      val useVFile = useFile.virtualFile
      val index = ProjectFileIndex.getInstance(useFile.project)
      if (useVFile == null || !index.isInLibrarySource(useVFile)) {
        if (targetFile != null && targetFile.isPhysical) {
          return checkAccess(targetFile, useFile, targetPackageName, quick)
        }
        else if (useVFile != null) {
          val target = JavaPsiFacade.getInstance(useFile.project).findPackage(targetPackageName)
          if (target != null) {
            val module = index.getModuleForFile(useVFile)
            if (module != null) {
              val test = index.isInTestSourceContent(useVFile)
              val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(test)
              val dirs = target.getDirectories(moduleScope)
              if (dirs.isEmpty()) {
                if (target.getFiles(moduleScope).isEmpty()) {
                  return if (quick) ERR else ErrorWithFixes(JavaErrorBundle.message("package.not.found", target.qualifiedName))
                }
                else {
                  return null
                }
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
      }
    }

    return null
  }

  private val ERR = ErrorWithFixes("-")

  private fun checkAccess(target: PsiFileSystemItem, place: PsiFileSystemItem, packageName: String, quick: Boolean): ErrorWithFixes? {
    val targetModule = JavaModuleGraphUtil.findDescriptorByElement(target)
    val useModule = JavaModuleGraphUtil.findDescriptorByElement(place)

    if (targetModule != null) {
      if (targetModule == useModule) {
        return null
      }

      val targetName = targetModule.name
      val useName = useModule?.name ?: "ALL-UNNAMED"
      val module = place.virtualFile?.let { ProjectFileIndex.getInstance(place.project).getModuleForFile(it) }

      if (useModule == null) {
        val origin = targetModule.containingFile?.virtualFile
        if (origin == null || module == null || ModuleRootManager.getInstance(module).fileIndex.getOrderEntryForFile(origin) !is JdkOrderEntry) {
          return null  // a target is not on the mandatory module path
        }

        var isRoot = !targetName.startsWith("java.") || inAddedModules(module, targetName) || hasUpgrade(module, targetName, packageName, place)
        if (!isRoot) {
          val root = JavaPsiFacade.getInstance(place.project).findModule("java.se", module.moduleWithLibrariesScope)
          isRoot = root == null || JavaModuleGraphUtil.reads(root, targetModule)
        }
        if (!isRoot) {
          return if (quick) ERR else ErrorWithFixes(
            JavaErrorBundle.message("module.access.not.in.graph", packageName, targetName),
            listOf(AddModulesOptionFix(module, targetName)))
        }
      }

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
          null -> ErrorWithFixes(JavaErrorBundle.message("module.access.from.unnamed", packageName, targetName), fixes)
          else -> ErrorWithFixes(JavaErrorBundle.message("module.access.from.named", packageName, targetName, useName), fixes)
        }
      }

      if (useModule != null && !(targetName == PsiJavaModule.JAVA_BASE || JavaModuleGraphUtil.reads(useModule, targetModule))) {
        return when {
          quick -> ERR
          PsiNameHelper.isValidModuleName(targetName, useModule) -> ErrorWithFixes(
            JavaErrorBundle.message("module.access.does.not.read", packageName, targetName, useName),
            listOf(AddRequiresDirectiveFix(useModule, targetName)))
          else -> ErrorWithFixes(JavaErrorBundle.message("module.access.bad.name", packageName, targetName))
        }
      }
    }
    else if (useModule != null) {
      return if (quick) ERR else ErrorWithFixes(JavaErrorBundle.message("module.access.to.unnamed", packageName, useModule.name))
    }

    return null
  }

  private fun hasUpgrade(module: Module, targetName: String, packageName: String, place: PsiFileSystemItem): Boolean {
    if (PsiJavaModule.UPGRADEABLE.contains(targetName)) {
      val target = JavaPsiFacade.getInstance(module.project).findPackage(packageName)
      if (target != null) {
        val useVFile = place.virtualFile
        if (useVFile != null) {
          val index = ModuleRootManager.getInstance(module).fileIndex
          val test = index.isInTestSourceContent(useVFile)
          val dirs = target.getDirectories(module.getModuleWithDependenciesAndLibrariesScope(test))
          return dirs.any { index.getOrderEntryForFile(it.virtualFile) !is JdkOrderEntry }
        }
      }
    }

    return false
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
      .any { it == moduleName || it == "ALL-SYSTEM" || it == "ALL-MODULE-PATH" }
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
    @NonNls override fun getFamilyName() = "Fix compiler option" // not visible

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

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = null

    override fun startInWriteAction() = true
  }

  private class AddExportsOptionFix(module: Module, targetName: String, packageName: String, private val useName: String) : CompilerOptionFix(module) {
    private val qualifier = "${targetName}/${packageName}"

    override fun getText() = QuickFixBundle.message("add.compiler.option.fix.name", "--add-exports ${qualifier}=${useName}")

    override fun update(options: MutableList<String>) {
      var idx = -1; var candidate = -1; var offset = 0
      val prefix = "--add-exports"
      for ((i, option) in options.withIndex()) {
        if (option.startsWith(prefix)) {
          if (option.length == prefix.length) { candidate = i + 1 ; offset = 0 }
          else if (option[prefix.length] == '=') { candidate = i; offset = prefix.length + 1 }
        }
        if (i == candidate && option.startsWith(qualifier, offset)) {
          val qualifierEnd = qualifier.length + offset
          if (option.length == qualifierEnd || option[qualifierEnd] == '=') {
            idx = i
          }
        }
      }
      when (idx) {
        -1 -> options += listOf(prefix, "${qualifier}=${useName}")
        else -> options[idx] = "${options[idx].trimEnd(',')},${useName}"
      }
    }
  }

  private class AddModulesOptionFix(module: Module, private val moduleName: String) : CompilerOptionFix(module) {
    override fun getText() = QuickFixBundle.message("add.compiler.option.fix.name", "--add-modules ${moduleName}")

    override fun update(options: MutableList<String>) {
      var idx = -1
      val prefix = "--add-modules"
      for ((i, option) in options.withIndex()) {
        if (option.startsWith(prefix)) {
          if (option.length == prefix.length) idx = i + 1
          else if (option[prefix.length] == '=') idx = i
        }
      }
      when (idx) {
        -1 -> options += listOf(prefix, moduleName)
        options.size -> options += moduleName
        else -> {
          val value = options[idx]
          options[idx] = if (value.endsWith('=') || value.endsWith(',')) value + moduleName else "${value},${moduleName}"
        }
      }
    }
  }
}
