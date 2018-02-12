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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ChangeSignatureTargetTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInMethodParameters() {
    doTest("foo");
  }

  public void testInMethodArguments() {
    doTest("foo");
  }

  public void testInClassTypeParameters() {
    doTest("A1");
  }

  public void testInTypeArguments() {
    doTest("A1");
  }

  public void testDisabledForEnum() {
    configureByFile("/refactoring/changeSignatureTarget/" + getTestName(true) + ".java");
    assertNull(new JavaChangeSignatureHandler().findTargetMember(getFile(), getEditor()));
  }

  private void doTest(String expectedMemberName) {
    String basePath = "/refactoring/changeSignatureTarget/" + getTestName(true);
    @NonNls final String filePath = basePath + ".java";
    configureByFile(filePath);
    final PsiElement member = new JavaChangeSignatureHandler().findTargetMember(getFile(), getEditor());
    assertNotNull(member);
    assertEquals(expectedMemberName, ((PsiMember)member).getName());
  }
}
