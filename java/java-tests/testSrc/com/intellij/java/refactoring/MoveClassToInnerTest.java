// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class MoveClassToInnerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClassToInner/");

  @Test
  public void testContextChange1() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testContextChange2() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testInnerImport() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testInnerEnum() {
    doTest(new String[] { "pack2.AEnum" }, "pack1.Class1");
  }

  @Test
  public void testInnerInsideMoved() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testInsertInnerClassImport() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testSimultaneousMove() {
    doTest(new String[] { "pack1.Class1", "pack0.Class0" }, "pack2.A");
  }

  @Test
  public void testMoveMultiple1() {
    doTest(new String[] { "pack1.Class1", "pack1.Class2" }, "pack2.A");
  }

  @Test
  public void testRefToInner() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testRefToConstructor() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  @Test
  public void testSecondaryClass() {
    doTest(new String[] { "pack1.Class2" }, "pack1.User");
  }

  @Test
  public void testStringsAndComments() {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  @Test
  public void testStringsAndComments2() {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  @Test
  public void testNonJava() {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  @Test
  public void testLocallyUsedPackageLocalToPublicInterface() {
    doTest(new String[]{"pack1.Class1"}, "pack2.A");
  }

  @Test
  public void testPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Field <b><code>Class1.c2</code></b> uses package-private class <b><code>pack1.Class2</code></b>");
  }

  @Test
  public void testMoveIntoPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack1.Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  @Test
  public void testMoveOfPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack1.Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  @Test
  public void testMoveIntoPrivateInnerClass() {
    doTestConflicts("pack1.Class1", "pack1.A.PrivateInner", "Class <b><code>pack1.Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  @Test
  public void testMoveWithPackageLocalMember() {
    doTestConflicts("pack1.Class1", "pack2.A", "Method <b><code>Class1.doStuff()</code></b> will no longer be accessible from method <b><code>Class2.test()</code></b>");
  }

  @Test
  public void testDuplicateInner() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack2.A</code></b> already contains an inner class named <b><code>Class1</code></b>");
  }

  private void doTest(String[] classNames, String targetClassName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      final PsiClass[] classes = new PsiClass[classNames.length];
      for(int i = 0; i < classes.length; i++){
        String className = classNames[i];
        classes[i] = myFixture.findClass(className);
      }
      PsiClass targetClass = myFixture.findClass(targetClassName);
      new MoveClassToInnerProcessor(myFixture.getProject(), classes, targetClass, true, true, null).run();
    });
  }

  private void doTestConflicts(String className, String targetClassName, String... expectedConflicts) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    myFixture.copyDirectoryToProject(myFixture.getTestName(true) + "/before", "");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass classToMove = myFixture.findClass(className);
      PsiClass targetClass = myFixture.findClass(targetClassName);
      MoveClassToInnerProcessor processor = new MoveClassToInnerProcessor(myFixture.getProject(), new PsiClass[]{classToMove}, targetClass, true, true, null);
      UsageInfo[] usages = processor.findUsages();
      MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
      assertThat(conflicts.values()).containsOnly(expectedConflicts);
    });
  }
}
