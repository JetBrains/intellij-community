// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCustomDataConsumer
import com.intellij.openapi.vfs.VirtualFileCustomDataProvider
import com.intellij.psi.PsiManager
import com.intellij.util.LineSeparator
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jdom.Element
import kotlin.reflect.KType
import kotlin.reflect.typeOf


abstract class CodeStyleSettingsCustomDataSynchronizer<T : CustomCodeStyleSettings> : VirtualFileCustomDataProvider<ByteArray>, VirtualFileCustomDataConsumer<ByteArray> {
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
      trySend(Unit)
      object : CodeStyleSettingsListener {
        override fun codeStyleSettingsChanged(event: CodeStyleSettingsChangeEvent) {
          if (event.project != project ||
              event.virtualFile != null && event.virtualFile != virtualFile) return
          trySend(Unit)
        }
      }
    }.mapNotNull {
      val (common, custom) = readAction {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: run {
          LOG.trace { "Cannot find PSI file for ${virtualFile.path}. provider.id=$id" }
          return@readAction null
        }
        val custom = CodeStyle.getSettings(psiFile).getCustomSettings(customCodeStyleSettingsClass)
        val common = CodeStyle.getSettings(psiFile).getCommonSettings(language)
        Pair(common, custom)
      } ?: return@mapNotNull null

      val provider = LanguageCodeStyleProvider.forLanguage(language) ?: run {
        LOG.trace { "Cannot find LanguageCodeStyleProvider for lang ${language}. provider.id=$id" }
        return@mapNotNull null
      }

      val commonSettingsElement = Element(COMMON_SETTINGS_TAG)
      val customSettingsElement = Element(CUSTOM_SETTINGS_TAG)

      try {
        common.writeExternal(commonSettingsElement, provider)
        custom.writeExternal(customSettingsElement, provider.createCustomSettings(CodeStyleSettings.getDefaults()))
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

  // inspired by com.intellij.util.JdomKt.toByteArray. no dependency on module with this code
  private fun Element.toByteArray(): ByteArray {
    val out = BufferExposingByteArrayOutputStream(1024)
    JDOMUtil.write(this, out, LineSeparator.LF.separatorString)
    return out.toByteArray()
  }

  final override fun consumeValue(project: Project, virtualFile: VirtualFile, value: ByteArray) {
    val element = try {
      JDOMUtil.load(value)
    }
    catch (e: Exception) {
      LOG.error(e)
      return
    }

    val (common, custom) = ApplicationManager.getApplication().runReadAction<Pair<CommonCodeStyleSettings, T>> {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: run {
        LOG.trace { "Cannot find PSI file for ${virtualFile.path}. provider.id=$id" }
        return@runReadAction null
      }
      val custom = CodeStyle.getSettings(psiFile).getCustomSettings(customCodeStyleSettingsClass)
      val common = CodeStyle.getSettings(psiFile).getCommonSettings(language)
      Pair(common, custom)
    } ?: return

    val provider = LanguageCodeStyleProvider.forLanguage(language) ?: run {
      LOG.trace { "Cannot find LanguageCodeStyleProvider for lang ${language}. provider.id=$id" }
      return
    }

    val commonSettingsElement = element.getChild(COMMON_SETTINGS_TAG)
    val customSettingsElement = element.getChild(CUSTOM_SETTINGS_TAG)

    // Turning the current settings instance to a "default" state before applying the changes
    // got from host is necessary because these changes represent a difference between
    // the actual settings state on host and the default state.
    // TODO: restore the state of each settings in case of exception during the settings modification

    common.copyFrom(provider.defaultCommonSettings)
    common.readExternal(commonSettingsElement)

    resetToDefaultState(custom, provider.createCustomSettings(CodeStyleSettings.getDefaults()))
    custom.readExternal(customSettingsElement)

    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile)
  }

  private fun resetToDefaultState(customCodeStyleSettings: T, defaultCustomSettings: CustomCodeStyleSettings) {
    val defaultsElement = Element("default")
    defaultCustomSettings.writeExternal(defaultsElement, customCodeStyleSettings)
    customCodeStyleSettings.readExternal(defaultsElement)
  }
}