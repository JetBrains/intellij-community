/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 *
 * @see LookupElementDecorator
 * @see com.intellij.codeInsight.completion.PrioritizedLookupElement
 */
public final class LookupElementBuilder extends LookupElement {
  @NotNull private final String myLookupString;
  @NotNull private final Object myObject;
  @Nullable private final SmartPsiElementPointer<?> myPsiElement;
  private final boolean myCaseSensitive;
  @Nullable private final InsertHandler<LookupElement> myInsertHandler;
  @Nullable private final LookupElementRenderer<LookupElement> myRenderer;
  @Nullable private final LookupElementPresentation myHardcodedPresentation;
  @NotNull private final Set<String> myAllLookupStrings;

  private LookupElementBuilder(@NotNull String lookupString, @NotNull Object object, @Nullable InsertHandler<LookupElement> insertHandler,
                               @Nullable LookupElementRenderer<LookupElement> renderer,
                               @Nullable LookupElementPresentation hardcodedPresentation,
                               @Nullable SmartPsiElementPointer<?> psiElement,
                               @NotNull Set<String> allLookupStrings,
                               boolean caseSensitive) {
    myLookupString = lookupString;
    myObject = object;
    myInsertHandler = insertHandler;
    myRenderer = renderer;
    myHardcodedPresentation = hardcodedPresentation;
    myPsiElement = psiElement;
    myAllLookupStrings = Collections.unmodifiableSet(allLookupStrings);
    myCaseSensitive = caseSensitive;
  }

  private LookupElementBuilder(@NotNull String lookupString, @NotNull Object object) {
    this(lookupString, object, null, null, null, null, Collections.singleton(lookupString), true);
  }

  @NotNull
  public static LookupElementBuilder create(@NotNull String lookupString) {
    return new LookupElementBuilder(lookupString, lookupString);
  }
  
  public static LookupElementBuilder create(@NotNull Object object) {
    return new LookupElementBuilder(object.toString(), object);
  }

