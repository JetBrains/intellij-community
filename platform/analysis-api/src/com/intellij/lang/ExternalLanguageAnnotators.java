// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ExternalLanguageAnnotators extends LanguageExtension<ExternalAnnotator> {
  public static final ExtensionPointName<LanguageExtensionPoint<ExternalAnnotator>> EP_NAME = ExtensionPointName.create("com.intellij.externalAnnotator");

  public static final ExternalLanguageAnnotators INSTANCE = new ExternalLanguageAnnotators();

  private ExternalLanguageAnnotators() {
    super(EP_NAME.getName());
  }

  @NotNull
  public static List<ExternalAnnotator> allForFile(@NotNull Language language, @NotNull final PsiFile file) {
    List<ExternalAnnotator> annotators = INSTANCE.allForLanguage(language);
    List<ExternalAnnotatorsFilter> filters = ExternalAnnotatorsFilter.EXTENSION_POINT_NAME.getExtensionList();
    return ContainerUtil.findAll(annotators, annotator -> {
      return filters.stream().noneMatch(filter -> filter.isProhibited(annotator, file));
    });
  }
}