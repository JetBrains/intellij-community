// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemTag
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

sealed class LspCompletionCustomizer

/**
 * Handles [CompletionItem](https://microsoft.github.io/language-server-protocol/specification#completionItem) objects
 * received from the LSP server.
 * Implementations may fine-tune the code completion behavior.
 * For example, they may filter out unneeded completion items or tweak completion item decoration.
 */
open class LspCompletionSupport : LspCompletionCustomizer() {
  /**
   * This function is called when the user types [charTyped] in the editor,
   * but only if the typed character is listed in the server capabilities as one of the characters that should trigger code completion
   * ([CompletionOptions.triggerCharacters](https://microsoft.github.io/language-server-protocol/specification/#completionOptions)).
   * According to the LSP specification, the IDE should initiate a code completion session when such a character is typed.
   *
   * By default, this function returns `true`, which triggers a code completion session.
   * All applicable code completion contributors will be invoked to provide their completion items.
   * LSP-based code completion is controlled by the [shouldRunCodeCompletion] function and other functions in this class.
   *
   * Implementations may override this function and return `false` to ignore the presence of the typed character
   * in the server's `CompletionOptions.triggerCharacters`.
   */
  @RequiresEdt
  @RequiresWriteLock
  open fun isTriggerCharacterRespected(charTyped: Char): Boolean = true

  /**
   * This function is called when the IDE is running a code completion session.
   * Code completion could have been triggered, for example, by pressing a shortcut, by typing an identifier,
   * or by typing a 'trigger' character (see [isTriggerCharacterRespected]).
   * This function controls whether the LSP server should be asked for code completion items at the given location.
   */
  @RequiresReadLock
  open fun shouldRunCodeCompletion(parameters: CompletionParameters): Boolean = true

  /**
   * Controls whether the IDE should try to resolve [completionItem] before updating its lookup element presentation.
   *
   * If this function returns `true`, and the LSP server advertises `CompletionOptions.resolveProvider`, the IDE sends the
   * [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specification/#completionItem_resolve) request.
   *
   * Override this function and return `false` for completion items that should use only the data returned by `textDocument/completion`,
   * for example, to avoid expensive or redundant resolve requests.
   */
  open fun shouldResolveCompletionItem(completionItem: CompletionItem): Boolean = true

  /**
   * A code completion prefix is a sequence of characters to the left of the caret in the editor,
   * which is used by the IDE to filter completion items.
   *
   * It's recommended that the LSP server explicitly provides
   * [CompletionItem.textEdit](https://microsoft.github.io/language-server-protocol/specification/#completionItem).
   * In this case, a completion prefix is calculated based on the LSP server response.
   *
   * If the LSP server doesn't provide `CompletionItem.textEdit` explicitly,
   * the IDE uses the result returned by this function to filter completion items received from the server.
   *
   * Override this function if the LSP server doesn't provide `CompletionItem.textEdit`
   * and the [defaultPrefix] calculated by the IDE is incorrect.
   *
   * @param defaultPrefix completion prefix calculated by the IDE using the [CompletionService.suggestPrefix] method
   */
  @ApiStatus.Experimental
  @RequiresReadLock
  @RequiresBackgroundThread
  open fun getCompletionPrefix(parameters: CompletionParameters, defaultPrefix: String): String = defaultPrefix

  /**
   * Converts [CompletionItem](https://microsoft.github.io/language-server-protocol/specification#completionItem) object
   * received from the LSP server into [LookupElement] object.
   *
   * Note that implementations shouldn't manipulate the completion item presentation in this function.
   * To tune the presentation, override functions like [getIcon], [isBold], [isStrikeout], [getTypeText], or [getTailText].
   * In advanced use cases, plugins can override [renderLookupElement].
   *
   * Some ideas that the overriding functions can implement:
   * - return `null` if they want to ignore this [item]
   * - tune completion item priority:
   *   ```
   *   PrioritizedLookupElement.withPriority(super.createLookupElement(parameters, item), priority)
   *   ```
   * - use [parameters] if they need to check the context, in which this code completion session has started.
   *   Note that [parameters.originalFile][CompletionParameters.getOriginalFile] might be an
   *   [injected](https://plugins.jetbrains.com/docs/intellij/language-injection.html) file,
   *   while all `lsp4j` entities (including [item]) always deal with the host file (also known as a top-level file).
   *   [InjectedLanguageManager] helps to map offsets between an injected and a host file.
   */
  open fun createLookupElement(parameters: CompletionParameters, item: CompletionItem): LookupElement? {
    val toUseForPrefixMatching = item.filterText?.let { StringUtilRt.convertLineSeparators(it) }
                                 ?: item.label
    val toInsertRaw = item.textEdit?.map ({ it.newText }, { it.newText })
                      ?: item.insertText
                      ?: item.label
    val toInsert = StringUtilRt.convertLineSeparators(toInsertRaw)

    return LookupElementBuilder.create(item, toInsert)
      .let { if (toInsert == toUseForPrefixMatching) it else it.withLookupString(toUseForPrefixMatching) }
  }

