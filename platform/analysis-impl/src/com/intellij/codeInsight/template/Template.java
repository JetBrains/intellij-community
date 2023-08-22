// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.PresentableLookupValue;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Used to build and run a live template.
 *
 * @see TemplateManager
 */
public abstract class Template implements PresentableLookupValue {

  public enum Property {
    USE_STATIC_IMPORT_IF_POSSIBLE
  }

  @NonNls public static final String END = "END";
  @NonNls public static final String SELECTION = "SELECTION";

  private boolean myUseStaticImport;

  public abstract void addTextSegment(@NotNull String text);

  public abstract void addVariableSegment(@NonNls @NotNull String name);

  @NotNull
  public Variable addVariable(@NonNls @NotNull String name, @NotNull Expression defaultValueExpression, boolean isAlwaysStopAt) {
    return addVariable(name, defaultValueExpression, defaultValueExpression, isAlwaysStopAt);
  }

  public abstract List<Variable> getVariables();

  @NotNull
  public abstract Variable addVariable(@NotNull Expression expression, boolean isAlwaysStopAt);

  @NotNull
  public Variable addVariable(@NonNls @NotNull String name,
                              Expression expression,
                              Expression defaultValueExpression,
                              boolean isAlwaysStopAt) {
    return addVariable(name, expression, defaultValueExpression, isAlwaysStopAt, false);
  }

  @NotNull
  public abstract Variable addVariable(@NonNls @NotNull String name,
                                       Expression expression,
                                       Expression defaultValueExpression,
                                       boolean isAlwaysStopAt,
                                       boolean skipOnStart);
  @NotNull
  public abstract Variable addVariable(@NonNls @NotNull String name, @NonNls String expression, @NonNls String defaultValueExpression, boolean isAlwaysStopAt);

  public abstract void addVariable(@NotNull Variable variable);

  public abstract void addEndVariable();
  public abstract void addSelectionStartVariable();
  public abstract void addSelectionEndVariable();

  public abstract @NonNls String getId();
  public abstract @NlsSafe String getKey();

  @Nullable
  public abstract @NlsContexts.DetailedDescription String getDescription();

  public abstract boolean isToReformat();

  public abstract void setToReformat(boolean toReformat);

  public abstract void setToIndent(boolean toIndent);

  /**
   * Inline templates do not insert text. They install editing segments (red rectangles) in existing text
   * in document: from the `caret offset` to `caret offset + templateString length`.
   * 
   * E.g. they might be useful for inplace rename.
   * 
   * @see com.intellij.codeInsight.template.impl.TemplateState#start
   */
  public abstract void setInline(boolean isInline);

  public abstract int getSegmentsCount();

  @NotNull
  public abstract String getSegmentName( int segmentIndex);

  public abstract int getSegmentOffset(int segmentIndex);

  /**
   * @return template text as it appears in Live Template settings, including variables surrounded with '$'
   * @see #getTemplateText()
   */
  @NotNull
  public abstract @NlsSafe String getString();

  /**
   * @return template text without any variables and with '$' character escapes removed.
   * @see #getString()
   */
  @NotNull
  public abstract @NlsSafe String getTemplateText();

  public abstract boolean isToShortenLongNames();
  public abstract void setToShortenLongNames(boolean toShortenLongNames);

  public boolean getValue(@NotNull Property key) {
    return myUseStaticImport;
  }

  public void setValue(@NotNull Property key, boolean value) {
    myUseStaticImport = value;
  }

  public static boolean getDefaultValue(@NotNull Property key) {
    return false;
  }

  @Override
  public @NlsSafe String getPresentation() {
    return getKey();
  }
}
