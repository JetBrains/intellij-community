/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.PossibleHeapPollutionVarargsInspection;
import com.intellij.codeInspection.RedundantLambdaCodeBlockInspection;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.siyeh.ig.controlflow.FallthruInSwitchStatementInspection;


public class RemoveRedundantSuppressionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
    enableInspectionTools(new RedundantSuppressInspection(), 
                          new PossibleHeapPollutionVarargsInspection(),
                          new UncheckedWarningLocalInspection(),
                          new FallthruInSwitchStatementInspection(),
                          new UnusedDeclarationInspection(true),
                          new RedundantLambdaCodeBlockInspection());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantUncheckedVarargs";
  }

}
