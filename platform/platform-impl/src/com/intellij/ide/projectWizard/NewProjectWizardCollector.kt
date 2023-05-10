// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

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
import java.lang.Integer.min
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged as logAddSampleCodeChangedImpl

class NewProjectWizardCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  companion object {

    private val GROUP = EventLogGroup("new.project.wizard.interactions", 17)

    private val sessionIdField = EventFields.Int("wizard_session_id")
    private val screenNumField = EventFields.Int("screen")
    private val typedCharField = IntEventField("typed_chars")
    private val hitField = IntEventField("hits")
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

    private val baseFields = arrayOf(sessionIdField, screenNumField)
    private val languageFields = arrayOf(*baseFields, languageField)
    private val buildSystemFields = arrayOf(*languageFields, buildSystemField)

    // @formatter:off
    private val open = GROUP.registerVarargEvent("wizard.dialog.open", *baseFields)
    private val finish = GROUP.registerVarargEvent("wizard.dialog.finish",*baseFields, isSucceededField, EventFields.DurationMs)
    private val next = GROUP.registerVarargEvent("navigate.next", *baseFields, inputMaskField)
    private val prev = GROUP.registerVarargEvent("navigate.prev", *baseFields, inputMaskField)
    private val projectCreated = GROUP.registerVarargEvent("project.created", *baseFields)
    private val search = GROUP.registerVarargEvent("search", *baseFields, typedCharField, hitField)
    private val generatorSelected = GROUP.registerVarargEvent("generator.selected", *baseFields, generatorTypeField)
    private val generatorFinished = GROUP.registerVarargEvent("generator.finished", *baseFields, generatorTypeField)
    private val templateSelected = GROUP.registerVarargEvent("select.custom.template", *baseFields)
    private val helpNavigation = GROUP.registerVarargEvent("navigate.help", *baseFields)

    private val locationChanged = GROUP.registerVarargEvent("project.location.changed", *baseFields, generatorTypeField)
    private val nameChanged = GROUP.registerVarargEvent("project.name.changed", *baseFields, generatorTypeField)
    private val languageSelected = GROUP.registerVarargEvent("select.language", *baseFields, languageField)
    private val languageFinished = GROUP.registerVarargEvent("language.finished", *baseFields, languageField)
    private val languageAddAction = GROUP.registerVarargEvent("add.plugin.clicked", *baseFields)
    private val languageLoadAction = GROUP.registerVarargEvent("plugin.selected", *baseFields, pluginField)
    private val gitChanged = GROUP.registerVarargEvent("git.changed", *baseFields)
    private val gitFinish = GROUP.registerVarargEvent("git.finished", *baseFields, gitField)
    private val addSampleCodeChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.code.changed", *buildSystemFields, addSampleCodeField)
    private val addSampleOnboardingTipsChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.onboarding.tips.changed", *buildSystemFields, addSampleOnboardingTipsField)

    private val buildSystemChangedEvent = GROUP.registerVarargEvent("build.system.changed", *languageFields, buildSystemField)
    private val buildSystemFinishedEvent = GROUP.registerVarargEvent("build.system.finished", *languageFields, buildSystemField)
    private val sdkChangedEvent = GROUP.registerVarargEvent("build.system.sdk.changed", *buildSystemFields, buildSystemSdkField)
    private val sdkFinishedEvent = GROUP.registerVarargEvent("build.system.sdk.finished", *buildSystemFields, buildSystemSdkField)
    private val parentChangedEvent = GROUP.registerVarargEvent("build.system.parent.changed", *buildSystemFields, buildSystemParentField)
    private val parentFinishedEvent = GROUP.registerVarargEvent("build.system.parent.finished", *buildSystemFields, buildSystemParentField)

    private val moduleNameChangedEvent = GROUP.registerVarargEvent("build.system.module.name.changed", *buildSystemFields)
    private val contentRootChangedEvent = GROUP.registerVarargEvent("build.system.content.root.changed", *buildSystemFields)
    private val moduleFileLocationChangedEvent = GROUP.registerVarargEvent("build.system.module.file.location.changed", *buildSystemFields)

