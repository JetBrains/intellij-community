// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.debugger.engine.evaluation.EvaluateException;

public interface EvaluatingComputable<T> {
  T compute() throws EvaluateException;
}
