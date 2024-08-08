// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class TodoPattern implements Cloneable {
  private static final String CASE_SENS_ATT = "case-sensitive";
  private static final String PATTERN_ATT = "pattern";

  private final IndexPattern myIndexPattern;
  private TodoAttributes myAttributes;

  @Internal
  public TodoPattern(@NotNull TodoAttributes attributes) {
    this("", attributes, false);
  }

  @Internal
  public TodoPattern(@NotNull Element state, @NotNull TextAttributes defaultTodoAttributes) {
    myAttributes = new TodoAttributes(state, defaultTodoAttributes);
    myIndexPattern = new IndexPattern(
      state.getAttributeValue(PATTERN_ATT, "").trim(),
      Boolean.parseBoolean(state.getAttributeValue(CASE_SENS_ATT)));
  }

  public TodoPattern(@NotNull String patternString, @NotNull TodoAttributes attributes, boolean caseSensitive) {
    myIndexPattern = new IndexPattern(patternString, caseSensitive);
    myAttributes = attributes;
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public TodoPattern clone() {
    return new TodoPattern(myIndexPattern.getPatternString(), myAttributes.clone(), myIndexPattern.isCaseSensitive());
  }

  public @NotNull @NlsSafe String getPatternString() {
    return myIndexPattern.getPatternString();
  }

  public void setPatternString(@NotNull String patternString) {
    myIndexPattern.setPatternString(patternString);
  }

  public @NotNull TodoAttributes getAttributes() {
    return myAttributes;
  }

  public void setAttributes(@NotNull TodoAttributes attributes) {
    myAttributes = attributes;
  }

  public boolean isCaseSensitive() {
    return myIndexPattern.isCaseSensitive();
  }

  public void setCaseSensitive(boolean caseSensitive) {
    myIndexPattern.setCaseSensitive(caseSensitive);
  }

  public @Nullable Pattern getPattern() {
    return myIndexPattern.getPattern();
  }

  public void writeExternal(@NotNull Element element) {
    myAttributes.writeExternal(element);
    if (myIndexPattern.isCaseSensitive()) {
      element.setAttribute(CASE_SENS_ATT, "true");
    }
    element.setAttribute(PATTERN_ATT, myIndexPattern.getPatternString());
  }

  public boolean equals(Object o) {
    return this == o || o instanceof TodoPattern that && myIndexPattern.equals(that.myIndexPattern);
  }

  public int hashCode() {
    return myIndexPattern.hashCode();
  }

  public IndexPattern getIndexPattern() {
    return myIndexPattern;
  }
}
