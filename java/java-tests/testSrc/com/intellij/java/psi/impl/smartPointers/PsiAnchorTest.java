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
package com.intellij.java.psi.impl.smartPointers;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.xml.XmlToken;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class PsiAnchorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSingleTokenAnchor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("a.xml", "<");
    PsiAnchor anchor = ReadAction.computeBlocking(() -> PsiAnchor.create(assertInstanceOf(XmlToken.class, file.findElementAt(0))));

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> file.getViewProvider().getDocument().replaceString(0, 1, " "));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());

    assertInstanceOf(PsiWhiteSpace.class, file.findElementAt(0));
    ReadAction.runBlocking(() -> assertNull(anchor.retrieve()));
  }

  @Test
  public void testInjectedAnchor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new MyTestInjector(PsiManager.getInstance(myFixture.getProject())).injectAll(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class X { String java=\"class <caret>Foo {}\"}");
    PsiClass cls = ReadAction.computeBlocking(() -> (PsiClass)myFixture.getElementAtCaret());
    ReadAction.runBlocking(() -> assertEquals("Foo", cls.getName()));
    PsiAnchor anchor = ReadAction.computeBlocking(() -> PsiAnchor.create(cls));

    myFixture.type('\n');
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());

    ReadAction.runBlocking(() -> anchor.retrieve()); // file is changed, so we probably can't restore. But this call shouldn't throw exceptions.
  }
}
