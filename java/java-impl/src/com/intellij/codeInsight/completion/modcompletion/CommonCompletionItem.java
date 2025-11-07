// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Set;

/**
 * A simple completion item to insert text with an optional tail.
 */
@NotNullByDefault
public final class CommonCompletionItem extends PsiUpdateCompletionItem {
  private final String myText;
  private final Set<String> myAdditionalStrings;
  private final Object myObject;
  private final MarkupText myPresentation;
  private final ModNavigatorTailType myTail;
  private final boolean myAdjustIndent;

  public CommonCompletionItem(@NlsSafe String text) { 
    myText = text; 
    myObject = text;
    myPresentation = MarkupText.plainText(text);
    myAdditionalStrings = Set.of();
    myTail = (ModNavigatorTailType)TailTypes.noneType();
    myAdjustIndent = false;
  }
  
  private CommonCompletionItem(@NlsSafe String text, Set<String> additionalStrings, Object object, MarkupText presentation,
                               ModNavigatorTailType tail, boolean indent) {
    myText = text;
    myAdditionalStrings = additionalStrings;
    myObject = object;
    myPresentation = presentation;
    myTail = tail;
    myAdjustIndent = indent;
  }

  /**
   * @param tail tail type to use
   * @return new CommonCompletionItem with the given tail type
   */
  public CommonCompletionItem withTail(ModNavigatorTailType tail) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, myPresentation, tail, myAdjustIndent);
  }

  /**
   * @param presentation presentation to use for item rendering
   * @return new CommonCompletionItem with the given presentation
   */
  public CommonCompletionItem withPresentation(MarkupText presentation) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, presentation, myTail, myAdjustIndent);
  }

  /**
   * @param string an additional lookup string to match
   * @return new CommonCompletionItem with the given additional lookup string (previously added strings are not removed)
   */
  public CommonCompletionItem addLookupString(String string) {
    return new CommonCompletionItem(myText, StreamEx.of(myAdditionalStrings).append(string).toSet(), myObject, myPresentation, myTail,
                                    myAdjustIndent);
  }

  /**
   * @return a CommonCompletionItem, which will automatically adjust the indentation of current line
   */
  public CommonCompletionItem adjustIndent() {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, myPresentation, myTail, true);
  }

  @Override
  public String mainLookupString() {
    return myText;
  }

  @Override
  public Object contextObject() {
    return myObject;
  }

  @Override
  public CompletionItemPresentation presentation() {
    return new CompletionItemPresentation(myPresentation);
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, PsiFile file, ModPsiUpdater updater) {
    myTail.processTail(actionContext.project(), updater, actionContext.offset());
    if (myAdjustIndent) {
      PsiDocumentManager.getInstance(actionContext.project()).commitDocument(file.getFileDocument());
      CodeStyleManager.getInstance(actionContext.project()).adjustLineIndent(file, actionContext.selection().getStartOffset());
    }
  }
}
