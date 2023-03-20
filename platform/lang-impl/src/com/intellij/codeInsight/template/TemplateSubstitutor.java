// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSubstitutionContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TemplateSubstitutor {
  ExtensionPointName<TemplateSubstitutor> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateSubstitutor");

  /**
   * @return template that should be used instead of {@code template} or null if we can't substitute a template
   */
  @Nullable
  TemplateImpl substituteTemplate(@NotNull TemplateSubstitutionContext substitutionContext, @NotNull TemplateImpl template);
}
