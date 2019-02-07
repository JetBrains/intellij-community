// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@ApiStatus.Experimental
public interface ExternalFormatProcessor {
  ExtensionPointName<ExternalFormatProcessor> EP_NAME = ExtensionPointName.create("com.intellij.externalFormatProcessor");

  /**
   * @param source the source file with code
   * @return true, if external processor selected as active (enabled) for the source file
   */
  boolean activeForFile(@NotNull PsiFile source);

  /**
   * Formats the range in a source file.
   *
   * @param source the source file with code
   * @param range the range for formatting
   * @return the range after formatting or the initial range, if external format procedure cannot be applied to the source
   */
  TextRange format(@NotNull PsiFile source, @NotNull TextRange range);

  /**
   * @return the unique id for external formatter
   */
  @NonNls
  @NotNull
  String getId();

  /**
   * @param source the source file with code
   * @return true, if there is an active external (enabled) formatter for the source
   */
  static boolean useExternalFormatter(@NotNull PsiFile source) {
    return EP_NAME.getExtensionList().stream().anyMatch(efp -> efp.activeForFile(source));
  }

  /**
   * @param externalFormatterId the unique id for external formatter
   * @return the external formatter with the unique id, if any
   */
  @NotNull
  static Optional<ExternalFormatProcessor> findExternalFormatter(@NonNls @NotNull String externalFormatterId) {
    return EP_NAME.getExtensionList().stream().filter(efp -> externalFormatterId.equals(efp.getId())).findFirst();
  }

  /**
   * @param source the source file with code
   * @param range the range for formatting
   * @return the range after formatting or empty value, if external format procedure was not found or inactive (disabled)
   */
  @NotNull
  static Optional<TextRange> formatExternally(@NotNull PsiFile source, @NotNull TextRange range) {
    for (ExternalFormatProcessor efp : EP_NAME.getExtensionList()) {
      if (efp.activeForFile(source)) {
        return Optional.of(efp.format(source, range));
      }
    }
    return Optional.empty();
  }
}
