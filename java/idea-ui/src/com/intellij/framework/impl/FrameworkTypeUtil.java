// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.impl;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.FrameworkTypeEx;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public final class FrameworkTypeUtil {
  public static final Comparator<FrameworkType> FRAMEWORK_TYPE_COMPARATOR =
    (o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName());

  public static Map<String, FrameworkType> computeFrameworkTypeByIdMap() {
    Map<String, FrameworkType> frameworkTypes = new HashMap<>();
    for (FrameworkTypeEx type : FrameworkTypeEx.EP_NAME.getExtensions()) {
      frameworkTypes.put(type.getId(), type);
    }
    return frameworkTypes;
  }
}
