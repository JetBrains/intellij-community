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
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@HeavyPlatformTestCase.WrapInCommand
@TestApplication
public class IntroduceVariableMultifileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_5);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceVariable/");

  @Test
  public void testSamePackageRef() {
    multiFile.get().doTest(
      createAction("pack1.A",
                   new MockIntroduceVariableHandler("b", false, false, false, "pack1.B")
      )
    );
  }

  @Test
  public void testGenericTypeWithInner() {
    multiFile.get().doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  @Test
  public void testGenericTypeWithInner1() {
    multiFile.get().doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  @Test
  public void testGenericWithTwoParameters() {
    multiFile.get().doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "util.Pair<java.lang.String,util.Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  @Test
  public void testGenericWithTwoParameters2() {
    multiFile.get().doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "Pair<java.lang.String,Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  ThrowableRunnable<Exception> createAction(final String className, final IntroduceVariableBase testMe) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    return () -> {
      final PsiClass aClass = myFixture.findClass(className);
      final PsiFile containingFile = aClass.getContainingFile();
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      assertNotNull(virtualFile);
      myFixture.configureFromExistingVirtualFile(virtualFile);
      testMe.invoke(myFixture.getProject(), myFixture.getEditor(), containingFile, null);
    };
  }
}
