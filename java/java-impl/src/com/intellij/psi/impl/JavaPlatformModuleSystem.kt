// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.codeInsight.JavaModuleSystemEx
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes
import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.vmModules.VmModulesService
import com.intellij.java.JavaBundle
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.JavaModuleSystem.*
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.annotations.NonNls
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private const val MAIN = "main"
private val javaVersionPattern: Pattern by lazy { Pattern.compile("java\\d+") }

/**
 * Checks package accessibility according to JLS 7 "Packages and Modules".
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html">JLS 7 "Packages and Modules"</a>
 * @see <a href="http://openjdk.org/jeps/261">JEP 261: Module System</a>
 */
internal class JavaPlatformModuleSystem : JavaModuleSystemEx {
  override fun getName(): String = JavaBundle.message("java.platform.module.system.name")

  override fun isAccessible(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): Boolean {
    return getProblem(targetPackageName, targetFile, place, true, this::isExported) == null
  }

  override fun checkAccess(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): ErrorWithFixes? {
    return getProblem(targetPackageName, targetFile, place, false) { use, _, target, _, _ -> JavaModuleGraphUtil.reads(use, target) }
  }

  private fun isExported(useModule: PsiJavaModule, packageName: String, targetModule: PsiJavaModule, useModuleName: String, module: Module?): Boolean {
    if (!targetModule.isPhysical || JavaModuleGraphUtil.exports(targetModule, packageName, useModule)) return true
    if (module == null) return false
    return inAddedExports(module, targetModule.name, packageName, useModuleName)
  }

