/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.lookup;

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
 * A typical way to create lookup element is to use {@link LookupElementBuilder}.
 * Another way is to subclass it. Use the latter way only if you need it to implement some additional interface, to modify equals/hashCode
 * or other advanced logic.
 *
 * @see com.intellij.codeInsight.completion.PrioritizedLookupElement
 * @author peter
 */
public abstract class LookupElement extends UserDataHolderBase {
  public static final LookupElement[] EMPTY_ARRAY = new LookupElement[0];

  @NotNull
  public abstract String getLookupString();

  public Set<String> getAllLookupStrings() {
    return Collections.singleton(getLookupString());
  }

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
      return ((SmartPsiElementPointer)o).getElement();
    }
    return null;
  }

  public boolean isValid() {
    final Object object = getObject();
    if (object instanceof PsiElement) {
      return ((PsiElement)object).isValid();
    }
    return true;
  }

  public void handleInsert(InsertionContext context) {
  }

  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.SETTINGS_DEPENDENT;
  }

  @Override
  public String toString() {
    return getLookupString();
  }

  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
  }

  /**
   * use {@link #as(ClassConditionKey)} instead
   */
  @Deprecated
  @Nullable
  public final <T> T as(Class<T> aClass) {
    return as(ClassConditionKey.create(aClass));
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T as(ClassConditionKey<T> conditionKey) {
    return conditionKey.isInstance(this) ? (T) this : null;
  }
  
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
