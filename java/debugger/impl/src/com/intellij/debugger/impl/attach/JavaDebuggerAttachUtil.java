// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.openapi.project.Project;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author egor
 */
public class JavaDebuggerAttachUtil {
  public static boolean canAttach(int pid) {
    return JavaAttachDebuggerProvider.getProcessAttachInfo(String.valueOf(pid)) != null;
  }

  public static boolean attach(int pid, Project project) {
    JavaAttachDebuggerProvider.LocalAttachInfo info = JavaAttachDebuggerProvider.getProcessAttachInfo(String.valueOf(pid));
    if (info != null) {
      JavaAttachDebuggerProvider.attach(info, project);
      return true;
    }
    return false;
  }

  @NotNull
  public static VirtualMachine attachVirtualMachine(String id) throws IOException, AttachNotSupportedException {
    // avoid attaching to the 3rd party vms
    if (VirtualMachine.list().stream().map(VirtualMachineDescriptor::id).noneMatch(id::equals)) {
      throw new AttachNotSupportedException("AttachProvider for the vm is not found");
    }
    return VirtualMachine.attach(id);
  }
}
