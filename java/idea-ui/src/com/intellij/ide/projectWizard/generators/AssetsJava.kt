// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ide.projectWizard.generators

import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.icon
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shortcut
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shouldRenderOnboardingTips
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.*

private const val DEFAULT_FILE_NAME = "Main.java"

object AssetsJava {

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
}

fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(
  sourceRootPath: String,
  aPackage: String,
  generateOnboardingTips: Boolean,
) {
  val templateName = when {
    !generateOnboardingTips -> "SampleCode"
    shouldRenderOnboardingTips() -> "SampleCodeWithRenderedOnboardingTips.java"
    else -> "SampleCodeWithOnboardingTips.java"
  }

  val sourcePath = AssetsJava.createJavaSourcePath(sourceRootPath, aPackage, DEFAULT_FILE_NAME)
  withJavaSampleCodeAsset(sourcePath, templateName, aPackage, generateOnboardingTips)
}

fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(
  sourcePath: String,
  templateName: String,
  aPackage: String,
  generateOnboardingTips: Boolean,
) {
  addTemplateAsset(sourcePath, templateName, buildMap {
    put("PACKAGE_NAME", aPackage)
    if (generateOnboardingTips) {
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
    }
  })
  addFilesToOpen(sourcePath)
}

fun AssetsNewProjectWizardStep.prepareJavaSampleOnboardingTips(project: Project) {
  prepareOnboardingTips(project, "SampleCode", DEFAULT_FILE_NAME) { charSequence ->
    charSequence.indexOf("System.out.println").takeIf { it >= 0 }
  }
}