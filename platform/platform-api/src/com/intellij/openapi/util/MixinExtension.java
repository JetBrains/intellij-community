// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.MixinEP;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public final class MixinExtension {
  private MixinExtension() {
  }

  @Nullable
  public static <T> T getInstance(ExtensionPointName<MixinEP<T>> name, Object key) {
    final List<MixinEP<T>> eps = name.getExtensionList();
    for(MixinEP<T> ep: eps) {
      if (ep.getKey().isInstance(key)) {
        return ep.getInstance();
      }
    }
    return null;
  }
}
