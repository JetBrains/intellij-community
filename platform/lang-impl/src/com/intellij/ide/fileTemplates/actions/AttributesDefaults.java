// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.actions;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @author Roman Chernyatchik
 */
public final class AttributesDefaults {
  private final String myDefaultName;
  private final TextRange myDefaultRange;
  private final Map<String, Pair<String, TextRange>> myNamesToValueAndRangeMap = new HashMap<>();
  private Properties myDefaultProperties = null;
  private boolean myFixedName;
  private @Nullable Map<String, @Nls String> myAttributesVisibleNames = null;

  public AttributesDefaults() {
    this(null, null);
  }

  public AttributesDefaults(@Nullable String defaultName) {
    this(defaultName, null);
  }

  public AttributesDefaults(@Nullable String defaultName, @Nullable TextRange defaultRange) {
    myDefaultName = defaultName;
    myDefaultRange = defaultRange;
  }

  public @Nullable String getDefaultFileName() {
    return myDefaultName;
  }

  public @Nullable TextRange getDefaultFileNameSelection() {
    return myDefaultRange;
  }

  public void add(@NotNull String attributeKey, @NotNull String value) {
    add(attributeKey, value, null);
  }

  public void add(@NotNull String attributeKey, @NotNull String value, @Nullable TextRange selectionRange) {
    myNamesToValueAndRangeMap.put(attributeKey, pair(value, selectionRange));
  }

  public void addPredefined(@NotNull String key, @NotNull String value) {
    if (myDefaultProperties == null) {
      myDefaultProperties = new Properties();
    }
    myDefaultProperties.setProperty(key, value);
  }

  public Properties getDefaultProperties() {
    return myDefaultProperties;
  }

  public @Nullable TextRange getRangeFor(@NotNull String attributeKey) {
    final Pair<String, TextRange> valueAndRange = myNamesToValueAndRangeMap.get(attributeKey);
    return Pair.getSecond(valueAndRange);
  }

  public @Nullable String getDefaultValueFor(@NotNull String attributeKey) {
    final Pair<String, TextRange> valueAndRange = myNamesToValueAndRangeMap.get(attributeKey);
    return Pair.getFirst(valueAndRange);
  }

  public void setAttributeVisibleNames(Map<String, String> visibleNames) {
    myAttributesVisibleNames = visibleNames;
  }

  public @Nullable Map<String, @Nls String> getAttributeVisibleNames() {
    return myAttributesVisibleNames;
  }

  public boolean isFixedName() {
    return myFixedName;
  }

  public AttributesDefaults withFixedName(boolean fixedName) {
    myFixedName = fixedName;
    return this;
  }
}