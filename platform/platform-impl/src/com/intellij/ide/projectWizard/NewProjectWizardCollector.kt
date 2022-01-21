// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardLanguageStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.lang.JavaVersion
import java.lang.Integer.min

class NewProjectWizardCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    // @formatter:off
    private val GROUP = EventLogGroup("new.project.wizard.interactions", 3)

    private val sessionIdField = EventFields.Int("wizard_session_id")
    private val screenNumField = IntEventField("screen")
    private val typedCharsField = IntEventField("typed_chars")
    private val hitsField = IntEventField("hits")
    private val generatorTypeField = ClassEventField("generator")
    private val languageField = EventFields.String("language", NewProjectWizardLanguageStep.allLanguages.keys.toList())
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
    @JvmStatic fun logLanguageChanged(context: WizardContext, language: String) = languageSelected.log(context.project, context.sessionId.id, language)
    @JvmStatic fun logGitChanged(context: WizardContext) = gitChanged.log(context.project, context.sessionId.id)
    @JvmStatic fun logGeneratorSelected(context: WizardContext, title: Class<*>) = generator.log(context.project, context.sessionId.id, title)
    @JvmStatic fun logCustomTemplateSelected(context: WizardContext) = templateSelected.log(context.project, context.sessionId.id)
    @JvmStatic fun logNext(context: WizardContext, inputMask: Long = -1) = next.log(context.project, context.sessionId.id, inputMask)
    @JvmStatic fun logPrev(context: WizardContext, inputMask: Long = -1) = prev.log(context.project, context.sessionId.id, inputMask)
    @JvmStatic fun logHelpNavigation(context: WizardContext) = helpNavigation.log(context.project, context.sessionId.id)

    //finish
    @JvmStatic fun logFinished(activity: StructuredIdeActivity, success: Boolean) = activity.finished { listOf(isSucceededField with success)}
    @JvmStatic fun logProjectCreated(project: Project?, context: WizardContext) = projectCreated.log(project, context.sessionId.id)
    @JvmStatic fun logLanguageFinished(context: WizardContext, language: String) = languageFinished.log(context.project, context.sessionId.id, language)
    @JvmStatic fun logGitFinished(context: WizardContext, git: Boolean) = gitFinish.log(context.project, context.sessionId.id, git)
    @JvmStatic fun logGeneratorFinished(context: WizardContext, generator: Class<*>) = generatorFinished.log(context.project, context.sessionId.id, generator)
    // @formatter:on
  }

  object BuildSystem {
    // @formatter:off
    private val buildSystemField = BoundedStringEventField("build.system", "intellij", "maven", "gradle")
    private val buildSystemDslField = BoundedStringEventField("build.system.dsl", "groovy", "kotlin")
    private val buildSystemSdkField = EventFields.Int("build.system.sdk.version")
    private val buildSystemParentField = EventFields.Boolean("build.system.parent")

    private val buildSystemEvent = GROUP.registerEvent("build.system", sessionIdField, languageField, buildSystemField)
    private val buildSystemFinishedEvent = GROUP.registerEvent("build.system.finished", sessionIdField, languageField, buildSystemField)
    private val sdkEvent = GROUP.registerVarargEvent("build.system.sdk", sessionIdField, languageField, buildSystemField, buildSystemSdkField)
    private val sdkFinishedEvent = GROUP.registerVarargEvent("build.system.sdk.finished", sessionIdField, languageField, buildSystemField, buildSystemSdkField)
    private val dslEvent = GROUP.registerVarargEvent("build.system.dsl", sessionIdField, languageField, buildSystemField, buildSystemDslField)
    private val parentEvent = GROUP.registerVarargEvent("build.system.parent", sessionIdField, languageField, buildSystemField, buildSystemParentField)
    private val addSampleCodeEvent = GROUP.registerEvent("build.system.add.sample.code", sessionIdField, languageField, buildSystemField)
    private val moduleNameEvent = GROUP.registerEvent("build.system.module.name", sessionIdField, languageField, buildSystemField)
    private val contentRootEvent = GROUP.registerEvent("build.system.content.root", sessionIdField, languageField, buildSystemField)
    private val moduleFileLocationEvent = GROUP.registerEvent("build.system.module.file.location", sessionIdField, languageField, buildSystemField)
    private val groupIdEvent = GROUP.registerEvent("build.system.group.id", sessionIdField, languageField, buildSystemField)
    private val artifactIdEvent = GROUP.registerEvent("build.system.artifact.id", sessionIdField, languageField, buildSystemField)
    private val versionEvent = GROUP.registerEvent("build.system.version", sessionIdField, languageField, buildSystemField)

    fun logBuildSystemChanged(context: WizardContext, language: String, buildSystem: String) = buildSystemEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logBuildSystemFinished(context: WizardContext, language: String, buildSystem: String) = buildSystemFinishedEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logSdkChanged(context: WizardContext, language: String, buildSystem: String, sdk: Sdk?) = logSdkChanged(context, language, buildSystem, sdk?.featureVersion ?: -1)
    fun logSdkChanged(context: WizardContext, language: String, buildSystem: String, version: Int) = sdkEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, version))
    fun logSdkFinished(context: WizardContext, language: String, buildSystem: String, sdk: Sdk?) = logSdkFinished(context, language, buildSystem, sdk?.featureVersion ?: -1)
    fun logSdkFinished(context: WizardContext, language: String, buildSystem: String, version: Int) = sdkFinishedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, version))
    fun logDslChanged(context: WizardContext, language: String, buildSystem: String, isUseKotlinDsl: Boolean) = logDslChanged(context, language, buildSystem, if (isUseKotlinDsl) "kotlin" else "groovy")
    fun logDslChanged(context: WizardContext, language: String, buildSystem: String, dsl: String) = dslEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemDslField, dsl))
    fun logParentChanged(context: WizardContext, language: String, buildSystem: String, isNone: Boolean) = parentEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemParentField, isNone))
    fun logAddSampleCodeChanged(context: WizardContext, language: String, buildSystem: String) = addSampleCodeEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logModuleNameChanged(context: WizardContext, language: String, buildSystem: String) = moduleNameEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logContentRootChanged(context: WizardContext, language: String, buildSystem: String) = contentRootEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logModuleFileLocationChanged(context: WizardContext, language: String, buildSystem: String) = moduleFileLocationEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logGroupIdChanged(context: WizardContext, language: String, buildSystem: String) = groupIdEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logArtifactIdChanged(context: WizardContext, language: String, buildSystem: String) = artifactIdEvent.log(context.project, context.sessionId.id, language, buildSystem)
    fun logVersionChanged(context: WizardContext, language: String, buildSystem: String) = versionEvent.log(context.project, context.sessionId.id, language, buildSystem)

    fun <S> S.logBuildSystemChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logBuildSystemChanged(context, buildSystem, language)
    fun <S> S.logBuildSystemFinished() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logBuildSystemFinished(context, buildSystem, language)
    fun <S> S.logSdkChanged(sdk: Sdk?) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logSdkChanged(context, buildSystem, language, sdk)
    fun <S> S.logSdkFinished(sdk: Sdk?) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logSdkFinished(context, buildSystem, language, sdk)
    fun <S> S.logDslChanged(isUseKotlinDsl: Boolean) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logDslChanged(context, buildSystem, language, isUseKotlinDsl)
    fun <S> S.logParentChanged(isNone: Boolean) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logParentChanged(context, buildSystem, language, isNone)
    fun <S> S.logAddSampleCodeChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logAddSampleCodeChanged(context, buildSystem, language)
    fun <S> S.logModuleNameChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logModuleNameChanged(context, buildSystem, language)
    fun <S> S.logContentRootChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logContentRootChanged(context, buildSystem, language)
    fun <S> S.logModuleFileLocationChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logModuleFileLocationChanged(context, buildSystem, language)
    fun <S> S.logGroupIdChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logGroupIdChanged(context, buildSystem, language)
    fun <S> S.logArtifactIdChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logArtifactIdChanged(context, buildSystem, language)
    fun <S> S.logVersionChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logVersionChanged(context, buildSystem, language)
    // @formatter:on

    private val Sdk.featureVersion: Int?
      get() = JavaVersion.tryParse(versionString)?.feature
  }

  private class BoundedStringEventField(name: String, vararg allowedValues: String) : StringEventField(name) {

    private val myAllowedValues = allowedValues.map { it.lowercase() }

    override fun addData(fuData: FeatureUsageData, value: String?) {
      var boundedValue = value?.lowercase() ?: return
      if (boundedValue !in myAllowedValues) {
        boundedValue = OTHER
      }
      super.addData(fuData, boundedValue)
    }

    override val validationRule = EventFields.String(name, myAllowedValues + OTHER).validationRule

    companion object {
      private const val OTHER = "other"
    }
  }
}