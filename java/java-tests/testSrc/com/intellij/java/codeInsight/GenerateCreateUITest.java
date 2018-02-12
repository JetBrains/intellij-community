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
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.actions.GenerateCreateUIAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author anna
 */
public class GenerateCreateUITest extends LightCodeInsightTestCase {
  public void testSOE() {
    configureByFile("/codeInsight/generate/generateCreateUI/beforeSOE.java");
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiClass.class);
    assertNotNull(targetClass);
    assertFalse(new GenerateCreateUIAction().isValidForClass(targetClass));
  }
  
}
