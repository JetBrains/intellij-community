// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ide.projectWizard.generators

import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.icon
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shortcut
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shouldRenderOnboardingTips
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.javaData
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import com.intellij.pom.java.JavaFeature
import org.jetbrains.annotations.ApiStatus
import java.util.*

private const val DEFAULT_FILE_NAME = "Main.java"
private const val DEFAULT_TEMPLATE_WITH_ONBOARDING_TIPS_NAME = "SampleCodeWithOnboardingTips.java"
private const val DEFAULT_TEMPLATE_WITH_RENDERED_ONBOARDING_TIPS_NAME = "SampleCodeWithRenderedOnboardingTips.java"

private const val DEFAULT_TEMPLATE_WITH_ONBOARDING_TIPS_NAME_INSTANCE_MAIN = "SampleCodeWithOnboardingTipsInstanceMain.java"
private const val DEFAULT_TEMPLATE_WITH_RENDERED_ONBOARDING_TIPS_NAME_INSTANCE_MAIN = "SampleCodeWithRenderedOnboardingTipsInstanceMain.java"

object AssetsJava {

  @Deprecated("The onboarding tips generated unconditionally")
  fun getJavaSampleTemplateName(generateOnboardingTips: Boolean): String =
    getJavaSampleTemplateName()

  @ApiStatus.Internal
  fun getJavaSampleTemplateName(projectWizardStep: AssetsNewProjectWizardStep?): String {
    val intent = projectWizardStep?.javaData?.jdkIntent
    val minimumLevel = JavaFeature.JAVA_LANG_IO.minimumLevel
    if (intent != null && intent.isAtLeast(minimumLevel.feature(), true)) {
      //use compact source file
      return when (shouldRenderOnboardingTips()) {
        true -> DEFAULT_TEMPLATE_WITH_RENDERED_ONBOARDING_TIPS_NAME_INSTANCE_MAIN
        else -> DEFAULT_TEMPLATE_WITH_ONBOARDING_TIPS_NAME_INSTANCE_MAIN
      }
    }
    else {
      return getJavaSampleTemplateName()
    }
  }

  @ApiStatus.Internal
  fun getJavaSampleTemplateName(): String {
    return when (shouldRenderOnboardingTips()) {
      true -> DEFAULT_TEMPLATE_WITH_RENDERED_ONBOARDING_TIPS_NAME
      else -> DEFAULT_TEMPLATE_WITH_ONBOARDING_TIPS_NAME
    }
  }

  fun getJavaSampleSourcePath(sourceRootPath: String, packageName: String?, fileName: String): String {
    val pathJoiner = StringJoiner("/")
    pathJoiner.add(sourceRootPath)
    if (packageName != null) {
      pathJoiner.add(packageName.replace('.', '/'))
    }
    pathJoiner.add(fileName)
    return pathJoiner.toString()
  }

  @ApiStatus.Internal
  fun prepareJavaSampleOnboardingTips(project: Project, fileName: String) {
    AssetsOnboardingTips.prepareOnboardingTips(project, fileName) { charSequence ->
      charSequence.indexOf("System.out.println").takeIf { it >= 0 }
    }
  }
}

@Deprecated("The onboarding tips generated unconditionally")
fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(sourceRootPath: String, packageName: String?, generateOnboardingTips: Boolean) {
  val templateName = AssetsJava.getJavaSampleTemplateName()
  val sourcePath = AssetsJava.getJavaSampleSourcePath(sourceRootPath, packageName, DEFAULT_FILE_NAME)
  withJavaSampleCodeAsset(sourcePath, packageName, templateName)
}

@Deprecated("The onboarding tips generated unconditionally")
fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(sourcePath: String, templateName: String, packageName: String?, generateOnboardingTips: Boolean): Unit =
  withJavaSampleCodeAsset(sourcePath, packageName, templateName)

fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(
  project: Project,
  sourceRootPath: String,
  packageName: String? = null,
  fileName: String = DEFAULT_FILE_NAME,
  templateName: String = AssetsJava.getJavaSampleTemplateName(this),
) {
  val sourcePath = AssetsJava.getJavaSampleSourcePath(sourceRootPath, packageName, fileName)
  AssetsJava.prepareJavaSampleOnboardingTips(project, fileName)
  withJavaSampleCodeAsset(sourcePath, packageName, templateName)
}

private fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(
  sourcePath: String,
  packageName: String?,
  templateName: String,
) {
  addTemplateAsset(sourcePath, templateName, buildMap {
    if (packageName != null) {
      put("PACKAGE_NAME", packageName)
    }
    val tipsContext = object : KeymapTextContext() {
      override fun isSimplifiedMacShortcuts(): Boolean = ClientSystemInfo.isMac()
    }
    if (shouldRenderOnboardingTips()) {
      //@formatter:off
      put("RunComment1", JavaStartersBundle.message("onboarding.run.comment.render.1", shortcut(IdeActions.ACTION_DEFAULT_RUNNER)))
      put("RunComment2", JavaStartersBundle.message("onboarding.run.comment.render.2", icon("AllIcons.Actions.Execute")))

      put("ShowIntentionComment1", JavaStartersBundle.message("onboarding.show.intention.tip.comment.render.1", shortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)))
      put("ShowIntentionComment2", JavaStartersBundle.message("onboarding.show.intention.tip.comment.render.2", ApplicationNamesInfo.getInstance().fullProductName))

      put("DebugComment1", JavaStartersBundle.message("onboarding.debug.comment.render.1", shortcut(IdeActions.ACTION_DEFAULT_DEBUGGER), icon("AllIcons.Debugger.Db_set_breakpoint")))
      put("DebugComment2", JavaStartersBundle.message("onboarding.debug.comment.render.2", shortcut(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)))
      //@formatter:on
    }
    else {
      //@formatter:off
      put("SearchEverywhereComment1", JavaStartersBundle.message("onboarding.search.everywhere.tip.comment.1", "Shift"))
      put("SearchEverywhereComment2", JavaStartersBundle.message("onboarding.search.everywhere.tip.comment.2"))

      put("ShowIntentionComment1", JavaStartersBundle.message("onboarding.show.intention.tip.comment.1", tipsContext.getShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)))
      put("ShowIntentionComment2", JavaStartersBundle.message("onboarding.show.intention.tip.comment.2", ApplicationNamesInfo.getInstance().fullProductName))

      put("RunComment", JavaStartersBundle.message("onboarding.run.comment", tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_RUNNER)))

      put("DebugComment1", JavaStartersBundle.message("onboarding.debug.comment.1", tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_DEBUGGER)))
      put("DebugComment2", JavaStartersBundle.message("onboarding.debug.comment.2", tipsContext.getShortcutText(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)))
      //@formatter:on
    }
  })
  addFilesToOpen(sourcePath)
}

@Deprecated("The onboarding tips are prepared in the withJavaSampleCodeAsset function")
fun AssetsNewProjectWizardStep.prepareJavaSampleOnboardingTips(project: Project): Unit =
  AssetsJava.prepareJavaSampleOnboardingTips(project, DEFAULT_FILE_NAME)