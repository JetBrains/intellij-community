// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RenameClassTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameClass/";
  }

  public void testNonJava() {
    doTest("pack1.Class1", "Class1New");
  }

  public void testCollision() {
    doTest("pack1.MyList", "List");
  }

  public void testPackageClassConflict() {
    doTest("Fabric.AFabric", "MetaFabric");
  }

  public void testInnerClass() {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  public void testImport() {
    //noinspection SpellCheckingInspection
    doTest("a.Blubfoo", "BlubFoo");
  }

  public void testInSameFile() {
    doTest("Two", "Object");
  }

  public void testConstructorJavadoc() {
    doTest("Test", "Test1");
  }

  public void testCollision1() {
    doTest("Loader", "Reader");
  }

  public void testImplicitReferenceToDefaultCtr() {
    doTest("pack1.Parent", "ParentXXX");
  }

  public void testImplicitlyImported() {
    doTest("pack1.A", "Object");
  }

  public void testAutomaticRenameVars() {
    doRenameClass("XX", "Y");
  }

  public void testAutomaticRenameLambdaParams() {
    doRenameClass("Bar", "Baz");
  }

  private void doRenameClass(final String className, final String newName) {
    doTest(() -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull("Class XX not found", aClass);

      final RenameProcessor processor = new RenameProcessor(getProject(), aClass, newName, true, true);
      for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        processor.addRenamerFactory(factory);
      }
      processor.run();
    });
  }

  public void testAutomaticRenameInheritors() {
    doRenameClass("MyClass", "MyClass1");
  }

  public void testAutomaticRenameVarsCollision() {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls final String qClassName, @NonNls final String newName) {
    doTest(() -> this.performAction(qClassName, newName));
  }

  private void performAction(String qClassName, String newName) {
    PsiClass aClass = myFixture.findClass(qClassName);
    assertNotNull("Class " + qClassName + " not found", aClass);

    new RenameProcessor(getProject(), aClass, newName, true, true).run();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
