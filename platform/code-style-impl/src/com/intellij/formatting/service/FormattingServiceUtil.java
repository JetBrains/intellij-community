// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class FormattingServiceUtil {
  private final static Logger LOG = Logger.getInstance(FormattingServiceUtil.class);

  private FormattingServiceUtil() {
  }

  public static @NotNull FormattingService findService(@NotNull PsiFile file, boolean isExplicit) {
    FormattingService formattingService = ContainerUtil.find(FormattingService.EP_NAME.getExtensionList(), s -> s.canFormat(file, isExplicit));
    LOG.assertTrue(formattingService != null,
                   "At least 1 formatting service which can handle PsiFile " + file.getName() + " should be registered.");
    return formattingService;
  }
}
