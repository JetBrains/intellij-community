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

package com.intellij.ui.classFilter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Tag("class-filter")
public class ClassFilter implements JDOMExternalizable, Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.classFilter.ClassFilter");
  public static final ClassFilter[] EMPTY_ARRAY = new ClassFilter[0];

  @Attribute("pattern")
  public String PATTERN = "";
  @Attribute("enabled")
  public boolean ENABLED = true;

  @Attribute("include")
  public boolean INCLUDE = true;

  private Matcher myMatcher;  // to speedup matching

  public ClassFilter() {
  }

  public ClassFilter(String pattern) {
    PATTERN = pattern;
    ENABLED = true;
  }

  @Transient
  public String getPattern() {
    return PATTERN;
  }

  @Transient
  public boolean isEnabled() {
    return ENABLED;
  }

  @Transient
  public boolean isInclude() {
    return INCLUDE;
  }

  public void setPattern(String pattern) {
    if (pattern != null && !pattern.equals(PATTERN)) {
      PATTERN = pattern;
      myMatcher = null;
    }
  }
  public void setEnabled(boolean value) {
    ENABLED = value;
  }

  public void setInclude(boolean value) {
    INCLUDE = value;
  }

  public String toString() {
    return getPattern();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.addContent(new Element("option").setAttribute("name", "PATTERN").setAttribute("value", PATTERN));
    element.addContent(new Element("option").setAttribute("name", "ENABLED").setAttribute("value", String.valueOf(ENABLED)));
    if (!INCLUDE) {
      element.addContent(new Element("option").setAttribute("name", "INCLUDE").setAttribute("value", "false"));
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassFilter)) return false;

    ClassFilter classFilter = (ClassFilter)o;

    if (isEnabled() != classFilter.isEnabled()) return false;
    if (!getPattern().equals(classFilter.getPattern())) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = PATTERN.hashCode();
    result = 29 * result + (ENABLED ? 1 : 0);
    return result;
  }

  @Override
  public ClassFilter clone() {
    try {
      return (ClassFilter) super.clone();
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean matches(String name) {
    return getMatcher(name).matches();
  }

  private Matcher getMatcher(final String name) {
    if (myMatcher == null) {
      // need to quote dots and dollars
      final String regex = getPattern().replaceAll("\\.", "\\\\.").replaceAll("\\$", "\\\\\\$").replaceAll("\\*", ".*");
      final Pattern pattern = Pattern.compile(regex);
      myMatcher = pattern.matcher("");
    }
    myMatcher.reset(name);
    return myMatcher;
  }

}