  public static LookupElementBuilder createWithSmartPointer(@NotNull String lookupString, @NotNull PsiElement element) {
    PsiUtilCore.ensureValid(element);
    return new LookupElementBuilder(lookupString, 
                                    SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element));
  }

  public static LookupElementBuilder create(@NotNull PsiNamedElement element) {
    PsiUtilCore.ensureValid(element);
    return new LookupElementBuilder(StringUtil.notNullize(element.getName()), element);
  }

  public static LookupElementBuilder createWithIcon(@NotNull PsiNamedElement element) {
    PsiUtilCore.ensureValid(element);
    return create(element).withIcon(element.getIcon(0));
  }

  public static LookupElementBuilder create(@NotNull Object lookupObject, @NotNull String lookupString) {
    if (lookupObject instanceof PsiElement) {
      PsiUtilCore.ensureValid((PsiElement)lookupObject);
    }
    return new LookupElementBuilder(lookupString, lookupObject);
  }

  /**
   * @deprecated use {@link #withInsertHandler(InsertHandler)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setInsertHandler(@Nullable InsertHandler<LookupElement> insertHandler) {
    return withInsertHandler(insertHandler);
  }

  @Contract(pure=true)
  public LookupElementBuilder withInsertHandler(@Nullable InsertHandler<LookupElement> insertHandler) {
    return new LookupElementBuilder(myLookupString, myObject, insertHandler, myRenderer, myHardcodedPresentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #withRenderer(LookupElementRenderer)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setRenderer(@Nullable LookupElementRenderer<LookupElement> renderer) {
    return withRenderer(renderer);
  }
  @Contract(pure=true)
  public LookupElementBuilder withRenderer(@Nullable LookupElementRenderer<LookupElement> renderer) {
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, renderer, myHardcodedPresentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  @Override
  @NotNull
  public Set<String> getAllLookupStrings() {
    return myAllLookupStrings;
  }

  /**
   * @deprecated use {@link #withIcon(Icon)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setIcon(@Nullable Icon icon) {
    return withIcon(icon);
  }

  @Contract(pure=true)
  public LookupElementBuilder withIcon(@Nullable Icon icon) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setIcon(icon);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  @NotNull
  private LookupElementPresentation copyPresentation() {
    final LookupElementPresentation presentation = new LookupElementPresentation();
    if (myHardcodedPresentation != null) {
      presentation.copyFrom(myHardcodedPresentation);
    } else {
      presentation.setItemText(myLookupString);
    }
    return presentation;
  }

  /**
   * @deprecated use {@link #withLookupString(String)}
   */
  @Contract(pure=true)
  public LookupElementBuilder addLookupString(@NotNull String another) {
    return withLookupString(another);
  }
  @Contract(pure=true)
  public LookupElementBuilder withLookupString(@NotNull String another) {
    final THashSet<String> set = new THashSet<>(myAllLookupStrings);
    set.add(another);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, myRenderer, myHardcodedPresentation, myPsiElement,
                                    Collections.unmodifiableSet(set), myCaseSensitive);
  }

  @Override
  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  /**
   * @deprecated use {@link #withCaseSensitivity(boolean)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setCaseSensitive(boolean caseSensitive) {
    return withCaseSensitivity(caseSensitive);
  }
  /**
   * @param caseSensitive if this lookup item should be completed in the same letter case as prefix
   * @return modified builder
   * @see com.intellij.codeInsight.completion.CompletionResultSet#caseInsensitive()
   */
  @Contract(pure=true)
  public LookupElementBuilder withCaseSensitivity(boolean caseSensitive) {
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, myRenderer, myHardcodedPresentation, myPsiElement,
                                    myAllLookupStrings, caseSensitive);
  }

  /**
   * Allows to pass custom PSI that will be returned from {@link #getPsiElement()}.
   */
  @Contract(pure=true)
  public LookupElementBuilder withPsiElement(@Nullable PsiElement psi) {
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, myRenderer, myHardcodedPresentation,
                                    psi == null ? null : SmartPointerManager.createPointer(psi),
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #withItemTextForeground(Color)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setItemTextForeground(@NotNull Color itemTextForeground) {
    return withItemTextForeground(itemTextForeground);
  }
  @Contract(pure=true)
  public LookupElementBuilder withItemTextForeground(@NotNull Color itemTextForeground) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemTextForeground(itemTextForeground);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement, myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #withItemTextUnderlined(boolean)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setItemTextUnderlined(boolean underlined) {
    return withItemTextUnderlined(underlined);
  }
  @Contract(pure=true)
  public LookupElementBuilder withItemTextUnderlined(boolean underlined) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemTextUnderlined(underlined);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement, myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #withTypeText(String)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setTypeText(@Nullable String typeText) {
    return withTypeText(typeText);
  }
  @Contract(pure=true)
  public LookupElementBuilder withTypeText(@Nullable String typeText) {
    return withTypeText(typeText, false);
  }

  /**
   * @deprecated use {@link #withTypeText(String, boolean)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setTypeText(@Nullable String typeText, boolean grayed) {
    return withTypeText(typeText, grayed);
  }

  @Contract(pure=true)
  public LookupElementBuilder withTypeText(@Nullable String typeText, boolean grayed) {
    return withTypeText(typeText, null, grayed);
  }

  @Contract(pure=true)
  public LookupElementBuilder withTypeText(@Nullable String typeText, @Nullable Icon typeIcon, boolean grayed) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTypeText(typeText, typeIcon);
    presentation.setTypeGrayed(grayed);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @since 2018.1
   */
  public LookupElementBuilder withTypeIconRightAligned(boolean typeIconRightAligned) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTypeIconRightAligned(typeIconRightAligned);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #withPresentableText(String)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setPresentableText(@NotNull String presentableText) {
    return withPresentableText(presentableText);
  }
  @Contract(pure=true)
  public LookupElementBuilder withPresentableText(@NotNull String presentableText) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemText(presentableText);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #bold()}
   */
  @Contract(pure=true)
  public LookupElementBuilder setBold() {
    return bold();
  }
  @Contract(pure=true)
  public LookupElementBuilder bold() {
    return withBoldness(true);
  }

  /**
   * @deprecated use {@link #withBoldness(boolean)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setBold(boolean bold) {
    return withBoldness(bold);
  }
  @Contract(pure=true)
  public LookupElementBuilder withBoldness(boolean bold) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemTextBold(bold);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #strikeout()}
   */
  @Contract(pure=true)
  public LookupElementBuilder setStrikeout() {
    return strikeout();
  }
  @Contract(pure=true)
  public LookupElementBuilder strikeout() {
    return withStrikeoutness(true);
  }

  /**
   * @deprecated use {@link #withStrikeoutness(boolean)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setStrikeout(boolean strikeout) {
    return withStrikeoutness(strikeout);
  }
  @Contract(pure=true)
  public LookupElementBuilder withStrikeoutness(boolean strikeout) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setStrikeout(strikeout);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  /**
   * @deprecated use {@link #withTailText(String)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setTailText(@Nullable String tailText) {
    return withTailText(tailText);
  }
  @Contract(pure=true)
  public LookupElementBuilder withTailText(@Nullable String tailText) {
    return withTailText(tailText, false);
  }

  /**
   * @deprecated use {@link #withTailText(String, boolean)}
   */
  @Contract(pure=true)
  public LookupElementBuilder setTailText(@Nullable String tailText, boolean grayed) {
    return withTailText(tailText, grayed);
  }
  @Contract(pure=true)
  public LookupElementBuilder withTailText(@Nullable String tailText, boolean grayed) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTailText(tailText, grayed);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement,
                                    myAllLookupStrings, myCaseSensitive);
  }

  @Contract(pure=true)
  public LookupElementBuilder appendTailText(@NotNull String tailText, boolean grayed) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.appendTailText(tailText, grayed);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation, myPsiElement, myAllLookupStrings, myCaseSensitive);
  }

  @Contract(pure=true)
  public LookupElement withAutoCompletionPolicy(AutoCompletionPolicy policy) {
    return policy.applyPolicy(this);
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myLookupString;
  }

  @Nullable
  public InsertHandler<LookupElement> getInsertHandler() {
    return myInsertHandler;
  }

  @NotNull
  @Override
  public Object getObject() {
    return myObject;
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    if (myPsiElement != null) return myPsiElement.getElement();
    return super.getPsiElement();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    if (myInsertHandler != null) {
      myInsertHandler.handleInsert(context, this);
    }
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    if (myRenderer != null) {
      myRenderer.renderElement(this, presentation);
    }
    else if (myHardcodedPresentation != null) {
      presentation.copyFrom(myHardcodedPresentation);
    } else {
      presentation.setItemText(myLookupString);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LookupElementBuilder that = (LookupElementBuilder)o;

    final InsertHandler<LookupElement> insertHandler = that.myInsertHandler;
    if (myInsertHandler != null && insertHandler != null ? !myInsertHandler.getClass().equals(insertHandler.getClass())
                                                         : myInsertHandler != insertHandler) return false;
    if (!myLookupString.equals(that.myLookupString)) return false;
    if (!myObject.equals(that.myObject)) return false;

    final LookupElementRenderer<LookupElement> renderer = that.myRenderer;
    if (myRenderer != null && renderer != null ? !myRenderer.getClass().equals(renderer.getClass()) : myRenderer != renderer) return false;

    return true;
  }

  @Override
  public String toString() {
    return "LookupElementBuilder: string=" + getLookupString() + "; handler=" + myInsertHandler;
  }

  @Override
  public int hashCode() {
    int result = 0;
    result = 31 * result + (myInsertHandler != null ? myInsertHandler.getClass().hashCode() : 0);
    result = 31 * result + (myLookupString.hashCode());
    result = 31 * result + (myObject.hashCode());
    result = 31 * result + (myRenderer != null ? myRenderer.getClass().hashCode() : 0);
    return result;
  }

}
