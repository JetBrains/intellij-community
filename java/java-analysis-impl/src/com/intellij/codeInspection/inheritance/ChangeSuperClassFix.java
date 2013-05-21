/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.inheritance;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ChangeSuperClassFix implements LocalQuickFix {
  @NotNull
  private final PsiClass myNewSuperClass;
  @NotNull
  private final PsiClass myOldSuperClass;
  private final int myPercent;

  public ChangeSuperClassFix(@NotNull final PsiClass newSuperClass, final int percent, @NotNull final PsiClass oldSuperClass) {
    myNewSuperClass = newSuperClass;
    myOldSuperClass = oldSuperClass;
    myPercent = percent;
  }

  @NotNull
  @TestOnly
  public PsiClass getNewSuperClass() {
    return myNewSuperClass;
  }

  @TestOnly
  public int getPercent() {
    return myPercent;
  }

  @NotNull
  @Override
  public String getName() {
    return String.format("Make extends '%s' - %s%%", myNewSuperClass.getQualifiedName(), myPercent);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor problemDescriptor) {
    changeSuperClass((PsiClass)problemDescriptor.getPsiElement(), myOldSuperClass, myNewSuperClass);
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
    if (!FileModificationService.getInstance().preparePsiElementForWrite(aClass)) return;

    if (!FileModificationService.getInstance().prepareFileForWrite(aClass.getContainingFile())) return;
    CommandProcessor.getInstance().executeCommand(newSuperClass.getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
            if (aClass instanceof PsiAnonymousClass) {
              ((PsiAnonymousClass)aClass).getBaseClassReference().replace(factory.createClassReferenceElement(newSuperClass));
            }
            else if (oldSuperClass.isInterface()) {
              final PsiReferenceList interfaceList = aClass.getImplementsList();
              if (interfaceList != null) {
                for (final PsiJavaCodeReferenceElement interfaceRef : interfaceList.getReferenceElements()) {
                  final PsiElement aInterface = interfaceRef.resolve();
                  if (aInterface != null && aInterface.isEquivalentTo(oldSuperClass)) {
                    interfaceRef.delete();
                  }
                }
              }

              final PsiReferenceList extendsList = aClass.getExtendsList();
              if (extendsList != null) {
                final PsiJavaCodeReferenceElement newClassReference = factory.createClassReferenceElement(newSuperClass);
                if (extendsList.getReferenceElements().length == 0) {
                  extendsList.add(newClassReference);
                }
              }
            }
            else {
              final PsiReferenceList extendsList = aClass.getExtendsList();
              if (extendsList != null && extendsList.getReferenceElements().length == 1) {
                extendsList.getReferenceElements()[0].delete();
                PsiElement ref = extendsList.add(factory.createClassReferenceElement(newSuperClass));
                JavaCodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(ref);
              }
            }
          }
        });
      }
    }, "Changing inheritance", null);
  }

  public static class LowPriority extends ChangeSuperClassFix implements LowPriorityAction {
    public LowPriority(@NotNull final PsiClass newSuperClass, final int percent, @NotNull final PsiClass oldSuperClass) {
      super(newSuperClass, percent, oldSuperClass);
    }
  }
}
