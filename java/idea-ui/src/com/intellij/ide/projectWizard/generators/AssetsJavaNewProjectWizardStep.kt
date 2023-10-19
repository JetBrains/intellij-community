// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.wizard.NewProjectOnboardingTips
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.OnboardingTipsInstallationInfo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
abstract class AssetsJavaNewProjectWizardStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {

  fun withJavaSampleCodeAsset(sourceRootPath: String, aPackage: String, generateOnboardingTips: Boolean) {
    val renderedOnboardingTips = Registry.`is`("doc.onboarding.tips.render")
    val templateName = when {
      !generateOnboardingTips -> "SampleCode"
      renderedOnboardingTips -> "SampleCodeWithRenderedOnboardingTips.java"
      else -> "SampleCodeWithOnboardingTips.java"
    }

    val sourcePath = createJavaSourcePath(sourceRootPath, aPackage, "Main.java")
    addTemplateAsset(sourcePath, templateName, buildMap {
      put("PACKAGE_NAME", aPackage)
      if (generateOnboardingTips) {
        val tipsContext = object : KeymapTextContext() {
          override fun isSimplifiedMacShortcuts(): Boolean = SystemInfo.isMac
        }
        if (renderedOnboardingTips) {
          fun rawShortcut(shortcut: String) = """<shortcut raw="$shortcut"/>"""
          fun shortcut(actionId: String) = """<shortcut actionId="$actionId"/>"""
          fun icon(allIconsId: String) = """<icon src="$allIconsId"/>"""

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
      }
    })
    addFilesToOpen(sourcePath)
  }

  fun prepareTipsInEditor(project: Project) {
    val templateManager = FileTemplateManager.getDefaultInstance()
    val properties = getTemplateProperties()
    val defaultProperties = templateManager.defaultProperties
    val template = templateManager.getInternalTemplate("SampleCode")
    val simpleSampleText = template.getText(defaultProperties + properties)
    for (extension in NewProjectOnboardingTips.EP_NAME.extensions) {
      extension.installTips(project, OnboardingTipsInstallationInfo(simpleSampleText) { text ->
        text.indexOf("System.out.println").takeIf { it >= 0 } ?: error("Cannot find place to install breakpoint")
      })
    }
  }

  companion object {

    fun createJavaSourcePath(sourceRootPath: String, aPackage: String, fileName: String): String {
      val packageDirectory = aPackage.replace('.', '/')
      val pathJoiner = StringJoiner("/")
      if (sourceRootPath.isNotEmpty()) {
        pathJoiner.add(sourceRootPath)
      }
      if (packageDirectory.isNotEmpty()) {
        pathJoiner.add(packageDirectory)
      }
      pathJoiner.add(fileName)
      return pathJoiner.toString()
    }

    fun proposeToGenerateOnboardingTipsByDefault(): Boolean {
      return RecentProjectsManagerBase.getInstanceEx().getRecentPaths().isEmpty()
    }
  }
}