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
package com.intellij.testIntegration;

import com.intellij.psi.PsiClass;

public class GenerateTearDownClassMethodAction extends BaseGenerateTestSupportMethodAction {
  public GenerateTearDownClassMethodAction() {
    super(TestIntegrationUtils.MethodKind.TEAR_DOWN_CLASS);
  }

  @Override
  protected boolean isValidFor(PsiClass targetClass, TestFramework framework) {
    return super.isValidFor(targetClass, framework) && framework.findTearDownClassMethod(targetClass) == null;
  }
}
