// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaUpdateAddedFileProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

public class MoveClassTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClass/";
  }

  public void testContextChange1() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  public void testContextChange2() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  public void testMoveMultiple1() {
    doTest(new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
  }

  public void testSecondaryClass() {
    doTest(new String[]{"pack1.Class2"}, "pack1");
  }

  public void testStringsAndComments() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  public void testStringsAndComments2() {
    doTest(new String[]{"pack1.AClass"}, "pack2");
  }

  public void testNonJava() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  public void testRefInPropertiesFile() {
    doTest(new String[]{"p1.MyClass"}, "p");
  }

  /* IMPLEMENT: getReferences() in JspAttributeValueImpl should be dealed with (soft refs?)

  public void testJsp() throws Exception{
    doTest("jsp", new String[]{"pack1.TestClass"}, "pack2");
  }
  */

  public void testLocalClass() {
    doTest(new String[]{"pack1.A"}, "pack2");
  }

  public void testClassAndSecondary() {
    try {
      doTest(new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
      fail("Conflicts expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Package-local class <b><code>Class2</code></b> will no longer be accessible from field <b><code>User.class2</code></b>",
                   e.getMessage());
    }
  }

  public void testToDefaultPackage() {
    try {
      doTest(new String[]{"pack1.Class1"}, "");
      fail("Conflicts expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("class <b><code>pack1.Class1</code></b> won't be accessible from the default package", e.getMessage());
    }
  }

  public void testIdeadev27996() {
    doTest(new String[]{"pack1.X"}, "pack2");
  }

  public void testUnusedImport() {
    doTest(new String[]{"p2.F2"}, "p1");
  }

  public void testQualifiedRef() {
    doTest(new String[]{"p1.Test"}, "p2");
  }

  public void testConflictingNames() {
    doTest(new String[]{"p1.First", "p1.Second"}, "p3");
  }

  public void testImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest(() ->
               WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
                 myFixture.moveFile("pack1/Class1.java", "pack2");
               }));
    });
  }

  public void testAddUsualFile() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest(() -> {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          JavaUpdateAddedFileProcessor processor = new JavaUpdateAddedFileProcessor();
          String path = "pack1/Class1.java";
          PsiFile[] files = myFixture.configureByFiles(path);
          processor.update(files[0], null);
        });
      });
    });
  }

  public void testAddImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest(() -> {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          JavaUpdateAddedFileProcessor processor = new JavaUpdateAddedFileProcessor();
          String path = "pack1/Class1.java";
          PsiFile[] files = myFixture.configureByFiles(path);
          processor.update(files[0], null);
        });
      });
    });
  }

  private void doTest(@NonNls String[] classNames, @NonNls String newPackageName) {
    doTest(() -> performAction(classNames, newPackageName));
  }

  private void performAction(@NonNls String[] classNames, @NonNls String newPackageName) {
    final PsiClass[] classes = new PsiClass[classNames.length];
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i];
      classes[i] = myFixture.findClass(className);
    }

    PsiPackage aPackage = myFixture.findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories(GlobalSearchScope.projectScope(getProject()));
    assertEquals(1, dirs.length);

    new MoveClassesOrPackagesProcessor(getProject(), classes,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService
                                                                                                   .getInstance().getPackage(dirs[0])),
                                                                           dirs[0]),
                                       true, true, null).run();
  }
}
