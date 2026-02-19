// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.LiveTemplateContext;
import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class TemplateContextTypes {
  public static @Unmodifiable @NotNull List<TemplateContextType> getAllContextTypes() {
    return ContainerUtil.map(LiveTemplateContextService.getInstance().getLiveTemplateContexts(), LiveTemplateContext::getTemplateContextType);
  }

  @SuppressWarnings("unchecked")
  public static <T extends TemplateContextType> @NotNull T getByClass(@NotNull Class<T> clazz) {
    LiveTemplateContext liveTemplateContext = LiveTemplateContextService.getInstance().getLiveTemplateContext(clazz);
    if (liveTemplateContext == null) {
      throw new IllegalStateException("Template context type with class " + clazz + " is not registered");
    }
    return (T)liveTemplateContext.getTemplateContextType();
  }

  @SuppressWarnings("unchecked")
  public static <T extends TemplateContextType> @Nullable T getByClassOrNull(@NotNull Class<T> clazz) {
    LiveTemplateContext liveTemplateContext = LiveTemplateContextService.getInstance().getLiveTemplateContext(clazz);
    return liveTemplateContext != null ? (T)liveTemplateContext.getTemplateContextType() : null;
  }
}
