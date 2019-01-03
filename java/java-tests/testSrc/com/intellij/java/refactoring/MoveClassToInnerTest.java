// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

/**
 * @author yole
 */
public class MoveClassToInnerTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClassToInner/";
  }

  public void testContextChange1() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testContextChange2() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testInnerImport() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testInnerEnum() {
    doTest(new String[] { "pack2.AEnum" }, "pack1.Class1");
  }

  public void testInnerInsideMoved() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testInsertInnerClassImport() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testSimultaneousMove() {
    doTest(new String[] { "pack1.Class1", "pack0.Class0" }, "pack2.A");
  }

  public void testMoveMultiple1() {
    doTest(new String[] { "pack1.Class1", "pack1.Class2" }, "pack2.A");
  }

  public void testRefToInner() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testRefToConstructor() {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testSecondaryClass() {
    doTest(new String[] { "pack1.Class2" }, "pack1.User");
  }

  public void testStringsAndComments() {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testStringsAndComments2() {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testNonJava() {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testLocallyUsedPackageLocalToPublicInterface() {
    doTest(new String[]{"pack1.Class1"}, "pack2.A");
  }

  public void testPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Field <b><code>Class1.c2</code></b> uses package-private class <b><code>pack1.Class2</code></b>");
  }

  public void testMoveIntoPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  public void testMoveOfPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  public void testMoveIntoPrivateInnerClass() {
    doTestConflicts("pack1.Class1", "pack1.A.PrivateInner", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  public void testMoveWithPackageLocalMember() {
    doTestConflicts("pack1.Class1", "pack2.A", "Method <b><code>Class1.doStuff()</code></b> will no longer be accessible from method <b><code>Class2.test()</code></b>");
  }

  public void testDuplicateInner() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack2.A</code></b> already contains an inner class named <b><code>Class1</code></b>");
  }

  private void doTest(String[] classNames, String targetClassName) {
    doTest(() -> {
      final PsiClass[] classes = new PsiClass[classNames.length];
      for(int i = 0; i < classes.length; i++){
        String className = classNames[i];
        classes[i] = myFixture.findClass(className);
      }
      PsiClass targetClass = myFixture.findClass(targetClassName);
      new MoveClassToInnerProcessor(getProject(), classes, targetClass, true, true, null).run();
    });
  }

  private void doTestConflicts(String className, String targetClassName, String... expectedConflicts) {
    myFixture.copyDirectoryToProject(getTestName(true) + "/before", "");
    PsiClass classToMove = myFixture.findClass(className);
    PsiClass targetClass = myFixture.findClass(targetClassName);
    MoveClassToInnerProcessor processor = new MoveClassToInnerProcessor(getProject(), new PsiClass[]{classToMove}, targetClass, true, true, null);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
    assertSameElements(conflicts.values() , expectedConflicts);
  }
}
