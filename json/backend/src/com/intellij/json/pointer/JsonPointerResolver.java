// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.pointer;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JsonPointerResolver {
  private final JsonValue myRoot;
  private final String myPointer;

  public JsonPointerResolver(@NotNull JsonValue root, @NotNull String pointer) {
    myRoot = root;
    myPointer = pointer;
  }

  public @Nullable JsonValue resolve() {
    JsonValue root = myRoot;
    final List<JsonPointerPosition.Step> steps = JsonPointerPosition.parsePointer(myPointer).getSteps();
    for (JsonPointerPosition.Step step : steps) {
      String name = step.getName();
      if (name != null) {
        if (!(root instanceof JsonObject)) return null;
        JsonProperty property = ((JsonObject)root).findProperty(name);
        root = property == null ? null : property.getValue();
      }
      else {
        int idx = step.getIdx();
        if (idx < 0) return null;

        if (!(root instanceof JsonArray)) {
          if (root instanceof JsonObject) {
            JsonProperty property = ((JsonObject)root).findProperty(String.valueOf(idx));
            if (property == null) {
              return null;
            }
            root = property.getValue();
            continue;
          }
          else {
            return null;
          }
        }
        List<JsonValue> list = ((JsonArray)root).getValueList();
        if (idx >= list.size()) return null;
        root = list.get(idx);
      }
    }
    return root;
  }
}
