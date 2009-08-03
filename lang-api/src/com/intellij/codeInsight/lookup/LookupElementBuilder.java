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
  private Icon myIcon;
  private String myTypeText;
  private String myPresentableText;

  LookupElementBuilder(String lookupString) {
    this(lookupString, lookupString);
  }

  LookupElementBuilder(String lookupString, Object object) {
    myLookupString = lookupString;
    myObject = object;
  }

  public LookupElementBuilder withInsertHandler(InsertHandler<LookupElement> insertHandler) {
    myInsertHandler = insertHandler;
    return this;
  }

  public LookupElementBuilder withRenderer(LookupElementRenderer<LookupElement> renderer) {
    myRenderer = renderer;
    return this;
  }

  public LookupElementBuilder withIcon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  public LookupElementBuilder withTypeText(@Nullable String typeText) {
    myTypeText = typeText;
    return this;
  }

  public LookupElementBuilder withPresentableText(@NotNull String presentableText) {
    myPresentableText = presentableText;
    return this;
  }

  public LookupElementBuilder withAutoCompletionPolicy(AutoCompletionPolicy policy) {
    myAutoCompletionPolicy = policy;
    return this;
  }

  public LookupElement createLookupElement() {
    return new BuiltLookupElement(this);
  }

  private static class BuiltLookupElement extends LookupElement {
    private Icon myIcon;
    private InsertHandler<LookupElement> myInsertHandler;
    private String myLookupString;
    private String myPresentableText;
    private Object myObject;
    private LookupElementRenderer<LookupElement> myRenderer;
    private AutoCompletionPolicy myAutoCompletionPolicy;
    private String myTypeText;

    public BuiltLookupElement(LookupElementBuilder builder) {
      myIcon = builder.myIcon;
      myInsertHandler = builder.myInsertHandler;
      myLookupString = builder.myLookupString;
      myPresentableText = builder.myPresentableText != null ? builder.myPresentableText : myLookupString;
      myObject = builder.myObject;
      myRenderer = builder.myRenderer;
      myAutoCompletionPolicy = builder.myAutoCompletionPolicy;
      myTypeText = builder.myTypeText;
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
    public InsertHandler<? extends LookupElement> getInsertHandler() {
      throw new UnsupportedOperationException();
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
        presentation.setIcon(myIcon);
        presentation.setItemText(myPresentableText);
        presentation.setTypeText(myTypeText);
      }
    }

    @NotNull
    @Override
    protected LookupElementRenderer<? extends LookupElement> getRenderer() {
      throw new UnsupportedOperationException("Method getRenderer is not yet implemented in " + getClass().getName());
    }
  }
}
