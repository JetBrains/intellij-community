// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.GROOVY
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.NULL
import com.intellij.ide.projectWizard.NewProjectWizardConstants.OTHER
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter.Companion.NPW_PREFIX
import com.intellij.ide.wizard.LanguageNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.lang.JavaVersion
import java.lang.Integer.min
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged as logAddSampleCodeChangedImpl

class NewProjectWizardCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    private val GROUP = EventLogGroup("new.project.wizard.interactions", 15)

    private val sessionIdField = EventFields.Int("wizard_session_id")
    private val screenNumField = EventFields.Int("screen")
    private val typedCharsField = IntEventField("typed_chars")
    private val hitsField = IntEventField("hits")
    private val generatorTypeField = GeneratorEventField("generator")
    private val languageField = EventFields.String("language", NewProjectWizardConstants.Language.ALL)
    private val gitField = EventFields.Boolean("git")
    private val isSucceededField = EventFields.Boolean("project_created")
    private val inputMaskField = EventFields.Long("input_mask")
    private val addSampleCodeField = EventFields.Boolean("add_sample_code")
    private val addSampleOnboardingTipsField = EventFields.Boolean("add_sample_onboarding_tips")
    private val buildSystemField = EventFields.String("build_system", NewProjectWizardConstants.BuildSystem.ALL)
    private val buildSystemDslField = EventFields.String("build_system_dsl", NewProjectWizardConstants.Language.ALL_DSL)
    private val buildSystemSdkField = EventFields.Int("build_system_sdk_version")
    private val buildSystemParentField = EventFields.Boolean("build_system_parent")
    private val groovyVersionField = EventFields.Version
    private val groovySourceTypeField = EventFields.String("groovy_sdk_type", listOf("maven", "local", NULL))
    private val pluginField = EventFields.String("plugin_selected", NewProjectWizardConstants.Language.ALL)

    // @formatter:off
    private val open = GROUP.registerVarargEvent("wizard.dialog.open", sessionIdField, screenNumField)
    private val finish = GROUP.registerVarargEvent("wizard.dialog.finish", sessionIdField, screenNumField, isSucceededField, EventFields.DurationMs)
    private val next = GROUP.registerVarargEvent("navigate.next", sessionIdField, screenNumField, inputMaskField)
    private val prev = GROUP.registerVarargEvent("navigate.prev", sessionIdField, screenNumField, inputMaskField)
    private val projectCreated = GROUP.registerVarargEvent("project.created", screenNumField)
    private val search = GROUP.registerVarargEvent("search", sessionIdField, screenNumField, typedCharsField, hitsField)
    private val generatorSelected = GROUP.registerVarargEvent("generator.selected", sessionIdField, screenNumField, generatorTypeField)
    private val generatorFinished = GROUP.registerVarargEvent("generator.finished", sessionIdField, screenNumField, generatorTypeField)
    private val templateSelected = GROUP.registerVarargEvent("select.custom.template", screenNumField)
    private val helpNavigation = GROUP.registerVarargEvent("navigate.help", screenNumField)

    private val location = GROUP.registerVarargEvent("project.location.changed", sessionIdField, screenNumField, generatorTypeField)
    private val name = GROUP.registerVarargEvent("project.name.changed", sessionIdField, screenNumField, generatorTypeField)
    private val languageSelected = GROUP.registerVarargEvent("select.language", sessionIdField, screenNumField, languageField)
    private val languageFinished = GROUP.registerVarargEvent("language.finished", sessionIdField, screenNumField, languageField)
    private val languageAddAction = GROUP.registerVarargEvent("add.plugin.clicked", screenNumField)
    private val languageLoadAction = GROUP.registerVarargEvent("plugin.selected", sessionIdField, screenNumField, pluginField)
    private val gitChanged = GROUP.registerVarargEvent("git.changed", screenNumField)
    private val gitFinish = GROUP.registerVarargEvent("create.git.repo", sessionIdField, screenNumField, gitField)
    private val addSampleCodeChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.code.changed", sessionIdField, screenNumField, languageField, buildSystemField, addSampleCodeField)
    private val addSampleOnboardingTipsChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.onboarding.tips.changed", sessionIdField, screenNumField, languageField, buildSystemField, addSampleOnboardingTipsField)

    private val buildSystemChangedEvent = GROUP.registerVarargEvent("build.system.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val buildSystemFinishedEvent = GROUP.registerVarargEvent("build.system.finished", sessionIdField, screenNumField, languageField, buildSystemField)
    private val sdkChangedEvent = GROUP.registerVarargEvent("build.system.sdk.changed", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemSdkField)
    private val sdkFinishedEvent = GROUP.registerVarargEvent("build.system.sdk.finished", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemSdkField)
    private val parentChangedEvent = GROUP.registerVarargEvent("build.system.parent.changed", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemParentField)

    private val moduleNameChangedEvent = GROUP.registerVarargEvent("build.system.module.name.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val contentRootChangedEvent = GROUP.registerVarargEvent("build.system.content.root.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val moduleFileLocationChangedEvent = GROUP.registerVarargEvent("build.system.module.file.location.changed", sessionIdField, screenNumField, languageField, buildSystemField)

    private val groupIdChangedEvent = GROUP.registerVarargEvent("build.system.group.id.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val artifactIdChangedEvent = GROUP.registerVarargEvent("build.system.artifact.id.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val versionChangedEvent = GROUP.registerVarargEvent("build.system.version.changed", sessionIdField, screenNumField, languageField, buildSystemField)

    private val dslChangedEvent = GROUP.registerVarargEvent("build.system.dsl.changed", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemDslField)

    private val groovyLibraryChanged = GROUP.registerVarargEvent("groovy.lib.changed", sessionIdField, screenNumField, groovySourceTypeField, groovyVersionField)
    private val groovyLibraryFinished = GROUP.registerVarargEvent("groovy.lib.finished", sessionIdField, screenNumField, groovySourceTypeField, groovyVersionField)
    // @formatter:on

    // @formatter:off
    @JvmStatic fun logOpen(context: WizardContext) = open.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen)
    @JvmStatic fun logFinish(context: WizardContext, success: Boolean, duration: Long) = finish.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, isSucceededField with success, EventFields.DurationMs with duration)
    @JvmStatic fun logNext(context: WizardContext, inputMask: Long = -1) = next.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, inputMaskField with inputMask)
    @JvmStatic fun logPrev(context: WizardContext, inputMask: Long = -1) = prev.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, inputMaskField with inputMask)
    @JvmStatic fun logProjectCreated(project: Project?, context: WizardContext) = projectCreated.log(project, screenNumField with context.screen)
    @JvmStatic fun logSearchChanged(context: WizardContext, chars: Int, results: Int) = search.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, typedCharsField with min(chars, 10), hitsField with results)
    @JvmStatic fun logGeneratorSelected(context: WizardContext) = generatorSelected.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, generatorTypeField with context.generator)
    @JvmStatic fun logGeneratorFinished(context: WizardContext) = generatorFinished.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, generatorTypeField with context.generator)
    @JvmStatic fun logCustomTemplateSelected(context: WizardContext) = templateSelected.log(context.project, screenNumField with context.screen)
    @JvmStatic fun logHelpNavigation(context: WizardContext) = helpNavigation.log(context.project, screenNumField with context.screen)
    // @formatter:on

    private val Sdk.featureVersion: Int?
      get() = JavaVersion.tryParse(versionString)?.feature

    private val WizardContext.generator: ModuleBuilder?
      get() = projectBuilder as? ModuleBuilder

    private val NewProjectWizardStep.generator: ModuleBuilder?
      get() = context.generator

    private val NewProjectWizardStep.language: String
      get() = (this as? LanguageNewProjectWizardData)?.language
              ?: data.getUserData(LanguageNewProjectWizardData.KEY)?.language
              ?: OTHER

    private val NewProjectWizardStep.buildSystem: String
      get() = (this as? BuildSystemNewProjectWizardData)?.buildSystem
              ?: OTHER
  }

  object Base {
    // @formatter:off
    fun NewProjectWizardStep.logNameChanged() = name.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, generatorTypeField with generator)
    fun NewProjectWizardStep.logLocationChanged() = location.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, generatorTypeField with generator)
    fun NewProjectWizardStep.logLanguageChanged() = languageSelected.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language)
    fun NewProjectWizardStep.logLanguageFinished() = languageFinished.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language)
    fun NewProjectWizardStep.logLanguageAddAction() = languageAddAction.log(context.project, screenNumField with context.screen)
    fun NewProjectWizardStep.logLanguageLoadAction(plugin: String) = languageLoadAction.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, pluginField with plugin)
    fun NewProjectWizardStep.logGitChanged() = gitChanged.log(context.project, screenNumField with context.screen)
    fun NewProjectWizardStep.logGitFinished(git: Boolean) = gitFinish.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, gitField with git)
    fun NewProjectWizardStep.logAddSampleCodeChanged(isSelected: Boolean) = addSampleCodeChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem, addSampleCodeField with isSelected)
    fun NewProjectWizardStep.logAddSampleOnboardingTipsChangedEvent(isSelected: Boolean) = addSampleOnboardingTipsChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem, addSampleOnboardingTipsField with isSelected)
    // @formatter:on
  }

  object BuildSystem {
    // @formatter:off
    fun NewProjectWizardStep.logBuildSystemChanged() = buildSystemChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun NewProjectWizardStep.logBuildSystemFinished() = buildSystemFinishedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun NewProjectWizardStep.logSdkChanged(sdk: Sdk?) = sdkChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, sdk?.featureVersion ?: -1))
    fun NewProjectWizardStep.logSdkFinished(sdk: Sdk?) = sdkFinishedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, sdk?.featureVersion ?: -1))
    fun NewProjectWizardStep.logParentChanged(isNone: Boolean) = parentChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemParentField, isNone))
    // @formatter:on

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Moved. Please use same function NewProjectWizardCollector.Base.logAddSampleCodeChanged")
    fun NewProjectWizardStep.logAddSampleCodeChanged(isSelected: Boolean) = logAddSampleCodeChangedImpl(isSelected)
  }

  object Intellij {
    // @formatter:off
    fun NewProjectWizardStep.logModuleNameChanged() = moduleNameChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun NewProjectWizardStep.logContentRootChanged() = contentRootChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun NewProjectWizardStep.logModuleFileLocationChanged()  = moduleFileLocationChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    // @formatter:on
  }

  object Maven {
    // @formatter:off
    fun NewProjectWizardStep.logGroupIdChanged() = groupIdChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun NewProjectWizardStep.logArtifactIdChanged() = artifactIdChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun NewProjectWizardStep.logVersionChanged() = versionChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    // @formatter:on
  }

  object Gradle {
    // @formatter:off
    fun NewProjectWizardStep.logDslChanged(isUseKotlinDsl: Boolean) = dslChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemDslField, if (isUseKotlinDsl) KOTLIN else GROOVY))
    // @formatter:on
  }

  object Groovy {
    // @formatter:off
    fun NewProjectWizardStep.logGroovyLibraryChanged(groovyLibrarySource: String?, groovyLibraryVersion: String?) = groovyLibraryChanged.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, groovySourceTypeField with (groovyLibrarySource ?: NULL), groovyVersionField with groovyLibraryVersion)
    fun NewProjectWizardStep.logGroovyLibraryFinished(groovyLibrarySource: String?, groovyLibraryVersion: String?) = groovyLibraryFinished.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, groovySourceTypeField with (groovyLibrarySource ?: NULL), groovyVersionField with groovyLibraryVersion)
    // @formatter:on
  }

  private class GeneratorEventField(override val name: String) : PrimitiveEventField<ModuleBuilder?>() {

    override fun addData(fuData: FeatureUsageData, value: ModuleBuilder?) {
      fuData.addPluginInfo(value?.let { getPluginInfo(it.javaClass) })
      fuData.addData(name, value?.builderId?.removePrefix(NPW_PREFIX) ?: OTHER)
    }

    override val validationRule: List<String>
      get() = listOf("{util#${GeneratorValidationRule.ID}}")
  }

  class GeneratorValidationRule : CustomValidationRule() {
    override fun getRuleId(): String = ID

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      if (isThirdPartyValue(data) || OTHER == data) return ValidationResultType.ACCEPTED
      return acceptWhenReportedByPluginFromPluginRepository(context)
    }

    companion object {
      const val ID = "npw_generator"
    }
  }
}