    private val groupIdChangedEvent = GROUP.registerVarargEvent("build.system.group.id.changed", *buildSystemFields)
    private val artifactIdChangedEvent = GROUP.registerVarargEvent("build.system.artifact.id.changed", *buildSystemFields)
    private val versionChangedEvent = GROUP.registerVarargEvent("build.system.version.changed", *buildSystemFields)

    private val dslChangedEvent = GROUP.registerVarargEvent("build.system.dsl.changed", *buildSystemFields, buildSystemDslField)

    private val groovyLibraryChanged = GROUP.registerVarargEvent("groovy.lib.changed", *baseFields, groovySourceTypeField, groovyVersionField)
    private val groovyLibraryFinished = GROUP.registerVarargEvent("groovy.lib.finished", *baseFields, groovySourceTypeField, groovyVersionField)
    // @formatter:on

    @JvmStatic
    fun logOpen(context: WizardContext) =
      open.logBaseEvent(context)

    @JvmStatic
    fun logFinish(context: WizardContext, success: Boolean, duration: Long) =
      finish.logBaseEvent(context, isSucceededField with success, EventFields.DurationMs with duration)

    @JvmStatic
    fun logNext(context: WizardContext, inputMask: Long = -1) =
      next.logBaseEvent(context, inputMaskField with inputMask)

    @JvmStatic
    fun logPrev(context: WizardContext, inputMask: Long = -1) =
      prev.logBaseEvent(context, inputMaskField with inputMask)

    @JvmStatic
    fun logProjectCreated(newProject: Project?, context: WizardContext) =
      projectCreated.logBaseEvent(newProject, context)

    @JvmStatic
    fun logSearchChanged(context: WizardContext, chars: Int, results: Int) =
      search.logBaseEvent(context, typedCharField with min(chars, 10), hitField with results)

    @JvmStatic
    fun logGeneratorSelected(context: WizardContext) =
      generatorSelected.logBaseEvent(context, generatorTypeField with context.generator)

    @JvmStatic
    fun logGeneratorFinished(context: WizardContext) =
      generatorFinished.logBaseEvent(context, generatorTypeField with context.generator)

    @JvmStatic
    fun logCustomTemplateSelected(context: WizardContext) =
      templateSelected.logBaseEvent(context)

    @JvmStatic
    fun logHelpNavigation(context: WizardContext) =
      helpNavigation.logBaseEvent(context)

    private fun VarargEventId.logBaseEvent(context: WizardContext, vararg arguments: EventPair<*>) =
      logBaseEvent(context.project, context, *arguments)

    private fun VarargEventId.logBaseEvent(project: Project?, context: WizardContext, vararg arguments: EventPair<*>) =
      log(project, sessionIdField with context.sessionId.id, screenNumField with context.screen, *arguments)

    private fun VarargEventId.logLanguageEvent(step: NewProjectWizardStep, vararg arguments: EventPair<*>) =
      logBaseEvent(step.context, languageField with step.language, *arguments)

    private fun VarargEventId.logBuildSystemEvent(step: NewProjectWizardStep, vararg arguments: EventPair<*>) =
      logLanguageEvent(step, buildSystemField with step.buildSystem, *arguments)

    private val Sdk?.featureVersion: Int
      get() {
        val sdk = this ?: return -1
        val versionString = sdk.versionString
        val version = JavaVersion.tryParse(versionString) ?: return -1
        return version.feature
      }

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

    fun NewProjectWizardStep.logNameChanged() =
      nameChanged.logBaseEvent(context, generatorTypeField with generator)

    fun NewProjectWizardStep.logLocationChanged() =
      locationChanged.logBaseEvent(context, generatorTypeField with generator)

    fun NewProjectWizardStep.logLanguageChanged() =
      languageSelected.logBaseEvent(context, languageField with language)

    fun NewProjectWizardStep.logLanguageFinished() =
      languageFinished.logBaseEvent(context, languageField with language)

    fun NewProjectWizardStep.logLanguageAddAction() =
      languageAddAction.logBaseEvent(context)

