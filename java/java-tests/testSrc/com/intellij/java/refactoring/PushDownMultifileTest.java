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
      assertEquals(e.getMessage(), "Class <b><code>b.B</code></b> is package-private and will not be accessible from file <b><code>A.form</code></b>.");
      return;
    }
    fail("Conflict was not detected");
  }
}
