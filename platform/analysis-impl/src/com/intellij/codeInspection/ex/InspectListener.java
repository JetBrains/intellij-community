// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

public interface InspectListener {
  default void inspectionFinished(long start, long finish, long threadId, InspectionToolWrapper<?, ?> tool, InspectionKind kind) {}

  enum InspectionKind {
    LOCAL,
    LOCAL_PRIORITY,
    GLOBAL_SIMPLE,
    GLOBAL
  }
}
