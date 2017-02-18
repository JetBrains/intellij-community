/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
public abstract class JsonObjectMixin extends JsonContainerImpl implements JsonObject {
  private final CachedValueProvider<Map<String, JsonProperty>> myPropertyCache =
    () -> {
      final Map<String, JsonProperty> cache = new HashMap<>();
      for (JsonProperty property : getPropertyList()) {
        final String propertyName = property.getName();
        // Preserve the old behavior - return the first value in findProperty()
        if (!cache.containsKey(propertyName)) {
          cache.put(propertyName, property);
        }
      }
      // Cached value is invalidated every time file containing this object is modified
      return CachedValueProvider.Result.createSingleDependency(cache, this);
    };

  public JsonObjectMixin(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JsonProperty findProperty(@NotNull String name) {
    return CachedValuesManager.getCachedValue(this, myPropertyCache).get(name);
  }
}
