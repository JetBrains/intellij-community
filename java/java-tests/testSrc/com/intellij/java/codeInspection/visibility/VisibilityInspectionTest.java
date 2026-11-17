// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.visibility;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class VisibilityInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/visibility");

  private final VisibilityInspection myTool = new VisibilityInspection();

  private void doTest() {
    javaInspection.get().doTest(javaInspection.get().getTestName(false), myTool);
  }

  @Test
  public void testinnerConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  @Test
  public void testpackageLevelTops() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest();
  }

  @Test
  public void testSCR5008() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  @Test
  public void testSCR6856() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  @Test
  public void testSCR11792() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  @Test
  public void testIDEADEV10312() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    doTest();
  }

  @Test
  public void testIDEADEV10883() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    doTest();
  }

  @Test
  public void testDefaultConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testImplicitConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testEnumConstantsVisibility() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testAnnotationUsages() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testTypeArguments() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testUsedFromAnnotationsExtendsList() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testUsedFromAnnotationsExtendsList2() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testOverrideInInnerClass() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testUsedFromAnotherPackage() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  // IDEA-175921
  @Test
  public void testInnerClassMethodUsedInsideOtherInnerClassInheritor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testMethodUsedInInheritorInnerClass() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testInnerClassMethodUsedInsideInheritor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }

  @Test
  public void testSameFileInheritance() {
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool);
  }

  @Test
  public void testRecords() {
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool);
  }

  @Test
  public void testEntryPointWithPredefinedVisibility() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), EntryPointsManagerBase.DEAD_CODE_EP_NAME, new EntryPointWithVisibilityLevel() {
      @Override
      public void readExternal(Element element) throws InvalidDataException {}

      @Override
      public void writeExternal(Element element) throws WriteExternalException {}

      @NotNull
      @Override
      public String getDisplayName() {
        return "accepted visibility";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return isEntryPoint(psiElement);
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiMethod && "foo".equals(((PsiMethod)psiElement).getName()) || psiElement instanceof PsiClass;
      }

      @Override
      public int getMinVisibilityLevel(PsiMember member) {
        return member instanceof PsiMethod && isEntryPoint(member) ? PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL : ACCESS_LEVEL_INVALID;
      }

      @Override
      public boolean isSelected() {
        return true;
      }

      @Override
      public void setSelected(boolean selected) {}

      @Override
      public String getTitle() {
        return getDisplayName();
      }

      @Override
      public String getId() {
        return getDisplayName();
      }
    }, javaInspection.get().getFixture().getTestRootDisposable());
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool, false, true);
  }
}
