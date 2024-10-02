// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ref;

import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;

public class IgnoredTraverseReference implements IgnoredTraverseEntry {


  private final String myField;
  private final int myIndex;

  /**
   * Constructs a rule that allows to ignore some known leakage path. It allows to keep the LeakHunter-based checks enabled while "skipping"
   * some well-known leaks.
   *
   * @param field field name in the CLASS_FQN.field_name format
   * @param index index of the {@param field} frame. Index can be positive and negative. If it's positive it will be enumerated starting
   *              from the traverse root, from the traverse leaf if it's negative.
   */
  public IgnoredTraverseReference(@NotNull final String field, int index) {
    myField = field;
    myIndex = index;
  }

  @Override
  public boolean test(@NotNull final DebugReflectionUtil.BackLink<?> link) {
    if (myIndex >= 0) {
      throw new NotImplementedError("Handling of positive indexes is not implemented yet");
    }
    return checkNegativeIndex(link);
  }

  private boolean checkNegativeIndex(@NotNull final DebugReflectionUtil.BackLink<?> link) {
    int currentIndex = myIndex;
    DebugReflectionUtil.BackLink<?> backLink = link;

    while (currentIndex < -1) {
      backLink = backLink.prev();
      currentIndex++;
      if (backLink == null) {
        return false;
      }
    }

    return myField.equals(backLink.getFieldName());
  }
}
