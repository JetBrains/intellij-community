// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInspection.util.IntentionName
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiPackageAccessibilityStatement
import com.intellij.psi.util.PsiUtil

public abstract class AddModuleDirectiveFix(module: PsiJavaModule) : PsiUpdateModCommandAction<PsiJavaModule>(module) {
  override fun getFamilyName(): String = QuickFixBundle.message("module.info.add.directive.family.name")

  @IntentionName
  public abstract fun getText(): String

  override fun getPresentation(context: ActionContext, module: PsiJavaModule): Presentation? {
    return if (PsiUtil.isAvailable(JavaFeature.MODULES, module)) Presentation.of(getText()) else null
  }
}

public class AddRequiresDirectiveFix(module: PsiJavaModule, private val requiredName: String) : AddModuleDirectiveFix(module) {
  override fun getText(): String {
    return QuickFixBundle.message("module.info.add.requires.name", requiredName)
  }

  override fun invoke(context: ActionContext, module: PsiJavaModule, updater: ModPsiUpdater) {
    JavaModuleGraphUtil.addDependency(module, requiredName, null, false)
  }
}

public class AddExportsDirectiveFix(
  module: PsiJavaModule,
  private val packageName: String,
  targetName: String
) : AddPackageAccessibilityFix(JavaKeywords.EXPORTS, module, packageName, targetName) {
  override fun getText(): String = QuickFixBundle.message("module.info.add.exports.name", packageName)

  override fun invoke(context: ActionContext, module: PsiJavaModule, updater: ModPsiUpdater) {
    addPackageAccessibility(context.project, module, module.exports)
  }
}

public class AddOpensDirectiveFix(
  module: PsiJavaModule,
  private val packageName: String,
  targetName: String
) : AddPackageAccessibilityFix(JavaKeywords.OPENS, module, packageName, targetName) {
  override fun getText(): String = QuickFixBundle.message("module.info.add.opens.name", packageName)

  override fun invoke(context: ActionContext, module: PsiJavaModule, updater: ModPsiUpdater) {
    addPackageAccessibility(context.project, module, module.opens)
  }
}

public abstract class AddPackageAccessibilityFix(
  private val directive: String,
  module: PsiJavaModule,
  private val packageName: String,
  private val targetName: String
) : AddModuleDirectiveFix(module) {
  protected fun addPackageAccessibility(
    project: Project,
    module: PsiJavaModule,
    accessibilityStatements: Iterable<PsiPackageAccessibilityStatement>
  ) {
    val existing = accessibilityStatements.find { packageName == it.packageName }
    if (existing == null) {
      PsiUtil.addModuleStatement(module, "$directive $packageName")
    }
    else if (!targetName.isEmpty()) {
      val targets = existing.moduleNames
      if (!targets.isEmpty() && targetName !in targets) {
        existing.add(PsiElementFactory.getInstance(project).createModuleReferenceFromText(targetName, null))
      }
    }
  }
}

public class AddUsesDirectiveFix(module: PsiJavaModule, private val svcName: String) : AddModuleDirectiveFix(module) {
  override fun getText(): String = QuickFixBundle.message("module.info.add.uses.name", svcName)

  override fun invoke(context: ActionContext, module: PsiJavaModule, updater: ModPsiUpdater) {
    if (module.uses.find { svcName == it.classReference?.qualifiedName } == null) {
      PsiUtil.addModuleStatement(module, JavaKeywords.USES + ' ' + svcName)
    }
  }
}