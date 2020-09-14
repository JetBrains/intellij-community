// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.project.DumbUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MockDumbUtil implements DumbUtil {

  @Override
  public @NotNull <T> List<T> filterByDumbAwarenessHonoringIgnoring(@NotNull Collection<? extends T> collection) {
    return collection instanceof List ? (List<T>)collection : new ArrayList<>(collection);
  }

  @Override
  public boolean mayUseIndices() {
    return true;
  }
}
