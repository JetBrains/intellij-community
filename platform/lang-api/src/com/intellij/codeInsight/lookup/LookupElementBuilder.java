/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class LookupElementBuilder extends LookupElement {
  @NotNull private final String myLookupString;
  @NotNull private final Object myObject;
  private final AutoCompletionPolicy myAutoCompletionPolicy;
  @Nullable private final InsertHandler<LookupElement> myInsertHandler;
  @Nullable private final LookupElementRenderer<LookupElement> myRenderer;
  @Nullable private final LookupElementPresentation myHardcodedPresentation;

  private LookupElementBuilder(String lookupString,
                              Object object,
                              AutoCompletionPolicy autoCompletionPolicy,
                              InsertHandler<LookupElement> insertHandler,
                              LookupElementRenderer<LookupElement> renderer,
                              LookupElementPresentation hardcodedPresentation) {
    myLookupString = lookupString;
    myObject = object;
    myAutoCompletionPolicy = autoCompletionPolicy;
    myInsertHandler = insertHandler;
    myRenderer = renderer;
    myHardcodedPresentation = hardcodedPresentation;
  }

  private LookupElementBuilder(@NotNull String lookupString, @NotNull Object object) {
    this(lookupString, object, AutoCompletionPolicy.SETTINGS_DEPENDENT, null, null, null);
  }

  public static LookupElementBuilder create(@NotNull String lookupString) {
    return new LookupElementBuilder(lookupString, lookupString);
  }

  public static LookupElementBuilder create(@NotNull PsiNamedElement element) {
    return new LookupElementBuilder(ObjectUtils.assertNotNull(element.getName()), element);
  }

  public static LookupElementBuilder create(@NotNull String lookupString, @NotNull Object lookupObject) {
    return new LookupElementBuilder(lookupString, lookupObject);
  }

  public LookupElementBuilder setInsertHandler(@Nullable InsertHandler<LookupElement> insertHandler) {
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, insertHandler, myRenderer, myHardcodedPresentation);
  }

  public LookupElementBuilder setRenderer(@Nullable LookupElementRenderer<LookupElement> renderer) {
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, renderer, myHardcodedPresentation);
  }

  public LookupElementBuilder setIcon(@Nullable Icon icon) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setIcon(icon);
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, null, presentation);
  }

  @NotNull
  private LookupElementPresentation copyPresentation() {
    final LookupElementPresentation presentation = new LookupElementPresentation(false);
    if (myHardcodedPresentation != null) {
      presentation.copyFrom(myHardcodedPresentation);
    } else {
      presentation.setItemText(myLookupString);
    }
    return presentation;
  }

  public LookupElementBuilder setTypeText(@Nullable String typeText) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTypeText(typeText);
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, null, presentation);
  }

  public LookupElementBuilder setPresentableText(@NotNull String presentableText) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemText(presentableText);
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, null, presentation);
  }

  public LookupElementBuilder setAutoCompletionPolicy(@NotNull AutoCompletionPolicy policy) {
    return new LookupElementBuilder(myLookupString, myObject, policy, myInsertHandler, myRenderer, myHardcodedPresentation);
  }

  public LookupElementBuilder setBold() {
    return setBold(true);
  }

  public LookupElementBuilder setBold(boolean bold) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemTextBold(bold);
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, null, presentation);
  }

  public LookupElementBuilder setStrikeout() {
    return setStrikeout(true);
  }
  
  public LookupElementBuilder setStrikeout(boolean strikeout) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setStrikeout(strikeout);
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, null, presentation);
  }

  public LookupElementBuilder setTailText(@Nullable String tailText) {
    return setTailText(tailText, false);
  }

  public LookupElementBuilder setTailText(@Nullable String tailText, boolean grayed) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTailText(tailText, grayed);
    return new LookupElementBuilder(myLookupString, myObject, myAutoCompletionPolicy, myInsertHandler, null, presentation);
  }

  @Deprecated
  public LookupElement createLookupElement() {
    return this;
  }

  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return myAutoCompletionPolicy;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myLookupString;
  }

  @NotNull
  @Override
  public Object getObject() {
    return myObject;
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
    else {
      //noinspection ConstantConditions
      presentation.copyFrom(myHardcodedPresentation);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LookupElementBuilder that = (LookupElementBuilder)o;

    if (myAutoCompletionPolicy != that.myAutoCompletionPolicy) return false;
    if (myInsertHandler != null ? !myInsertHandler.getClass().equals(that.myInsertHandler.getClass()) : that.myInsertHandler != null) return false;
    if (myLookupString != null ? !myLookupString.equals(that.myLookupString) : that.myLookupString != null) return false;
    if (myObject != null ? !myObject.equals(that.myObject) : that.myObject != null) return false;
    if (myRenderer != null ? !myRenderer.getClass().equals(that.myRenderer.getClass()) : that.myRenderer != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    result = 31 * result + (myInsertHandler != null ? myInsertHandler.getClass().hashCode() : 0);
    result = 31 * result + (myLookupString != null ? myLookupString.hashCode() : 0);
    result = 31 * result + (myObject != null ? myObject.hashCode() : 0);
    result = 31 * result + (myRenderer != null ? myRenderer.getClass().hashCode() : 0);
    result = 31 * result + (myAutoCompletionPolicy != null ? myAutoCompletionPolicy.hashCode() : 0);
    return result;
  }

}
