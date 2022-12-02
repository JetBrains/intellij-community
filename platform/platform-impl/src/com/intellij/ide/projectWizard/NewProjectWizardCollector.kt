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
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.lang.Integer.min

class NewProjectWizardCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    // @formatter:off
    private val GROUP = EventLogGroup("new.project.wizard.interactions", 13)

    private val sessionIdField = EventFields.Int("wizard_session_id")
    private val screenNumField = EventFields.Int("screen")
    private val typedCharsField = IntEventField("typed_chars")
    private val hitsField = IntEventField("hits")
    private val generatorTypeField = GeneratorEventField("generator")
    private val languageField = BoundedStringEventField.lowercase("language", *NewProjectWizardConstants.Language.ALL)
    private val gitField = EventFields.Boolean("git")
    private val isSucceededField = EventFields.Boolean("project_created")
    private val inputMaskField = EventFields.Long("input_mask")
    private val addSampleCodeField = EventFields.Boolean("add_sample_code")
    private val addSampleOnboardingTipsField = EventFields.Boolean("add_sample_onboarding_tips")
    private val buildSystemField = BoundedStringEventField.lowercase("build_system", *NewProjectWizardConstants.BuildSystem.ALL)
    private val buildSystemDslField = BoundedStringEventField.lowercase("build_system_dsl", *NewProjectWizardConstants.Language.ALL_DSL)
    private val buildSystemSdkField = EventFields.Int("build_system_sdk_version")
    private val buildSystemParentField = EventFields.Boolean("build_system_parent")
    private val groovyVersionField = EventFields.Version
    private val groovySourceTypeField = BoundedStringEventField.lowercase("groovy_sdk_type", "maven", "local", NULL)
    private val pluginField = BoundedStringEventField.lowercase("plugin_selected", *NewProjectWizardConstants.Language.ALL)
    private val projectsWithTipsField = EventFields.Int("projects_with_tips")

    //events
    private val open = GROUP.registerVarargEvent("wizard.dialog.open", sessionIdField, screenNumField)
    private val finish = GROUP.registerVarargEvent("wizard.dialog.finish", sessionIdField, screenNumField, isSucceededField, EventFields.DurationMs)
    private val next = GROUP.registerVarargEvent("navigate.next", sessionIdField, screenNumField, inputMaskField)
    private val prev = GROUP.registerVarargEvent("navigate.prev", sessionIdField, screenNumField, inputMaskField)
    private val projectCreated = GROUP.registerVarargEvent("project.created", screenNumField)
    private val search = GROUP.registerVarargEvent("search", sessionIdField, screenNumField, typedCharsField, hitsField)
    private val generatorSelected = GROUP.registerVarargEvent("generator.selected", sessionIdField, screenNumField, generatorTypeField)
    private val location = GROUP.registerVarargEvent("project.location.changed", sessionIdField, screenNumField, generatorTypeField)
    private val name = GROUP.registerVarargEvent("project.name.changed", sessionIdField, screenNumField, generatorTypeField)
    private val languageSelected = GROUP.registerVarargEvent("select.language", sessionIdField, screenNumField, languageField)
    private val gitChanged = GROUP.registerVarargEvent("git.changed", screenNumField)
    private val templateSelected = GROUP.registerVarargEvent("select.custom.template", screenNumField)
    private val helpNavigation = GROUP.registerVarargEvent("navigate.help", screenNumField)
    private val buildSystemChangedEvent = GROUP.registerVarargEvent("build.system.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val sdkChangedEvent = GROUP.registerVarargEvent("build.system.sdk.changed", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemSdkField)
    private val dslChangedEvent = GROUP.registerVarargEvent("build.system.dsl.changed", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemDslField)
    private val parentChangedEvent = GROUP.registerVarargEvent("build.system.parent.changed", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemParentField)
    private val addSampleCodeChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.code.changed", sessionIdField, screenNumField, languageField, buildSystemField, addSampleCodeField)
    private val addSampleOnboardingTipsChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.onboarding.tips.changed", sessionIdField, screenNumField, languageField, buildSystemField, addSampleOnboardingTipsField)
    private val moduleNameChangedEvent = GROUP.registerVarargEvent("build.system.module.name.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val contentRootChangedEvent = GROUP.registerVarargEvent("build.system.content.root.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val moduleFileLocationChangedEvent = GROUP.registerVarargEvent("build.system.module.file.location.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val groupIdChangedEvent = GROUP.registerVarargEvent("build.system.group.id.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val artifactIdChangedEvent = GROUP.registerVarargEvent("build.system.artifact.id.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val versionChangedEvent = GROUP.registerVarargEvent("build.system.version.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val groovyLibraryChanged = GROUP.registerVarargEvent("groovy.lib.changed", sessionIdField, screenNumField, groovySourceTypeField, groovyVersionField)
    private val addPlugin = GROUP.registerVarargEvent("add.plugin.clicked", screenNumField)
    private val pluginSelected = GROUP.registerVarargEvent("plugin.selected", sessionIdField, screenNumField, pluginField)

    private val disableOnboardingTipsEvent = GROUP.registerVarargEvent("onboarding.tips.disabled", projectsWithTipsField)
    private val hideOnboardingTipsDisableProposalEvent = GROUP.registerVarargEvent("hide.onboarding.tips.disable.proposal", projectsWithTipsField)

    //finish events
    private val gitFinish = GROUP.registerVarargEvent("create.git.repo", sessionIdField, screenNumField, gitField)
    private val generatorFinished = GROUP.registerVarargEvent("generator.finished", sessionIdField, screenNumField, generatorTypeField)
    private val languageFinished = GROUP.registerVarargEvent("language.finished", sessionIdField, screenNumField, languageField)
    private val buildSystemFinishedEvent = GROUP.registerVarargEvent("build.system.finished", sessionIdField, screenNumField, languageField, buildSystemField)
    private val sdkFinishedEvent = GROUP.registerVarargEvent("build.system.sdk.finished", sessionIdField, screenNumField, languageField, buildSystemField, buildSystemSdkField)
    private val groovyLibraryFinished = GROUP.registerVarargEvent("groovy.lib.finished", sessionIdField, screenNumField, groovySourceTypeField, groovyVersionField)

    //logs
    @JvmStatic fun logOpen(context: WizardContext) = open.log(context.project,sessionIdField with context.sessionId.id, screenNumField with context.screen)
    @JvmStatic fun logFinish(context: WizardContext, success: Boolean, duration: Long) = finish.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, isSucceededField with success, EventFields.DurationMs with duration)
    @JvmStatic fun logSearchChanged(context: WizardContext, chars: Int, results: Int) = search.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, typedCharsField with min(chars, 10), hitsField with results)
    @JvmStatic fun logLocationChanged(context: WizardContext, generator: ModuleBuilder?) = location.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logNameChanged(context: WizardContext, generator: ModuleBuilder?) = name.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logLanguageChanged(context: WizardContext, language: String) = languageSelected.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language)
    @JvmStatic fun logGitChanged(context: WizardContext) = gitChanged.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logGeneratorSelected(context: WizardContext, generator: ModuleBuilder?) = generatorSelected.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logCustomTemplateSelected(context: WizardContext) = templateSelected.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logNext(context: WizardContext, inputMask: Long = -1) = next.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, inputMaskField with inputMask)
    @JvmStatic fun logPrev(context: WizardContext, inputMask: Long = -1) = prev.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, inputMaskField with inputMask)
    @JvmStatic fun logHelpNavigation(context: WizardContext) = helpNavigation.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logDisableOnboardingTips(project: Project?, projectsWithTips: Int) = disableOnboardingTipsEvent.log(project, projectsWithTipsField with projectsWithTips)
    @JvmStatic fun logHideOnboardingTipsDisableProposal(project: Project?, projectsWithTips: Int) = hideOnboardingTipsDisableProposalEvent.log(project, projectsWithTipsField with projectsWithTips)

    //finish
    @JvmStatic fun logProjectCreated(project: Project?, context: WizardContext) = projectCreated.log(project,screenNumField with context.screen)
    @JvmStatic fun logLanguageFinished(context: WizardContext, language: String) = languageFinished.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language)
    @JvmStatic fun logGitFinished(context: WizardContext, git: Boolean) = gitFinish.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, gitField with git)
    @JvmStatic fun logGeneratorFinished(context: WizardContext, generator: ModuleBuilder?) = generatorFinished.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logAddPlugin(context: WizardContext) = addPlugin.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logPluginSelected(context: WizardContext, plugin: String) = pluginSelected.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, pluginField with plugin)

    fun NewProjectWizardStep.logNameChanged() = logNameChanged(context, context.generator)
    fun NewProjectWizardStep.logLocationChanged() = logLocationChanged(context, context.generator)
    fun NewProjectWizardStep.logLanguageChanged() = logLanguageChanged(context, language)
    fun WizardContext.logGeneratorSelected() = logGeneratorSelected(this, generator)
    fun WizardContext.logGeneratorFinished() = logGeneratorFinished(this, generator)
    // @formatter:on

    private val Sdk.featureVersion: Int?
      get() = JavaVersion.tryParse(versionString)?.feature

    private val WizardContext.generator: ModuleBuilder?
      get() = projectBuilder as? ModuleBuilder

    private val NewProjectWizardStep.language: String
      get() = (this as? LanguageNewProjectWizardData)?.language
              ?: data.getUserData(LanguageNewProjectWizardData.KEY)?.language
              ?: OTHER

    private val NewProjectWizardStep.buildSystem: String
      get() = (this as? BuildSystemNewProjectWizardData)?.buildSystem
              ?: OTHER
  }

  object BuildSystem {
    // @formatter:off
    fun logBuildSystemChanged(context: WizardContext, language: String, buildSystem: String) = buildSystemChangedEvent.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logBuildSystemFinished(context: WizardContext, language: String, buildSystem: String) = buildSystemFinishedEvent.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logSdkChanged(context: WizardContext, language: String, buildSystem: String, sdk: Sdk?) = logSdkChanged(context, language, buildSystem, sdk?.featureVersion ?: -1)
    fun logSdkChanged(context: WizardContext, language: String, buildSystem: String, version: Int) = sdkChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, version))
    fun logSdkFinished(context: WizardContext, language: String, buildSystem: String, sdk: Sdk?) = logSdkFinished(context, language, buildSystem, sdk?.featureVersion ?: -1)
    fun logSdkFinished(context: WizardContext, language: String, buildSystem: String, version: Int) = sdkFinishedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, version))
    fun logDslChanged(context: WizardContext, language: String, buildSystem: String, isUseKotlinDsl: Boolean) = logDslChanged(context, language, buildSystem, if (isUseKotlinDsl) KOTLIN else GROOVY)
    fun logDslChanged(context: WizardContext, language: String, buildSystem: String, dsl: String) = dslChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemDslField, dsl))
    fun logParentChanged(context: WizardContext, language: String, buildSystem: String, isNone: Boolean) = parentChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemParentField, isNone))
    fun logAddSampleCodeChanged(context: WizardContext, language: String, buildSystem: String, isSelected: Boolean) = addSampleCodeChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem, addSampleCodeField with isSelected)
    fun logAddSampleOnboardingTipsChanged(context: WizardContext, language: String, buildSystem: String, isSelected: Boolean) = addSampleOnboardingTipsChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem, addSampleOnboardingTipsField with isSelected)
    fun logModuleNameChanged(context: WizardContext, language: String, buildSystem: String) = moduleNameChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logContentRootChanged(context: WizardContext, language: String, buildSystem: String) = contentRootChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logModuleFileLocationChanged(context: WizardContext, language: String, buildSystem: String) = moduleFileLocationChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logGroupIdChanged(context: WizardContext, language: String, buildSystem: String) = groupIdChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logArtifactIdChanged(context: WizardContext, language: String, buildSystem: String) = artifactIdChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logVersionChanged(context: WizardContext, language: String, buildSystem: String) = versionChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)

    fun NewProjectWizardStep.logBuildSystemChanged() = logBuildSystemChanged(context, language, buildSystem)
    fun NewProjectWizardStep.logBuildSystemFinished() = logBuildSystemFinished(context, language, buildSystem)
    fun NewProjectWizardStep.logSdkChanged(sdk: Sdk?) = logSdkChanged(context, language, buildSystem, sdk)
    fun NewProjectWizardStep.logSdkFinished(sdk: Sdk?) = logSdkFinished(context, language, buildSystem, sdk)
    fun NewProjectWizardStep.logDslChanged(isUseKotlinDsl: Boolean) = logDslChanged(context, language, buildSystem, isUseKotlinDsl)
    fun NewProjectWizardStep.logParentChanged(isNone: Boolean) = logParentChanged(context, language, buildSystem, isNone)
    fun NewProjectWizardStep.logAddSampleCodeChanged(isSelected: Boolean) = logAddSampleCodeChanged(context, language, buildSystem, isSelected)
    fun NewProjectWizardStep.logAddSampleOnboardingTipsChangedEvent(isSelected: Boolean) = logAddSampleOnboardingTipsChanged(context, language, buildSystem, isSelected)
    fun NewProjectWizardStep.logModuleNameChanged() = logModuleNameChanged(context, language, buildSystem)
    fun NewProjectWizardStep.logContentRootChanged() = logContentRootChanged(context, language, buildSystem)
    fun NewProjectWizardStep.logModuleFileLocationChanged()  = logModuleFileLocationChanged(context, language, buildSystem)
    fun NewProjectWizardStep.logGroupIdChanged() = logGroupIdChanged(context, language, buildSystem)
    fun NewProjectWizardStep.logArtifactIdChanged() = logArtifactIdChanged(context, language, buildSystem)
    fun NewProjectWizardStep.logVersionChanged() = logVersionChanged(context, language, buildSystem)

    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logBuildSystemChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logBuildSystemChanged(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logBuildSystemFinished() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logBuildSystemFinished(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logSdkChanged(sdk: Sdk?) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logSdkChanged(context, language, buildSystem, sdk)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logSdkFinished(sdk: Sdk?) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logSdkFinished(context, language, buildSystem, sdk)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logDslChanged(isUseKotlinDsl: Boolean) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logDslChanged(context, language, buildSystem, isUseKotlinDsl)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logParentChanged(isNone: Boolean) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logParentChanged(context, language, buildSystem, isNone)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logAddSampleCodeChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logAddSampleCodeChanged(context, language, buildSystem, true)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logModuleNameChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logModuleNameChanged(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logContentRootChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logContentRootChanged(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logModuleFileLocationChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep  = logModuleFileLocationChanged(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logGroupIdChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logGroupIdChanged(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logArtifactIdChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logArtifactIdChanged(context, language, buildSystem)
    @Deprecated("Please recompile", level = DeprecationLevel.HIDDEN) @ScheduledForRemoval @Suppress("unused") fun <S> S.logVersionChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logVersionChanged(context, language, buildSystem)
    // @formatter:on
  }

  object Groovy {
    // @formatter:off
    fun logGroovyLibraryChanged(context: WizardContext, groovyLibrarySource: String?, groovyLibraryVersion: String?) = groovyLibraryChanged.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, groovySourceTypeField with (groovyLibrarySource ?: NULL), groovyVersionField with groovyLibraryVersion)
    fun logGroovyLibraryFinished(context: WizardContext, groovyLibrarySource: String?, groovyLibraryVersion: String?) = groovyLibraryFinished.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, groovySourceTypeField with (groovyLibrarySource ?: NULL), groovyVersionField with groovyLibraryVersion)
    // @formatter:on
  }

  private class BoundedStringEventField private constructor(
    name: String,
    allowedValues: List<String>,
    private val transform: (String) -> String
  ) : StringEventField(name) {

    private val myAllowedValues = (allowedValues + OTHER).map(transform)

    override fun addData(fuData: FeatureUsageData, value: String?) {
      var boundedValue = value?.let(transform) ?: return
      if (boundedValue !in myAllowedValues) {
        boundedValue = OTHER
      }
      super.addData(fuData, boundedValue)
    }

    override val validationRule = EventFields.String(name, myAllowedValues).validationRule

    companion object {
      fun lowercase(name: String, vararg allowedValues: String): BoundedStringEventField {
        return BoundedStringEventField(name, allowedValues.toList(), String::lowercase)
      }

      @Suppress("unused")
      fun enum(name: String, vararg allowedValues: String): BoundedStringEventField {
        return BoundedStringEventField(name, allowedValues.toList()) { it }
      }
    }
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