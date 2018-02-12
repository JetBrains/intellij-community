// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import org.jetbrains.annotations.Nullable;

/**
 * Unique object, which has the same {@link ProblemGroup#getProblemName()} for all of the problems of this group.
 * It is used to split some inspection to several fake inspections.
 */
public interface ProblemGroup {
  @Nullable
  String getProblemName();
}