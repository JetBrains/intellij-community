// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import java.lang.Integer.min

class NewProjectWizardCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    private val GROUP = EventLogGroup("new.project.wizard.interactions", 1)

    private val sessionIdField = EventFields.Int("wizard_session_id")
    private val screenNumField = IntEventField("screen")
    private val typedCharsField = IntEventField("typed_chars")
    private val hitsField = IntEventField("hits")
    private val generatorTypeField = ClassEventField("generator")
    private val languageField = ClassEventField("language")
    private val gitField = EventFields.Boolean("git")
    private val isSucceededField = EventFields.Boolean("project_created")
    private val inputMaskField = EventFields.Long("input_mask")

    //events
    private val activity = GROUP.registerIdeActivity("new_project_wizard", finishEventAdditionalFields = arrayOf(isSucceededField))

    private val open = GROUP.registerEvent("wizard.dialog.open", sessionIdField)
    private val screen = GROUP.registerEvent("screen", sessionIdField, screenNumField)
    private val next = GROUP.registerEvent("navigate.next", sessionIdField, inputMaskField)
    private val prev = GROUP.registerEvent("navigate.prev", sessionIdField, inputMaskField)
    private val projectCreated = GROUP.registerEvent("project.created", sessionIdField)
    private val search = GROUP.registerEvent("search", sessionIdField, typedCharsField, hitsField)
    private val generator = GROUP.registerEvent("search", sessionIdField, generatorTypeField)
    private val location = GROUP.registerEvent("project.location.changed", sessionIdField, generatorTypeField)
    private val name = GROUP.registerEvent("project.name.changed", sessionIdField, generatorTypeField)
    private val languageSelected = GROUP.registerEvent("select.language", sessionIdField, languageField)
    private val gitChanged = GROUP.registerEvent("git.changed", sessionIdField)
    private val templateSelected = GROUP.registerEvent("select.custom.template", sessionIdField)
    private val helpNavigation = GROUP.registerEvent("navigate.help", sessionIdField)

    //finish events
    private val gitFinish = GROUP.registerEvent("create.git.repo", sessionIdField, gitField)
    private val generatorFinished = GROUP.registerEvent("generator.finished", sessionIdField, generatorTypeField)
    private val languageFinished = GROUP.registerEvent("language.finished", sessionIdField, languageField)

    //logs
    @JvmStatic fun logStarted(project: Project?) = activity.started(project)
    @JvmStatic fun logScreen(context: WizardContext, screenNumber: Int) = screen.log(context.project, context.sessionId.id, screenNumber)
    @JvmStatic fun logOpen(context: WizardContext) = open.log(context.project, context.sessionId.id)
    @JvmStatic fun logSearchChanged(context: WizardContext, chars: Int, results: Int) = search.log(context.project, context.sessionId.id, min(chars, 10), results)
    @JvmStatic fun logLocationChanged(context: WizardContext, generator: Class<*>) = location.log(context.project, context.sessionId.id, generator)
    @JvmStatic fun logNameChanged(context: WizardContext, generator: Class<*>) = name.log(context.project, context.sessionId.id, generator)
    @JvmStatic fun logLanguageChanged(context: WizardContext, language: Class<*>) = languageSelected.log(context.project, context.sessionId.id, language)
    @JvmStatic fun logGitChanged(context: WizardContext) = gitChanged.log(context.project, context.sessionId.id)
    @JvmStatic fun logGeneratorSelected(context: WizardContext, title: Class<*>) = generator.log(context.project, context.sessionId.id, title)
    @JvmStatic fun logCustomTemplateSelected(context: WizardContext) = templateSelected.log(context.project, context.sessionId.id)
    @JvmStatic fun logNext(context: WizardContext, inputMask: Long = -1) = next.log(context.project, context.sessionId.id, inputMask)
    @JvmStatic fun logPrev(context: WizardContext, inputMask: Long = -1) = prev.log(context.project, context.sessionId.id, inputMask)
    @JvmStatic fun logHelpNavigation(context: WizardContext) = helpNavigation.log(context.project, context.sessionId.id)

    //finish
    @JvmStatic fun logFinished(activity: StructuredIdeActivity, success: Boolean) = activity.finished { listOf(isSucceededField with success)}
    @JvmStatic fun logProjectCreated(project: Project?, context: WizardContext) = projectCreated.log(project, context.sessionId.id)
    @JvmStatic fun logLanguageFinished(context: WizardContext, language: Class<*>) = languageFinished.log(context.project, context.sessionId.id, language)
    @JvmStatic fun logGitFinished(context: WizardContext, git: Boolean) = gitFinish.log(context.project, context.sessionId.id, git)
    @JvmStatic fun logGeneratorFinished(context: WizardContext, generator: Class<*>) = generatorFinished.log(context.project, context.sessionId.id, generator)
  }

  open class BuildSystemCollector(buildSystemList: List<String>) {
    private val buildSystemField = BuildSystemField(buildSystemList)
    private val buildSystem = GROUP.registerEvent("select.build.system", sessionIdField, buildSystemField)

    private class BuildSystemField(buildSystemList: List<String>) : StringEventField("build_system") {
      override val validationRule: List<String> = buildSystemList
    }

    fun logBuildSystemChanged(context: WizardContext, name: String) = buildSystem.log(context.project, context.sessionId.id, name)
  }
}