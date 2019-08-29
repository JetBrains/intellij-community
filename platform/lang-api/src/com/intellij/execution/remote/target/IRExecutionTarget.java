// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote.target;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.remote.IR;

public abstract class IRExecutionTarget extends ExecutionTarget {
  public abstract IR.RemoteRunner getRemoteRunner();
}