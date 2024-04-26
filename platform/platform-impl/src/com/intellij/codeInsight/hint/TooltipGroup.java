// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class TooltipGroup implements Comparable<TooltipGroup> {
  private final String myName;
  // the higher priority the more probable this tooltip will overlap other tooltips
  private final int myPriority;

  public TooltipGroup(@NotNull @NonNls String name, int priority) {
    myName = name;
    myPriority = priority;
  }

  @Override
  public int compareTo(TooltipGroup tooltipGroup) {
    return myPriority - tooltipGroup.myPriority;
  }

  @Override
  public String toString() {
    return myName;
  }
}