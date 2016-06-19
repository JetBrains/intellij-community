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

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.Variable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to build and run a live template.
 * @see TemplateManager
 */
public abstract class Template {

  public enum Property {
    USE_STATIC_IMPORT_IF_POSSIBLE
  }

  private boolean myUseStaticImport;

  public abstract void addTextSegment(@NotNull String text);
  public abstract void addVariableSegment(@NonNls @NotNull String name);

  public Variable addVariable(@NonNls @NotNull String name, @NotNull Expression defaultValueExpression, boolean isAlwaysStopAt) {
    return addVariable(name, defaultValueExpression, defaultValueExpression, isAlwaysStopAt);
  }
  public abstract Variable addVariable(Expression expression, boolean isAlwaysStopAt);

  public Variable addVariable(@NonNls @NotNull String name, Expression expression, Expression defaultValueExpression, boolean isAlwaysStopAt) {
    return addVariable(name, expression, defaultValueExpression, isAlwaysStopAt, false);
  }

  public abstract Variable addVariable(@NonNls @NotNull String name,
                                       Expression expression,
                                       Expression defaultValueExpression,
                                       boolean isAlwaysStopAt,
                                       boolean skipOnStart);
  public abstract Variable addVariable(@NonNls @NotNull String name, @NonNls String expression, @NonNls String defaultValueExpression, boolean isAlwaysStopAt);

  public abstract void addEndVariable();
  public abstract void addSelectionStartVariable();
  public abstract void addSelectionEndVariable();

  public abstract String getId();
  public abstract String getKey();

  @Nullable
  public abstract String getDescription();

  public abstract boolean isToReformat();

  public abstract void setToReformat(boolean toReformat);

  public abstract void setToIndent(boolean toIndent);

  public abstract void setInline(boolean isInline);

  public abstract int getSegmentsCount();

  public abstract String getSegmentName( int segmentIndex);

  public abstract int getSegmentOffset(int segmentIndex);

  /**
   * @return template text as it appears in Live Template settings, including variables surrounded with '$'
   * @see #getTemplateText()
   */
  @NotNull
  public abstract String getString();

  /**
   * @return template text without any variables and with '$' character escapes removed.
   * @see #getString()
   */
  @NotNull
  public abstract String getTemplateText();

  public abstract boolean isToShortenLongNames();
  public abstract void setToShortenLongNames(boolean toShortenLongNames);

  public boolean getValue(@NotNull Property key) {
    return myUseStaticImport;
  }

  public void setValue(@NotNull Property key, boolean value) {
    myUseStaticImport = true;
  }

  public static boolean getDefaultValue(@NotNull Property key) {
    return false;
  }
}
