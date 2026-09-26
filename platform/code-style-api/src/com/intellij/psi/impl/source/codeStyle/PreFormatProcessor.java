// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to extends the code formatting process by performing additional changes before the code formatting.
 */
public interface PreFormatProcessor {
  ExtensionPointName<PreFormatProcessor> EP_NAME = ExtensionPointName.create("com.intellij.preFormatProcessor");

  /**
   * Callback to be invoked before formatting. Implementation is allowed to do the following:
   * <pre>
   * <ul>
   *   <li>
   *     return not given text range but adjusted one. E.g. we want to reformat a field at a java class but 
   *     have 'align field in columns' settings on. That's why the range should be expanded in order to
   *     cover all fields group;
   *   </li>
   *   <li>
   *     another use-case might be target PSI file/document modification (e.g. change words case). The only requirement here
   *     is that given element stays valid;
   *   </li>
   * </ul>
   * </pre>
   * 
   * <b>Note:</b> the callback might expect to be called for every injected root at the target formatting context.
   * 
   * @param element  target element which contents can be adjusted if necessary
   * @param range    target range within the given element
   * @return         range recommended to use for further processing
   */
  @NotNull
  TextRange process(@NotNull ASTNode element, @NotNull TextRange range);

  /**
   * Adjusts the formatting range before any PSI or document modifications occur.
   * Intended purely for range boundary changes (e.g., expanding the range to cover an alignment group).
   * Unlike {@link #process}, this method must not modify the PSI or document.
   *
   * @param element target element whose formatting range can be adjusted
   * @param range   current formatting range within the given element
   * @return        adjusted range to use for further processing; return {@code range} unchanged if no adjustment is needed
   */
  @ApiStatus.Experimental
  default @NotNull TextRange adjustRange(@NotNull ASTNode element, @NotNull TextRange range) {
    return range;
  }

  /**
   * Returns true if this preprocessor changes only whitespaces and can run when the canChangeWhiteSpacesOnly flag is passed to the formatter.
   */
  default boolean changesWhitespacesOnly() {
    return false;
  }
}
