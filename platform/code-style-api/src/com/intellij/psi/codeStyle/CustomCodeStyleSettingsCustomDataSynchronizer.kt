// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCustomDataConsumer
import com.intellij.openapi.vfs.VirtualFileCustomDataProvider
import com.intellij.psi.PsiManager
import com.intellij.util.LineSeparator
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jdom.Element
import kotlin.reflect.KType
import kotlin.reflect.typeOf

abstract class CustomCodeStyleSettingsCustomDataSynchronizer<T : CustomCodeStyleSettings> : VirtualFileCustomDataProvider<ByteArray>, VirtualFileCustomDataConsumer<ByteArray> {
  companion object {
    private val LOG = logger<CustomCodeStyleSettingsCustomDataSynchronizer<*>>()
  }

  protected abstract val customCodeStyleSettingsClass: Class<T>

  /**
   * Any instance of [T] class, but it must be immutable,
   * and it must return equal results independently on is it called on Host side or Thin Client side
   */
  protected abstract val defaultSettings: T

  final override val dataType: KType
    get() = typeOf<ByteArray>()

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
      val customCodeStyleSettings = readAction {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: run {
          LOG.trace("Cannot find PSI file for ${virtualFile.path}. provider.id=$id")
          return@readAction null
        }
        CodeStyle.getSettings(psiFile).getCustomSettings(customCodeStyleSettingsClass)
      } ?: return@mapNotNull null

      val element = Element("name")
      try {
        customCodeStyleSettings.writeExternal(element, defaultSettings)
      }
      catch (e: Exception) {
        LOG.error(e)
        return@mapNotNull null
      }
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

    val customCodeStyleSettings = ApplicationManager.getApplication().runReadAction<T?> {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: run {
        LOG.trace("Cannot find PSI file for ${virtualFile.path}. provider.id=$id")
        return@runReadAction null
      }
      return@runReadAction CodeStyle.getSettings(psiFile).getCustomSettings(customCodeStyleSettingsClass)
    } ?: return

    // turning current settings to a "default" state before applying the changes got from host,
    // because these changes represent a difference between the actual settings state on host and the default state.
    resetToDefaultState(customCodeStyleSettings)

    // applying the changes got from host
    customCodeStyleSettings.readExternal(element)

    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(virtualFile)
  }

  private fun resetToDefaultState(customCodeStyleSettings: T) {
    val defaultsElement = Element("default")
    defaultSettings.writeExternal(defaultsElement, customCodeStyleSettings)
    customCodeStyleSettings.readExternal(defaultsElement)
  }
}