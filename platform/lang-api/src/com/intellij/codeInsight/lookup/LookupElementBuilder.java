/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ObjectUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public class LookupElementBuilder extends LookupElement {
  @NotNull private final String myLookupString;
  @NotNull private final Object myObject;
  private final boolean myCaseSensitive;
  @Nullable private final InsertHandler<LookupElement> myInsertHandler;
  @Nullable private final LookupElementRenderer<LookupElement> myRenderer;
  @Nullable private final LookupElementPresentation myHardcodedPresentation;
  @NotNull private final Set<String> myAllLookupStrings;

  private LookupElementBuilder(String lookupString, Object object, InsertHandler<LookupElement> insertHandler,
                               LookupElementRenderer<LookupElement> renderer,
                               LookupElementPresentation hardcodedPresentation,
                               Set<String> allLookupStrings,
                               boolean caseSensitive) {
    myLookupString = lookupString;
    myObject = object;
    myInsertHandler = insertHandler;
    myRenderer = renderer;
    myHardcodedPresentation = hardcodedPresentation;
    myAllLookupStrings = allLookupStrings;
    myCaseSensitive = caseSensitive;
  }

  private LookupElementBuilder(LookupElementBuilder other) {
    this(other.myLookupString, other.myObject, other.myInsertHandler, other.myRenderer, other.myHardcodedPresentation, other.myAllLookupStrings, other.myCaseSensitive);
  }

  private LookupElementBuilder(@NotNull String lookupString, @NotNull Object object) {
    this(lookupString, object, null, null, null, Collections.singleton(lookupString), true);
  }

  public static LookupElementBuilder create(@NotNull String lookupString) {
    return new LookupElementBuilder(lookupString, lookupString);
  }

  public static LookupElementBuilder create(@NotNull PsiNamedElement element) {
    return new LookupElementBuilder(ObjectUtils.assertNotNull(element.getName()), element);
  }

  public static LookupElementBuilder create(@NotNull Object lookupObject, @NotNull String lookupString) {
    return new LookupElementBuilder(lookupString, lookupObject);
  }

  public LookupElementBuilder setInsertHandler(@Nullable InsertHandler<LookupElement> insertHandler) {
    return new LookupElementBuilder(myLookupString, myObject, insertHandler, myRenderer, myHardcodedPresentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  public LookupElementBuilder setRenderer(@Nullable LookupElementRenderer<LookupElement> renderer) {
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, renderer, myHardcodedPresentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  @Override
  @NotNull
  public Set<String> getAllLookupStrings() {
    return myAllLookupStrings;
  }

  public LookupElementBuilder setIcon(@Nullable Icon icon) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setIcon(icon);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation,
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

  public LookupElementBuilder addLookupString(@NotNull String another) {
    final THashSet<String> set = new THashSet<String>(myAllLookupStrings);
    set.add(another);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, myRenderer, myHardcodedPresentation,
                                    Collections.unmodifiableSet(set), myCaseSensitive);
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public LookupElementBuilder setCaseSensitive(boolean caseSensitive) {
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, myRenderer, myHardcodedPresentation,
                                    myAllLookupStrings, caseSensitive);
  }

  public LookupElementBuilder setTypeText(@Nullable String typeText) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTypeText(typeText);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  public LookupElementBuilder setPresentableText(@NotNull String presentableText) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemText(presentableText);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  public LookupElementBuilder setBold() {
    return setBold(true);
  }

  public LookupElementBuilder setBold(boolean bold) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setItemTextBold(bold);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  public LookupElementBuilder setStrikeout() {
    return setStrikeout(true);
  }
  
  public LookupElementBuilder setStrikeout(boolean strikeout) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setStrikeout(strikeout);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  public LookupElementBuilder setTailText(@Nullable String tailText) {
    return setTailText(tailText, false);
  }

  public LookupElementBuilder setTailText(@Nullable String tailText, boolean grayed) {
    final LookupElementPresentation presentation = copyPresentation();
    presentation.setTailText(tailText, grayed);
    return new LookupElementBuilder(myLookupString, myObject, myInsertHandler, null, presentation,
                                    myAllLookupStrings, myCaseSensitive);
  }

  public LookupElement setAutoCompletionPolicy(AutoCompletionPolicy policy) {
    return policy.applyPolicy(this);
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
    if (!myCaseSensitive) {
      CompletionService.getCompletionService().correctCaseInsensitiveString(this, context);
    }

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
  public int hashCode() {
    int result = 0;
    result = 31 * result + (myInsertHandler != null ? myInsertHandler.getClass().hashCode() : 0);
    result = 31 * result + (myLookupString.hashCode());
    result = 31 * result + (myObject.hashCode());
    result = 31 * result + (myRenderer != null ? myRenderer.getClass().hashCode() : 0);
    return result;
  }

}
