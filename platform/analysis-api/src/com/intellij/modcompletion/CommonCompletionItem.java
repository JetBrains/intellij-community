// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A simple completion item to insert text with an optional tail.
 */
@NotNullByDefault
public final class CommonCompletionItem extends PsiUpdateCompletionItem<Object> {
  private final Set<String> myAdditionalStrings;
  private final ModCompletionItemPresentation myPresentation;
  private final ModNavigatorTailType myTail;
  private final InsertionAwareUpdateHandler myAdditionalUpdater;
  private final AutoCompletionPolicy myPolicy;
  private final double myPriority;

  public CommonCompletionItem(@NlsSafe String text) {
    super(text, text);
    myPresentation = new ModCompletionItemPresentation(MarkupText.plainText(text));
    myAdditionalStrings = Set.of();
    myTail = (ModNavigatorTailType)TailTypes.noneType();
    myPriority = 0;
    myAdditionalUpdater = (UpdateHandler)(start, updater) -> { };
    myPolicy = AutoCompletionPolicy.SETTINGS_DEPENDENT;
  }
  
  private CommonCompletionItem(@NlsSafe String text, Set<String> additionalStrings, Object object, ModCompletionItemPresentation presentation,
                               ModNavigatorTailType tail, double priority, InsertionAwareUpdateHandler additionalUpdater,
                               AutoCompletionPolicy policy) {
    super(text, object);
    myAdditionalStrings = additionalStrings;
    myPresentation = presentation;
    myTail = tail;
    myPriority = priority;
    myAdditionalUpdater = additionalUpdater;
    myPolicy = policy;
  }

  @Override
  public boolean isValid() {
    if (contextObject() instanceof PsiElement element) {
      return element.isValid();
    }
    return true;
  }

  /**
   * @param tail tail type to use
   * @return new CommonCompletionItem with the given tail type
   */
  public CommonCompletionItem withTail(ModNavigatorTailType tail) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, contextObject(), myPresentation, tail, myPriority,
                                    myAdditionalUpdater, myPolicy);
  }

  /**
   * @param presentation presentation to use for item rendering
   * @return new CommonCompletionItem with the given presentation
   */
  public CommonCompletionItem withPresentation(MarkupText presentation) {
    return withPresentation(new ModCompletionItemPresentation(presentation));
  }

  /**
   * @param presentation presentation to use for item rendering
   * @return new CommonCompletionItem with the given presentation
   */
  public CommonCompletionItem withPresentation(ModCompletionItemPresentation presentation) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, contextObject(), presentation, myTail, myPriority,
                                    myAdditionalUpdater, myPolicy);
  }

  /**
   * @param object context object to use
   * @return a new completion item with the given context object
   */
  public CommonCompletionItem withObject(Object object) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, object, myPresentation, myTail, myPriority,
                                    myAdditionalUpdater, myPolicy);
  }

  /**
   * @param priority priority of the item. Can be positive or negative. Default is 0.
   * @return a new completion item with the given priority
   */
  public CommonCompletionItem withPriority(double priority) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, contextObject(), myPresentation, myTail, priority,
                                    myAdditionalUpdater, myPolicy);
  }

  /**
   * @param updater updater to be executed after tail-type processing (previous updater will be replaced).
   *                The updater behavior depends on the completion character.
   * @return a new completion item with the given updater
   */
  public CommonCompletionItem withAdditionalUpdater(InsertionAwareUpdateHandler updater) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, contextObject(), myPresentation, myTail, myPriority, updater, myPolicy);
  }

  /**
   * @param updater updater to be executed after tail-type processing (previous updater will be replaced)
   * @return a new completion item with the given updater
   */
  public CommonCompletionItem withAdditionalUpdater(UpdateHandler updater) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, contextObject(), myPresentation, myTail, myPriority, updater, myPolicy);
  }

  /**
   * @return a CommonCompletionItem, with an additional handler, which will automatically adjust the indentation of current line
   */
  public CommonCompletionItem adjustIndent() {
    return withAdditionalUpdater((start, updater) -> {
      CodeStyleManager.getInstance(updater.getProject()).adjustLineIndent(updater.getPsiFile(), start);
    });
  }

  /**
   * @param string an additional lookup string to match
   * @return new CommonCompletionItem with the given additional lookup string (previously added strings are not removed)
   */
  public CommonCompletionItem addLookupString(String string) {
    Set<String> additionalStrings = Stream.concat(myAdditionalStrings.stream(), Stream.of(string)).collect(Collectors.toUnmodifiableSet());
    return new CommonCompletionItem(mainLookupString(), additionalStrings, contextObject(), myPresentation, myTail,
                                    myPriority, myAdditionalUpdater, myPolicy);
  }

  /**
   * @param policy desired completion policy when this element is the only available element
   * @return new CommonCompletionItem with the given completion policy
   */
  public CommonCompletionItem withAutoCompletionPolicy(AutoCompletionPolicy policy) {
    return new CommonCompletionItem(mainLookupString(), myAdditionalStrings, contextObject(), myPresentation, myTail, myPriority,
                                    myAdditionalUpdater, policy);
  }

  @Override
  public double priority() {
    return myPriority;
  }

  @Override
  public ModCompletionItemPresentation presentation() {
    return myPresentation;
  }

  @Override
  public AutoCompletionPolicy autoCompletionPolicy() {
    return myPolicy;
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
    Project project = actionContext.project();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = updater.getDocument();
    manager.commitDocument(document);
    myAdditionalUpdater.update(actionContext.selection().getStartOffset(), updater, insertionContext);
    manager.commitDocument(document);
    manager.doPostponedOperationsAndUnblockDocument(document);
    myTail.processTail(updater, updater.getCaretOffset());
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
     * @param updater         updater; its caret position points to the end of the inserted lookup string
     */
    void update(int completionStart, ModPsiUpdater updater);
    
    @Override
    default void update(int completionStart, ModPsiUpdater updater, InsertionContext insertionContext) {
      update(completionStart, updater);
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
     * @param completionStart  offset of the completion start position
     * @param updater          updater; its caret position points to the end of the inserted lookup string
     * @param insertionContext insertion context
     */
    void update(int completionStart, ModPsiUpdater updater, InsertionContext insertionContext);
  }
}
