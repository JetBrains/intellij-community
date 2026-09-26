// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class ScopeAttributesUtil {
  private static final ConcurrentMap<String, TextAttributesKey> ourCache =
    ConcurrentFactoryMap.createMap(scope -> TextAttributesKey.find("SCOPE_KEY_" + scope));
  public static @NotNull TextAttributesKey getScopeTextAttributeKey(@NotNull String scope) {
    return ourCache.get(scope);
  }
}
