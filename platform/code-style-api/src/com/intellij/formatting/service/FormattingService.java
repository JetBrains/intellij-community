// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Base class for any kind of formatting tools, both built-in and external. The default implementation is {@code CoreFormattingService}
 * based on {@link com.intellij.formatting.FormattingDocumentModel}. Alternative implementations are searched using
 * {@link #canFormat(PsiFile)} method and features supported by a tool {@link #getFeatures()}.
 * <p>
 * For integration with external command line formatting tools consider using {@link AsyncDocumentFormattingService}.
 */
public interface FormattingService {
  ExtensionPointName<FormattingService> EP_NAME = ExtensionPointName.create("com.intellij.formattingService");

  /**
   * A feature supported by the service.
   */
  enum Feature {
    /**
     * The service can provide fast formatting of multiple collected ranges, for example, upon refactoring. It must also support
     * {@link #FORMAT_FRAGMENTS} feature.
     */
    AD_HOC_FORMATTING,
    /**
     * The service can format multiple text ranges within the same document as opposed to complete file only.
     */
    FORMAT_FRAGMENTS,
    /**
     * The service supports imports optimization. If {@link #getImportOptimizers(PsiFile)} returns an empty set, the service
     * can only do it as a part of formatting call.
     */
    OPTIMIZE_IMPORTS
  }

  /**
   * @return The features supported by the service or an empty list if only the whole file formatting is supported.
   */
  @NotNull
  Set<Feature> getFeatures();

  /**
   *
   * @param file The PSI file to check service availability for.
   * @return True if the service can be used to format the file, false otherwise.
   */
  boolean canFormat(@NotNull PsiFile file);

  /**
   * Format the element and return its formatted equivalent.
   *
   * @param element                 The original element.
   * @param canChangeWhiteSpaceOnly True if only whitespaces can be changed (indents, spacing), other elements can't be inserted, changed
   *                                or removed.
   * @return The formatted equivalent of the original element.
   */
  @NotNull
  PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpaceOnly);

  /**
   * Format the element within the given text range and return its formatted equivalent.
   *
   * @param element                 The original element.
   * @param range                   The text range to format within.
   * @param canChangeWhiteSpaceOnly True if only whitespaces can be changed (indents, spacing), other elements can't be inserted, changed
   *                                or removed.
   * @return The formatted equivalent of the original element.
   */
  @NotNull
  PsiElement formatElement(@NotNull PsiElement element, @NotNull TextRange range, boolean canChangeWhiteSpaceOnly);

  /**
   * Format multiple ranges within the PSI file.
   *
   * @param file                    The PSI file to format.
   * @param rangesInfo              Ranges information, see {@link FormattingRangesInfo}
   * @param canChangeWhiteSpaceOnly True if only whitespaces can be changed (indents, spacing), other elements can't be inserted, changed
   *                                or removed.
   * @param quickFormat             Only quick format is allowed. The {@code true} value is passed if the service supports
   *                                {@link Feature#AD_HOC_FORMATTING}. This mode is used, for example, in quick fixes and refactorings.
   */
  void formatRanges(@NotNull PsiFile file, FormattingRangesInfo rangesInfo, boolean canChangeWhiteSpaceOnly, boolean quickFormat);

  /**
   * @param file The file
   * @return A set of import optimizers for the given file.
   */
  @NotNull
  Set<ImportOptimizer> getImportOptimizers(@NotNull PsiFile file);

  /**
   * @return A class of the service which should be run prior to this FormattingService (default is {@code null})
   * <p>
   * For example:
   * <pre><code>
   *   Class<? extends FormattingService> runAfter() {
   *     return CoreFormattingService.class;
   *   }
   * </code></pre>
   * will call platform formatter prior to executing the current formatting service.
   * <p>
   * <b>NOTE:</b> It works only if all the file content is formatted. In case of subrange(s) only the current service
   * is called since original ranges become invalid after formatting.
   */
  default @Nullable Class<? extends FormattingService> runAfter() {
    return null;
  }
}
