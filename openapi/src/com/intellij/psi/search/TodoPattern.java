/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Vladimir Kondratyev
 */
public class TodoPattern implements Cloneable, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.TodoPattern");

  /**
   * Pattern for "todo" matching. It cannot be null.
   */
  private String myPatternString;

  /**
   * Specify Icon and text attributes.
   */
  private TodoAttributes myAttributes;

  private boolean myCaseSensitive;

  private Pattern myPattern;

  public TodoPattern(){
    this("", TodoAttributes.createDefault(), false);
  }

  public TodoPattern(String patternString, TodoAttributes attributes, boolean caseSensitive){
    LOG.assertTrue(patternString != null);
    LOG.assertTrue(attributes != null);
    myPatternString = patternString;
    myAttributes = attributes;
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  public TodoPattern clone(){
    try{
      TodoAttributes attributes = myAttributes.clone();
      TodoPattern pattern = (TodoPattern)super.clone();
      pattern.myAttributes = attributes;

      return pattern;
    }
    catch(CloneNotSupportedException e){
      LOG.error(e);
      return null;
    }
  }

  public String getPatternString(){
    return myPatternString;
  }

  public void setPatternString(String patternString){
    LOG.assertTrue(patternString != null);
    myPatternString = patternString;
    compilePattern();
  }

  public TodoAttributes getAttributes(){
    return myAttributes;
  }

  public void setAttributes(TodoAttributes attributes){
    LOG.assertTrue(attributes != null);
    myAttributes = attributes;
  }

  public boolean isCaseSensitive(){
    return myCaseSensitive;
  }

  public void setCaseSensitive(boolean caseSensitive){
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  public Pattern getPattern(){
    return myPattern;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myAttributes = new TodoAttributes();
    myAttributes.readExternal(element);
    myCaseSensitive = Boolean.valueOf(element.getAttributeValue("case-sensitive")).booleanValue();
    String attributeValue = element.getAttributeValue("pattern");
    if (attributeValue != null){
      myPatternString = attributeValue.trim();
    }
    compilePattern();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myAttributes.writeExternal(element);
    element.setAttribute("case-sensitive", myCaseSensitive ? "true" : "false");
    element.setAttribute("pattern", myPatternString);
  }

  private void compilePattern(){
    try{
      if (myCaseSensitive){
        myPattern = Pattern.compile(myPatternString);
      }
      else{
        myPattern = Pattern.compile(myPatternString, Pattern.CASE_INSENSITIVE);
      }
    }
    catch(PatternSyntaxException e){
      myPattern = null;
    }
  }

  public boolean equals(Object obj){
    if (!(obj instanceof TodoPattern)){
      return false;
    }

    TodoPattern pattern = (TodoPattern)obj;

    if (!Comparing.equal(myPatternString, pattern.myPatternString)){
      return false;
    }

    if (!Comparing.equal(myAttributes, pattern.myAttributes)){
      return false;
    }

    if (myCaseSensitive != pattern.myCaseSensitive){
      return false;
    }

    return true;
  }

  public int hashCode(){
    return myPatternString.hashCode();
  }
}
