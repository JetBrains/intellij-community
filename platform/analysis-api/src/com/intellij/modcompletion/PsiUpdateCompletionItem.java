// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * A {@link ModCompletionItem} that performs an update via {@link ModCommand#psiUpdate} API. The overwrite mode is handled automatically.
 * 
 * @param <T> type of context object
 */
@NotNullByDefault
public abstract class PsiUpdateCompletionItem<T> implements ModCompletionItem {
  private final String myLookupString;
  private final T myContext;

  protected PsiUpdateCompletionItem(String lookupString, T context) {
    myLookupString = lookupString;
    myContext = context; 
  }

  @Override
  public T contextObject() {
    return myContext;
  }

  @Override
  public String mainLookupString() {
    return myLookupString;
  }

  @Override
  public ModCommand perform(ActionContext actionContext, InsertionContext insertionContext) {
    String lookupString = mainLookupString();
    int completionStart = actionContext.selection().getStartOffset();
    int prefixEnd = actionContext.selection().getEndOffset();
    int updatedCaretPos = completionStart + lookupString.length();
    ActionContext finalActionContext = actionContext
      .withSelection(TextRange.create(updatedCaretPos, updatedCaretPos))
      .withOffset(updatedCaretPos);
    return ModCommand.psiUpdate(finalActionContext, doc -> {
      doc.deleteString(completionStart, prefixEnd);
    }, updater -> {
      Document document = updater.getDocument();
      document.replaceString(completionStart,
                             insertionContext.mode() == InsertionMode.OVERWRITE ?
                             calculateEndOffsetForOverwrite(document, completionStart) : completionStart, lookupString);
      updater.moveCaretTo(updatedCaretPos);
      update(actionContext.withOffset(updatedCaretPos)
               .withSelection(TextRange.create(completionStart, updatedCaretPos)), insertionContext, updater);
    });
  }

  /**
   * @param document the document to use 
   * @param startFrom a caret position
   * @return the position of the identifier end. The default implementation uses Java identifier rules.
   * Override this method to use different rules for ID boundary.
   */
  protected int calculateEndOffsetForOverwrite(Document document, int startFrom) {
    final CharSequence text = document.getCharsSequence();
    int idEnd = startFrom;
    while (idEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(idEnd))) {
      idEnd++;
    }
    return idEnd;
  }

  /**
   * Performs PSI/document update of the file copy to generate a final {@link ModCommand}.
   *
   * @param actionContext    context of the action
   * @param insertionContext context of the insertion (like which character was used to finish the completion)
   * @param updater          an updater to use
   */
  public abstract void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater);

  @Override
  public String toString() {
    return mainLookupString();
  }
}
