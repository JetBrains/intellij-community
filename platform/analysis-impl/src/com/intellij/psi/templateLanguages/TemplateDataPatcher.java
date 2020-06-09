// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public interface TemplateDataPatcher {
  LanguageExtension<TemplateDataPatcher> EXTENSION = new LanguageExtension<>("com.intellij.templateDataPatcher");

  void prepareContentForParsing(@NotNull TemplateDataElementType.RangeCollector collector, @NotNull StringBuilder modifiedText);
}
