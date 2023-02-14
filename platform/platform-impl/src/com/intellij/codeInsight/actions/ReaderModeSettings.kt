// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.actions.ReaderModeProvider.ReaderMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

interface ReaderModeSettings : Disposable {
  companion object {
    private val EP_READER_MODE_PROVIDER = ExtensionPointName<ReaderModeProvider>("com.intellij.readerModeProvider")
    private val EP_READER_MODE_MATCHER = ExtensionPointName<ReaderModeMatcher>("com.intellij.readerModeMatcher")

    fun getInstance(project: Project): ReaderModeSettings = project.getService(ReaderModeSettings::class.java)

    @RequiresEdt
    fun applyReaderMode(project: Project,
                        editor: Editor?,
                        file: VirtualFile?,
                        fileIsOpenAlready: Boolean = false,
                        forceUpdate: Boolean = false) {
      if (editor == null || file == null || !file.isValid) {
        return
      }

      if (isBlockingApplication()) {
        val matchMode = matchMode(project, file, editor)
        if (matchMode || forceUpdate) {
          applyModeChanged(project, editor, matchMode, fileIsOpenAlready)
        }
      }
      else {
        // caching is required for instant reopen of file with the previously computed mode without irritating file UI changes
        val matchCachedValue = file.getMatchModeCached()

        if (!forceUpdate && matchCachedValue != null) {
          if (matchCachedValue) {
            applyModeChanged(project, editor, true, fileIsOpenAlready)
          }
        }
        else {
          getInstance(project).coroutineScope.launch {
            val matchMode = readAction {
              val value = matchMode(project, file, editor)
              file.setMatchModeCached(value)
              value
            }

            if (matchMode || forceUpdate) {
              withContext(Dispatchers.EDT) {
                applyModeChanged(project, editor, matchMode, fileIsOpenAlready)
              }
            }
          }
        }
      }
    }

    private val MATCHES_READER_MODE_KEY: Key<Boolean> = Key.create("readerMode.matches")

    private fun VirtualFile.getMatchModeCached(): Boolean? {
      return this.getUserData(MATCHES_READER_MODE_KEY)
    }

    private fun VirtualFile.setMatchModeCached(value: Boolean) {
      this.putUserData(MATCHES_READER_MODE_KEY, value)
    }

    @RequiresEdt
    private fun applyModeChanged(project: Project, editor: Editor, matchMode: Boolean, fileIsOpenAlready: Boolean) {
      if (editor.isDisposed) return

      val modeEnabledForFile = getInstance(project).enabled && matchMode
      for (provider in EP_READER_MODE_PROVIDER.extensionList) {
        provider.applyModeChanged(project, editor, modeEnabledForFile, fileIsOpenAlready)
      }
    }

    @Internal
    @Deprecated("Method is not used anymore", ReplaceWith("matchMode(project, file, editor)"))
    @ScheduledForRemoval
    @JvmStatic
    fun matchModeForStats(project: Project, file: VirtualFile, editor: Editor? = null): Boolean {
      return getInstance(project).enabled && matchMode(project, file, editor)
    }

    @RequiresReadLock
    fun matchMode(project: Project?, file: VirtualFile?, editor: Editor? = null): Boolean {
      if (project == null || file == null) return false
      if (PsiManager.getInstance(project).findFile(file) == null) return false
      if (editor != null && editor.isDisposed) return false

      return matchMode(project, file, editor, getInstance(project).mode)
    }

    @RequiresReadLock
    private fun matchMode(project: Project, file: VirtualFile, editor: Editor?, mode: ReaderMode): Boolean {
      for (m in EP_READER_MODE_MATCHER.lazySequence()) {
        val matched = m.matches(project, file, editor, mode)
        if (matched != null) {
          return matched
        }
      }

      if (ApplicationManager.getApplication().isHeadlessEnvironment) return false

      val inFileInLibraries by lazy {
        FileIndexFacade.getInstance(project).isInLibraryClasses(file)
        || FileIndexFacade.getInstance(project).isInLibrarySource(file)
      }
      val isWritable = file.isWritable

      return when (mode) {
        ReaderMode.LIBRARIES_AND_READ_ONLY -> !isWritable || inFileInLibraries
        ReaderMode.LIBRARIES -> inFileInLibraries
        ReaderMode.READ_ONLY -> !isWritable
      }
    }

    private fun isBlockingApplication(): Boolean {
      val application = ApplicationManager.getApplication()
      return application.isHeadlessEnvironment || application.isUnitTestMode
    }
  }

  data class Scheme(var name: String? = CodeStyleScheme.DEFAULT_SCHEME_NAME, var isProjectLevel: Boolean = false)

  @get:Internal
  val coroutineScope: CoroutineScope

  fun getVisualFormattingCodeStyleSettings(project: Project): CodeStyleSettings? {
    return if (enableVisualFormatting) {
      if (useActiveSchemeForVisualFormatting) {
        CodeStyle.getSettings(project)
      }
      else {
        val codeStyleSchemes = CodeStyleSchemes.getInstance()
        if (visualFormattingChosenScheme.name == CodeStyleScheme.PROJECT_SCHEME_NAME
            && visualFormattingChosenScheme.isProjectLevel) {
          CodeStyleSettingsManager.getInstance(project).mainProjectCodeStyle
        }
        else {
          visualFormattingChosenScheme.name?.let { codeStyleSchemes.findSchemeByName(it)?.codeStyleSettings }
        } ?: codeStyleSchemes.defaultScheme.codeStyleSettings
      }
    }
    else {
      null
    }
  }

  var visualFormattingChosenScheme: Scheme

  var useActiveSchemeForVisualFormatting: Boolean

  var enableVisualFormatting: Boolean

  var showLigatures: Boolean

  var increaseLineSpacing: Boolean

  var showInlaysHints: Boolean

  var showRenderedDocs: Boolean

  var showWarnings: Boolean

  var enabled: Boolean

  var mode: ReaderMode
}