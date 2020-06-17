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
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.MakeSealedAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MakeSealedActionMultiFileTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  public void testMultiFile() {
    PsiFile main = myFixture.configureByText("Main.java", "class Mai<caret>n {}");
    PsiClass direct1 = myFixture.addClass("class Direct1 extends Main {}");
    PsiClass direct2 = myFixture.addClass("class Direct2 extends Main {}");
    PsiClass indirect = myFixture.addClass("class Indirect extends Direct1 {}");

    MakeSealedAction action = new MakeSealedAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      action.invoke(getProject(), getEditor(), getFile());
    });
    assertEquals("sealed class Main permits Direct1, Direct2 {}", main.getText());
    assertEquals("non-sealed class Direct1 extends Main {}", direct1.getText());
    assertEquals("non-sealed class Direct2 extends Main {}", direct2.getText());
    assertEquals("class Indirect extends Direct1 {}", indirect.getText());
  }
}
