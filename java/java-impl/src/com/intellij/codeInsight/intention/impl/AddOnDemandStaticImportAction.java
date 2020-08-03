/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author ven
 */
public class AddOnDemandStaticImportAction extends BaseElementAtCaretIntentionAction {
  private static final Logger LOG = Logger.getInstance(AddOnDemandStaticImportAction.class);

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.add.on.demand.static.import.family");
  }

  /**
   * Allows to check if static import may be performed for the given element.
   *
   * @param element     element to check
   * @return            target class that may be statically imported if any; {@code null} otherwise
   */
  @Nullable
  public static PsiClass getClassToPerformStaticImport(@NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
    if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiJavaCodeReferenceElement)) {
      return null;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiErrorElement.class, PsiImportStatementBase.class) != null) return null;
    PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    if (refExpr instanceof  PsiMethodReferenceExpression) return null;
    final PsiElement gParent = refExpr.getParent();
    if (gParent instanceof PsiMethodReferenceExpression) return null;
    if (!(gParent instanceof PsiJavaCodeReferenceElement) ||
        isParameterizedReference((PsiJavaCodeReferenceElement)gParent)) return null;

    PsiElement resolved = refExpr.resolve();
    if (!(resolved instanceof PsiClass)) {
      return null;
    }
    PsiClass psiClass = (PsiClass)resolved;
    if (PsiUtil.isFromDefaultPackage(psiClass) ||
        psiClass.hasModifierProperty(PsiModifier.PRIVATE) ||
        psiClass.getQualifiedName() == null) return null;

    final PsiElement ggParent = gParent.getParent();
    if (ggParent instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)ggParent.copy();
      final PsiElement qualifier = call.getMethodExpression().getQualifier();
      if (qualifier == null) return null;
      qualifier.delete();
      final PsiMethod method = call.resolveMethod();
      if (method != null && method.getContainingClass() != psiClass)  return null;
    }
    else {
      PsiElement refNameElement = ((PsiJavaCodeReferenceElement)gParent).getReferenceNameElement();
      if (refNameElement == null) return null;
      final PsiJavaCodeReferenceElement copy = JavaPsiFacade.getElementFactory(refNameElement.getProject())
        .createReferenceFromText(refNameElement.getText(), refExpr);
      final PsiElement target = copy.resolve();
      if (target != null) {
        PsiClass parentClass = PsiTreeUtil.getParentOfType(target, PsiClass.class);
        if (parentClass != psiClass) {
          if (parentClass == null || parentClass.isPhysical()) {
            return null;
          }
          // In preview mode we could resolve to real class instead of non-physical one
          String qualifiedName = parentClass.getQualifiedName();
          if (qualifiedName == null || !qualifiedName.equals(psiClass.getQualifiedName())) {
            return null;
          }
        }
      }
      PsiElement resolve = ((PsiJavaCodeReferenceElement)gParent).resolve();
      if (resolve instanceof PsiMember && !((PsiMember)resolve).hasModifierProperty(PsiModifier.STATIC)) return null;
    }

    PsiFile file = refExpr.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    if (importList == null) return null;

    return psiClass;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiClass classToImport = getClassToPerformStaticImport(element);
    if (classToImport != null) {
      String text = JavaBundle.message("intention.add.on.demand.static.import.text", classToImport.getQualifiedName());
      setText(text);
    }
    return classToImport != null;
  }

  public static boolean invoke(final Project project, PsiFile file, final Editor editor, PsiElement element) {
    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    final PsiClass aClass = (PsiClass)refExpr.resolve();
    if (aClass == null) {
      return false;
    }
    final PsiClass containingClass = PsiUtil.getTopLevelClass(refExpr);
    if (aClass != containingClass || !ImportUtils.isInsideClassBody(element, aClass)) {
      PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList == null) {
        return false;
      }
      boolean alreadyImported = false;
      for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
        if (!statement.isOnDemand()) continue;
        PsiClass staticResolve = statement.resolveTargetClass();
        if (aClass == staticResolve) {
          alreadyImported = true;
          break;
        }
      }
      if (!alreadyImported) {
        PsiImportStaticStatement importStaticStatement =
          JavaPsiFacade.getElementFactory(file.getProject()).createImportStaticStatement(aClass, "*");
        importList.add(importStaticStatement);
      }
    }

    Ref<Boolean> conflict = new Ref<>(false);
    List<PsiFile> roots = file.getViewProvider().getAllFiles();
    for (final PsiFile root : roots) {
      PsiElement copy = root.copy();
      final PsiManager manager = root.getManager();

      List<PsiJavaCodeReferenceElement> expressionsToDequalify = new ArrayList<>();

      copy.accept(new JavaRecursiveElementWalkingVisitor() {
        int delta;
        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement expression) {
          if (isParameterizedReference(expression) ||
              expression instanceof PsiMethodReferenceExpression ||
              expression.getParent() instanceof PsiErrorElement) {
            super.visitElement(expression);
            return;
          }
          PsiElement qualifierExpression = expression.getQualifier();
          if (qualifierExpression instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifierExpression).isReferenceTo(aClass)) {
            try {
              PsiElement resolved = expression.resolve();
              int end = expression.getTextRange().getEndOffset();
              qualifierExpression.delete();
              delta += end - expression.getTextRange().getEndOffset();
              PsiElement after = expression.resolve();
              if (manager.areElementsEquivalent(after, resolved)) {
                int offset = expression.getTextRange().getStartOffset() + delta;
                PsiJavaCodeReferenceElement originalExpression =
                  PsiTreeUtil.findElementOfClassAtOffset(root, offset, PsiJavaCodeReferenceElement.class, false);
                if (originalExpression != null) {
                  PsiElement originalQualifier = originalExpression.getQualifier();
                  if (originalQualifier instanceof PsiJavaCodeReferenceElement &&
                      ((PsiJavaCodeReferenceElement)originalQualifier).isReferenceTo(aClass)) {
                    expressionsToDequalify.add(originalExpression);
                  }
                }
              }
              else {
                conflict.set(true);
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
          super.visitElement(expression);
        }
      });

      for (PsiJavaCodeReferenceElement expression : expressionsToDequalify) {
        new CommentTracker().deleteAndRestoreComments(Objects.requireNonNull(expression.getQualifier()));
      }
      if (editor != null && !(editor instanceof ImaginaryEditor)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (collectChangedPlaces(project, editor, expressionsToDequalify)) {
            WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
          }
        }, project.getDisposed());
      }
    }
    return conflict.get();
  }

  private static boolean collectChangedPlaces(Project project, Editor editor, List<PsiJavaCodeReferenceElement> expressionsToDequalify) {
    boolean found = false;
    for (PsiJavaCodeReferenceElement expression : expressionsToDequalify) {
      if (!expression.isValid()) continue;
      found = true;
      HighlightManager.getInstance(project).addRangeHighlight(
        editor, expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset(),
        EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
    }
    return found;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    invoke(project, element.getContainingFile(), editor, element);
  }

  private static boolean isParameterizedReference(final PsiJavaCodeReferenceElement expression) {
    PsiReferenceParameterList parameterList = expression.getParameterList();
    return parameterList != null && parameterList.getFirstChild() != null;
  }
}
