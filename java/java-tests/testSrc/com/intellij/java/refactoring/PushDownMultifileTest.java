// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.Collections;

//push first method from class a.A to class b.B
public class PushDownMultifileTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/pushDown/";
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean fail) {
    doTest(fail, "a.A", "b.B");
  }

  private void doTest(final boolean fail, final String sourceClassName, final String... targetClassNames) {
    try {
      doTest(() -> {
        final PsiClass srcClass = myFixture.findClass(sourceClassName);

        for (String targetClassName : targetClassNames) {
          myFixture.findClass(targetClassName);
        }

        final PsiMethod[] methods = srcClass.getMethods();
        assertTrue("No methods found", methods.length > 0);
        final MemberInfo memberInfo = new MemberInfo(methods[0]);
        memberInfo.setChecked(true);
        new PushDownProcessor<>(srcClass, Collections.singletonList(memberInfo), new DocCommentPolicy(DocCommentPolicy.ASIS)).run();


        //LocalFileSystem.getInstance().refresh(false);
        //FileDocumentManager.getInstance().saveAllDocuments();
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (fail) {
        return;
      }
      else {
        throw e;
      }
    }
    if (fail) {
      fail("Conflict was not detected");
    }
  }


  public void testStaticImportsInsidePushedMethod() {
    doTest();
  }

  public void testStaticImportOfPushedMethod() {
    doTest();
  }

  public void testReuseOverrideMethod() {
    doTest();
  }

  public void testFromInterface() {
    doTest(false, "a.I", "a.I1");
  }

  public void testTwoInheritors() {
    doTest(false, "c.Super", "a.SomeA", "b.SomeB");
  }

  public void testUsagesInXml() {
    try {
      doTest(() -> {
        final PsiClass srcClass = myFixture.findClass("a.A");

        myFixture.findClass("b.B");

        final PsiField[] fields = srcClass.getFields();
        assertTrue("No methods found", fields.length > 0);
        final MemberInfo memberInfo = new MemberInfo(fields[0]);
        memberInfo.setChecked(true);
        new PushDownProcessor<>(srcClass, Collections.singletonList(memberInfo), new DocCommentPolicy(DocCommentPolicy.ASIS)).run();


        //LocalFileSystem.getInstance().refresh(false);
        //FileDocumentManager.getInstance().saveAllDocuments();
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Class <b><code>b.B</code></b> is package-private and will not be accessible from file <b><code>A.form</code></b>.",
                   e.getMessage());
      return;
    }
    fail("Conflict was not detected");
  }
}
