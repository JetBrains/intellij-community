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

  public static final @NotNull SimpleColoredText EMPTY = new SimpleColoredText() {
    @Override
    public void append(@Nls @NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void append(@NotNull ColoredText coloredText) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void appendToComponent(@NotNull ColoredTextContainer component) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insert(int index, @Nls @NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SimpleColoredText derive(SimpleTextAttributes attributes, boolean override) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nls String toString() {
      return "";
    }

    @Override
    public @NotNull ColoredText toColoredText() {
      return ColoredText.empty();
    }
  };

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

  public @NotNull ColoredText toColoredText() {
    if (myTexts.isEmpty()) {
      return ColoredText.empty();
    }
    if (myTexts.size() == 1) {
      return ColoredText.singleFragment(myTexts.get(0), myAttributes.get(0));
    }

    ColoredText.Builder builder = ColoredText.builder();
    for (int i = 0; i < myTexts.size(); i++) {
      builder.append(myTexts.get(i), myAttributes.get(i));
    }
    return builder.build();
  }
}
