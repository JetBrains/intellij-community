// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface CustomizableLookupElementTemplate {
  void renderElement(@NotNull LookupElementPresentation presentation);

  Collection<String> getAllLookupStrings();
}
