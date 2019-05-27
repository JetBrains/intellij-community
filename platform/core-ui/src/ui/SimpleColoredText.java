// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public class SimpleColoredText implements ColoredTextContainer {
  private final ArrayList<String> myTexts;
  private final ArrayList<SimpleTextAttributes> myAttributes;
  private String myCachedToString = null;

  public SimpleColoredText() {
    myTexts = new ArrayList<>(3);
    myAttributes = new ArrayList<>(3);
  }

  public SimpleColoredText(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
    this();
    append(fragment, attributes);
  }

  @Override
  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes){
    myTexts.add(fragment);
    myCachedToString = null;
    myAttributes.add(attributes);
  }

  public void insert(int index, @NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
    myTexts.add(index, fragment);
    myCachedToString = null;
    myAttributes.add(index, attributes);
  }

  @Override
  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
    append(fragment, attributes);
  }

  @Override
  public void setIcon(@Nullable Icon icon) {
  }

  @Override
  public void setToolTipText(@Nullable String text) {
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

  public String toString() {
    if (myCachedToString == null) {
      myCachedToString = StringUtil.join(myTexts,"");
    }
    return myCachedToString;
  }

  public ArrayList<String> getTexts() {
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
