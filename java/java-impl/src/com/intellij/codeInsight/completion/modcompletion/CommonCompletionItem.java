// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
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
  private final CompletionItemPresentation myPresentation;
  private final ModNavigatorTailType myTail;
  private final InsertionAwareUpdateHandler myAdditionalUpdater;
  private final double myPriority;

  public CommonCompletionItem(@NlsSafe String text) { 
    myText = text; 
    myObject = text;
    myPresentation = new CompletionItemPresentation(MarkupText.plainText(text));
    myAdditionalStrings = Set.of();
    myTail = (ModNavigatorTailType)TailTypes.noneType();
    myPriority = 0;
    myAdditionalUpdater = (UpdateHandler)(start, file, updater) -> { };
  }
  
  private CommonCompletionItem(@NlsSafe String text, Set<String> additionalStrings, Object object, CompletionItemPresentation presentation,
                               ModNavigatorTailType tail, double priority, InsertionAwareUpdateHandler additionalUpdater) {
    myText = text;
    myAdditionalStrings = additionalStrings;
    myObject = object;
    myPresentation = presentation;
    myTail = tail;
    myPriority = priority;
    myAdditionalUpdater = additionalUpdater;
  }

  /**
   * @param tail tail type to use
   * @return new CommonCompletionItem with the given tail type
   */
  public CommonCompletionItem withTail(ModNavigatorTailType tail) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, myPresentation, tail, myPriority,
                                    myAdditionalUpdater);
  }

  /**
   * @param presentation presentation to use for item rendering
   * @return new CommonCompletionItem with the given presentation
   */
  public CommonCompletionItem withPresentation(MarkupText presentation) {
    return withPresentation(new CompletionItemPresentation(presentation));
  }

  /**
   * @param presentation presentation to use for item rendering
   * @return new CommonCompletionItem with the given presentation
   */
  public CommonCompletionItem withPresentation(CompletionItemPresentation presentation) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, presentation, myTail, myPriority,
                                    myAdditionalUpdater);
  }

  /**
   * @param object context object to use
   * @return a new completion item with the given context object
   */
  public CommonCompletionItem withObject(Object object) {
    return new CommonCompletionItem(myText, myAdditionalStrings, object, myPresentation, myTail, myPriority,
                                    myAdditionalUpdater);
  }

  /**
   * @param priority priority of the item. Can be positive or negative. Default is 0.
   * @return a new completion item with the given priority
   */
  public CommonCompletionItem withPriority(double priority) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, myPresentation, myTail, priority,
                                    myAdditionalUpdater);
  }

  /**
   * @param updater updater to be executed after tail-type processing (previous updater will be replaced).
   *                The updater behavior depends on the completion character.
   * @return a new completion item with the given updater
   */
  public CommonCompletionItem withAdditionalUpdater(InsertionAwareUpdateHandler updater) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, myPresentation, myTail, myPriority, updater);
  }

  /**
   * @param updater updater to be executed after tail-type processing (previous updater will be replaced)
   * @return a new completion item with the given updater
   */
  public CommonCompletionItem withAdditionalUpdater(UpdateHandler updater) {
    return new CommonCompletionItem(myText, myAdditionalStrings, myObject, myPresentation, myTail, myPriority, updater);
  }

  /**
   * @return a CommonCompletionItem, with an additional handler, which will automatically adjust the indentation of current line
   */
  public CommonCompletionItem adjustIndent() {
    return withAdditionalUpdater((start, file, updater) -> {
      CodeStyleManager.getInstance(file.getProject()).adjustLineIndent(file, start);
    });
  }

  /**
   * @param string an additional lookup string to match
   * @return new CommonCompletionItem with the given additional lookup string (previously added strings are not removed)
   */
  public CommonCompletionItem addLookupString(String string) {
    return new CommonCompletionItem(myText, StreamEx.of(myAdditionalStrings).append(string).toSet(), myObject, myPresentation, myTail,
                                    myPriority, myAdditionalUpdater);
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
  public double priority() {
    return myPriority;
  }

  @Override
  public CompletionItemPresentation presentation() {
    return myPresentation;
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, PsiFile file, ModPsiUpdater updater) {
    Project project = actionContext.project();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = file.getFileDocument();
    manager.commitDocument(document);
    myAdditionalUpdater.update(actionContext.selection().getStartOffset(), updater.getWritable(file), updater, insertionContext);
    manager.commitDocument(document);
    manager.doPostponedOperationsAndUnblockDocument(document);
    myTail.processTail(project, updater, updater.getCaretOffset());
  }

  /**
   * Update handler.
   */
  @FunctionalInterface
  public interface UpdateHandler extends InsertionAwareUpdateHandler {
    /**
     * Perform an update. Executed after lookup string insertion but before tail processing.
     * 
     * @param completionStart offset of the completion start position
     * @param writableFile file to be updated
     * @param updater updater; its caret position points to the end of the inserted lookup string
     */
    void update(int completionStart, PsiFile writableFile, ModPsiUpdater updater);
    
    @Override
    default void update(int completionStart, PsiFile writableFile, ModPsiUpdater updater, InsertionContext insertionContext) {
      update(completionStart, writableFile, updater);
    }
  }

  /**
   * Update handler which can use insertion context. Use only if you actually need insertion context, as
   * this might disable some optimizations.
   */
  @FunctionalInterface
  public interface InsertionAwareUpdateHandler {
    /**
     * Perform an update. Executed after lookup string insertion but before tail processing.
     *
     * @param completionStart offset of the completion start position
     * @param writableFile file to be updated
     * @param updater updater; its caret position points to the end of the inserted lookup string
     * @param insertionContext insertion context
     */
    void update(int completionStart, PsiFile writableFile, ModPsiUpdater updater, InsertionContext insertionContext);
  }
}
