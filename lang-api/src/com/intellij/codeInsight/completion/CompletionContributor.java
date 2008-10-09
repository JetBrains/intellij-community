/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Completion FAQ:<p>
 *
 * Q: How do I implement code completion?<br>
 * A: Define a completion.contributor extension of type {@link CompletionContributor}.
 * Or, if the place you want to complete in contains a {@link PsiReference}, just return the variants
 * you want to suggest from its {@link PsiReference#getVariants()} method as {@link String}s,
 * {@link com.intellij.psi.PsiElement}s, or better {@link LookupElement}s.<p>
 *
 * Q: How do I get automatic lookup element filtering by prefix?<br>
 * A: When you return variants from reference ({@link PsiReference#getVariants()}), the filtering will be done
 * automatically, with prefix taken as the reference text from its start ({@link PsiReference#getRangeInElement()}) to
 * the caret position.
 * In {@link CompletionContributor} you will be given a {@link com.intellij.codeInsight.completion.CompletionResultSet}
 * which will match {@link LookupElement}s againts its prefix matcher {@link CompletionResultSet#getPrefixMatcher()}.
 * If the default prefix calculated by IntelliJ IDEA doesn't satisfy you, you can obtain another result set via
 * {@link com.intellij.codeInsight.completion.CompletionResultSet#withPrefixMatcher(PrefixMatcher)} and feed your lookup elements to the latter.
 * It's one of the item's lookup strings ({@link LookupElement#getAllLookupStrings()} that is matched against prefix matcher.<p>
 *
 * Q: How do I plug into those funny texts below the items in shown lookup?<br>
 * A: Use {@link CompletionContributor#advertise(CompletionParameters)} or
 * {@link CompletionService#setAdvertisementText(String)}. Don't forget to check whether you are in correct place
 * (see {@link CompletionParameters}).<p>
 *
 * Q: How do I change the text that gets shown when there are no suitable variants at all? <br>
 * A: Use {@link CompletionContributor#handleEmptyLookup(CompletionParameters, Editor)}.
 * Don't forget to check whether you are in correct place (see {@link CompletionParameters}).<p>
 *
 * Q: How do I affect lookup element's appearance (icon, text attributes, etc.)?<br>
 * A: See {@link LookupElement#renderElement(LookupElementPresentation)} and {@link LookupElement#getRenderer()}.<p>
 *
 * Q: I'm not satisfied that completion just inserts the item's lookup string on item selection. How make IDEA write something else?<br>
 * A: See {@link LookupElement#handleInsert(InsertionContext)} and {@link LookupElement#getInsertHandler()}.<p>
 *
 * Q: What if I select item with a Tab key?<br>
 * A: Semantics is, that the identifier that you're stanging inside gets removed completely, and then the lookup string is inserted. You can change
 * the deleting range end offset, do it in {@link CompletionContributor#beforeCompletion(CompletionInitializationContext)}
 * by putting new offset to {@link CompletionInitializationContext#getOffsetMap()} as {@link com.intellij.codeInsight.completion.CompletionInitializationContext#IDENTIFIER_END_OFFSET}.<p>
 *
 * @author peter
 */
public abstract class CompletionContributor extends AbstractCompletionContributor<CompletionParameters>{
  public static final ExtensionPointName<CompletionContributor> EP_NAME = ExtensionPointName.create("com.intellij.completion.contributor");

  private final MultiMap<CompletionType, Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>>> myMap =
      new MultiMap<CompletionType, Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>>>();

  public final void extend(@Nullable CompletionType type, final ElementPattern<? extends PsiElement> place, CompletionProvider<CompletionParameters> provider) {
    myMap.putValue(type, new Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>>(place, provider));
  }

  public boolean fillCompletionVariants(final CompletionParameters parameters, CompletionResultSet result) {
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>> pair : myMap.get(parameters.getCompletionType())) {
      final ProcessingContext context = new ProcessingContext();
      if (isPatternSuitable(pair.first, parameters, context)) {
        if (!pair.second.addCompletionVariants(parameters, context, result)) return false;
      }
    }
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>> pair : myMap.get(null)) {
      final ProcessingContext context = new ProcessingContext();
      if (isPatternSuitable(pair.first, parameters, context)) {
        if (!pair.second.addCompletionVariants(parameters, context, result)) return false;
      }
    }
    return true;
  }

  /**
   * Invoked before completion is started. Is used mainly for determining custom offsets in editor, and to change default dummy identifier.
   * @param context
   */
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
  }

  /**
   * @param parameters
   * @return text to be shown at the bottom of lookup list
   */
  @Nullable
  public String advertise(@NotNull CompletionParameters parameters) {
    return null;
  }

  /**
   *
   * @param parameters
   * @param editor
   * @return hint text to be shown if no variants are found, typically "No suggestions"
   */
  @Nullable
  public String handleEmptyLookup(@NotNull CompletionParameters parameters, final Editor editor) {
    return null;
  }

  /**
   * @param actionId
   * @return String representation of action shortcut. Useful while advertising something
   * @see #advertise(CompletionParameters)
   */
  protected static String getActionShortcut(@NonNls final String actionId) {
    return KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(actionId));
  }

}
