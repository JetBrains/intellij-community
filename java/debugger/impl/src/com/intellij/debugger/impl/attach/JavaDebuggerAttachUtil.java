// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.openapi.project.Project;

/**
 * @author egor
 */
public class JavaDebuggerAttachUtil {
  public static boolean canAttach(Process process) {
    return JavaAttachDebuggerProvider.getProcessAttachInfo(String.valueOf(OSProcessUtil.getProcessID(process))) != null;
  }

  public static void attach(Process process, Project project) {
    JavaAttachDebuggerProvider.LocalAttachInfo info =
      JavaAttachDebuggerProvider.getProcessAttachInfo(String.valueOf(OSProcessUtil.getProcessID(process)));
    if (info != null) {
      JavaAttachDebuggerProvider.attach(info, project);
    }
  }
}
