// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Excludes a {@link FormattingService} from selection for a particular file
 * (see {@code FormattingServiceUtil#findService}); the next applicable service is used instead,
 * with {@link CoreFormattingService} as the final fallback.
 * <p>
 * Used to keep external formatters away from files opened in the safe mode:
 * such tools may discover configs and plugins next to the formatted file and execute them.
 */
@ApiStatus.Internal
public interface FormattingServiceSuppressor {
  ExtensionPointName<FormattingServiceSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.formattingServiceSuppressor");

  boolean isSuppressed(@NotNull PsiFile file, @NotNull FormattingService service);
}
