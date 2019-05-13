/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

public class ScopeAttributesUtil {
  private static final ConcurrentMap<String, TextAttributesKey> ourCache =
    ConcurrentFactoryMap.createMap(scope -> TextAttributesKey.find("SCOPE_KEY_" + scope));
  @NotNull
  public static TextAttributesKey getScopeTextAttributeKey(@NotNull String scope) {
    return ourCache.get(scope);
  }
}
