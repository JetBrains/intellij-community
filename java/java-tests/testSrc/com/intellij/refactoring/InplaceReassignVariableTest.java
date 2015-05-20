/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.refactoring.introduceVariable.ReassignVariableUtil;

public class InplaceReassignVariableTest extends AbstractJavaInplaceIntroduceTest {

  @Override
  protected void runTest() throws Throwable {
    doRunTest();
  }

  public void testReassignSimple() throws Exception {
    doTest();
  }

  public void testReassignWhenVariableWasPutInLoopBody() throws Exception {
    doTest();
  }

  private void doTest() {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      invokeRefactoring();
      ReassignVariableUtil.reassign(getEditor());

      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  @Override
  protected String getBasePath() {
    return "/refactoring/inplaceIntroduceVariable/";
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new InplaceIntroduceVariableTest.MyIntroduceVariableHandler();
  }
}
