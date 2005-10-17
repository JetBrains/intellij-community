package com.intellij.debugger;

import com.intellij.debugger.engine.evaluation.EvaluateException;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface EvaluatingComputable <T>{
  T compute() throws EvaluateException;
}
