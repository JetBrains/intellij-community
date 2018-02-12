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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.ToolExtensionPoints;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class VisibilityInspectionTest extends InspectionTestCase {
  private VisibilityInspection myTool = new VisibilityInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("visibility/" + getTestName(false), myTool);
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
    doTest("visibility/defaultConstructor", myTool, false, true);
  }

  public void testImplicitConstructor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/implicitConstructor", myTool, false, true);
  }

  public void testEnumConstants() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/enumConstantsVisibility", myTool, false, true);
  }

  public void testUsagesFromAnnotations() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/annotationUsages", myTool, false, true);
  }

  public void testTypeArguments() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/typeArguments", myTool, false, true);
  }
  
  public void testUsedFromAnnotationsExtendsList() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest("visibility/usedFromAnnotationsExtendsList", myTool, false, true);
  }
  
  public void testUsedQualifiedFromAnotherPackage() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest("visibility/usedFromAnotherPackage", myTool, false, true);
  }

  // IDEA-175921
  public void testInnerClassMethodUsedInsideOtherInnerClassInheritor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest("visibility/innerClassMethodUsedInsideOtherInnerClassInheritor", myTool, false, true);
  }

  public void testMethodUsedInInheritorInnerClass() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest("visibility/methodUsedInInheritorInnerClass", myTool, false, true);
  }

  public void testInnerClassMethodUsedInsideInheritor() {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest("visibility/innerClassMethodUsedInsideInheritor", myTool, false, true);
  }

  public void testEntryPointWithPredefinedVisibility() {
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), ExtensionPointName.create(ToolExtensionPoints.DEAD_CODE_TOOL), new EntryPointWithVisibilityLevel() {
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
        return member instanceof PsiMethod && isEntryPoint(member) ? PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL : -1;
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
    doTest("visibility/entryPointWithPredefinedVisibility", myTool, false, true);
  }
}
