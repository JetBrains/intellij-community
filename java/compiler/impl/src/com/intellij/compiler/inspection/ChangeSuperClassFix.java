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
package com.intellij.compiler.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public class ChangeSuperClassFix implements LocalQuickFix, HighPriorityAction {
  @NotNull
  private final SmartPsiElementPointer<PsiClass> myNewSuperClass;
  @NotNull
  private final SmartPsiElementPointer<PsiClass> myOldSuperClass;
  private final int myInheritorCount;
  @NotNull
  private final String myNewSuperName;
  private final boolean myNewSuperIsInterface;

  public ChangeSuperClassFix(@NotNull final PsiClass newSuperClass, final int percent, @NotNull final PsiClass oldSuperClass) {
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(newSuperClass.getProject());
    myNewSuperName = ObjectUtils.notNull(newSuperClass.getQualifiedName());
    myNewSuperIsInterface = newSuperClass.isInterface();
    myNewSuperClass = smartPointerManager.createSmartPsiElementPointer(newSuperClass);
    myOldSuperClass = smartPointerManager.createSmartPsiElementPointer(oldSuperClass);
    myInheritorCount = percent;
  }

  @NotNull
  @TestOnly
  public PsiClass getNewSuperClass() {
    return ObjectUtils.notNull(myNewSuperClass.getElement());
  }

  @TestOnly
  public int getInheritorCount() {
    return myInheritorCount;
  }

  @NotNull
  @Override
  public String getName() {
    return String.format("Make " + (myNewSuperIsInterface ? "implements" : "extends") + " '%s'", myNewSuperName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor problemDescriptor) {
    final PsiClass oldSuperClass = myOldSuperClass.getElement();
    final PsiClass newSuperClass = myNewSuperClass.getElement();
    if (oldSuperClass == null || newSuperClass == null) return;
    changeSuperClass((PsiClass)problemDescriptor.getPsiElement(), oldSuperClass, newSuperClass);
  }

  /**
   * myOldSuperClass and myNewSuperClass can be interfaces or classes in any combination
   * <p/>
   * 1. not checks that myOldSuperClass is really super of aClass
   * 2. not checks that myNewSuperClass not exists in currently existed supers
   */
  private static void changeSuperClass(@NotNull final PsiClass aClass,
                                       @NotNull final PsiClass oldSuperClass,
                                       @NotNull final PsiClass newSuperClass) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(aClass.getProject());
    PsiElementFactory factory = psiFacade.getElementFactory();
    if (aClass instanceof PsiAnonymousClass) {
      ((PsiAnonymousClass)aClass).getBaseClassReference().replace(factory.createClassReferenceElement(newSuperClass));
      return;
    }
    PsiReferenceList extendsList = ObjectUtils.notNull(aClass.getExtendsList());
    PsiJavaCodeReferenceElement[] refElements =
      ArrayUtil.mergeArrays(getReferences(extendsList), getReferences(aClass.getImplementsList()));
    for (PsiJavaCodeReferenceElement refElement : refElements) {
      if (refElement.isReferenceTo(oldSuperClass)) {
        refElement.delete();
      }
    }

    PsiReferenceList list;
    if (newSuperClass.isInterface()) {
      list = aClass.getImplementsList();
    }
    else {
      list = extendsList;
      PsiJavaCodeReferenceElement[] elements = list.getReferenceElements();
      if (elements.length == 1 &&
          elements[0].isReferenceTo(psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, aClass.getResolveScope()))) {
        elements[0].delete();
      }
    }
    PsiElement ref = list.add(factory.createClassReferenceElement(newSuperClass));
    JavaCodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(ref);
  }

  private static PsiJavaCodeReferenceElement[] getReferences(PsiReferenceList list) {
    return list == null ? PsiJavaCodeReferenceElement.EMPTY_ARRAY : list.getReferenceElements();
  }
}
