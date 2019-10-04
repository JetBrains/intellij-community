// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class TypePresentationService {

  public static TypePresentationService getService() {
    return ServiceManager.getService(TypePresentationService.class);
  }

  @Nullable
  public abstract Icon getIcon(Object o);

  @Nullable
  public abstract Icon getTypeIcon(Class type);

  @Nullable
  public abstract String getTypePresentableName(Class type);

  @Nullable
  public abstract String getTypeName(Object o);

  @ApiStatus.Internal
  @Nullable
  public abstract String getObjectName(Object o);

  public static String getDefaultTypeName(final Class aClass) {
    String simpleName = aClass.getSimpleName();
    final int i = simpleName.indexOf('$');
    if (i >= 0) {
      simpleName = simpleName.substring(i + 1);
    }
    return StringUtil.capitalizeWords(StringUtil.join(NameUtilCore.nameToWords(simpleName), " "), true);
  }
}
