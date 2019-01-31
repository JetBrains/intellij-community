// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class DelegateWithDefaultParamValueTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void doAction(@NotNull ActionHint actionHint, String testFullPath, String testName)
    throws Exception {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    super.doAction(actionHint, testFullPath, testName);

    if (actionHint.shouldPresent()) {
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      if (state != null) {
        state.gotoEnd(false);
      }
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/delegateWithDefaultValue";
  }
}
