// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import kotlin.random.Random

class InlineCompletionRequest(
  val event: InlineCompletionEvent,

  val file: PsiFile,
  val editor: Editor,
  val document: Document,
  val startOffset: Int,
  val endOffset: Int,
  val lookupElement: LookupElement? = null,
) : UserDataHolderBase() {
  @ApiStatus.Internal
  val requestId: Long = Random.nextLong()
}

sealed interface TypingEvent {

  val typed: String

  val range: TextRange

  class OneSymbol @ApiStatus.Internal constructor(symbol: Char, offset: Int) : TypingEvent {
    override val typed: String = symbol.toString()

    override val range: TextRange = TextRange(offset, offset + 1)
  }

  class NewLine @ApiStatus.Internal constructor(override val typed: String, override val range: TextRange) : TypingEvent

  class PairedEnclosureInsertion @ApiStatus.Internal constructor(override val typed: String, offset: Int) : TypingEvent {
    override val range: TextRange = TextRange(offset, offset) // caret does not move
  }
}

/**
 * Represents an event that occurs in the editor and directly affects inline completion.
 *
 * The `InlineCompletionEvent` interface serves two primary purposes:
 * 1. To invoke inline completion and create a new session. See [InlineCompletionProvider.isEnabled].
 * 2. To update the currently rendered inline completion. See [InlineCompletionSuggestionUpdateManager].
 *
 * Some events may have restrictions (either natural or technical) and might not be usable for both purposes.
 *
 * **Note:** Please do not extend this interface outside the IntelliJ platform. If custom functionality is needed,
 * use [ManualCall] instead. At some point, this interface might become `sealed`.
 */
@ApiStatus.NonExtendable
interface InlineCompletionEvent {

  fun toRequest(): InlineCompletionRequest?

