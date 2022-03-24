// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

public class ConvertCompactConstructorToCanonicalActionTest extends LightIntentionActionTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/compactToCanonical";
  }

}
