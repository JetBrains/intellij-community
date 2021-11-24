// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import org.jetbrains.annotations.NotNull;

public class AddFinallyTest extends LightQuickFixParameterizedTestCase5 {

  @Override
  protected @NotNull String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addFinally";
  }
}
