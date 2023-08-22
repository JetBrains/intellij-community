// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

public final class ScopeAttributesUtil {
  private static final ConcurrentMap<String, TextAttributesKey> ourCache =
    ConcurrentFactoryMap.createMap(scope -> TextAttributesKey.find("SCOPE_KEY_" + scope));
  @NotNull
  public static TextAttributesKey getScopeTextAttributeKey(@NotNull String scope) {
    return ourCache.get(scope);
  }
}
