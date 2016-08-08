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
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 * @since 15-Nov-2005
 */
public class RedundantThrowsDeclaration extends BaseJavaBatchLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("redundant.throws.declaration");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantThrowsDeclaration";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final Set<ProblemDescriptor> problems = new HashSet<>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        final ProblemDescriptor descriptor = checkExceptionsNeverThrown(reference, manager, isOnTheFly);
        if (descriptor != null) {
          problems.add(descriptor);
        }
      }

    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static ProblemDescriptor checkExceptionsNeverThrown(PsiJavaCodeReferenceElement referenceElement,
                                                              InspectionManager inspectionManager,
                                                              boolean onTheFly) {
    if (!(referenceElement.getParent() instanceof PsiReferenceList)) return null;
    PsiReferenceList referenceList = (PsiReferenceList)referenceElement.getParent();
    if (!(referenceList.getParent() instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)referenceList.getParent();
    if (referenceList != method.getThrowsList()) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    PsiManager manager = referenceElement.getManager();
    PsiClassType exceptionType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(referenceElement);
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(exceptionType)) return null;

    PsiCodeBlock body = method.getBody();
    if (body == null) return null;

    PsiModifierList modifierList = method.getModifierList();
    if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)
        && !modifierList.hasModifierProperty(PsiModifier.STATIC)
        && !modifierList.hasModifierProperty(PsiModifier.FINAL)
        && !method.isConstructor()
        && !(containingClass instanceof PsiAnonymousClass)
        && !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }

    Collection<PsiClassType> types = ExceptionUtil.collectUnhandledExceptions(body, method, false);
    Collection<PsiClassType> unhandled = new HashSet<>(types);
    if (method.isConstructor()) {
      // there may be field initializer throwing exception
      // that exception must be caught in the constructor
      PsiField[] fields = containingClass.getFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) continue;
        unhandled.addAll(ExceptionUtil.collectUnhandledExceptions(initializer, field));
      }
    }

    for (PsiClassType unhandledException : unhandled) {
      if (unhandledException.isAssignableFrom(exceptionType) || exceptionType.isAssignableFrom(unhandledException)) {
        return null;
      }
    }

    if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return null;

    String description = JavaErrorMessages.message("exception.is.never.thrown", JavaHighlightUtil.formatType(exceptionType));
    LocalQuickFix quickFixes = new DeleteThrowsFix(method, exceptionType);
    return inspectionManager.createProblemDescriptor(referenceElement, description, quickFixes, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
  }
}
