/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
