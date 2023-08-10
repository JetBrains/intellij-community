/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * An extension that allows specifying the behavior of specific characters when performing code completion.
 * By default, typing non-alphanumeric symbol (that cannot be a part of Java identifier) dismisses the completion popup.
 * In some languages and contexts, this could be an undesired behavior. Implement this extension to provide custom
 * rules for your language and context.
 * <p>
 *   Please note that for historical reasons, this extension point is not language-specific and all the filters
 *   are invoked for any language. It's highly recommended that implementors check the current language by themselves
 *   (e. g., by checking {@code lookup.getPsiFile().getLanguage()}). Otherwise, your filter may affect unrelated languages.
 * </p>
 */
public abstract class CharFilter {
  public static final ExtensionPointName<CharFilter> EP_NAME = ExtensionPointName.create("com.intellij.lookup.charFilter");

  public enum Result {
    /**
     * Indicates that typed character should be appended to current prefix and completion session should continue
     */
    ADD_TO_PREFIX,
    /**
     * Indicates that completion session should be finished and the currently selected item should be inserted to the document
     */
    SELECT_ITEM_AND_FINISH_LOOKUP,
    /**
     * Indicates that completion session should be cancelled without inserting the currently selected item
     */
    HIDE_LOOKUP
  }

  /**
   * Informs about further action on typing character c when completion lookup has specified prefix.
   * <p>
   * @implNote for historical reasons, this extension point is not language-specific and all the filters
   *  are invoked for any language. It's highly recommended that implementors check the current language by themselves
   *  (e. g., by checking {@code lookup.getPsiFile().getLanguage()}). Otherwise, your filter may affect unrelated languages.
   *
   * @param c character being inserted
   * @param prefixLength number of prefix characters
   * @param lookup current lookup
   * @return further action or null, which indicates that some other {@link CharFilter}
   * should handle this char. {@linkplain com.intellij.codeInsight.completion.DefaultCharFilter Default char filter}
   * handles common cases like finishing with ' ', '(', ';', etc.
   */
  @Nullable
  public abstract Result acceptChar(char c, final int prefixLength, final Lookup lookup);
}
