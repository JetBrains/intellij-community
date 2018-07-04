// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.process;

import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public abstract class BuildProcessHandler extends ProcessHandler {
  public abstract String getExecutionName();

  public void forceProcessDetach() {
    notifyProcessDetached();
  }
}
