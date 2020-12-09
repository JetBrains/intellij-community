// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.BundleBase;
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
import java.util.function.Supplier;

/**
 * Defines a highlighting severity level for an annotation.
 *
 * @see Annotation
 */
public final class HighlightSeverity implements Comparable<HighlightSeverity> {
  @NotNull
  public final @NonNls String myName;

  public final int myVal;

  @Nullable
  private final Supplier<@Nls String> myDisplayName;
  @Nullable
  private final Supplier<@Nls String> myCapitalizedDisplayName;
  @Nullable
  private final Supplier<@Nls String> myCountMessageTemplate;

  /**
   * The standard severity level for information annotations.
   */
  @SuppressWarnings("UnresolvedPropertyKey")
  public static final HighlightSeverity INFORMATION =
    new HighlightSeverity(
      "INFORMATION",
      10,
      InspectionsBundle.messagePointer("information.severity"),
      InspectionsBundle.messagePointer("information.severity.capitalized"),
      InspectionsBundle.messagePointer("information.severity.count.message"));

  /**
   * The severity level for errors or warnings obtained from server.
   */
  @SuppressWarnings("UnresolvedPropertyKey")
  public static final HighlightSeverity GENERIC_SERVER_ERROR_OR_WARNING =
    new HighlightSeverity(
      "SERVER PROBLEM",
      100,
      InspectionsBundle.messagePointer("server.problem.severity"),
      InspectionsBundle.messagePointer("server.problem.severity.capitalized"),
      InspectionsBundle.messagePointer("server.problem.severity.count.message")
    );

  /** @deprecated use {@link #WEAK_WARNING} */
  @Deprecated
  @SuppressWarnings("UnresolvedPropertyKey")
  public static final HighlightSeverity INFO =
    new HighlightSeverity(
      "INFO",
      200,
      InspectionsBundle.messagePointer("info.severity"),
      InspectionsBundle.messagePointer("info.severity.capitalized"),
      InspectionsBundle.messagePointer("info.severity.count.message")
    );

  @SuppressWarnings("UnresolvedPropertyKey")
  public static final HighlightSeverity WEAK_WARNING =
    new HighlightSeverity(
      "WEAK WARNING",
      200,
      InspectionsBundle.messagePointer("weak.warning.severity"),
      InspectionsBundle.messagePointer("weak.warning.severity.capitalized"),
      InspectionsBundle.messagePointer("weak.warning.severity.count.message")
    );

  /**
   * The standard severity level for warning annotations.
   */
  @SuppressWarnings("UnresolvedPropertyKey")
  public static final HighlightSeverity WARNING =
    new HighlightSeverity(
      "WARNING",
      300,
      InspectionsBundle.messagePointer("warning.severity"),
      InspectionsBundle.messagePointer("warning.severity.capitalized"),
      InspectionsBundle.messagePointer("warning.severity.count.message")
    );

  /**
   * The standard severity level for error annotations.
   */
  @SuppressWarnings("UnresolvedPropertyKey")
  public static final HighlightSeverity ERROR =
    new HighlightSeverity(
      "ERROR",
      400,
      InspectionsBundle.messagePointer("error.severity"),
      InspectionsBundle.messagePointer("error.severity.capitalized"),
      InspectionsBundle.messagePointer("error.severity.count.message")
    );

  /**
   * Standard severity levels.
   */
  public static final HighlightSeverity[] DEFAULT_SEVERITIES =
    {INFORMATION, GENERIC_SERVER_ERROR_OR_WARNING, INFO, WEAK_WARNING, WARNING, ERROR};

  /**
   * Creates a new highlighting severity level with the specified name and value.
   *
   * @param name                   the name of the highlighting level.
   * @param val                    the value of the highlighting level. Used for comparing the annotations -
   *                               if two annotations with different severity levels cover the same text range, only
   *                               the annotation with a higher severity level is displayed.
   * @param displayName            the supplier of the localized name for the level.
   * @param capitalizedDisplayName the supplier of the localized name with capitalization for the level.
   * @param countMessageTemplate   the supplier of the count problems message template for the level.
   */
  public HighlightSeverity(@NotNull String name,
                           int val,
                           @Nullable Supplier<@Nls String> displayName,
                           @Nullable Supplier<@Nls String> capitalizedDisplayName,
                           @Nullable Supplier<@Nls String> countMessageTemplate) {
    myName = name;
    myVal = val;
    myDisplayName = displayName;
    myCapitalizedDisplayName = capitalizedDisplayName;
    myCountMessageTemplate = countMessageTemplate;
  }

  public HighlightSeverity(@NotNull String name, int val) {
    this(name, val, null, null, null);
  }

  public HighlightSeverity(@NotNull Element element) {
    this(readField(element, "myName"), Integer.parseInt(readField(element, "myVal")), null, null, null);
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
    return getBundleMessage(myDisplayName);
  }

  public @Nls @NotNull String getDisplayCapitalizedName() {
    return getBundleMessage(myCapitalizedDisplayName);
  }

  public @Nls @NotNull String getCountMessage(int count) {
    if (myCountMessageTemplate != null) return BundleBase.format(myCountMessageTemplate.get(), count);
    return InspectionsBundle.message("custom.severity.count.message", count, myName);
  }

  private @NotNull @Nls String getBundleMessage(@Nullable Supplier<@Nls String> messageSupplier) {
    if (messageSupplier != null) return messageSupplier.get();
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
