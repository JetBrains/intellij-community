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
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * @author Vladimir Kondratyev
 */
public class TodoPattern implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.TodoPattern");

  private IndexPattern myIndexPattern;

  /**
   * Specify Icon and text attributes.
   */
  private TodoAttributes myAttributes;

  @NonNls private static final String CASE_SENS_ATT = "case-sensitive";
  @NonNls private static final String PATTERN_ATT = "pattern";

  public TodoPattern(@NotNull TodoAttributes attributes){
    this("", attributes, false);
  }

  public TodoPattern(@NotNull @NonNls String patternString, @NotNull TodoAttributes attributes, boolean caseSensitive) {
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
  public String getPatternString(){
    return myIndexPattern.getPatternString();
  }

  public void setPatternString(@NotNull String patternString){
    myIndexPattern.setPatternString(patternString);
  }

  @NotNull
  public TodoAttributes getAttributes(){
    return myAttributes;
  }

  public void setAttributes(@NotNull TodoAttributes attributes){
    myAttributes = attributes;
  }

  public boolean isCaseSensitive(){
    return myIndexPattern.isCaseSensitive();
  }

  public void setCaseSensitive(boolean caseSensitive){
    myIndexPattern.setCaseSensitive(caseSensitive);
  }

  public Pattern getPattern(){
    return myIndexPattern.getPattern();
  }

  public void readExternal(Element element, @NotNull TextAttributes defaultTodoAttributes) {
    try {
      myAttributes = new TodoAttributes(element,defaultTodoAttributes);
    }
    catch (InvalidDataException e) {
      throw new RuntimeException(e);
    }

    myIndexPattern.setCaseSensitive(Boolean.valueOf(element.getAttributeValue(CASE_SENS_ATT)).booleanValue());
    String attributeValue = element.getAttributeValue(PATTERN_ATT);
    if (attributeValue != null){
      myIndexPattern.setPatternString(attributeValue.trim());
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myAttributes.writeExternal(element);
    element.setAttribute(CASE_SENS_ATT, Boolean.toString(myIndexPattern.isCaseSensitive()));
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
