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
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    super.doAction(actionHint, testFullPath, testName);

    if (actionHint.shouldPresent()) {
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      if (state != null) {
        state.gotoEnd(false);
      }
    }
  }

  public void test() {
    doAllTests();

  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/delegateWithDefaultValue";
  }
}
