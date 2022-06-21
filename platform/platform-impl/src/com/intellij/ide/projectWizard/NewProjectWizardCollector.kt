// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardLanguageStep
import com.intellij.ide.wizard.NewProjectWizardStep
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
    private val GROUP = EventLogGroup("new.project.wizard.interactions", 7)

    private val sessionIdField = EventFields.Int("wizard_session_id")
    private val screenNumField = EventFields.Int("screen")
    private val typedCharsField = IntEventField("typed_chars")
    private val hitsField = IntEventField("hits")
    private val generatorTypeField = ClassEventField("generator")
    private val languageField = EventFields.String("language", NewProjectWizardLanguageStep.allLanguages.keys.toList())
    private val gitField = EventFields.Boolean("git")
    private val isSucceededField = EventFields.Boolean("project_created")
    private val inputMaskField = EventFields.Long("input_mask")
    private val buildSystemField = BoundedStringEventField("build_system", "intellij", "maven", "gradle")
    private val buildSystemDslField = BoundedStringEventField("build_system_dsl", "groovy", "kotlin")
    private val buildSystemSdkField = EventFields.Int("build_system_sdk_version")
    private val buildSystemParentField = EventFields.Boolean("build_system_parent")
    private val groovyVersionField = EventFields.Version
    private val groovySourceTypeField = BoundedStringEventField("groovy_sdk_type", "maven", "local")
    private val pluginField = EventFields.String("plugin_selected", NewProjectWizardLanguageStep.allLanguages.keys.toList())

    //events
    private val open = GROUP.registerVarargEvent("wizard.dialog.open", sessionIdField, screenNumField)
    private val finish = GROUP.registerVarargEvent("wizard.dialog.finish", sessionIdField, screenNumField, isSucceededField, EventFields.DurationMs)
    private val next = GROUP.registerVarargEvent("navigate.next", sessionIdField, screenNumField, inputMaskField)
    private val prev = GROUP.registerVarargEvent("navigate.prev", sessionIdField, screenNumField, inputMaskField)
    private val projectCreated = GROUP.registerVarargEvent("project.created", screenNumField)
    private val search = GROUP.registerVarargEvent("search", sessionIdField, screenNumField, typedCharsField, hitsField)
    private val generator = GROUP.registerVarargEvent("generator.selected", sessionIdField, screenNumField, generatorTypeField)
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
    private val addSampleCodeChangedEvent = GROUP.registerVarargEvent("build.system.add.sample.code.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val moduleNameChangedEvent = GROUP.registerVarargEvent("build.system.module.name.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val contentRootChangedEvent = GROUP.registerVarargEvent("build.system.content.root.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val moduleFileLocationChangedEvent = GROUP.registerVarargEvent("build.system.module.file.location.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val groupIdChangedEvent = GROUP.registerVarargEvent("build.system.group.id.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val artifactIdChangedEvent = GROUP.registerVarargEvent("build.system.artifact.id.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val versionChangedEvent = GROUP.registerVarargEvent("build.system.version.changed", sessionIdField, screenNumField, languageField, buildSystemField)
    private val groovyLibraryChanged = GROUP.registerVarargEvent("groovy.lib.changed", sessionIdField, screenNumField, groovySourceTypeField, groovyVersionField)
    private val addPlugin = GROUP.registerVarargEvent("add.plugin.clicked", screenNumField)
    private val pluginSelected = GROUP.registerVarargEvent("plugin.selected", sessionIdField, screenNumField, pluginField)

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
    @JvmStatic fun logLocationChanged(context: WizardContext, generator: Class<*>) = location.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logNameChanged(context: WizardContext, generator: Class<*>) = name.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logLanguageChanged(context: WizardContext, language: String) = languageSelected.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language)
    @JvmStatic fun logGitChanged(context: WizardContext) = gitChanged.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logGeneratorSelected(context: WizardContext, title: Class<*>) = generator.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with title)
    @JvmStatic fun logCustomTemplateSelected(context: WizardContext) = templateSelected.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logNext(context: WizardContext, inputMask: Long = -1) = next.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, inputMaskField with inputMask)
    @JvmStatic fun logPrev(context: WizardContext, inputMask: Long = -1) = prev.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, inputMaskField with inputMask)
    @JvmStatic fun logHelpNavigation(context: WizardContext) = helpNavigation.log(context.project,screenNumField with context.screen)

    //finish
    @JvmStatic fun logProjectCreated(project: Project?, context: WizardContext) = projectCreated.log(project,screenNumField with context.screen)
    @JvmStatic fun logLanguageFinished(context: WizardContext, language: String) = languageFinished.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language)
    @JvmStatic fun logGitFinished(context: WizardContext, git: Boolean) = gitFinish.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, gitField with git)
    @JvmStatic fun logGeneratorFinished(context: WizardContext, generator: Class<*>) = generatorFinished.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, generatorTypeField with generator)
    @JvmStatic fun logAddPlugin(context: WizardContext) = addPlugin.log(context.project,screenNumField with context.screen)
    @JvmStatic fun logPluginSelected(context: WizardContext, plugin: String) = pluginSelected.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, pluginField with plugin)
    // @formatter:on
  }

  object BuildSystem {
    // @formatter:off
    fun logBuildSystemChanged(context: WizardContext, language: String, buildSystem: String) = buildSystemChangedEvent.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logBuildSystemFinished(context: WizardContext, language: String, buildSystem: String) = buildSystemFinishedEvent.log(context.project, sessionIdField with context.sessionId.id,screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logSdkChanged(context: WizardContext, language: String, buildSystem: String, sdk: Sdk?) = logSdkChanged(context, language, buildSystem, sdk?.featureVersion ?: -1)
    fun logSdkChanged(context: WizardContext, language: String, buildSystem: String, version: Int) = sdkChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, version))
    fun logSdkFinished(context: WizardContext, language: String, buildSystem: String, sdk: Sdk?) = logSdkFinished(context, language, buildSystem, sdk?.featureVersion ?: -1)
    fun logSdkFinished(context: WizardContext, language: String, buildSystem: String, version: Int) = sdkFinishedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemSdkField, version))
    fun logDslChanged(context: WizardContext, language: String, buildSystem: String, isUseKotlinDsl: Boolean) = logDslChanged(context, language, buildSystem, if (isUseKotlinDsl) "kotlin" else "groovy")
    fun logDslChanged(context: WizardContext, language: String, buildSystem: String, dsl: String) = dslChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemDslField, dsl))
    fun logParentChanged(context: WizardContext, language: String, buildSystem: String, isNone: Boolean) = parentChangedEvent.log(context.project, EventPair(sessionIdField, context.sessionId.id), EventPair(screenNumField, context.screen), EventPair(languageField, language), EventPair(buildSystemField, buildSystem), EventPair(buildSystemParentField, isNone))
    fun logAddSampleCodeChanged(context: WizardContext, language: String, buildSystem: String) = addSampleCodeChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logModuleNameChanged(context: WizardContext, language: String, buildSystem: String) = moduleNameChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logContentRootChanged(context: WizardContext, language: String, buildSystem: String) = contentRootChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logModuleFileLocationChanged(context: WizardContext, language: String, buildSystem: String) = moduleFileLocationChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logGroupIdChanged(context: WizardContext, language: String, buildSystem: String) = groupIdChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logArtifactIdChanged(context: WizardContext, language: String, buildSystem: String) = artifactIdChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)
    fun logVersionChanged(context: WizardContext, language: String, buildSystem: String) = versionChangedEvent.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, languageField with language, buildSystemField with buildSystem)

    fun <S> S.logBuildSystemChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logBuildSystemChanged(context, language, buildSystem)
    fun <S> S.logBuildSystemFinished() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logBuildSystemFinished(context, language, buildSystem)
    fun <S> S.logSdkChanged(sdk: Sdk?) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logSdkChanged(context, language, buildSystem, sdk)
    fun <S> S.logSdkFinished(sdk: Sdk?) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logSdkFinished(context, language, buildSystem, sdk)
    fun <S> S.logDslChanged(isUseKotlinDsl: Boolean) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logDslChanged(context, language, buildSystem, isUseKotlinDsl)
    fun <S> S.logParentChanged(isNone: Boolean) where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logParentChanged(context, language, buildSystem, isNone)
    fun <S> S.logAddSampleCodeChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logAddSampleCodeChanged(context, language, buildSystem)
    fun <S> S.logModuleNameChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logModuleNameChanged(context, language, buildSystem)
    fun <S> S.logContentRootChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logContentRootChanged(context, language, buildSystem)
    fun <S> S.logModuleFileLocationChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logModuleFileLocationChanged(context, language, buildSystem)
    fun <S> S.logGroupIdChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logGroupIdChanged(context, language, buildSystem)
    fun <S> S.logArtifactIdChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logArtifactIdChanged(context, language, buildSystem)
    fun <S> S.logVersionChanged() where S: BuildSystemNewProjectWizardData, S: NewProjectWizardStep = logVersionChanged(context, language, buildSystem)
    // @formatter:on

    private val Sdk.featureVersion: Int?
      get() = JavaVersion.tryParse(versionString)?.feature
  }

  object Groovy {
    fun logGroovyLibraryChanged(context: WizardContext, groovyLibrarySource: String, groovyLibraryVersion: String) = groovyLibraryChanged.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, groovySourceTypeField with groovyLibrarySource, groovyVersionField with groovyLibraryVersion)
    fun logGroovyLibraryFinished(context: WizardContext, groovyLibrarySource: String, groovyLibraryVersion: String) = groovyLibraryFinished.log(context.project, sessionIdField with context.sessionId.id, screenNumField with context.screen, groovySourceTypeField with groovyLibrarySource, groovyVersionField with groovyLibraryVersion)
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