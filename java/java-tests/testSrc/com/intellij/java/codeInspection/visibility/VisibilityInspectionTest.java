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
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class VisibilityInspectionTest extends JavaInspectionTestCase {
  private VisibilityInspection myTool = new VisibilityInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/visibility";
  }

  private void doTest() {
    doTest(getTestName(false), myTool);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testinnerConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  public void testpackageLevelTops() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest();
  }

  public void testSCR5008() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  public void testSCR6856() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  public void testSCR11792() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest();
  }

  public void testIDEADEV10312() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    doTest();
  }

  public void testIDEADEV10883() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    doTest();
  }

  public void testDefaultConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testImplicitConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testEnumConstantsVisibility() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testAnnotationUsages() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testTypeArguments() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testUsedFromAnnotationsExtendsList() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testUsedFromAnnotationsExtendsList2() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testOverrideInInnerClass() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testUsedFromAnotherPackage() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }

  // IDEA-175921
  public void testInnerClassMethodUsedInsideOtherInnerClassInheritor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testMethodUsedInInheritorInnerClass() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testInnerClassMethodUsedInsideInheritor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }

  public void testSameFileInheritance() {
    doTest(getTestName(true), myTool);
  }

  public void testRecords() {
    doTest(getTestName(true), myTool);
  }

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
    }, getTestRootDisposable());
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest(getTestName(true), myTool, false, true);
  }
}
