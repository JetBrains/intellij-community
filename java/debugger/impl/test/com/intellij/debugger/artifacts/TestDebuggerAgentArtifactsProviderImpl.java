// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.artifacts;

import com.intellij.debugger.testFramework.TestDebuggerAgentArtifactsProvider;
import com.intellij.testFramework.common.bazel.BazelLabel;
import com.intellij.testFramework.common.BazelTestUtil;

import java.nio.file.Path;

public class TestDebuggerAgentArtifactsProviderImpl implements TestDebuggerAgentArtifactsProvider {

  @Override
  public Path getDebuggerAgentJar() {
    String debuggerAgentLabel = "@debugger_test_deps_debugger_agent//file:debugger-agent.jar";
    BazelLabel label = BazelLabel.Companion.fromString(debuggerAgentLabel);

    if (BazelTestUtil.isUnderBazelTest()) {
      return BazelTestUtil.getFileFromBazelRuntime(label);
    } else {
      throw new IllegalStateException("Expected to be used only in bazel-test environment");
    }
  }
}
