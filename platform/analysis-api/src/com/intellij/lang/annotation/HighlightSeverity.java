// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Defines a highlighting severity level for an annotation.
 *
 * @see Annotation
 */
public final class HighlightSeverity implements Comparable<HighlightSeverity> {
  @NotNull
  public final @NonNls String myName;
  @Nullable
  private final String myBundleKey;

  public final int myVal;

  /**
   * The standard severity level for information annotations.
   */
  public static final HighlightSeverity INFORMATION = new HighlightSeverity("INFORMATION", 10, "information.severity");

  /**
   * The severity level for errors or warnings obtained from server.
   */
  public static final HighlightSeverity GENERIC_SERVER_ERROR_OR_WARNING =
    new HighlightSeverity("SERVER PROBLEM", 100, "server.problem.severity");

  /** @deprecated use {@link #WEAK_WARNING} */
  @Deprecated
  public static final HighlightSeverity INFO = new HighlightSeverity("INFO", 200, "info.severity");

  public static final HighlightSeverity WEAK_WARNING = new HighlightSeverity("WEAK WARNING", 200, "weak.warning.severity");

  /**
   * The standard severity level for warning annotations.
   */
  public static final HighlightSeverity WARNING = new HighlightSeverity("WARNING", 300, "warning.severity");

  /**
   * The standard severity level for error annotations.
   */
  public static final HighlightSeverity ERROR = new HighlightSeverity("ERROR", 400, "error.severity");

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
   * @param bundleKey the key for the localized name of the highlighting level.
   */
  public HighlightSeverity(@NotNull String name, int val, @Nullable String bundleKey) {
    myName = name;
    myVal = val;
    myBundleKey = bundleKey;
  }

  public HighlightSeverity(@NotNull String name, int val) {
    this(name, val, null);
  }

  public HighlightSeverity(@NotNull Element element) {
    this(readField(element, "myName"), Integer.parseInt(readField(element, "myVal")), null);
  }

  private static String readField(Element element, String name) {
    String value = JDOMExternalizerUtil.readField(element, name);
    if (value == null) throw new IllegalArgumentException("Element '" + element + "' misses attribute '" + name + "'");
    return value;
  }

  public @NonNls @NotNull String getName() {
    return myName;
  }

  public @Nls @NotNull String getDisplayName() {
    return getBundleText("");
  }

  public @Nls @NotNull String getDisplayLowercaseName() {
    return getBundleText(".lowercase");
  }

  public @Nls @NotNull String getDisplayLowercaseCapitalizedName() {
    return getBundleText(".lowercase.capitalized");
  }

  public @Nls @NotNull String getDisplayLowercasePluralName() {
    return getBundleText(".lowercase.plural");
  }

  private @NotNull @Nls String getBundleText(@NotNull String suffix) {
    if (myBundleKey != null) return InspectionsBundle.message(myBundleKey + suffix);
    @NlsSafe String name = myName;
    return name;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HighlightSeverity that = (HighlightSeverity)o;
    return myVal == that.myVal && myName.equals(that.myName);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(myName) + myVal;
  }

  @Override
  public String toString() {
    return myName;
  }
}
