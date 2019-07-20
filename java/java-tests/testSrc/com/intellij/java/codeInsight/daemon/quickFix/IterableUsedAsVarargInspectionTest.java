// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.miscGenerics.IterableUsedAsVarargInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_10;


public class IterableUsedAsVarargInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new IterableUsedAsVarargInspection[]{new IterableUsedAsVarargInspection()};
  }
  
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_10;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/iterableUsedAsVararg";
  }
}