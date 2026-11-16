// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.JavaUpdateAddedFileProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class MoveClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClass/");

  @Test
  public void testContextChange1() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  @Test
  public void testContextChange2() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  @Test
  public void testMoveMultiple1() {
    doTest(new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
  }

  @Test
  public void testSecondaryClass() {
    doTest(new String[]{"pack1.Class2"}, "pack1");
  }

  @Test
  public void testStringsAndComments() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  @Test
  public void testStringsAndComments2() {
    doTest(new String[]{"pack1.AClass"}, "pack2");
  }

  @Test
  public void testNonJava() {
    doTest(new String[]{"pack1.Class1"}, "pack2");
  }

  @Test
  public void testRefInPropertiesFile() {
    doTest(new String[]{"p1.MyClass"}, "p");
  }

  /* IMPLEMENT: getReferences() in JspAttributeValueImpl should be dealed with (soft refs?)

  public void testJsp() throws Exception{
    doTest("jsp", new String[]{"pack1.TestClass"}, "pack2");
  }
  */

  @Test
  public void testLocalClass() {
    doTest(new String[]{"pack1.A"}, "pack2");
  }

  @Test
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

  @Test
  public void testToDefaultPackage() {
    try {
      doTest(new String[]{"pack1.Class1"}, "");
      fail("Conflicts expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("class <b><code>pack1.Class1</code></b> won't be accessible from the default package", e.getMessage());
    }
  }

  @Test
  public void testIdeadev27996() {
    doTest(new String[]{"pack1.X"}, "pack2");
  }

  @Test
  public void testUnusedImport() {
    doTest(new String[]{"p2.F2"}, "p1");
  }

  @Test
  public void testWithoutReference() {
    doTest(new String[]{"pack1.X"}, "pack2");
  }

  @Test
  public void testQualifiedRef() {
    doTest(new String[]{"p1.Test"}, "p2");
  }

  @Test
  public void testConflictingNames() {
    doTest(new String[]{"p1.First", "p1.Second"}, "p3");
  }

  @Test
  public void testImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      multiFile.get().doTest(() ->
               WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
                 myFixture.moveFile("pack1/Class1.java", "pack2");
               }));
    });
  }

  @Test
  public void testAddUsualFile() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      multiFile.get().doTest(() -> {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          JavaUpdateAddedFileProcessor processor = new JavaUpdateAddedFileProcessor();
          String path = "pack1/Class1.java";
          PsiFile[] files = myFixture.configureByFiles(path);
          processor.update(files[0], null);
        });
      });
    });
  }

  @Test
  public void testAddImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      multiFile.get().doTest(() -> {
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
    multiFile.get().doTest(() -> performAction(classNames, newPackageName));
  }

  private void performAction(@NonNls String[] classNames, @NonNls String newPackageName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final PsiClass[] classes = new PsiClass[classNames.length];
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i];
      classes[i] = myFixture.findClass(className);
    }

    PsiPackage aPackage = myFixture.findPackage(newPackageName);
    assertNotNull(aPackage, "Package " + newPackageName + " not found");
    final PsiDirectory[] dirs = aPackage.getDirectories(GlobalSearchScope.projectScope(myFixture.getProject()));
    assertEquals(1, dirs.length);

    new MoveClassesOrPackagesProcessor(myFixture.getProject(), classes,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService
                                                                                                   .getInstance().getPackage(dirs[0])),
                                                                           dirs[0]),
                                       true, true, null).run();
  }
}
