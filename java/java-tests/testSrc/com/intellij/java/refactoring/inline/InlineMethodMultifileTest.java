// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.MockInlineMethodOptions;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.InlineOptions;
import com.intellij.refactoring.util.InlineUtil;

public class InlineMethodMultifileTest extends LightMultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineMethod/multifile/";
  }

  public void testRemoveStaticImports() {
    doTest("Foo", "foo");
  }
  public void testPreserveStaticImportsIfOverloaded() {
    doTest("Foo", "foo");
  }

  public void testDecodeQualifierInMethodReference() {
    doTest("Foo", "foo");
  }

  private void doTest(String className, String methodName) {
    doTest(() -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass);
      PsiMethod method = aClass.findMethodsByName(methodName, false)[0];
      final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
      assertFalse("Bad returns found", condition);

      InlineOptions options = new MockInlineMethodOptions();
      final InlineMethodProcessor processor =
        new InlineMethodProcessor(getProject(), method, null, getEditor(), options.isInlineThisOnly());
      processor.run();
    });
  }
}
