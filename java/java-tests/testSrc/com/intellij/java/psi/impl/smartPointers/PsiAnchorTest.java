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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.xml.XmlToken;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class PsiAnchorTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSingleTokenAnchor() {
    PsiFile file = myFixture.configureByText("a.xml", "<");
    PsiAnchor anchor = PsiAnchor.create(assertInstanceOf(file.findElementAt(0), XmlToken.class));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> file.getViewProvider().getDocument().replaceString(0, 1, " "));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertInstanceOf(file.findElementAt(0), PsiWhiteSpace.class);
    assertNull(anchor.retrieve());
  }

  public void testInjectedAnchor() {
    new MyTestInjector(getPsiManager()).injectAll(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class X { String java=\"class <caret>Foo {}\"}");
    PsiClass cls = (PsiClass)myFixture.getElementAtCaret();
    assertEquals("Foo", cls.getName());
    PsiAnchor anchor = PsiAnchor.create(cls);

    myFixture.type('\n');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    anchor.retrieve(); // file is changed, so we probably can't restore. But this call shouldn't throw exceptions.
  }
}
