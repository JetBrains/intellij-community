// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

/**
 * Proposals provider for inline completion.
 *
 * Leave feedback or feature requests in [YouTrack](https://youtrack.jetbrains.com/issues/IDEA?q=Subsystem:%20%7BEditor.%20Code%20Completion.%20Inline%7D%20)
 *
 * #### Things to note:
 *   - Currently delay ([Flow.debounce]) for [InlineCompletionEvent.DocumentChange] is disabled and might be implemented later.
 *   You may implement it on provider side or use [DebouncedInlineCompletionProvider] as an entrypoint,
 *   otherwise, proposals will be generated/canceled on each typing.
 *   - Any inline completion request will be cancelled if inline is in rendering mode
 *   - In case a newer inline completion proposals are generated, previous call will be cancelled and hidden
 *   - If some event requires hiding of shown elements, implement [restartOn]
 *   - If you need to do something specific after insertion of provided elements, provide custom [InlineCompletionInsertHandler]
 *   - If some elements are rendered and a user types a new symbol, [suggestionUpdateManager] is used to update rendered elements.
 *
 * If you need custom logic, like invoking completion, getting current state or listen for events:
 * - [InlineCompletionHandler] for everything related to actions with inline completion, adding listeners, ect (get for editor via [InlineCompletion.getHandlerOrNull])
 * - By default only main editor is supported. If you need custom one - use [InlineCompletion.install]
 * - To get if inline completion is currently shown or get current state see [InlineCompletionSession] and [InlineCompletionContext]
 * - To test this feature use `InlineCompletionLifecycleTestDSL` via `CodeInsightTestFixture.testInlineCompletion`
 *
 * @see InlineCompletionElement
 * @see InlineCompletionRequest
 * @see InlineCompletionEvent
 * @see InlineCompletionInsertHandler
 * @see InlineCompletionSuggestionUpdateManager
 * @see InlineCompletionProviderPresentation
 */
interface InlineCompletionProvider {
  /**
   * Provider identifier, must match extension `id` in `plugin.xml`. Used to identify provider.
   *
   * - Prefer to use class qualified name as id to avoid duplicates
   *
   * ```
   * <inline.completion.provider id="<id_here>" .../>
   * ```
   */
  val id: InlineCompletionProviderID

  /**
   * Allow to add custom tooltip presentation for inline suggestion
   */
  val providerPresentation: InlineCompletionProviderPresentation
    get() = InlineCompletionProviderPresentation.dummy(this)

  /**
   * Retrieves an inline completion suggestion based on the provided request.
   *
   * Suggestion now can return multiple variants,
   * and they might be rendered as streaming using [Flow].
   *
   * @param request The inline completion request containing information about the event, file, editor, document,
   * startOffset, endOffset, and lookupElement.
   * @return The inline completion suggestion. Use [InlineCompletionSuggestion.Empty] to return empty suggestion.
   */
  suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion

  /**
   * Determines whether the given inline completion event enables the feature.
   *
   * This method runs in edt and will block UI thread, use only simple check here like settings.
   * Note, that request might be invoked by different events (typing in editor, lookup navigation, action call or custom one)
   * and provider needs to handle them all.
   *
   * @return True if the feature is enabled for the event, false otherwise.
   */
  @RequiresEdt
  fun isEnabled(event: InlineCompletionEvent): Boolean

  /**
   * Determines whether the given inline completion event should restart current session and .
   * If provider renders something and returns true here, then getSuggestion is invoked again.
   * Also, the same provider will be called in this case.
   *
   * @return True if the session should be restarted, false otherwise.
   */
  fun restartOn(event: InlineCompletionEvent): Boolean = false

  /**
   * This method allows to implement any specific behavior needed after the inline completion suggestion has been selected and inserted.
   * This can include any necessary cleanup or additional insertion behavior.
   */
  val insertHandler: InlineCompletionInsertHandler
    get() = DefaultInlineCompletionInsertHandler.INSTANCE

  /**
   * Reacts on [InlineCompletionEvent] while a session exists and update the current suggestions.
   *
   * @see InlineCompletionSuggestionUpdateManager
   */
  val suggestionUpdateManager: InlineCompletionSuggestionUpdateManager
    get() = InlineCompletionSuggestionUpdateManager.Default.INSTANCE

  companion object {
    val EP_NAME = ExtensionPointName.create<InlineCompletionProvider>("com.intellij.inline.completion.provider")
    fun extensions(): List<InlineCompletionProvider> = EP_NAME.extensionList
  }

  object DUMMY : InlineCompletionProvider {
    override val id = InlineCompletionProviderID("DUMMY")
    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion = InlineCompletionSuggestion.Empty
    override fun isEnabled(event: InlineCompletionEvent): Boolean = false
  }
}

@JvmInline
value class InlineCompletionProviderID(val id: String)
