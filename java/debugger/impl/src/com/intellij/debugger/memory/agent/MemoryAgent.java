// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface MemoryAgent {
  boolean isLoaded();

  boolean canEvaluateObjectSize();

  long evaluateObjectSize(@NotNull ObjectReference reference) throws EvaluateException;

  boolean canEvaluateObjectsSizes();

  List<Long> evaluateObjectsSizes(@NotNull List<ObjectReference> references) throws EvaluateException;

  boolean canFindGcRoots();

  @Nullable
  ReferringObjectsProvider findGcRoots(@NotNull ObjectReference reference) throws EvaluateException;
}
