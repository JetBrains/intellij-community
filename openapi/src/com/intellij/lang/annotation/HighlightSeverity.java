/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * Defines a highlighting severity level for an annotation.
 *
 * @author max
 * @see Annotation
 */

public class HighlightSeverity implements Comparable<HighlightSeverity>, JDOMExternalizable {
  public String myName;
  public int myVal;

  /**
   * The standard severity level for information annotations.
   */
  public static final HighlightSeverity INFORMATION = new HighlightSeverity("INFORMATION", 0);


  /**
   * The severity level for errors or warnings obtained from server.
   */
  public static final HighlightSeverity GENERIC_SERVER_ERROR_OR_WARNING = new HighlightSeverity("SERVER PROBLEM", 100);



  /**
   * The standard severity level for 'weak' :) warning annotations.
   */
  public static final HighlightSeverity INFO = new HighlightSeverity("INFO", 200);

  /**
   * The standard severity level for warning annotations.
   */
  public static final HighlightSeverity WARNING = new HighlightSeverity("WARNING", 300);

  /**
   * The standard severity level for error annotations.
   */
  public static final HighlightSeverity ERROR = new HighlightSeverity("ERROR", 400);

  /**
   * Creates a new highlighting severity level with the specified name and value.
   *
   * @param name the name of the highlighting level.
   * @param val  the value of the highlighting level. Used for comparing the annotations -
   *             if two annotations with different severity levels cover the same text range, only
   *             the annotation with a higher severity level is displayed.
   */
  public HighlightSeverity(@NonNls String name, int val) {
    myName = name;
    myVal = val;
  }


  //read external only
  public HighlightSeverity() {
  }

  public String toString() {
    return myName;
  }

  public int compareTo(final HighlightSeverity highlightSeverity) {
    return myVal - highlightSeverity.myVal;
  }

  public void setVal(final int val) {
    myVal = val;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final HighlightSeverity that = (HighlightSeverity)o;

    if (!myName.equals(that.myName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myName.hashCode();
    return result;
  }
}
