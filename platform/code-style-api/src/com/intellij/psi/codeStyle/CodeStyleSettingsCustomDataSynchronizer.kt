// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCustomDataConsumer
import com.intellij.openapi.vfs.VirtualFileCustomDataProvider
import com.intellij.util.LineSeparator
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jdom.Element
import kotlin.reflect.KType
import kotlin.reflect.typeOf


abstract class CodeStyleSettingsCustomDataSynchronizer<T : CustomCodeStyleSettings>
  : VirtualFileCustomDataProvider<ByteArray>, VirtualFileCustomDataConsumer<ByteArray> {
  companion object {
    private val LOG = logger<CodeStyleSettingsCustomDataSynchronizer<*>>()

    private const val COMMON_SETTINGS_TAG = "common"
    private const val CUSTOM_SETTINGS_TAG = "custom"
  }

  protected abstract val language: Language

  protected abstract val customCodeStyleSettingsClass: Class<T>

  final override val id: String get() = "CodeStyleSettingsCustomDataSynchronizer_${language.id}"

  final override val dataType: KType get() = typeOf<ByteArray>()

  final override fun getValues(project: Project, virtualFile: VirtualFile): Flow<ByteArray> {
    return project.messageBus.subscribeAsFlow(CodeStyleSettingsListener.TOPIC) {
      trySend(null)
      object : CodeStyleSettingsListener {
        override fun codeStyleSettingsChanged(event: CodeStyleSettingsChangeEvent) {
          if (event.project != project ||
              event.virtualFile != null && event.virtualFile != virtualFile) return
          trySend(event.settings)
        }
      }
    }.mapNotNull {
      val (common, custom) = getActiveCodeStyleSettings(project, virtualFile, it) ?: return@mapNotNull null

      val provider = LanguageCodeStyleProvider.forLanguage(language) ?: run {
        LOG.trace { "Cannot find LanguageCodeStyleProvider for lang ${language}. provider.id=$id" }
        return@mapNotNull null
      }

      val commonSettingsElement = Element(COMMON_SETTINGS_TAG)
      val customSettingsElement = Element(CUSTOM_SETTINGS_TAG)

      try {
        common.writeExternal(commonSettingsElement, provider)
        val customSettingsInstance = provider.createCustomSettings(CodeStyleSettings.getDefaults())
        if (customSettingsInstance != null) {
          custom.writeExternal(customSettingsElement, customSettingsInstance)
        } else {
          LOG.warn("Unable to create custom settings instance for ${language.id}, provider.id=$id. Custom Settings will not be written")
        }
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        LOG.error(e)
        return@mapNotNull null
      }

      val element = Element("root")
      element.addContent(commonSettingsElement)
      element.addContent(customSettingsElement)

      element.toByteArray()
    }
  }

  final override suspend fun consumeValue(project: Project, virtualFile: VirtualFile, value: ByteArray) {
    val element = try {
      JDOMUtil.load(value)
    }
    catch (e: Exception) {
      LOG.error(e)
      return
    }

    val (common, custom) = getActiveCodeStyleSettings(project, virtualFile, null) ?: return

    val provider = LanguageCodeStyleProvider.forLanguage(language) ?: run {
      LOG.trace { "Cannot find LanguageCodeStyleProvider for lang ${language}. provider.id=$id" }
      return
    }

    val commonSettingsElement = element.getChild(COMMON_SETTINGS_TAG)
    val customSettingsElement = element.getChild(CUSTOM_SETTINGS_TAG)

    val oldCommon = common.clone(CodeStyleSettings.getDefaults())
    val oldCustom = custom.clone() as CustomCodeStyleSettings

    try {
      common.applyFromExternal(commonSettingsElement, provider.defaultCommonSettings)
      val customSettingsInstance = provider.createCustomSettings(CodeStyleSettings.getDefaults())
      if (customSettingsInstance != null) {
        custom.applyFromExternal(customSettingsElement, customSettingsInstance)
      } else {
        LOG.warn("Unable to create custom settings instance for ${language.id}, provider.id=$id. Custom Settings will not be applied")
      }

      CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile)
    }
    catch (e: Exception) {
      try {
        if (e is CancellationException) LOG.trace("cancellation exception during code style settings synchronization. synchronizer.id=$id")
        else LOG.error("exception during code style settings synchronization. synchronizer.id=$id", e)

        common.copyFrom(oldCommon)
        custom.copyFrom(oldCustom)
      }
      finally {
        if (e is CancellationException) throw e
      }
    }
  }

  private suspend fun getActiveCodeStyleSettings(project: Project,
                                                 virtualFile: VirtualFile,
                                                 eventSettings: CodeStyleSettings?): Pair<CommonCodeStyleSettings, T>? {
    return readAction {
      val settings = if (eventSettings != null) {
        eventSettings
      }
      else {
        val psiFile = VirtualFileCustomDataProvider.getPsiFileSafe(virtualFile, project) ?: run {
          LOG.trace { "Cannot get PSI file for ${virtualFile.path}. provider.id=$id" }
          return@readAction null
        }
        CodeStyle.getSettings(psiFile)
      }
      val common = settings.getCommonSettings(language)
      val custom = settings.getCustomSettings(customCodeStyleSettingsClass)
      Pair(common, custom)
    }
  }

  private fun CommonCodeStyleSettings.applyFromExternal(commonSettingsElement: Element?,
                                                        defaultSettings: CommonCodeStyleSettings) {
    // Turning the current settings instance to a "default" state before applying the changes
    // got from host is necessary because these serialized changes represent a difference between
    // the actual settings state on host and the default state.
    copyFrom(defaultSettings)
    readExternal(commonSettingsElement)
  }

  private fun CustomCodeStyleSettings.applyFromExternal(customSettingsElement: Element?,
                                                        defaultSettings: CustomCodeStyleSettings) {
    // same as CodeStyleSettingsCustomDataSynchronizer.applyFromExternal(CommonCodeStyleSettings, Element, CommonCodeStyleSettings)
    copyFrom(defaultSettings)
    readExternal(customSettingsElement)
  }

  private fun CustomCodeStyleSettings.copyFrom(original: CustomCodeStyleSettings) {
    val differenceElement = Element("difference")
    original.writeExternal(differenceElement, this)
    this.readExternal(differenceElement)
  }

  // inspired by com.intellij.util.JdomKt.toByteArray. no dependency on module with this code
  private fun Element.toByteArray(): ByteArray {
    val out = BufferExposingByteArrayOutputStream(1024)
    JDOMUtil.write(this, out, LineSeparator.LF.separatorString)
    return out.toByteArray()
  }
}