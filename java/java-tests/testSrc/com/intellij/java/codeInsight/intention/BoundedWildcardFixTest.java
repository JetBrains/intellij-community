// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.refactoring.BaseRefactoringProcessor;

public class BoundedWildcardFixTest extends LightIntentionActionTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/boundedWildcard";
  }

  @Override
  protected void doSingleTest(String fileSuffix, String testDataPath) {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()-> super.doSingleTest(fileSuffix, testDataPath));
  }
}
