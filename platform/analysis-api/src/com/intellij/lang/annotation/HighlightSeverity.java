// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a highlighting severity level for an annotation.
 *
 * @author max
 * @see Annotation
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
   *
   * @deprecated use {@link #WEAK_WARNING}
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
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
   * Standard severity levels.
   */
  public static final HighlightSeverity[] DEFAULT_SEVERITIES =
    {INFORMATION, GENERIC_SERVER_ERROR_OR_WARNING, INFO, WEAK_WARNING, WARNING, ERROR};

  /**
   * Creates a new highlighting severity level with the specified name and value.
   *
   * @param name the name of the highlighting level.
   * @param val  the value of the highlighting level. Used for comparing the annotations -
   *             if two annotations with different severity levels cover the same text range, only
   *             the annotation with a higher severity level is displayed.
   */
  public HighlightSeverity(@NotNull String name, int val) {
    myName = name;
    myVal = val;
  }

  public HighlightSeverity(@NotNull Element element) {
    this(readField(element, "myName"), Integer.valueOf(readField(element, "myVal")));
  }

  private static String readField(Element element, String name) {
    String value = JDOMExternalizerUtil.readField(element, name);
    if (value == null) throw new IllegalArgumentException("Element '" + element + "' misses attribute '" + name + "'");
    return value;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public int compareTo(@NotNull HighlightSeverity highlightSeverity) {
    return myVal - highlightSeverity.myVal;
  }

  @SuppressWarnings("deprecation")
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final HighlightSeverity that = (HighlightSeverity)o;

    if (myVal != that.myVal) return false;
    return myName.equals(that.myName);
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    return 31 * result + myVal;
  }

  @Override
  public String toString() {
    return myName;
  }
}