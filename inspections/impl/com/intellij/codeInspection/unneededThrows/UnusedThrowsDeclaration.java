/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
public class UnusedThrowsDeclaration extends LocalInspectionTool {
  public String getGroupDisplayName() {
    return "";
  }

  public String getDisplayName() {
    return "unused throws";
  }

  @NonNls
  public String getShortName() {
    return "UnusedThrowsDeclaration";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    final Set<ProblemDescriptor> problems = new HashSet<ProblemDescriptor>();
    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        final ProblemDescriptor descriptor = checkExceptionsNeverThrown(reference, manager);
        if (descriptor != null) {
          problems.add(descriptor);
        }
      }

    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }



  //@top
  private static ProblemDescriptor checkExceptionsNeverThrown(PsiJavaCodeReferenceElement referenceElement, InspectionManager inspectionManager) {
    if (!(referenceElement.getParent() instanceof PsiReferenceList)) return null;
    PsiReferenceList referenceList = (PsiReferenceList)referenceElement.getParent();
    if (!(referenceList.getParent() instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)referenceList.getParent();
    if (referenceList != method.getThrowsList()) return null;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;

    PsiManager manager = referenceElement.getManager();
    PsiClassType exceptionType = manager.getElementFactory().createType(referenceElement);
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(exceptionType)) return null;

    PsiCodeBlock body = method.getBody();
    if (body == null) return null;

    PsiModifierList modifierList = method.getModifierList();
    PsiClass containingClass = method.getContainingClass();
    if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)
        && !modifierList.hasModifierProperty(PsiModifier.STATIC)
        && !modifierList.hasModifierProperty(PsiModifier.FINAL)
        && !method.isConstructor()
        && !(containingClass instanceof PsiAnonymousClass)
        && !(containingClass != null && containingClass.hasModifierProperty(PsiModifier.FINAL))) {
      return null;
    }

    PsiClassType[] types = ExceptionUtil.collectUnhandledExceptions(body, method);
    Collection<PsiClassType> unhandled = new HashSet<PsiClassType>(Arrays.asList(types));
    if (method.isConstructor()) {
      // there may be field initializer throwing exception
      // that exception must be caught in the constructor
      PsiField[] fields = aClass.getFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) continue;
        unhandled.addAll(Arrays.asList(ExceptionUtil.collectUnhandledExceptions(initializer, field)));
      }
    }

    for (PsiClassType unhandledException : unhandled) {
      if (unhandledException.isAssignableFrom(exceptionType) ||
          exceptionType.isAssignableFrom(unhandledException)) {
        return null;
      }
    }

    String description = JavaErrorMessages.message("exception.is.never.thrown", HighlightUtil.formatType(exceptionType));

    return inspectionManager.createProblemDescriptor(referenceElement, description, new LocalQuickFix []{ new RemoveFix(method, exceptionType)}, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.unnededThrows.UnnusedThrowsDeclaration");

  private static class RemoveFix implements LocalQuickFix {

    private final PsiMethod myMethod;
    private final PsiClassType myThrowsClassType;

    public RemoveFix(PsiMethod method, PsiClassType exceptionClass) {
      myMethod = method;
      myThrowsClassType = exceptionClass;
    }

    public String getName() {
      String methodName = PsiFormatUtil.formatMethod(myMethod,
                                                     PsiSubstitutor.EMPTY,
                                                     PsiFormatUtil.SHOW_NAME,
                                                     0);
      return QuickFixBundle.message("fix.throws.list.remove.exception",
                                    myThrowsClassType.getCanonicalText(),
                                    methodName);
    }

    public String getFamilyName() {
      return QuickFixBundle.message("fix.throws.list.family");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      PsiFile file = myMethod.getContainingFile();
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
      try {
        for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
          if (referenceElement.getCanonicalText().equals(myThrowsClassType.getCanonicalText())) {
            referenceElement.delete();
            break;
          }
        }
        UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

}
