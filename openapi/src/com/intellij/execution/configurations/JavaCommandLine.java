/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;

public interface JavaCommandLine extends RunProfileState {
  JavaParameters getJavaParameters() throws ExecutionException;
}