  /**
   * Indicates that this event can trigger only a provider with [providerId].
   * Other providers will not be asked for this event.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  sealed interface WithSpecificProvider {
    // Inheritors should not leak this to the public API as the way we 'filter' providers may change in the future.
    @get:ApiStatus.Internal
    val providerId: InlineCompletionProviderID
  }

  /**
   * A class representing a direct call in the code editor by [InsertInlineCompletionAction].
   *
   * Please do not invoke it directly as it has some additional semantics.
   * Like the 'no suggestions' tooltip if providers have nothing to propose.
   * It should be used only when a user explicitly invokes the inline completion.
   *
   * Use [ManualCall] instead as it guarantees that exactly your provider is going to be called.
   */
  class DirectCall @ApiStatus.Internal constructor(
    val editor: Editor,
    val caret: Caret,
    val context: DataContext? = null,
  ) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      return getRequest(event = this, editor = editor, specificCaret = caret)
    }
  }

  /**
   * Event for manually calling a specific provider.
   *
   * This event should be the sole event called manually. [additionalData] (not stable) can be used to differentiate
   * various purposes for manual calls.
   *
   * Only the provider with [providerId] will be asked, no other providers.
   *
   * Remote Development policy: TBD.
   *
   * Implementation notes: you still need to support this event in [InlineCompletionProvider.isEnabled].
   *
   * @param editor The editor instance.
   * @param providerId The ID of the specific provider to be triggered.
   * @param additionalData The data context for the call (not stable).
   */
  @ApiStatus.Experimental
  class ManualCall(
    val editor: Editor,

    @ApiStatus.Internal
    override val providerId: InlineCompletionProviderID,

    @ApiStatus.Experimental
    val additionalData: UserDataHolder,
  ) : InlineCompletionEvent, WithSpecificProvider {

    override fun toRequest(): InlineCompletionRequest? {
      return getRequest(event = this, editor = editor)
    }

    @ApiStatus.Internal
    companion object
  }

  /**
   * Represents a typing in an editor.
   *
   * Since document changes are hard to correctly track, it's forbidden to create them outside this module.
   */
  class DocumentChange @ApiStatus.Internal constructor(val typing: TypingEvent, val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      return getRequest(
        event = this,
        editor = editor,
        specificStartOffset = typing.range.startOffset,
        specificEndOffset = typing.range.endOffset
      )
    }
  }

  /**
   * Represents a backspace hit for removal of characters in an editor. Backspace is allowed if:
   * * There is no selection
   * * There is only one caret
   * * Only one character is removed
   *
   * More or fewer cases may be supported in the future.
   *
   * It is triggered after the backspace is processed.
   *
   * **Note**: for now, it's impossible to update a session with this event. Inline Completion will be hidden once a backspace is pressed.
   */
  @ApiStatus.Experimental
  class Backspace @ApiStatus.Internal constructor(val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      // TODO offset?
      return getRequest(event = this, editor = editor)
    }
  }

  /**
   * A class representing a lookup event.
   *
   * This class implements the [InlineCompletionEvent] interface and provides a method to convert the event to a request
   * using the [toRequest] method.
   *
   * @param event The lookup event.
   */
  class LookupChange @ApiStatus.Internal constructor(
    @ApiStatus.Experimental
    override val editor: Editor,
    override val event: LookupEvent,
  ) : InlineLookupEvent {

    @Deprecated("This event should not be created outside the platform.")
    @ScheduledForRemoval
    @ApiStatus.Internal
    constructor(event: LookupEvent) : this(
      runReadAction { event.lookup!!.editor },
      event
    )

    override fun toRequest(): InlineCompletionRequest? {
      return super.toRequest()?.takeIf { it.lookupElement != null }
    }
  }

  /**
   * Represents an event when a lookup is cancelled during inline completion.
   *
   * @param event The lookup event associated with the cancellation.
   */
  class LookupCancelled @ApiStatus.Internal constructor(
    @ApiStatus.Experimental
    override val editor: Editor,
    override val event: LookupEvent
  ) : InlineLookupEvent {

    @Deprecated("This event should not be created outside the platform.")
    @ScheduledForRemoval
    @ApiStatus.Internal
    constructor(event: LookupEvent) : this(
      runReadAction { event.lookup!!.editor },
      event
    )
  }

  sealed interface InlineLookupEvent : InlineCompletionEvent {

    @get:ApiStatus.Experimental
    val editor: Editor

    val event: LookupEvent

    override fun toRequest(): InlineCompletionRequest? {
      val editor = runReadAction { event.lookup?.editor } ?: return null
      return getRequest(
        event = this,
        editor = editor,
        getLookupElement = { event.item }
      )
    }
  }

  /**
   * Indicates that another Inline Completion suggestion is inserted.
   *
   * Cannot be used as an 'update event' in
   * [com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager],
   * because this event means that a session is finished.
   *
   * For now, only [providerId] can start a session with this event **by design**. This decision may be changed later.
   *
   * @param providerId the provider whose completion is inserted.
   */
  @ApiStatus.Experimental
  class SuggestionInserted @ApiStatus.Internal constructor(
    val editor: Editor,

    @ApiStatus.Internal
    override val providerId: InlineCompletionProviderID,
  ) : InlineCompletionEvent, WithSpecificProvider {

    override fun toRequest(): InlineCompletionRequest? {
      return getRequest(event = this, editor = editor)
    }
  }

  /**
   * Triggered by insertion of templates, like Live Templates.
   */
  @ApiStatus.Experimental
  class TemplateInserted @ApiStatus.Internal constructor(val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      return getRequest(event = this, editor = editor)
    }
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  sealed class PartialAccept @ApiStatus.Internal constructor(val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val session = InlineCompletionSession.getOrNull(editor) ?: return null
      // Offset depends on specific insertion implementation, so no way to guess it here
      return getRequest(event = this, editor = editor, specificFile = session.request.file)
    }
  }

  /**
   * **This event is not intended to be a start of the inline completion.**
   *
   * This event is to be called when some inline completion suggestion is already rendered.
   * It inserts the first word from the suggestion to the editor.
   */
  @ApiStatus.Experimental
  class InsertNextWord @ApiStatus.Internal constructor(editor: Editor) : PartialAccept(editor)

  /**
   * **This event is not intended to be a start of the inline completion.**
   *
   * This event is to be called when some inline completion suggestion is already rendered.
   * It inserts the first line from the suggestion to the editor.
   */
  @ApiStatus.Experimental
  class InsertNextLine @ApiStatus.Internal constructor(editor: Editor) : PartialAccept(editor)
}

private fun getPsiFile(caret: Caret, project: Project): PsiFile? {
  return runReadAction {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(caret.editor.document) ?: return@runReadAction null
    // * [PsiUtilBase] takes into account injected [PsiFile] (like in Jupyter Notebooks)
    // * However, it loads a file into the memory, which is expensive
    // * Some tests forbid loading a file when tearing down
    // * On tearing down, Lookup Cancellation happens, which causes the event
    // * Existence of [treeElement] guarantees that it's in the memory
    if (file.isLoadedInMemory()) {
      PsiUtilBase.getPsiFileInEditor(caret, project)
    }
    else {
      file
    }
  }
}

private fun PsiFile.isLoadedInMemory(): Boolean {
  return (this as? PsiFileImpl)?.treeElement != null
}

private inline fun getRequest(
  event: InlineCompletionEvent,
  editor: Editor,
  specificFile: PsiFile? = null,
  specificCaret: Caret? = null,
  specificStartOffset: Int? = null,
  specificEndOffset: Int? = null,
  crossinline getLookupElement: () -> LookupElement? = { null },
): InlineCompletionRequest? {
  return runReadAction {
    if (editor.caretModel.caretCount != 1) {
      return@runReadAction null
    }
    val caret = specificCaret ?: editor.caretModel.currentCaret
    val project = editor.project ?: return@runReadAction null
    val file = specificFile ?: getPsiFile(caret, project) ?: return@runReadAction null
    val offset = caret.offset
    InlineCompletionRequest(
      event = event,
      file = file,
      editor = editor,
      document = editor.document,
      startOffset = specificStartOffset ?: offset,
      endOffset = specificEndOffset ?: offset,
      lookupElement = getLookupElement()
    )
  }
}
