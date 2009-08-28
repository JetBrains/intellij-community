package com.intellij.openapi.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.MixinEP;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class MixinExtension {
  private MixinExtension() {
  }

  @Nullable
  public static <T> T getInstance(ExtensionPointName<MixinEP<T>> name, Object key) {
    final MixinEP<T>[] eps = Extensions.getExtensions(name);
    for(MixinEP<T> ep: eps) {
      if (ep.getKey().isInstance(key)) {
        return ep.getInstance();
      }
    }
    return null;
  }
}
