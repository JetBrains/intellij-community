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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class LookupElementBuilder {
  private final String myLookupString;
  private final Object myObject;
  private InsertHandler<LookupElement> myInsertHandler;
  private LookupElementRenderer<LookupElement> myRenderer;
  private AutoCompletionPolicy myAutoCompletionPolicy = AutoCompletionPolicy.SETTINGS_DEPENDENT;
  @Nullable private LookupElementPresentation myHardcodedPresentation;

  LookupElementBuilder(String lookupString) {
    this(lookupString, lookupString);
  }

  LookupElementBuilder(String lookupString, Object object) {
    myLookupString = lookupString;
    myObject = object;
  }

  public LookupElementBuilder setInsertHandler(InsertHandler<LookupElement> insertHandler) {
    myInsertHandler = insertHandler;
    return this;
  }

  public LookupElementBuilder setRenderer(LookupElementRenderer<LookupElement> renderer) {
    myRenderer = renderer;
    return this;
  }

  public LookupElementBuilder setIcon(@Nullable Icon icon) {
    getHardcodedPresentation().setIcon(icon);
    return this;
  }

  @NotNull
  private LookupElementPresentation getHardcodedPresentation() {
    LookupElementPresentation p = myHardcodedPresentation;
    if (p == null) {
      p = myHardcodedPresentation = new LookupElementPresentation(false);
    }
    return p;
  }

  public LookupElementBuilder setTypeText(@Nullable String typeText) {
    getHardcodedPresentation().setTypeText(typeText);
    return this;
  }

  public LookupElementBuilder setPresentableText(@NotNull String presentableText) {
    getHardcodedPresentation().setItemText(presentableText);
    return this;
  }

  public LookupElementBuilder setAutoCompletionPolicy(AutoCompletionPolicy policy) {
    myAutoCompletionPolicy = policy;
    return this;
  }

  public LookupElementBuilder setBold() {
    return setBold(true);
  }

  public LookupElementBuilder setBold(boolean bold) {
    getHardcodedPresentation().setItemTextBold(bold);
    return this;
  }

  public LookupElementBuilder setStrikeout() {
    return setStrikeout(true);
  }
  
  public LookupElementBuilder setStrikeout(boolean strikeout) {
    getHardcodedPresentation().setStrikeout(strikeout);
    return this;
  }

  public LookupElementBuilder setTailText(String tailText) {
    return setTailText(tailText, false);
  }

  public LookupElementBuilder setTailText(String tailText, boolean grayed) {
    getHardcodedPresentation().setTailText(tailText, grayed);
    return this;
  }

  public LookupElement createLookupElement() {
    return new BuiltLookupElement(this);
  }

  private static class BuiltLookupElement extends LookupElement {
    private final InsertHandler<LookupElement> myInsertHandler;
    private final String myLookupString;
    private final Object myObject;
    @Nullable private final LookupElementRenderer<LookupElement> myRenderer;
    @Nullable private final LookupElementPresentation myHardcodedPresentation;
    private final AutoCompletionPolicy myAutoCompletionPolicy;

    public BuiltLookupElement(LookupElementBuilder builder) {
      myInsertHandler = builder.myInsertHandler;
      myLookupString = builder.myLookupString;
      myObject = builder.myObject;
      myRenderer = builder.myRenderer;
      myAutoCompletionPolicy = builder.myAutoCompletionPolicy;

      final LookupElementPresentation presentation = builder.myHardcodedPresentation;
      if (presentation != null || myRenderer == null) {
        myHardcodedPresentation = new LookupElementPresentation(false);
        if (presentation != null) {
          myHardcodedPresentation.copyFrom(presentation);
        }
        if (myHardcodedPresentation.getItemText() == null) {
          myHardcodedPresentation.setItemText(myLookupString);
        }
      } else {
        myHardcodedPresentation = null;
      }

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

      BuiltLookupElement that = (BuiltLookupElement)o;

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
}
