// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModNavigate;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    String textToInsert = getTextToInsert(actionContext, insertionContext);
    if (textToInsert != null) {
      ModCommand command = insertWithoutFileCopy(actionContext, insertionContext, textToInsert);
      if (command != null) {
        return command;
      }
    }
    return ModCommand.psiUpdate(actionContext, true, updater -> {
      Document document = updater.getDocument();
      String lookupString = mainLookupString();
      TextRange range = TextRange.create(updater.getCaretOffset(), updater.getCaretOffset() + lookupString.length());
      document.replaceString(range.getStartOffset(),
                             insertionContext.mode() == InsertionMode.OVERWRITE ?
                             calculateEndOffsetForOverwrite(document.getCharsSequence(), range.getStartOffset()) :
                             range.getStartOffset(), lookupString);
      updater.moveCaretTo(range.getEndOffset());
      update(actionContext.withOffset(range.getEndOffset())
               .withSelection(range), insertionContext, updater);
      addCompletionChar(updater, insertionContext);
    });
  }

  /**
   * Creates the command of {@link #perform} without a copy of the file.
   * The command inserts the given text, and moves the caret after it.
   *
   * @param actionContext    context of the action
   * @param insertionContext context of the insertion
   * @param insertion        text to insert, see {@link #getTextToInsert}
   * @return the command; null if the file is an injected fragment or has no virtual file.
   * An injected fragment needs {@link ModCommand#psiUpdate}, which escapes the inserted text for the host.
   */
  private @Nullable ModCommand insertWithoutFileCopy(ActionContext actionContext, InsertionContext insertionContext, String insertion) {
    PsiFile file = actionContext.file();
    if (InjectedLanguageManager.getInstance(actionContext.project()).isInjectedFragment(file)) return null;
    VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
    if (virtualFile == null) return null;
    CharSequence text = file.getFileDocument().getImmutableCharSequence();
    TextRange selection = actionContext.selection();
    int start = actionContext.offset();
    String oldText;
    if (selection.isEmpty()) {
      oldText = text.toString();
    }
    else {
      // The selection contains the prefix typed during the completion session. It is deleted before the command is applied.
      start = selection.getStartOffset();
      oldText = new StringBuilder(text.length() - selection.getLength())
        .append(text, 0, start).append(text, selection.getEndOffset(), text.length()).toString();
    }
    int end = insertionContext.mode() == InsertionMode.OVERWRITE ? calculateEndOffsetForOverwrite(oldText, start) : start;
    int caret = start + insertion.length();
    ModCommand navigate = new ModNavigate(file.getViewProvider().getVirtualFile(), caret, caret, caret);
    if (insertion.contentEquals(oldText.subSequence(start, end))) return navigate;
    String newText = oldText.substring(0, start) + insertion + oldText.substring(end);
    var fragment = new ModUpdateFileText.Fragment(start, end - start, insertion.length());
    return new ModUpdateFileText(virtualFile, oldText, newText, List.of(fragment)).andThen(navigate);
  }

  /**
   * Returns the text that {@link #perform} inserts in place of the lookup string range, if a simple insertion is possible.
   * Then {@link #perform} creates the command without a copy of the file, which is faster, and {@link #update} is not called.
   * The caret goes to the end of the inserted text.
   * <p>
   * The result must be the same as the result of the {@link ModCommand#psiUpdate} path.
   * Usually, it is {@link #getLookupStringWithCompletionChar}, when {@link #update} does nothing for the given contexts.
   *
   * @param actionContext    context of the action. Its selection is the prefix, which is deleted before the insertion.
   * @param insertionContext context of the insertion
   * @return the text to insert; null if {@link #update} must be called for the given contexts
   * @implNote The default implementation returns null.
   */
  protected @Nullable String getTextToInsert(ActionContext actionContext, InsertionContext insertionContext) {
    return null;
  }

  /**
   * A helper method which could be useful to implement {@link #getTextToInsert(ActionContext, InsertionContext)}.
   *
   * @param insertionContext context of the insertion
   * @return the lookup string, followed by the completion character if {@link #shouldAddCompletionChar} allows it
   */
  protected final String getLookupStringWithCompletionChar(InsertionContext insertionContext) {
    return shouldAddCompletionChar(insertionContext) ?
           mainLookupString() + insertionContext.insertionCharacter() : mainLookupString();
  }

  /**
   * Inserts a completion character to the document, if necessary.
   * 
   * @param updater updater to use
   * @param context insertion context
   */
  private void addCompletionChar(ModPsiUpdater updater, InsertionContext context) {
    if (!shouldAddCompletionChar(context)) return;
    PsiDocumentManager.getInstance(updater.getProject()).doPostponedOperationsAndUnblockDocument(updater.getDocument());
    int offset = updater.getCaretOffset();
    updater.getDocument().insertString(offset, String.valueOf(context.insertionCharacter()));
    updater.moveCaretTo(offset + 1);
  }

  /**
   * Informs whether the completion character should be inserted to the document.
   * <p>
   * @implNote The default implementation inserts any character, except tab and enter. 
   * The method could be overridden to suppress other characters. 
   * 
   * @param context insertion context
   * @return true if the completion character should be inserted to the document automatically.
   */
  protected boolean shouldAddCompletionChar(InsertionContext context) {
    char c = context.insertionCharacter();
    return c != Lookup.NORMAL_SELECT_CHAR &&
           c != Lookup.COMPLETE_STATEMENT_SELECT_CHAR &&
           c != Lookup.REPLACE_SELECT_CHAR &&
           c != Lookup.AUTO_INSERT_SELECT_CHAR;
  }

  /**
   * @param text the document text to use
   * @param startFrom a caret position
   * @return the position of the identifier end. The default implementation uses Java identifier rules.
   * Override this method to use different rules for ID boundary.
   */
  protected int calculateEndOffsetForOverwrite(CharSequence text, int startFrom) {
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
