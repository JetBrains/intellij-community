// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.jetbrains.sa.SAJDWPAttachingConnector;
import com.jetbrains.sa.SaJdwp;
import com.sun.jdi.connect.AttachingConnector;

import java.util.List;

/**
 * @author egor
 */
public class SAJDWPRemoteConnection extends PidRemoteConnection {
  private static final SAJDWPAttachingConnector CONNECTOR = new SAJDWPAttachingConnector();

  static {
    SaJdwp.setSudoCommandCreator(new SaJdwp.SudoCommandCreator() {
      @Override
      public List<String> createSudoCommand(List<String> cmds) throws Exception {
        GeneralCommandLine commandLine = ExecUtil.sudoCommand(new GeneralCommandLine(cmds),
                                                              "Please enter your password to attach with su privileges: ");
        return commandLine.getCommandLineList(null);
      }
    });
  }

  public SAJDWPRemoteConnection(String pid) {
    super(pid);
  }

  @Override
  public AttachingConnector getConnector() {
    return CONNECTOR;
  }

  public static boolean isAvailable() {
    return true;
  }
}
