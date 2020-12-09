// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl

import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.impl.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk

@Service //project
class CompilerDriverUnknownSdkTracker(
  private val project: Project
) {
  companion object {
    val LOG = logger<CompilerDriverUnknownSdkTracker>()

    @JvmStatic
    fun getInstance(project: Project) = project.service<CompilerDriverUnknownSdkTracker>()
  }

  fun fixSdkSettings(updateProjectSdk: Boolean,
                     modules: List<Module>,
                     @NlsSafe formattedModulesList: String
  ): Outcome {
    if (!Registry.`is`("unknown.sdk.modal.jps")) return Outcome.CONTINUE_COMPILE

    return ProgressManager.getInstance()
      .run(object : Task.WithResult<Outcome, Exception>(project, ProjectBundle.message("progress.title.resolving.sdks"), true) {
        override fun compute(indicator: ProgressIndicator): Outcome = try {
          computeImpl(indicator)
        }
        catch (t: Throwable) {
          if (t is ControlFlowException) throw t
          LOG.warn("Failed to test for Unknown SDKs. ${t.message}", t)
          Outcome.CONTINUE_COMPILE
        }

        fun computeImpl(indicator: ProgressIndicator): Outcome {
          val collector = object : UnknownSdkCollector(project) {
            override fun checkProjectSdk(project: Project): Boolean = updateProjectSdk
            override fun collectModulesToCheckSdk(project: Project) = modules
          }

          val allActions = UnknownSdkTracker.getInstance(project).collectUnknownSdks(collector, indicator)
          if (allActions.isEmpty()) return Outcome.CONTINUE_COMPILE

          val actions = UnknownSdkTracker
            .getInstance(project)
            .applyAutoFixesAndNotify(allActions, indicator)

          if (actions.isEmpty()) return Outcome.CONTINUE_COMPILE
          return processManualFixes(modules, actions)
        }
      })
  }

  enum class Outcome {
    //we were able to apply a fix (probably not enough fixes)
    STOP_COMPILE,

    //we see no reasons to stop the standard logic
    CONTINUE_COMPILE,
  }

  fun processManualFixes(modules: List<Module>,
                         actions: List<UnknownSdkFix>): Outcome {
    val actionsWithFix = actions.mapNotNull { it.suggestedFixAction }
    val actionsWithoutFix = actions.filter { it.suggestedFixAction == null }

    if (actionsWithFix.isEmpty()) {
      //nothing to do. We fallback to the default behaviour because there is nothing we can do better
      return Outcome.CONTINUE_COMPILE
    }

    val message = HtmlBuilder()

    message.append(JavaCompilerBundle.message(
      "dialog.message.error.jdk.not.specified.with.fixSuggestion",
      modules.size,
      modules.size)
    )

    message.append(HtmlChunk.ul().children(actionsWithFix.sortedBy { it.actionDetailedText.toLowerCase() }.map { fix ->
      var li = HtmlChunk.li().addText(fix.actionDetailedText)
      fix.actionTooltipText?.let {
        li = li.addText(it)
      }
      li
    }))

    if (actionsWithoutFix.isNotEmpty()) {
      message.append(JavaCompilerBundle.message("dialog.message.error.jdk.not.specified.with.noFix"))
      message.append(HtmlChunk.ul().children(actionsWithoutFix.sortedBy { it.notificationText.toLowerCase() }.map { fix ->
        HtmlChunk.li().addText(fix.notificationText)
      }))
    }

    CompileDriverNotifications
      .getInstance(project)
      .createCannotStartNotification()
      .withContent(message.toString())
      .withExpiringAction(JavaCompilerBundle.message("dialog.message.action.apply.fix")) { applySuggestions(actionsWithFix) }
      .withOpenSettingsAction(modules.firstOrNull()?.name, null)
      .showNotification()

    return Outcome.STOP_COMPILE
  }

  private fun applySuggestions(suggestions: List<UnknownSdkFixAction>) {
    if (suggestions.isEmpty()) return

    val task = object : Task.Backgroundable(project, ProjectBundle.message("progress.title.resolving.sdks"), true) {
      override fun run(indicator: ProgressIndicator) {
        for (suggestion in suggestions) {
          try {
            indicator.withPushPop {
              suggestion.applySuggestionBlocking(indicator)
            }
          }
          catch (t: Throwable) {
            if (t is ControlFlowException) break
            LOG.warn("Failed to apply suggestion $suggestion. ${t.message}", t)
          }
        }
      }
    }

    ProgressManager.getInstance().run(task)
  }
}
