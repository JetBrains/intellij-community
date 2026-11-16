// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

//push first method from class a.A to class b.B
@TestApplication
public class PushDownMultifileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/pushDown/");

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean fail) {
    doTest(fail, "a.A", "b.B");
  }

  private void doTest(final boolean fail, final String sourceClassName, final String... targetClassNames) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    try {
      multiFile.get().doTest(() -> {
        final PsiClass srcClass = myFixture.findClass(sourceClassName);

        for (String targetClassName : targetClassNames) {
          myFixture.findClass(targetClassName);
        }

        final PsiMethod[] methods = srcClass.getMethods();
        assertTrue(methods.length > 0, "No methods found");
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

  @Test
  public void testStaticImportsInsidePushedMethod() {
    doTest();
  }

  @Test
  public void testStaticImportOfPushedMethod() {
    doTest();
  }

  @Test
  public void testReuseOverrideMethod() {
    doTest();
  }

  @Test
  public void testFromInterface() {
    doTest(false, "a.I", "a.I1");
  }

  @Test
  public void testTwoInheritors() {
    doTest(false, "c.Super", "a.SomeA", "b.SomeB");
  }

  @Test
  public void testUsagesInXml() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    try {
      multiFile.get().doTest(() -> {
        final PsiClass srcClass = myFixture.findClass("a.A");

        myFixture.findClass("b.B");

        final PsiField[] fields = srcClass.getFields();
        assertTrue(fields.length > 0, "No methods found");
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
