// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LiveTemplateWeigher extends LookupElementWeigher {
  public LiveTemplateWeigher() {
    super("templates", Registry.is("ide.completion.show.live.templates.on.top"), false);
  }

  @Override
  public @Nullable Comparable weigh(@NotNull LookupElement element) {
    return element instanceof LiveTemplateLookupElement;
  }
}
