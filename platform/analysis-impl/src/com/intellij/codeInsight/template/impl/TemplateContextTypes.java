// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.LiveTemplateContextBean;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class TemplateContextTypes {
  public static @NotNull List<TemplateContextType> getAllContextTypes() {
    return ContainerUtil.map(LiveTemplateContextBean.EP_NAME.getExtensionList(), LiveTemplateContextBean::getInstance);
  }

  public static <T extends TemplateContextType> @NotNull T getByClass(@NotNull Class<T> clazz) {
    T templateContextType = getByClassOrNull(clazz);
    if (templateContextType == null) {
      throw new IllegalStateException("Template context type with class " + clazz + " is not registered");
    }

    return templateContextType;
  }

  @SuppressWarnings("unchecked")
  public static <T extends TemplateContextType> @Nullable T getByClassOrNull(@NotNull Class<T> clazz) {
    for (LiveTemplateContextBean templateContextEP : LiveTemplateContextBean.EP_NAME.getExtensionList()) {
      TemplateContextType instance = templateContextEP.getInstance();
      if (clazz.isInstance(instance)) {
        return (T)instance;
      }
    }
    return null;
  }
}
