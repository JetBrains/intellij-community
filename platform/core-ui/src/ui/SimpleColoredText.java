// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SimpleColoredText implements ColoredTextContainer {
  private final ArrayList<@Nls String> myTexts;
  private final ArrayList<SimpleTextAttributes> myAttributes;
  private @NlsSafe String myCachedToString = null;

  public SimpleColoredText() {
    myTexts = new ArrayList<>(3);
    myAttributes = new ArrayList<>(3);
  }

  public SimpleColoredText(@NotNull @NlsContexts.Label String fragment, @NotNull SimpleTextAttributes attributes) {
    this();
    append(fragment, attributes);
  }

  @Override
  public void append(@NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes) {
    myTexts.add(fragment);
    myCachedToString = null;
    myAttributes.add(attributes);
  }

  public void insert(int index, @NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes) {
    myTexts.add(index, fragment);
    myCachedToString = null;
    myAttributes.add(index, attributes);
  }

  public void clear() {
    myTexts.clear();
    myCachedToString = null;
    myAttributes.clear();
  }

  public void appendToComponent(@NotNull ColoredTextContainer component) {
    int size = myTexts.size();
    for (int i = 0; i < size; i++) {
      String text = myTexts.get(i);
      SimpleTextAttributes attribute = myAttributes.get(i);
      component.append(text, attribute);
    }
  }

  public @Nls String toString() {
    if (myCachedToString == null) {
      myCachedToString = String.join("", myTexts);
    }
    return myCachedToString;
  }

  public ArrayList<@Nls String> getTexts() {
    return myTexts;
  }

  public ArrayList<SimpleTextAttributes> getAttributes() {
    return myAttributes;
  }

  public SimpleColoredText derive(SimpleTextAttributes attributes, boolean override) {
    SimpleColoredText result = new SimpleColoredText();
    for (int i = 0; i < myTexts.size(); i++) {
      SimpleTextAttributes overridden = override
                                        ? SimpleTextAttributes.merge(myAttributes.get(i), attributes)
                                        : SimpleTextAttributes.merge(attributes, myAttributes.get(i));
      result.append(myTexts.get(i), overridden);
    }
    return result;
  }
}
