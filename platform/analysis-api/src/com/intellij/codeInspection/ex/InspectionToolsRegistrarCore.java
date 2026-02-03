// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

public final class InspectionToolsRegistrarCore {
  private static final Logger LOG = Logger.getInstance(InspectionToolsRegistrarCore.class);

  static <T> T instantiateTool(@NotNull Class<T> toolClass) {
    try {
      return ReflectionUtil.newInstance(toolClass);
    }
    catch (RuntimeException e) {
      LOG.error(e.getCause());
    }

    return null;
  }
}
