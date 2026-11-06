// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.warmup

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.template.CustomLiveTemplate
import com.intellij.codeInsight.template.TemplateSubstitutor
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

private val LOG = logger<TypingWarmUp>()

private val tracer by lazy { TelemetryManager.getSimpleTracer(Scope("startup")) }

/**
 * Creates on BG the typing extensions that the EDT otherwise creates on the first user action in an editor.
 * Warm-up creates the delegates of the typed, Enter/Backspace handlers,
 * completion contributors, etc.
 */
@Service(Service.Level.APP)
internal class TypingWarmUp(private val coroutineScope: CoroutineScope) {
  private val handlerDelegatesStarted = AtomicBoolean()
  private val actionExtensionsStarted = AtomicBoolean()
  private val languageIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** Starts the warm-up for the language of the [file] in the [editor] and returns at once. Each language warms up once. */
  fun start(project: Project, file: VirtualFile, editor: Editor) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    coroutineScope.launch(warmUpContext()) {
      if (handlerDelegatesStarted.compareAndSet(false, true)) {
        warmUpHandlerDelegates()
      }
      warmUpCompletionContributors(project, file)

      // awaitLoaded switches to the EDT twice, so the warm-up skips it when the rest is done already
      val writingAccess = project.serviceAsync<WritingAccessWarmUp>()
      if (writingAccess.isDone && actionExtensionsStarted.get()) {
        return@launch
      }
      if (!awaitLoaded(editor)) {
        return@launch
      }
      writingAccess.warmUpOnce()
      if (actionExtensionsStarted.compareAndSet(false, true)) {
        warmUpActionExtensions()
      }
    }
  }

  private suspend fun warmUpCompletionContributors(project: Project, file: VirtualFile) {
    val language = warmUp("file language") {
      readAction {
        if (!project.isDisposed && file.isValid) LanguageUtil.getLanguageForPsi(project, file) else null
      }
    }
    if (language == null || !languageIds.add(language.id)) {
      return
    }
    span("typing warm-up: completion contributors") {
      warmUp("completion contributors of ${language.id}") {
        CompletionContributor.forLanguage(language, null)
      }
    }
  }

  companion object {
    fun getInstance(): TypingWarmUp = service()
  }
}

/**
 * [WritingAccessProvider.EP] is a project extension point,
 * so each project creates its own providers on its first edit.
 */
@Service(Service.Level.PROJECT)
internal class WritingAccessWarmUp(private val project: Project) {
  private val started = AtomicBoolean()

  val isDone: Boolean
    get() = project.isDefault || started.get()

  suspend fun warmUpOnce() {
    if (project.isDefault || !started.compareAndSet(false, true)) {
      return
    }
    span("typing warm-up: writing access providers") {
      warmUp("writing access providers") { WritingAccessProvider.EP.getExtensions(project) }
    }
  }
}

private fun warmUpContext(): CoroutineContext {
  return Dispatchers.Default + ModalityState.any().asContextElement() + CoroutineName("typing warm-up") +
         tracer.rootSpan("typing warm-up", emptyArray())
}

private suspend fun warmUpHandlerDelegates() {
  span("typing warm-up: handler delegates") {
    warmUp("typed handler delegates") { TypedHandlerDelegate.EP_NAME.extensionList }
    warmUp("Enter handler delegates") { EnterHandlerDelegate.EP_NAME.extensionList }
    warmUp("Backspace handler delegates") { BackspaceHandlerDelegate.EP_NAME.extensionList }
  }
}

/** Creates the extensions of the first copy, paste and template expansion. */
private suspend fun warmUpActionExtensions() {
  span("typing warm-up: copy-paste processors") {
    warmUp("copy-paste pre-processors") { CopyPastePreProcessor.EP_NAME.extensionList }
    warmUp("copy-paste post-processors") { CopyPastePostProcessor.EP_NAME.extensionList }
  }
  span("typing warm-up: live template extensions") {
    warmUp("custom live templates") { CustomLiveTemplate.EP_NAME.extensionList }
    warmUp("template substitutors") { TemplateSubstitutor.EP_NAME.extensionList }
    warmUp("template context types") { TemplateManagerImpl.getAllContextTypes() }
  }
}

/** Waits until the [editor] is loaded. Returns false when the loading fails. A disposed editor cancels the warm-up. */
private suspend fun awaitLoaded(editor: Editor): Boolean {
  try {
    AsyncEditorLoader.awaitLoaded(editor)
    return true
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.debug("The editor did not load. The warm-up skips the editor action extensions.", e)
    return false
  }
}

private inline fun <T> warmUp(name: String, action: () -> T): T? {
  try {
    return action()
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot warm up the $name", e)
    return null
  }
}
