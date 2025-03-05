// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
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

  /**
   * A static key used to control the suppression of default frontend char filters in the Lookup implementation.
   * Used with {@link EditorImpl}
   * Now default char filters can choose between different results.
   * If this flag is active, the last filter will return always ADD_TO_PREFIX
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static final Key<Boolean> CUSTOM_DEFAULT_CHAR_FILTERS = Key.create("CUSTOM_DEFAULT_CHAR_FILTERS");


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
  public abstract @Nullable Result acceptChar(char c, final int prefixLength, final Lookup lookup);
}
