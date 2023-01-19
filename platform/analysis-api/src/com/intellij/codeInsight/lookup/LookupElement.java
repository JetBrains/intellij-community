// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * An item shown in a {@link Lookup} suggestion list, most often produced by code completion or live templates.
 * A typical way to create lookup element is to use {@link LookupElementBuilder}.
 * Another way is to subclass it. Use the latter way only if you need it to implement some additional interface, to modify equals/hashCode
 * or other advanced logic.
 *
 * @see com.intellij.codeInsight.completion.PrioritizedLookupElement
 */
public abstract class LookupElement extends UserDataHolderBase {
  public static final LookupElement[] EMPTY_ARRAY = new LookupElement[0];

  /**
   * @return the string which will be inserted into the editor when this lookup element is chosen
   */
  @NotNull
  public abstract String getLookupString();

  /**
   * @return a set of strings which will be matched against the prefix typed by the user.
   * If none of them match, this item won't be suggested to the user.
   * The returned set must contain {@link #getLookupString()}.
   * @see #isCaseSensitive()
   */
  public Set<String> getAllLookupStrings() {
    return Collections.singleton(getLookupString());
  }

  /**
   * @return some object that this lookup element represents, often a {@link PsiElement} or another kind of symbol.
   * This is mostly used by extensions analyzing the lookup elements, e.g. for sorting purposes.
   */
  @NotNull
  public Object getObject() {
    return this;
  }

  /**
   * @return a PSI element associated with this lookup element. It's used for navigation, showing quick documentation and sorting by proximity to the current location.
   * The default implementation tries to extract PSI element from {@link #getObject()} result.
   */
  @Nullable
  public PsiElement getPsiElement() {
    Object o = getObject();
    if (o instanceof PsiElement) {
      return (PsiElement)o;
    }
    if (o instanceof ResolveResult) {
      return ((ResolveResult)o).getElement();
    }
    if (o instanceof PsiElementNavigationItem) {
      return ((PsiElementNavigationItem)o).getTargetElement();
    }
    if (o instanceof SmartPsiElementPointer) {
      return ((SmartPsiElementPointer<?>)o).getElement();
    }
    return null;
  }

  /**
   * @return whether this lookup element is still valid (can be rendered, inserted, queried for {@link #getObject()}.
   * A lookup element may become invalidated if e.g. its underlying PSI becomes invalidated.
   * @see PsiElement#isValid()
   */
  public boolean isValid() {
    final Object object = getObject();
    if (object instanceof PsiElement) {
      return ((PsiElement)object).isValid();
    }
    return true;
  }

  /**
   * Performs changes after the current lookup element is chosen by the user.<p/>
   *
   * When this method is invoked, the lookup string is already inserted into the editor.
   * In addition, the document is committed, unless {@link #requiresCommittedDocuments()} returns false.<p/>
   *
   * This method is invoked inside a write action. If you need to show dialogs,
   * please do that inside {@link InsertionContext#setLaterRunnable}.
   *
   * @param context an object containing useful information about the circumstances of insertion
   * @see LookupElementDecorator#withInsertHandler(LookupElement, InsertHandler)
   */
  public void handleInsert(@NotNull InsertionContext context) {
  }

  /**
   * @return whether {@link #handleInsert} expects all documents to be committed at the moment of its invocation.
   * The default is {@code true}, overriders can change that, for example if automatic commit is too slow. 
   */
  public boolean requiresCommittedDocuments() {
    return true;
  }

  /**
   * @return the policy determining the auto-insertion behavior when this is the only matching item produced by completion contributors
   */
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.SETTINGS_DEPENDENT;
  }

  @Override
  public String toString() {
    return getLookupString();
  }

  /**
   * Fill the given presentation object with details specifying how this lookup element should look when rendered.
   * By default, just sets the item text to the lookup string.<p></p>
   *
   * This method is called before the item can be shown in the suggestion list, so it should be relatively fast to ensure that
   * list is shown as soon as possible. If there are heavy computations involved, consider making them optional and moving into
   * to {@link #getExpensiveRenderer()}.
   */
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
  }

  /**
   * @return a renderer (if any) that performs potentially expensive computations on this lookup element.
   * It's called on a background thread, not blocking this element from being shown to the user.
   * It may return this lookup element's presentation appended with more details than {@link #renderElement} has given.
   * If the {@link Lookup} is already shown, it will be repainted/resized to accommodate the changes.
   */
  public @Nullable LookupElementRenderer<? extends LookupElement> getExpensiveRenderer() {
    return null;
  }

  /** Prefer to use {@link #as(Class)} */
  @Nullable
  public <T> T as(@NotNull ClassConditionKey<T> conditionKey) {
    //noinspection unchecked
    return conditionKey.isInstance(this) ? (T)this : null;
  }

  /**
   * Return the first element of the given class in a {@link LookupElementDecorator} wrapper chain.
   * If this object is not a decorator, return it if it's instance of the given class, otherwise null.
   */
  @Nullable
  public <T> T as(@NotNull Class<T> clazz) {
    //noinspection unchecked
    return clazz.isInstance(this) ? (T) this : null;
  }

  /**
   * @return whether prefix matching should be done case-sensitively for this lookup element
   * @see #getAllLookupStrings()
   */
  public boolean isCaseSensitive() {
    return true;
  }

  /**
   * Invoked when the completion autopopup contains only the items that exactly match the user-entered prefix to determine
   * whether the popup should be closed to not get in the way when navigating through the code.
   * Should return true if there's some meaningful information in this item's presentation that the user will miss
   * if the autopopup is suddenly closed automatically. Java method parameters are a good example. For simple variables,
   * there's nothing else interesting besides the variable name which is already entered in the editor, so the autopopup may be closed.
   */
  public boolean isWorthShowingInAutoPopup() {
    final LookupElementPresentation presentation = new LookupElementPresentation();
    renderElement(presentation);
    return !presentation.getTailFragments().isEmpty();
  }
}