  private fun getProblem(targetPackageName: String, targetFile: PsiFile?, place: PsiElement, quick: Boolean,
                         isAccessible: (useModule: PsiJavaModule, packageName: String, targetModule: PsiJavaModule, useModuleName: String, module: Module?) -> Boolean): ErrorWithFixes? {
    val originalTargetFile = targetFile?.originalFile
    val useFile = place.containingFile?.originalFile ?: return null
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, useFile)) return null

    val useVFile = useFile.virtualFile
    val index = ProjectFileIndex.getInstance(useFile.project)
    if (useVFile != null && index.isInLibrarySource(useVFile)) return null
    if (originalTargetFile != null && originalTargetFile.isPhysical) {
      return checkAccess(originalTargetFile, useFile, targetPackageName, quick, isAccessible)
    }
    if (useVFile == null) return null

    val target = JavaPsiFacade.getInstance(useFile.project).findPackage(targetPackageName) ?: return null
    val module = index.getModuleForFile(useVFile) ?: return null
    val test = index.isInTestSourceContent(useVFile)
    val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(test)
    val dirs = target.getDirectories(moduleScope)
    if (dirs.isEmpty()) {
      return if (target.getFiles(moduleScope).isEmpty()) {
        ErrorWithFixes(JavaErrorBundle.message("package.not.found", target.qualifiedName))
      }
      else {
        null
      }
    }

    val error = checkAccess(dirs[0], useFile, target.qualifiedName, quick, isAccessible) ?: return null
    return when {
      dirs.size == 1 -> error
      dirs.asSequence().drop(1).any { checkAccess(it, useFile, target.qualifiedName, true, isAccessible) == null } -> null
      else -> error
    }
  }

  private val ERR = ErrorWithFixes("-")

  private fun checkAccess(target: PsiFileSystemItem, place: PsiFileSystemItem, packageName: String, quick: Boolean,
                          isAccessible: (useModule: PsiJavaModule, packageName: String, targetModule: PsiJavaModule, useModuleName: String, module: Module?) -> Boolean): ErrorWithFixes? {
    val targetModule = JavaModuleGraphUtil.findDescriptorByElement(target)
    val useModule = JavaModuleGraphUtil.findDescriptorByElement(place).let { if (it is LightJavaModule) null else it }
    val module = place.virtualFile?.let { ProjectFileIndex.getInstance(place.project).getModuleForFile(it) }

    if (targetModule != null) {
      if (targetModule == useModule) {
        return null
      }

      val targetName = targetModule.name
      val useName = useModule?.name ?: ALL_UNNAMED

      if (useModule == null) {
        val origin = targetModule.containingFile?.virtualFile
        if (origin == null || module == null ||
            ModuleRootManager.getInstance(module).fileIndex.getOrderEntryForFile(origin) !is JdkOrderEntry) {
          return null  // a target is not on the mandatory module path
        }

        if (targetName.startsWith("java.") &&
            targetName != PsiJavaModule.JAVA_BASE &&
            !inAddedModules(module, targetName) &&
            !hasUpgrade(module, targetName, packageName, place) &&
            !accessibleFromLoadedModules(module, targetName, place, isAccessible, packageName, targetModule, useName)) {
          return if (quick) ERR
          else ErrorWithFixes(
            JavaErrorBundle.message("module.access.not.in.graph", packageName, targetName),
            listOf(AddModulesOptionFix(module, targetName).asIntention()))
        }
      }

      if (targetModule !is LightJavaModule &&
          !JavaModuleGraphUtil.exports(targetModule, packageName, useModule) &&
          (module == null || !inAddedExports(module, targetName, packageName, useName)) &&
          (module == null || !isPatchedModule(targetName, module, place))) {
        if (quick) return ERR
        val fixes = when {
          packageName.isEmpty() -> emptyList()
          targetModule is PsiCompiledElement && module != null ->
            listOf(AddExportsOptionFix(module, targetName, packageName, useName).asIntention())
          targetModule !is PsiCompiledElement && useModule != null ->
            listOf(AddExportsDirectiveFix(targetModule, packageName, useName).asIntention())
          else -> emptyList()
        }
        return when (useModule) {
          null -> ErrorWithFixes(JavaErrorBundle.message("module.access.from.unnamed", packageName, targetName), fixes)
          else -> ErrorWithFixes(JavaErrorBundle.message("module.access.from.named", packageName, targetName, useName), fixes)
        }
      }

      if (useModule != null &&
          targetName != PsiJavaModule.JAVA_BASE &&
          !isAccessible(useModule, packageName, targetModule, useName, module) &&
          !inAddedReads(useModule, targetModule)) {
        return when {
          quick -> ERR
          PsiNameHelper.isValidModuleName(targetName, useModule) -> ErrorWithFixes(
            JavaErrorBundle.message("module.access.does.not.read", packageName, targetName, useName),
            listOf(AddRequiresDirectiveFix(useModule, targetName).asIntention()))
          else -> ErrorWithFixes(JavaErrorBundle.message("module.access.bad.name", packageName, targetName))
        }
      }
    }
    else if (useModule != null) {
      val autoModule = detectAutomaticModule(target)
      if ((autoModule == null) || ((!isAccessible(useModule, packageName, autoModule, useModule.name, module) && !inAddedReads(useModule, null)) &&
                                   !inSameMultiReleaseModule(place, target))
      ) {
        return if (quick) ERR else ErrorWithFixes(JavaErrorBundle.message("module.access.to.unnamed", packageName, useModule.name))
      }
    }

    return null
  }

  private fun accessibleFromLoadedModules(module: Module,
                                          targetName: String,
                                          place: PsiFileSystemItem,
                                          isAccessible: (useModule: PsiJavaModule, packageName: String, targetModule: PsiJavaModule, useModuleName: String, module: Module?) -> Boolean,
                                          packageName: String,
                                          targetModule: PsiJavaModule,
                                          useName: String): Boolean {
    val modules = getLoadedModules(module)
    if (!modules.isEmpty()) {
      return modules.contains(targetName)
    }
    else {
      val root = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode<PsiJavaModule, Throwable> {
        JavaPsiFacade.getInstance(place.project).findModule("java.se", module.moduleWithLibrariesScope)
      }
      return root == null || isAccessible(root, packageName, targetModule, useName, module)
    }
  }

  private fun getLoadedModules(module: Module): List<String> {
    val sdk = ModuleRootManager.getInstance(module).sdk ?: return listOf()
    if (sdk.sdkType is JavaSdk) {
      val sdkHome = sdk.homePath ?: return listOf()
      try {
        val modules = VmModulesService.getInstance().getOrComputeModulesForJdk(sdkHome)
        return runBlockingCancellable {
          withBackgroundProgress(module.project, JavaBundle.message("load.modules.from.jdk")) {
            modules.get(1, TimeUnit.SECONDS) ?: listOf()
          }
        }
      }
      catch (ignore: Exception) {
      }
    }
    return listOf()
  }

  private fun inSameMultiReleaseModule(place: PsiElement, target: PsiElement): Boolean {
    val placeModule = ModuleUtilCore.findModuleForPsiElement(place) ?: return false
    val targetModule = ModuleUtilCore.findModuleForPsiElement(target) ?: return false
    if (targetModule.name.endsWith(".$MAIN")) {
      val baseModuleName = targetModule.name.substringBeforeLast(MAIN)
      return javaVersionPattern.matcher(placeModule.name.substringAfter(baseModuleName)).matches()
    }
    return false
  }

  private fun detectAutomaticModule(target: PsiFileSystemItem): PsiJavaModule? {
    val project = target.project
    val m = ProjectFileIndex.getInstance(project).getModuleForFile(target.virtualFile) ?: return null
    return JavaPsiFacade.getInstance(project).findModule(LightJavaModule.moduleName(m.name), GlobalSearchScope.moduleScope(m))
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

  private fun isPatchedModule(targetModuleName: String, module: Module, place: PsiFileSystemItem): Boolean {
    val rootForFile = ProjectRootManager.getInstance(place.project).fileIndex.getSourceRootForFile(place.virtualFile)
    return rootForFile != null && JavaCompilerConfigurationProxy.isPatchedModuleRoot(targetModuleName, module, rootForFile)
  }

  private fun inAddedExports(module: Module, targetName: String, packageName: String, useName: String): Boolean {
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module)
    if (options.isEmpty()) return false
    val prefix = "${targetName}/${packageName}="
    return JavaCompilerConfigurationProxy.optionValues(options, ADD_EXPORTS_OPTION)
      .filter { it.startsWith(prefix) }
      .map { it.substring(prefix.length) }
      .flatMap { it.splitToSequence(",") }
      .any { it == useName }
  }

  private fun inAddedModules(module: Module, moduleName: String): Boolean {
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module)
    return JavaCompilerConfigurationProxy.optionValues(options, ADD_MODULES_OPTION)
      .flatMap { it.splitToSequence(",") }
      .any { it == moduleName || it == ALL_SYSTEM || it == ALL_MODULE_PATH }
  }

  private fun inAddedReads(fromJavaModule: PsiJavaModule, toJavaModule: PsiJavaModule?): Boolean {
    val fromModule = ModuleUtilCore.findModuleForPsiElement(fromJavaModule) ?: return false
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(fromModule.project, fromModule)
    return JavaCompilerConfigurationProxy.optionValues(options, ADD_READS_OPTION)
      .flatMap { it.splitToSequence(",") }
      .any {
        val (optFromModuleName, optToModuleName) = it.split("=").apply { it.first() to it.last() }
        fromJavaModule.name == optFromModuleName &&
        (toJavaModule?.name == optToModuleName || (optToModuleName == ALL_UNNAMED && isUnnamedModule(toJavaModule)))
      }
  }

  private fun isUnnamedModule(module: PsiJavaModule?) = module == null || module is LightJavaModule

  private abstract class CompilerOptionFix(private val module: Module) : ModCommandAction {
    @NonNls
    override fun getFamilyName() = "Fix compiler option" // not visible

    override fun getPresentation(context: ActionContext): Presentation? {
      if (module.isDisposed) return null
      return Presentation.of(getText())
    }

    override fun perform(context: ActionContext): ModCommand {
      return ModCommand.updateOptionList(context.file, "JavaCompilerConfiguration.additionalOptions", ::update)
    }

    protected abstract fun update(options: MutableList<String>)

    @IntentionName
    protected abstract fun getText(): String
  }

  private class AddExportsOptionFix(module: Module,
                                    targetName: String,
                                    packageName: String,
                                    private val useName: String) : CompilerOptionFix(module) {
    private val qualifier = "${targetName}/${packageName}"

    override fun getText() = QuickFixBundle.message("add.compiler.option.fix.name", "${ADD_EXPORTS_OPTION} ${qualifier}=${useName}")

    override fun update(options: MutableList<String>) {
      var idx = -1
      var candidate = -1
      var offset = 0
      for ((i, option) in options.withIndex()) {
        if (option.startsWith(ADD_EXPORTS_OPTION)) {
          if (option.length == ADD_EXPORTS_OPTION.length) {
            candidate = i + 1; offset = 0
          }
          else if (option[ADD_EXPORTS_OPTION.length] == '=') {
            candidate = i; offset = ADD_EXPORTS_OPTION.length + 1
          }
        }
        if (i == candidate && option.startsWith(qualifier, offset)) {
          val qualifierEnd = qualifier.length + offset
          if (option.length == qualifierEnd || option[qualifierEnd] == '=') {
            idx = i
          }
        }
      }
      when (idx) {
        -1 -> options += listOf(ADD_EXPORTS_OPTION, "${qualifier}=${useName}")
        else -> options[idx] = "${options[idx].trimEnd(',')},${useName}"
      }
    }
  }

  private class AddModulesOptionFix(module: Module, private val moduleName: String) : CompilerOptionFix(module) {
    override fun getText() = QuickFixBundle.message("add.compiler.option.fix.name", "${ADD_MODULES_OPTION} ${moduleName}")

    override fun update(options: MutableList<String>) {
      var idx = -1
      for ((i, option) in options.withIndex()) {
        if (option.startsWith(ADD_MODULES_OPTION)) {
          if (option.length == ADD_MODULES_OPTION.length) idx = i + 1
          else if (option[ADD_MODULES_OPTION.length] == '=') idx = i
        }
      }
      when (idx) {
        -1 -> options += listOf(ADD_MODULES_OPTION, moduleName)
        options.size -> options += moduleName
        else -> {
          val value = options[idx]
          options[idx] = if (value.endsWith('=') || value.endsWith(',')) value + moduleName else "${value},${moduleName}"
        }
      }
    }
  }
}