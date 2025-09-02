// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Map;

public final class LiveTemplateContextsSnapshot {

  private final Map<String, LiveTemplateContext> myLiveTemplateIds;

  public LiveTemplateContextsSnapshot(Map<String, LiveTemplateContext> ids) { myLiveTemplateIds = ids; }

  public @Nullable LiveTemplateContext getLiveTemplateContext(@Nullable String id) {
    return myLiveTemplateIds.get(id);
  }

  public @Unmodifiable Collection<LiveTemplateContext> getLiveTemplateContexts() {
    return myLiveTemplateIds.values();
  }
}
