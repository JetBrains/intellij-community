// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class TypePresentationService {

  public static TypePresentationService getService() {
    return ApplicationManager.getApplication().getService(TypePresentationService.class);
  }

  public abstract @Nullable Icon getIcon(@NotNull Object o);

  public abstract @Nullable Icon getTypeIcon(Class type);

  public abstract @Nullable @NlsSafe String getTypePresentableName(Class type);

  public abstract @Nullable @NlsSafe String getTypeName(@NotNull Object o);

  @ApiStatus.Internal
  public abstract @Nullable @NlsSafe String getObjectName(@NotNull Object o);

  public static @NotNull @NlsSafe String getDefaultTypeName(@NotNull Class aClass) {
    String simpleName = aClass.getSimpleName();
    final int i = simpleName.indexOf('$');
    if (i >= 0) {
      simpleName = simpleName.substring(i + 1);
    }
    return StringUtil.capitalizeWords(StringUtil.join(NameUtilCore.nameToWords(simpleName), " "), true);
  }
}
