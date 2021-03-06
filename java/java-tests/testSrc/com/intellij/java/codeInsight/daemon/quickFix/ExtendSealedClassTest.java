// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ExtendSealedClassTest extends LightQuickFixParameterizedTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
      return LightJavaCodeInsightFixtureTestCase.JAVA_15;
    }

    @Override
    protected String getBasePath() {
      return "/codeInsight/daemonCodeAnalyzer/quickFix/extendSealedClass";
    }

}