    fun NewProjectWizardStep.logLanguageLoadAction(plugin: String) =
      languageLoadAction.logBaseEvent(context, pluginField with plugin)

    fun NewProjectWizardStep.logGitChanged() =
      gitChanged.logBaseEvent(context)

    fun NewProjectWizardStep.logGitFinished(git: Boolean) =
      gitFinish.logBaseEvent(context, gitField with git)

    fun NewProjectWizardStep.logAddSampleCodeChanged(isSelected: Boolean) =
      addSampleCodeChangedEvent.logBuildSystemEvent(this, addSampleCodeField with isSelected)

    fun NewProjectWizardStep.logAddSampleOnboardingTipsChangedEvent(isSelected: Boolean) =
      addSampleOnboardingTipsChangedEvent.logBuildSystemEvent(this, addSampleOnboardingTipsField with isSelected)
  }

  object BuildSystem {

    fun NewProjectWizardStep.logBuildSystemChanged() =
      buildSystemChangedEvent.logLanguageEvent(this, buildSystemField with buildSystem)

    fun NewProjectWizardStep.logBuildSystemFinished() =
      buildSystemFinishedEvent.logLanguageEvent(this, buildSystemField with buildSystem)

    fun NewProjectWizardStep.logSdkChanged(sdk: Sdk?) =
      sdkChangedEvent.logBuildSystemEvent(this, buildSystemSdkField with sdk.featureVersion)

    fun NewProjectWizardStep.logSdkFinished(sdk: Sdk?) =
      sdkFinishedEvent.logBuildSystemEvent(this, buildSystemSdkField with sdk.featureVersion)

    fun NewProjectWizardStep.logParentChanged(isNone: Boolean) =
      parentChangedEvent.logBuildSystemEvent(this, buildSystemParentField with isNone)

    fun NewProjectWizardStep.logParentFinished(isNone: Boolean) =
      parentFinishedEvent.logBuildSystemEvent(this, buildSystemParentField with isNone)

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Moved. Please use same function NewProjectWizardCollector.Base.logAddSampleCodeChanged")
    fun NewProjectWizardStep.logAddSampleCodeChanged(isSelected: Boolean) = logAddSampleCodeChangedImpl(isSelected)
  }

  object Intellij {

    fun NewProjectWizardStep.logModuleNameChanged() =
      moduleNameChangedEvent.logBuildSystemEvent(this)

    fun NewProjectWizardStep.logContentRootChanged() =
      contentRootChangedEvent.logBuildSystemEvent(this)

    fun NewProjectWizardStep.logModuleFileLocationChanged() =
      moduleFileLocationChangedEvent.logBuildSystemEvent(this)
  }

  object Maven {

    fun NewProjectWizardStep.logGroupIdChanged() =
      groupIdChangedEvent.logBuildSystemEvent(this)

    fun NewProjectWizardStep.logArtifactIdChanged() =
      artifactIdChangedEvent.logBuildSystemEvent(this)

    fun NewProjectWizardStep.logVersionChanged() =
      versionChangedEvent.logBuildSystemEvent(this)
  }

  object Gradle {

    fun NewProjectWizardStep.logDslChanged(isUseKotlinDsl: Boolean) =
      dslChangedEvent.logBuildSystemEvent(
        this,
        buildSystemDslField with if (isUseKotlinDsl) NewProjectWizardConstants.Language.KOTLIN else NewProjectWizardConstants.Language.GROOVY
      )
  }

  object Groovy {

    fun NewProjectWizardStep.logGroovyLibraryChanged(groovyLibrarySource: String?, groovyLibraryVersion: String?) =
      groovyLibraryChanged.logBuildSystemEvent(
        this,
        groovySourceTypeField with (groovyLibrarySource ?: NULL),
        groovyVersionField with groovyLibraryVersion
      )

    fun NewProjectWizardStep.logGroovyLibraryFinished(groovyLibrarySource: String?, groovyLibraryVersion: String?) =
      groovyLibraryFinished.logBuildSystemEvent(
        this,
        groovySourceTypeField with (groovyLibrarySource ?: NULL),
        groovyVersionField with groovyLibraryVersion
      )
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