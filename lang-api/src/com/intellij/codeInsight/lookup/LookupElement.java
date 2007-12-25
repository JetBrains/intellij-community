/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public interface LookupElement<T> {
  LookupElement[] EMPTY_ARRAY = new LookupElement[0];

  @NotNull
  T getObject();

  @NotNull
  String getLookupString();

  @NotNull
  TailType getTailType();

  @NotNull
  LookupElement<T> setTailType(@NotNull TailType type);

  @NotNull
  LookupElement<T> setIcon(@Nullable Icon icon);

  /**
   * Sets the lookup element priority. Elements with higher priorities are on the top of the lookup, they are also preferred
   * in SmartType completion.
   * @param priority
   * @return this
   */
  @NotNull
  LookupElement<T> setPriority(double priority);

  /**
   * Sets the lookup element grouping. It works like priotity in lookup elements sorting but doesn't affect the auto-selection policy.
   * @param grouping
   * @return this
   */
  @NotNull
  LookupElement<T> setGrouping(int grouping);

  @NotNull
  LookupElement<T> setPresentableText(@NotNull String presentableText);

  /*@NotNull
  LookupElement setTypeText(@Nullable String text);*/

  @NotNull
  LookupElement<T> setCaseSensitive(boolean caseSensitive);

  LookupElement<T> setBold();
}
