// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable JsonProperty findProperty(@NotNull String name) {
    return CachedValuesManager.getCachedValue(this, myPropertyCache).get(name);
  }
}
