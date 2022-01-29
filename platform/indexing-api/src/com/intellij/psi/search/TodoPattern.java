// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class TodoPattern implements Cloneable {
  private static final Logger LOG = Logger.getInstance(TodoPattern.class);

  private IndexPattern indexPattern;

  private TodoAttributes attributes;

  @NonNls private static final String CASE_SENS_ATT = "case-sensitive";
  @NonNls private static final String PATTERN_ATT = "pattern";

  public TodoPattern(@NotNull TodoAttributes attributes) {
    this("", attributes, false);
  }

  public TodoPattern(@NotNull Element state, @NotNull TextAttributes defaultTodoAttributes) {
    attributes = new TodoAttributes(state, defaultTodoAttributes);
    indexPattern = new IndexPattern(state.getAttributeValue(PATTERN_ATT, "").trim(),
                                    Boolean.parseBoolean(state.getAttributeValue(CASE_SENS_ATT)));
  }

  public TodoPattern(@NotNull String patternString, @NotNull TodoAttributes attributes, boolean caseSensitive) {
    indexPattern = new IndexPattern(patternString, caseSensitive);
    this.attributes = attributes;
  }

  @Override
  public TodoPattern clone() {
    try {
      TodoAttributes attributes = this.attributes.clone();
      TodoPattern pattern = (TodoPattern)super.clone();
      pattern.indexPattern = new IndexPattern(indexPattern.getPatternString(), indexPattern.isCaseSensitive());
      pattern.attributes = attributes;

      return pattern;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  @NlsSafe
  public String getPatternString() {
    return indexPattern.getPatternString();
  }

  public void setPatternString(@NotNull String patternString) {
    indexPattern.setPatternString(patternString);
  }

  @NotNull
  public TodoAttributes getAttributes() {
    return attributes;
  }

  public void setAttributes(@NotNull TodoAttributes attributes) {
    this.attributes = attributes;
  }

  public boolean isCaseSensitive() {
    return indexPattern.isCaseSensitive();
  }

  public void setCaseSensitive(boolean caseSensitive) {
    indexPattern.setCaseSensitive(caseSensitive);
  }

  @Nullable
  public Pattern getPattern() {
    return indexPattern.getPattern();
  }

  public void writeExternal(@NotNull Element element) {
    attributes.writeExternal(element);
    if (indexPattern.isCaseSensitive()) {
      element.setAttribute(CASE_SENS_ATT, "true");
    }
    element.setAttribute(PATTERN_ATT, indexPattern.getPatternString());
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof TodoPattern)) {
      return false;
    }

    TodoPattern pattern = (TodoPattern)obj;

    if (!indexPattern.equals(pattern.indexPattern)) {
      return false;
    }

    if (!Comparing.equal(attributes, pattern.attributes)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return indexPattern.hashCode();
  }

  public IndexPattern getIndexPattern() {
    return indexPattern;
  }
}
