/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class AddExceptionToThrowsFix extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToThrowsFix");
  private final PsiElement myWrongElement;

  public AddExceptionToThrowsFix(PsiElement wrongElement) {
    myWrongElement = wrongElement;
  }

  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiMethod targetMethod = PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class);
    PsiElement element = findElement(myWrongElement, targetMethod);
    LOG.assertTrue(element != null);

    final Set<PsiClassType> unhandledExceptions = filterInProjectExceptions(targetMethod, ExceptionUtil.getUnhandledExceptions(element));

    addExceptionsToThrowsList(project, targetMethod, unhandledExceptions);
  }

  static void addExceptionsToThrowsList(final Project project, final PsiMethod targetMethod, final Set<PsiClassType> unhandledExceptions) {
    final PsiMethod[] superMethods = getSuperMethods(targetMethod);

    boolean hasSuperMethodsWithoutExceptions = hasSuperMethodsWithoutExceptions(superMethods, unhandledExceptions);

    final boolean processSuperMethods;
    if (hasSuperMethodsWithoutExceptions && superMethods.length > 0) {
      int result = Messages.showYesNoCancelDialog(
        QuickFixBundle.message("add.exception.to.throws.inherited.method.warning.text", targetMethod.getName()),
        QuickFixBundle.message("method.is.inherited.warning.title"),
        Messages.getQuestionIcon());

      if (result == 0) {
        processSuperMethods = true;
      }
      else if (result == 1) {
        processSuperMethods = false;
      }
      else {
        return;
      }
    }
    else {
      processSuperMethods = false;
    }

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          if (!CodeInsightUtilBase.prepareFileForWrite(targetMethod.getContainingFile())) return;
          if (processSuperMethods) {
            for (PsiMethod superMethod : superMethods) {
              if (!CodeInsightUtilBase.prepareFileForWrite(superMethod.getContainingFile())) return;
            }
          }

          try {
            processMethod(project, targetMethod, unhandledExceptions);

            if (processSuperMethods) {
              for (PsiMethod superMethod : superMethods) {
                processMethod(project, superMethod, unhandledExceptions);
              }
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  private static PsiMethod[] getSuperMethods(PsiMethod targetMethod) {
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    collectSuperMethods(targetMethod, result);
    return result.toArray(new PsiMethod[result.size()]);
  }

  private static void collectSuperMethods(PsiMethod method, List<PsiMethod> result) {
    PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      result.add(superMethod);
      collectSuperMethods(superMethod, result);
    }
  }

  private static boolean hasSuperMethodsWithoutExceptions(PsiMethod[] superMethods, Set<PsiClassType> unhandledExceptions) {
    for (PsiMethod superMethod : superMethods) {
      PsiClassType[] referencedTypes = superMethod.getThrowsList().getReferencedTypes();

      Set<PsiClassType> exceptions = new HashSet<PsiClassType>(unhandledExceptions);
      for (PsiClassType referencedType : referencedTypes) {
        for (PsiClassType exception : unhandledExceptions) {
          if (referencedType.isAssignableFrom(exception)) exceptions.remove(exception);
        }
      }

      if (!exceptions.isEmpty()) return true;
    }

    return false;
  }

  private static void processMethod(Project project, PsiMethod targetMethod, Set<PsiClassType> unhandledExceptions) throws IncorrectOperationException {
    for (PsiClassType unhandledException : unhandledExceptions) {
      PsiClass exceptionClass = unhandledException.resolve();
      if (exceptionClass != null) {
        PsiUtil.addException(targetMethod, exceptionClass);
      }
    }

    CodeStyleManager.getInstance(project).reformat(targetMethod.getThrowsList());
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (myWrongElement == null || !myWrongElement.isValid()) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class);
    if (method == null || !method.getThrowsList().isPhysical()) return false;
    PsiElement element = findElement(myWrongElement, method);
    if (element == null) return false;

    setText(QuickFixBundle.message("add.exception.to.throws.text"));
    return true;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.exception.to.throws.family");
  }

  @Nullable
  private static PsiElement findElement(PsiElement element, PsiMethod topElement) {
    if (element == null) return null;
    List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    if (!filterInProjectExceptions(topElement, unhandledExceptions).isEmpty()) {
      return element;
    }
    return findElement(element.getParent(), topElement);
  }

  private static Set<PsiClassType> filterInProjectExceptions(PsiMethod targetMethod, List<PsiClassType> unhandledExceptions) {
    if (targetMethod == null) return Collections.emptySet();

    Set<PsiClassType> result = new HashSet<PsiClassType>();

    if (!targetMethod.getManager().isInProject(targetMethod)) {
      PsiClassType[] referencedTypes = targetMethod.getThrowsList().getReferencedTypes();
      for (PsiClassType referencedType : referencedTypes) {
        PsiClass psiClass = referencedType.resolve();
        if (psiClass == null) continue;
        for (PsiClassType exception : unhandledExceptions) {
          if (referencedType.isAssignableFrom(exception)) result.add(exception);
        }
      }
    }
    else {
      PsiMethod[] superMethods = targetMethod.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        Set<PsiClassType> classTypes = filterInProjectExceptions(superMethod, unhandledExceptions);
        result.addAll(classTypes);
      }

      if (superMethods.length == 0) {
        result.addAll(unhandledExceptions);
      }
    }

    return result;
  }
}
