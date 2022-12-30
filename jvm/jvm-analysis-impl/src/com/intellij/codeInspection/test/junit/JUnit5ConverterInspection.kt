// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.*
import com.intellij.codeInspection.actions.CleanupInspectionUtil
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.refactoring.RefactoringManager
import com.intellij.refactoring.migration.MigrationMap
import com.intellij.refactoring.migration.MigrationProcessor
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.siyeh.ig.junit.JUnitCommonClassNames
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.*

class JUnit5ConverterInspection : AbstractBaseUastLocalInspectionTool(UClass::class.java) {
  private fun shouldInspect(file: PsiFile): Boolean =
    JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)
    && isJUnit4InScope(file) && isJUnit5InScope(file)

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaPsi = aClass.javaPsi
    val framework = TestFrameworks.detectFramework(javaPsi)
    if (framework == null || "JUnit4" != framework.name) return emptyArray()
    if (!canBeConvertedToJUnit5(javaPsi)) return emptyArray()
    val identifier = aClass.uastAnchor.sourcePsiElement ?: return emptyArray()
    return arrayOf(manager.createProblemDescriptor(
      identifier, JvmAnalysisBundle.message("jvm.inspections.junit5.converter.problem.descriptor"),
      isOnTheFly, arrayOf(MigrateToJUnit5()), ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    ))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return super.buildVisitor(holder, isOnTheFly)
  }

  private class MigrateToJUnit5 : LocalQuickFix, BatchQuickFix {
    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.junit5.converter.quickfix")

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      descriptor.psiElement.toUElement()?.getParentOfType<UClass>(false)?.let { uClass ->
        RefactoringManager.getInstance(project).migrateManager.findMigrationMap("JUnit (4.x -> 5.0)")?.let { migrationMap ->
          uClass.getContainingUFile()?.let { JUnit5MigrationProcessor(project, migrationMap, setOf(it)).run() }
        }
      }
    }

    override fun applyFix(
      project: Project,
      descriptors: Array<out CommonProblemDescriptor>,
      psiElementsToIgnore: MutableList<PsiElement>,
      refreshViews: Runnable?
    ) {
      val files = descriptors.mapNotNull { descriptor ->
        (descriptor as ProblemDescriptor).psiElement?.toUElement()?.getContainingUFile()
      }.toSet()
      if (files.isNotEmpty()) {
        RefactoringManager.getInstance(project).migrateManager.findMigrationMap("JUnit (4.x -> 5.0)")?.let { migrationMap ->
          JUnit5MigrationProcessor(project, migrationMap, files).run()
          refreshViews?.run()
        }
      }
    }

    private class JUnit5MigrationProcessor(project: Project, migrationMap: MigrationMap, private val files: Set<UFile>)
      : MigrationProcessor(
      project,
      migrationMap,
      GlobalSearchScope.filesWithoutLibrariesScope(project, ContainerUtil.map(files) { it.sourcePsi.virtualFile })
    ) {
      private class DescriptionBasedUsageInfo(val descriptor: ProblemDescriptor) : UsageInfo(descriptor.psiElement)

      override fun findUsages(): Array<UsageInfo> {
        val usages = super.findUsages()
        val inspectionManager = InspectionManager.getInstance(myProject)
        val globalContext = inspectionManager.createNewGlobalContext()
        val assertionsConverter = LocalInspectionToolWrapper(JUnit5AssertionsConverterInspection("JUnit4"))
        val descriptors = files.flatMap { InspectionEngine.runInspectionOnFile(it.sourcePsi, assertionsConverter, globalContext) }
        val descriptionUsages = descriptors.map { DescriptionBasedUsageInfo(it) }.toTypedArray()
        return ArrayUtil.mergeArrays(usages, descriptionUsages)
      }

      override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
        files.forEach { file ->
          file.classes.forEach { psiClass ->
            val inheritors = mutableSetOf<PsiClass>()
            ClassInheritorsSearch.search(psiClass.javaPsi).forEach(Processor { inheritor ->
              if (!canBeConvertedToJUnit5(inheritor)) {
                inheritors.add(inheritor)
                false
              }
              else true
            })
            if (inheritors.isNotEmpty()) {
              val problem = JvmAnalysisBundle.message(
                "jvm.inspections.junit5.converter.quickfix.conflict.inheritor",
                RefactoringUIUtil.getDescription(psiClass.javaPsi, true),
                StringUtil.join(inheritors, { it.qualifiedName }, ", ")
              )
              conflicts.putValue(psiClass, problem)
            }
          }
        }
        isPreviewUsages = true
        return showConflicts(conflicts, refUsages.get())
      }

      override fun performRefactoring(usages: Array<out UsageInfo>) {
        val migrateUsages = mutableListOf<UsageInfo>()
        val descriptions = mutableListOf<ProblemDescriptor>()
        for (usage in usages) {
          when (usage) {
            is DescriptionBasedUsageInfo -> descriptions.add(usage.descriptor)
            else -> migrateUsages.add(usage)
          }
        }
        super.performRefactoring(migrateUsages.toTypedArray())
        CleanupInspectionUtil.getInstance().applyFixes(
          myProject,
          JvmAnalysisBundle.message("jvm.inspections.junit5.converter.quickfix.presentation.text"),
          descriptions,
          JUnit5AssertionsConverterInspection.ReplaceObsoleteAssertsFix::class.java,
          false
        )
      }
    }
  }

  companion object {
    fun canBeConvertedToJUnit5(aClass: PsiClass): Boolean {
      if (AnnotationUtil.isAnnotated(aClass, TestUtils.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY)) return false
      for (field in aClass.allFields) {
        if (AnnotationUtil.isAnnotated(field!!, ruleAnnotations, 0)) return false
      }
      for (method in aClass.methods) {
        if (AnnotationUtil.isAnnotated(method, ruleAnnotations, 0)) return false
        val testAnnotation = AnnotationUtil.findAnnotation(method, true, JUnitCommonClassNames.ORG_JUNIT_TEST)
        if (testAnnotation != null && testAnnotation.parameterList.attributes.isNotEmpty()) return false
      }
      return true
    }

    private val ruleAnnotations = listOf(JUnitCommonClassNames.ORG_JUNIT_RULE, JUnitCommonClassNames.ORG_JUNIT_CLASS_RULE)
  }
}