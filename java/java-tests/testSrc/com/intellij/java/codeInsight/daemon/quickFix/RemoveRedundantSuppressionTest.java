// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.PossibleHeapPollutionVarargsInspection;
import com.intellij.codeInspection.RedundantLambdaCodeBlockInspection;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.siyeh.ig.controlflow.FallthruInSwitchStatementInspection;
import com.siyeh.ig.inheritance.RefusedBequestInspection;
import com.siyeh.ig.jdk.AutoBoxingInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RemoveRedundantSuppressionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
    enableInspectionTools(new RedundantSuppressInspection() {
                            @Nls(capitalization = Nls.Capitalization.Sentence)
                            @NotNull
                            @Override
                            public String getDisplayName() {
                              return InspectionsBundle.message("inspection.redundant.suppression.name");
                            }
                          },
                          new PossibleHeapPollutionVarargsInspection(),
                          new UncheckedWarningLocalInspection(),
                          new FallthruInSwitchStatementInspection(),
                          new UnusedDeclarationInspection(true),
                          new RedundantLambdaCodeBlockInspection(),
                          new AutoBoxingInspection(),
                          new RefusedBequestInspection());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantUncheckedVarargs";
  }
}
