// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class FormattingServiceUtil {
  private final static Logger LOG = Logger.getInstance(FormattingServiceUtil.class);

  private FormattingServiceUtil() {
  }

  public static @NotNull FormattingService findService(@NotNull PsiFile file, boolean isExplicit, boolean isCompleteFile) {
    FormattingService formattingService = ContainerUtil.find(
      FormattingService.EP_NAME.getExtensionList(),
      s -> s.canFormat(file) &&
           (isExplicit || s.getFeatures().contains(FormattingService.Feature.AD_HOC_FORMATTING)) &&
           (isCompleteFile || s.getFeatures().contains(FormattingService.Feature.FORMAT_FRAGMENTS))
    );
    LOG.assertTrue(formattingService != null,
                   "At least 1 formatting service which can handle PsiFile " + file.getName() + " should be registered.");
    return formattingService;
  }
}
