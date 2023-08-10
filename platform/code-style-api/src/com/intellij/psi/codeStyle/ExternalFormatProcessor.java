// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
   * @param canChangeWhiteSpacesOnly procedure can change only whitespaces
   * @param keepLineBreaks don't add or remove line breaks
   * @param enableBulkUpdate don't turn on the bulk update
   * @return the range after formatting or null, if external format procedure cannot be applied to the source
   */
  @Nullable
  TextRange format(@NotNull PsiFile source,
                   @NotNull TextRange range,
                   boolean canChangeWhiteSpacesOnly,
                   boolean keepLineBreaks,
                   boolean enableBulkUpdate,
                   int cursorOffset);

  /**
   * Indents the line.
   *
   * @param source the source file with code
   * @param lineStartOffset the offset of the indented line
   * @return the indentation String or null if nothing to be changed
   */
  @Nullable
  String indent(@NotNull PsiFile source, int lineStartOffset);

  default void createConfiguration(@NotNull Project project) {}

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
    return ContainerUtil.exists(EP_NAME.getExtensionList(), efp -> efp.activeForFile(source));
  }

  /**
   * @param externalFormatterId the unique id for external formatter
   * @return the external formatter with the unique id, if any
   */
  @NotNull
  static Optional<ExternalFormatProcessor> findExternalFormatter(@NonNls @NotNull String externalFormatterId) {
    return EP_NAME.getExtensionList().stream().filter(efp -> externalFormatterId.equals(efp.getId())).findFirst();
  }

  @Nullable
  static ExternalFormatProcessor activeExternalFormatProcessor(@NotNull PsiFile source) {
    return ContainerUtil.find(EP_NAME.getExtensionList(), efp -> efp.activeForFile(source));
  }

  /**
   * Indents the line.
   *
   * @param source the source file with code
   * @param lineStartOffset the offset of the indented line
   * @return the range after indentation or null if nothing to be changed
   */
  @Nullable
  static String indentLine(@NotNull PsiFile source, int lineStartOffset) {
    ExternalFormatProcessor efp = activeExternalFormatProcessor(source);
    return efp != null ? efp.indent(source, lineStartOffset) : null;
  }

  /**
   * @param source the source file with code
   * @param range the range for formatting
   * @param canChangeWhiteSpacesOnly procedure can change only whitespaces
   * @param keepLineBreaks don't add or remove line breaks
   * @param enableBulkUpdate don't turn on the bulk update
   * @return the range after formatting or null, if external format procedure was not found or inactive (disabled)
   */
  @Nullable
  static TextRange formatRangeInFile(@NotNull PsiFile source,
                                     @NotNull TextRange range,
                                     boolean canChangeWhiteSpacesOnly,
                                     boolean keepLineBreaks,
                                     boolean enableBulkUpdate,
                                     int cursorOffset) {
    ExternalFormatProcessor efp = activeExternalFormatProcessor(source);
    return efp != null ? efp.format(source, range, canChangeWhiteSpacesOnly, keepLineBreaks, enableBulkUpdate, cursorOffset) : null;
  }

  @Nullable
  static TextRange formatRangeInFile(@NotNull PsiFile source,
                                     @NotNull TextRange range,
                                     boolean canChangeWhiteSpacesOnly,
                                     boolean keepLineBreaks) {
    return formatRangeInFile(source, range, canChangeWhiteSpacesOnly, keepLineBreaks, true, -1);
  }

  /**
   * @param elementToFormat the element from code file
   * @param range the range for formatting
   * @param canChangeWhiteSpacesOnly procedure can change only whitespaces
   * @return the element after formatting
   */
  @NotNull
  static PsiElement formatElement(@NotNull PsiElement elementToFormat,
                                  @NotNull TextRange range,
                                  boolean canChangeWhiteSpacesOnly) {
    final PsiFile file = elementToFormat.getContainingFile();
    final Document document = file.getViewProvider().getDocument();
    if (document != null) {
      final TextRange rangeAfterFormat = formatRangeInFile(file, range, canChangeWhiteSpacesOnly, false);
      if (rangeAfterFormat != null) {
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        if (!elementToFormat.isValid()) {
          PsiElement elementAtStart = file.findElementAt(rangeAfterFormat.getStartOffset());
          if (elementAtStart instanceof PsiWhiteSpace) {
              elementAtStart = PsiTreeUtil.nextLeaf(elementAtStart);
          }
          if (elementAtStart != null) {
            PsiElement parent = PsiTreeUtil.getParentOfType(elementAtStart, elementToFormat.getClass());
            if (parent != null) {
              return parent;
            }
            return elementAtStart;
          }
        }
      }
    }
    return elementToFormat;
  }
}
