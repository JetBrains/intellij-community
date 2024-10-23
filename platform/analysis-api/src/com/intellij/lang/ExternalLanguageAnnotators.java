// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class ExternalLanguageAnnotators extends LanguageExtension<ExternalAnnotator<?,?>> {
  public static final ExtensionPointName<LanguageExtensionPoint<ExternalAnnotator<?,?>>> EP_NAME = ExtensionPointName.create("com.intellij.externalAnnotator");

  public static final ExternalLanguageAnnotators INSTANCE = new ExternalLanguageAnnotators();

  private ExternalLanguageAnnotators() {
    super(EP_NAME.getName());
  }

  @Unmodifiable
  public static @NotNull List<ExternalAnnotator<?,?>> allForFile(@NotNull Language language, final @NotNull PsiFile file) {
    List<ExternalAnnotator<?,?>> annotators = INSTANCE.allForLanguageOrAny(language);
    List<ExternalAnnotatorsFilter> filters = ExternalAnnotatorsFilter.EXTENSION_POINT_NAME.getExtensionList();
    return ContainerUtil.findAll(annotators, annotator ->
       !ContainerUtil.exists(filters, filter -> filter.isProhibited(annotator, file)));
  }
}