// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormatTextRanges;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class FormattingServiceUtil {
  private static final Logger LOG = Logger.getInstance(FormattingServiceUtil.class);

  private FormattingServiceUtil() {
  }

  public static @NotNull FormattingService findService(@NotNull PsiFile file, boolean isExplicit, boolean isCompleteFile) {
    FormattingService formattingService = ContainerUtil.find(
      FormattingService.EP_NAME.getExtensionList(),
      s -> (isExplicit || s.getFeatures().contains(FormattingService.Feature.AD_HOC_FORMATTING)) &&
           (isCompleteFile || s.getFeatures().contains(FormattingService.Feature.FORMAT_FRAGMENTS)) &&
           s.canFormat(file)
    );
    LOG.assertTrue(formattingService != null,
                   "At least 1 formatting service which can handle PsiFile " + file.getName() + " should be registered.");
    return formattingService;
  }

  public static @Nullable FormattingService findService(Class<? extends FormattingService> serviceClass) {
    return ContainerUtil.find(FormattingService.EP_NAME.getExtensionList(), s -> s.getClass().equals(serviceClass));
  }

  public static @NotNull FormattingService findImportsOptimizingService(@NotNull PsiFile file) {
    FormattingService importsOptimizer = ContainerUtil.find(
      FormattingService.EP_NAME.getExtensionList(),
      s -> s.getFeatures().contains(FormattingService.Feature.OPTIMIZE_IMPORTS) && s.canFormat(file)
    );
    LOG.assertTrue(importsOptimizer != null,
                   "At least 1 formatting service which can optimize imports in PsiFile " + file.getName() + " should be registered.");
    return importsOptimizer;
  }

  private static List<FormattingService> getChainedServices(@NotNull FormattingService formattingService) {
    List<FormattingService> serviceList = new ArrayList<>();
    FormattingService currService = formattingService;
    while (currService != null) {
      if (serviceList.contains(currService)) break;
      serviceList.add(0, currService);
      currService = ObjectUtils.doIfNotNull(currService.runAfter(), serviceClass -> findService(serviceClass));
    }
    return serviceList;
  }

  public static @NotNull PsiElement formatElement(@NotNull PsiElement element, boolean canChangeWhiteSpaceOnly) {
    PsiFile file = element.getContainingFile();
    PsiElement contextElement = element;
    FormattingService mainService = findService(file, true, element.getTextRange().equals(file.getTextRange()));
    for (FormattingService service : getChainedServices(mainService)) {
      contextElement = service.formatElement(contextElement, canChangeWhiteSpaceOnly);
    }
    return contextElement;
  }

  public static @NotNull PsiElement formatElement(@NotNull PsiElement element, @NotNull TextRange range, boolean canChangeWhiteSpacesOnly) {
    return formatElement(element, range, canChangeWhiteSpacesOnly, false);
  }

  public static void asyncFormatElement(@NotNull PsiElement element, @NotNull TextRange range, boolean canChangeWhitespaceOnly) {
     formatElement(element, range, canChangeWhitespaceOnly, true);
  }

  private static @NotNull PsiElement formatElement(@NotNull PsiElement element,
                                                  @NotNull TextRange range,
                                                  boolean canChangeWhiteSpacesOnly,
                                                  boolean forceAsync) {
    PsiFile file = element.getContainingFile();
    boolean isFullRange = range.equals(file.getTextRange());
    PsiElement contextElement = element;
    FormattingService mainService = findService(element.getContainingFile(), true, isFullRange);
    if (isFullRange) {
      for (FormattingService service : getChainedServices(mainService)) {
        contextElement = formatElement(service, contextElement, range, canChangeWhiteSpacesOnly, forceAsync);
      }
      return contextElement;
    }
    else {
      return formatElement(mainService, element, range, canChangeWhiteSpacesOnly, forceAsync);
    }
  }

  private static PsiElement formatElement(@NotNull FormattingService service,
                                          @NotNull PsiElement element,
                                          @NotNull TextRange range,
                                          boolean canChangeWhiteSpacesOnly,
                                          boolean forceAsync) {
    if (forceAsync && (service instanceof CoreFormattingService)) {
      ((CoreFormattingService)service).asyncFormatElement(element, range, canChangeWhiteSpacesOnly);
      return element;
    }
    else {
      return service.formatElement(element, range, canChangeWhiteSpacesOnly);
    }
  }

  public static void formatRanges(@NotNull PsiFile file, @NotNull FormatTextRanges ranges, boolean canChangeWhiteSpaceOnly, boolean isFullRange) {
    FormattingService mainService = findService(file, true, isFullRange);
    if (isFullRange) {
      for (FormattingService service : getChainedServices(mainService)) {
        service.formatRanges(file, ranges, canChangeWhiteSpaceOnly, false);
      }
    }
    else {
      // Range formatting can use only one service since the ranges become invalid after reformat.
      mainService.formatRanges(file, ranges, canChangeWhiteSpaceOnly, false);
    }
  }
}
