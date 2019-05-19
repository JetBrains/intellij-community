// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TooltipGroup implements Comparable<TooltipGroup> {
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