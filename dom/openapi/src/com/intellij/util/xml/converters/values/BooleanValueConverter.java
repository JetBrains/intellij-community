/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.converters.values;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomBundle;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class BooleanValueConverter extends ResolvingConverter<String> {
  @NonNls private static final String BOOLEAN = "boolean";

  @NonNls private static final String[] VARIANTS = {"false", "true"};

  private boolean myAllowEmpty;

  public static BooleanValueConverter getInstance(final boolean allowEmpty) {
     return new BooleanValueConverter(allowEmpty);
  }

  public BooleanValueConverter(final boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }

  @NonNls
  public String[] getAllValues() {
    final String[] strings = ArrayUtil.mergeArrays(getTrueValues(), getFalseValues(), String.class);

    Arrays.sort(strings);

    return strings;
  }

  @NonNls
  public String[] getTrueValues() {
    return new String[] {"true"};
  }

  @NonNls
  public String[] getFalseValues() {
    return new String[] {"false"};
  }

  public boolean isTrue(String s) {
    return Arrays.binarySearch(getTrueValues(), s) >= 0;
  }

  public String fromString(@Nullable @NonNls final String stringValue, final ConvertContext context) {
    if (stringValue != null && ((myAllowEmpty && stringValue.trim().length() == 0) || Arrays.binarySearch(getAllValues(), stringValue) >= 0)) {
      return stringValue;
    }
    return null;
  }

  public String toString(@Nullable final String s, final ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<? extends String> getVariants(final ConvertContext context) {
    return Arrays.asList(VARIANTS);
  }

  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return DomBundle.message("value.converter.format.exception", s, BOOLEAN);
  }
}