  /**
   * Typically, plugins don't need to override this function as they can tune the completion item presentation
   * by overriding [getIcon], [isBold], [isStrikeout], [getTypeText], or [getTailText].
   *
   * This function is usually called twice: first, for the initial [CompletionItem][item],
   * and later for the [resolved](https://microsoft.github.io/language-server-protocol/specification/#completionItem_resolve) one.
   */
  open fun renderLookupElement(item: CompletionItem, presentation: LookupElementPresentation) {
    presentation.itemText = item.label
    presentation.icon = getIcon(item)
    presentation.isItemTextBold = isBold(item)
    presentation.isStrikeout = isStrikeout(item)
    presentation.setTailText(getTailText(item), true)
    presentation.typeText = getTypeText(item)
    presentation.isTypeGrayed = true
  }

  @ApiStatus.Internal
  open suspend fun expensiveRenderLookupElement(completionItem: CompletionItem, presentation: LookupElementPresentation) {
    renderLookupElement(completionItem, presentation)
  }

  /**
   * Compare with [LspSymbolKindCustomizer.getIcon] for similar, but incompatible icon mapping
   */
  protected open fun getIcon(item: CompletionItem): Icon? = when (item.kind) {
    CompletionItemKind.Text -> AllIcons.Nodes.Word
    CompletionItemKind.Method -> AllIcons.Nodes.Method
    CompletionItemKind.Function -> AllIcons.Nodes.Function
    CompletionItemKind.Constructor -> AllIcons.Nodes.Class // no special icon
    CompletionItemKind.Field -> AllIcons.Nodes.Field
    CompletionItemKind.Variable -> AllIcons.Nodes.Variable
    CompletionItemKind.Class -> AllIcons.Nodes.Class
    CompletionItemKind.Interface -> AllIcons.Nodes.Interface
    CompletionItemKind.Module -> null // 'module' may mean different things
    CompletionItemKind.Property -> AllIcons.Nodes.Property
    CompletionItemKind.Unit -> null // no standard icon
    CompletionItemKind.Value -> null // no standard icon
    CompletionItemKind.Enum -> AllIcons.Nodes.Enum
    CompletionItemKind.Keyword -> null // icon is not needed
    CompletionItemKind.Snippet -> AllIcons.Nodes.Template
    CompletionItemKind.Color -> AllIcons.Actions.Colors
    CompletionItemKind.File -> AllIcons.FileTypes.Any_type
    CompletionItemKind.Reference -> null // no standard icon
    CompletionItemKind.Folder -> AllIcons.Nodes.Folder
    CompletionItemKind.EnumMember -> AllIcons.Nodes.Enum // the same as for Enum
    CompletionItemKind.Constant -> AllIcons.Nodes.Constant
    CompletionItemKind.Struct -> AllIcons.Json.Object // looks like `{}`
    CompletionItemKind.Event -> null // no standard icon
    CompletionItemKind.Operator -> null // no standard icon
    CompletionItemKind.TypeParameter -> AllIcons.Nodes.Type
    null -> null
  }

  protected open fun isBold(item: CompletionItem): Boolean = item.kind == CompletionItemKind.Keyword

  protected open fun isStrikeout(item: CompletionItem): Boolean {
    @Suppress("DEPRECATION") // old LSP server implementations may use deprecated property `item.deprecated`
    return item.deprecated == true || item.tags?.contains(CompletionItemTag.Deprecated) == true
  }

  protected open fun getTailText(item: CompletionItem): String? = item.labelDetails?.detail

  protected open fun getTypeText(item: CompletionItem): String? = item.labelDetails?.description ?: item.detail
}


object LspCompletionDisabled : LspCompletionCustomizer()
