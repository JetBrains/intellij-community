/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.navigation;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class ShowImplementationHandlerTest extends JavaCodeInsightFixtureTestCase {

  public void testMultipleImplsFromAbstractCall() throws Throwable {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {" +
                                                          "    {" +
                                                          "        Runnable r = () <caret>-> {};\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertTrue(element instanceof PsiClass);
    final String qualifiedName = ((PsiClass)element).getQualifiedName();
    assertEquals(CommonClassNames.JAVA_LANG_RUNNABLE, qualifiedName);
  }

}