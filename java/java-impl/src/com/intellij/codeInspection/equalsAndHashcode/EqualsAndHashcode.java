/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class EqualsAndHashcode extends BaseJavaLocalInspectionTool {

  private PsiMethod myHashCode;
  private PsiMethod myEquals;
  private final AtomicBoolean myInitialized = new AtomicBoolean();

  public void projectOpened(Project project) {
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!myInitialized.getAndSet(true)) {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(holder.getProject());
      final PsiClass psiObjectClass = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiClass>() {
            @Nullable
            public PsiClass compute() {
              return psiFacade.findClass("java.lang.Object");
            }
          }
      );
      if (psiObjectClass != null) {
        PsiMethod[] methods = psiObjectClass.getMethods();
        for (PsiMethod method : methods) {
          @NonNls final String name = method.getName();
          if ("equals".equals(name)) {
            myEquals = method;
          }
          else if ("hashCode".equals(name)) {
            myHashCode = method;
          }
        }
      }
    }

    //jdk wasn't configured for the project
    if (myEquals == null || myHashCode == null || !myEquals.isValid() || !myHashCode.isValid()) return new PsiElementVisitor() {};

    return new JavaElementVisitor() {
      @Override public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        boolean [] hasEquals = new boolean[] {false};
        boolean [] hasHashCode = new boolean[] {false};
        processClass(aClass, hasEquals, hasHashCode);
        if (hasEquals[0] != hasHashCode[0]) {
          PsiIdentifier identifier = aClass.getNameIdentifier();
          holder.registerProblem(identifier != null ? identifier : aClass,
                                 hasEquals[0]
                                  ? InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>equals()</code>", "<code>hashCode()</code>")
                                  : InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor","<code>hashCode()</code>", "<code>equals()</code>"),
                                 (LocalQuickFix[])null);
        }
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        //do nothing
      }
    };
  }

  private void processClass(final PsiClass aClass, final boolean[] hasEquals, final boolean[] hasHashCode) {
    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (MethodSignatureUtil.areSignaturesEqual(method, myEquals)) {
        hasEquals[0] = true;
      }
      else if (MethodSignatureUtil.areSignaturesEqual(method, myHashCode)) {
        hasHashCode[0] = true;
      }
    }
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.equals.hashcode.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return "EqualsAndHashcode";
  }

  public void projectClosed(Project project) {
    myEquals = null;
    myHashCode = null;
  }
}
