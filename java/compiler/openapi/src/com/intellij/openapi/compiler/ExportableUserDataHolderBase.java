// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ExportableUserDataHolderBase extends UserDataHolderBase implements ExportableUserDataHolder {
  @Override
  public final @NotNull Map<Key<?>, Object> exportUserData() {
    Map<Key<?>, Object> result = new HashMap<>();
    KeyFMap map = getUserMap();
    for (Key<?> k : map.getKeys()) {
      final Object data = map.get(k);
      if (data != null) {
        result.put(k, data);
      }
    }
    return result;
  }
}
