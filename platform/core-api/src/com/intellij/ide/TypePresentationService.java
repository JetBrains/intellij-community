// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class TypePresentationService {

  public static TypePresentationService getService() {
    return ApplicationManager.getApplication().getService(TypePresentationService.class);
  }

  @Nullable
  public abstract Icon getIcon(@NotNull Object o);

  @Nullable
  public abstract Icon getTypeIcon(Class type);

  @Nullable
  public abstract @NlsSafe String getTypePresentableName(Class type);

  @Nullable
  public abstract @NlsSafe String getTypeName(@NotNull Object o);

  @ApiStatus.Internal
  @Nullable
  public abstract @NlsSafe String getObjectName(@NotNull Object o);

  @NotNull
  public static @NlsSafe String getDefaultTypeName(@NotNull Class aClass) {
    String simpleName = aClass.getSimpleName();
    final int i = simpleName.indexOf('$');
    if (i >= 0) {
      simpleName = simpleName.substring(i + 1);
    }
    return StringUtil.capitalizeWords(StringUtil.join(NameUtilCore.nameToWords(simpleName), " "), true);
  }
}
