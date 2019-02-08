// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseExtensionPointName {
  private final String myName;

  public BaseExtensionPointName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  public final String toString() {
    return myName;
  }

  @Nullable
  protected static <T> T findExtension(@NotNull BaseExtensionPointName pointName, @NotNull Class<T> instanceOf, @Nullable AreaInstance areaInstance, boolean isRequired) {
    ExtensionPoint<T> point = Extensions.getArea(areaInstance).getExtensionPoint(pointName.getName());

    List<T> list = point.getExtensionList();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = list.size(); i < size; i++) {
      T object = list.get(i);
      if (instanceOf.isInstance(object)) {
        return object;
      }
    }

    if (isRequired) {
      String message = "could not find extension implementation " + instanceOf;
      if (((ExtensionPointImpl)point).isInReadOnlyMode()) {
        message += " (point in read-only mode)";
      }
      throw new IllegalArgumentException(message);
    }
    return null;
  }
}
