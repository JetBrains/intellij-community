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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
@PlatformTestCase.WrapInCommand
public class IntroduceVariableMultifileTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceVariable/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_5;
  }

  public void testSamePackageRef() {
    doTest(
      createAction("pack1.A",
                   new MockIntroduceVariableHandler("b", false, false, false, "pack1.B")
      )
    );
  }

  public void testGenericTypeWithInner() {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericTypeWithInner1() {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericWithTwoParameters() {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "util.Pair<java.lang.String,util.Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  public void testGenericWithTwoParameters2() {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "Pair<java.lang.String,Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  ThrowableRunnable<Exception> createAction(final String className, final IntroduceVariableBase testMe) {
    return () -> {
      final PsiClass aClass = myFixture.findClass(className);
      final PsiFile containingFile = aClass.getContainingFile();
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      assertNotNull(virtualFile);
      myFixture.configureFromExistingVirtualFile(virtualFile);
      testMe.invoke(getProject(), getEditor(), containingFile, null);
    };
  }
}
