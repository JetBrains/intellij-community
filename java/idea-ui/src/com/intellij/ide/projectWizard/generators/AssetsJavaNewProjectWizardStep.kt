// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.util.*

@ApiStatus.Experimental
abstract class AssetsJavaNewProjectWizardStep(parent: NewProjectWizardStep) : AssetsOnboardingTipsProjectWizardStep(parent) {
  fun withJavaSampleCodeAsset(sourceRootPath: String, aPackage: String, generateOnboardingTips: Boolean) {
    val renderedOnboardingTips = shouldRenderOnboardingTips()
    val templateName = when {
      !generateOnboardingTips -> "SampleCode"
      shouldRenderOnboardingTips() -> "SampleCodeWithRenderedOnboardingTips.java"
      else -> "SampleCodeWithOnboardingTips.java"
    }

    val sourcePath = createJavaSourcePath(sourceRootPath, aPackage, generatedFileName)
    addTemplateAsset(sourcePath, templateName, buildMap {
      put("PACKAGE_NAME", aPackage)
      if (generateOnboardingTips) {
        val tipsContext = object : KeymapTextContext() {
          override fun isSimplifiedMacShortcuts(): Boolean = SystemInfo.isMac
        }
        if (renderedOnboardingTips) {
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

  @ScheduledForRemoval
  @Deprecated("Use prepareOnboardingTips and it should be called before wizard project setup")
  fun prepareTipsInEditor(project: Project) { }

  fun prepareOnboardingTips(project: Project) {
    prepareOnboardingTips(project, "SampleCode", generatedFileName) { charSequence ->
      charSequence.indexOf("System.out.println").takeIf { it >= 0 }
    }
  }

  companion object {
    private const val generatedFileName = "Main.java"

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