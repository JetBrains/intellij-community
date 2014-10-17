/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.annotation;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a highlighting severity level for an annotation.
 *
 * @author max
 * @see com.intellij.lang.annotation.Annotation
 */

public class HighlightSeverity implements Comparable<HighlightSeverity> {
  public final String myName;
  public final int myVal;

  /**
   * The standard severity level for information annotations.
   */
  public static final HighlightSeverity INFORMATION = new HighlightSeverity("INFORMATION", 10);


  /**
   * The severity level for errors or warnings obtained from server.
   */
  public static final HighlightSeverity GENERIC_SERVER_ERROR_OR_WARNING = new HighlightSeverity("SERVER PROBLEM", 100);



  /**
   * The standard severity level for 'weak' :) warning annotations.
   */
  @Deprecated
  public static final HighlightSeverity INFO = new HighlightSeverity("INFO", 200);


  public static final HighlightSeverity WEAK_WARNING = new HighlightSeverity("WEAK WARNING", 200);

  /**
   * The standard severity level for warning annotations.
   */
  public static final HighlightSeverity WARNING = new HighlightSeverity("WARNING", 300);

  /**
   * The standard severity level for error annotations.
   */
  public static final HighlightSeverity ERROR = new HighlightSeverity("ERROR", 400);

  /**
   * Standard severities levels
   */
  public static final HighlightSeverity[] DEFAULT_SEVERITIES = {INFORMATION, GENERIC_SERVER_ERROR_OR_WARNING, INFO, WEAK_WARNING, WARNING, ERROR};

  /**
   * Creates a new highlighting severity level with the specified name and value.
   *
   * @param name the name of the highlighting level.
   * @param val  the value of the highlighting level. Used for comparing the annotations -
   *             if two annotations with different severity levels cover the same text range, only
   *             the annotation with a higher severity level is displayed.
   */
  public HighlightSeverity(@NonNls @NotNull String name, int val) {
    myName = name;
    myVal = val;
  }


  //read external only
  public HighlightSeverity(@NotNull Element element) {
    this(JDOMExternalizerUtil.readField(element, "myName"), Integer.valueOf(JDOMExternalizerUtil.readField(element, "myVal")));
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public int compareTo(@NotNull final HighlightSeverity highlightSeverity) {
    return myVal - highlightSeverity.myVal;
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final HighlightSeverity that = (HighlightSeverity)o;

    return myName.equals(that.myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
