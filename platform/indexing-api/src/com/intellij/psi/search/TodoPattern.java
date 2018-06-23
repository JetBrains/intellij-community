// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class TodoPattern implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.TodoPattern");

  private IndexPattern myIndexPattern;

  private TodoAttributes myAttributes;

  @NonNls private static final String CASE_SENS_ATT = "case-sensitive";
  @NonNls private static final String PATTERN_ATT = "pattern";

  public TodoPattern(@NotNull TodoAttributes attributes) {
    this("", attributes, false);
  }

  public TodoPattern(@NotNull Element state, @NotNull TextAttributes defaultTodoAttributes) {
    myAttributes = new TodoAttributes(state, defaultTodoAttributes);
    myIndexPattern = new IndexPattern(state.getAttributeValue(PATTERN_ATT, "").trim(), Boolean.parseBoolean(state.getAttributeValue(CASE_SENS_ATT)));
  }

  public TodoPattern(@NotNull String patternString, @NotNull TodoAttributes attributes, boolean caseSensitive) {
    myIndexPattern = new IndexPattern(patternString, caseSensitive);
    myAttributes = attributes;
  }

  @Override
  public TodoPattern clone(){
    try{
      TodoAttributes attributes = myAttributes.clone();
      TodoPattern pattern = (TodoPattern)super.clone();
      pattern.myIndexPattern = new IndexPattern(myIndexPattern.getPatternString(), myIndexPattern.isCaseSensitive());
      pattern.myAttributes = attributes;

      return pattern;
    }
    catch(CloneNotSupportedException e){
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public String getPatternString() {
    return myIndexPattern.getPatternString();
  }

  public void setPatternString(@NotNull String patternString) {
    myIndexPattern.setPatternString(patternString);
  }

  @NotNull
  public TodoAttributes getAttributes() {
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

  public Pattern getPattern(){
    return myIndexPattern.getPattern();
  }

  public void writeExternal(@NotNull Element element) {
    myAttributes.writeExternal(element);
    if (myIndexPattern.isCaseSensitive()) {
      element.setAttribute(CASE_SENS_ATT, "true");
    }
    element.setAttribute(PATTERN_ATT, myIndexPattern.getPatternString());
  }

  public boolean equals(Object obj){
    if (!(obj instanceof TodoPattern)){
      return false;
    }

    TodoPattern pattern = (TodoPattern)obj;

    if (!myIndexPattern.equals(pattern.myIndexPattern)) {
      return false;
    }

    if (!Comparing.equal(myAttributes, pattern.myAttributes)){
      return false;
    }

    return true;
  }

  public int hashCode(){
    return myIndexPattern.hashCode();
  }

  public IndexPattern getIndexPattern() {
    return myIndexPattern;
  }
}
