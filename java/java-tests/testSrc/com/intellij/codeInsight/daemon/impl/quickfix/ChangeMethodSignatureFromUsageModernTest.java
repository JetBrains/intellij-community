// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

public class ChangeMethodSignatureFromUsageModernTest extends LightQuickFixParameterizedTestCase {
  private static final LightJavaCodeInsightFixtureTestCase.ProjectDescriptor DESCRIPTOR = 
    new LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.JDK_25) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      MavenDependencyUtil.addFromMaven(model, "com.google.code.findbugs:jsr305:3.0.2");
    }
  };

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeMethodSignatureFromUsageModern";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }
}
