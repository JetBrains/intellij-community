// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.rt.execution;

import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class ForkedDebuggerConfiguration {
  private final int myForkSocketPort;
  private final int myDebugPort;

  private ForkedDebuggerConfiguration(int forkSocketPort, int debugPort) {
    this.myForkSocketPort = forkSocketPort;
    this.myDebugPort = debugPort;
  }

  public int getForkSocketPort() {
    return myForkSocketPort;
  }

  public int getDebugPort() {
    return myDebugPort;
  }

  @Nullable
  public static ForkedDebuggerConfiguration parse(@Nullable String jvmAgentSetup) {
    if (jvmAgentSetup != null && jvmAgentSetup.startsWith(ForkedDebuggerHelper.DEBUG_SETUP_PREFIX)) {
      int forkSocketIndex = jvmAgentSetup.indexOf(ForkedDebuggerHelper.DEBUG_FORK_SOCKET_PARAM);
      if (forkSocketIndex > 0) {
        try {
          int forkSocketPort =
            Integer.parseInt(jvmAgentSetup.substring(forkSocketIndex + ForkedDebuggerHelper.DEBUG_FORK_SOCKET_PARAM.length()));
          int debugPort = Integer.parseInt(jvmAgentSetup.substring(ForkedDebuggerHelper.DEBUG_SETUP_PREFIX.length(), forkSocketIndex - 1));
          return new ForkedDebuggerConfiguration(forkSocketPort, debugPort);
        }
        catch (NumberFormatException ignore) {
        }
      }
    }
    return null;
  }

  public String getJvmAgentSetup(boolean isJdk9orLater) {
    return ForkedDebuggerHelper.DEBUG_SETUP_PREFIX + (isJdk9orLater ? "127.0.0.1:" : "") + myDebugPort;
  }
}
