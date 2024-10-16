// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AddOnDemandStaticImportAction extends PsiUpdateModCommandAction<PsiIdentifier> {
  private static final Logger LOG = Logger.getInstance(AddOnDemandStaticImportAction.class);
  
  public AddOnDemandStaticImportAction() {
    super(PsiIdentifier.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.add.on.demand.static.import.family");
  }

  /**
   * Allows to check if static import may be performed for the given element.
   *
   * @param element     element to check
   * @return            target class that may be statically imported if any; {@code null} otherwise
   */
  public static @Nullable PsiClass getClassToPerformStaticImport(@NotNull PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.STATIC_IMPORTS, element)) return null;
    if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiJavaCodeReferenceElement refExpr)) {
      return null;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiErrorElement.class, PsiImportStatementBase.class) != null) return null;
    if (refExpr instanceof PsiMethodReferenceExpression) return null;
    final PsiElement gParent = refExpr.getParent();
    if (gParent instanceof PsiMethodReferenceExpression) return null;
    if (!(gParent instanceof PsiJavaCodeReferenceElement parentRef)) return null;
    if (isParameterizedReference(parentRef)) return null;

    if (PsiUtilCore.getElementType(PsiTreeUtil.nextCodeLeaf(gParent)) == JavaTokenType.ARROW &&
        !(gParent.getParent() instanceof PsiCaseLabelElementList)) {
        return null;
    }

    PsiElement resolved = refExpr.resolve();
    if (!(resolved instanceof PsiClass psiClass)) {
      return null;
    }
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
      PsiElement refNameElement = parentRef.getReferenceNameElement();
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
      if (parentRef.resolve() instanceof PsiMember member && !member.hasModifierProperty(PsiModifier.STATIC)) return null;
    }

    if (refExpr.getContainingFile() instanceof PsiJavaFile javaFile && javaFile.getImportList() != null) {
      return psiClass;
    }
    return null;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiIdentifier element) {
    PsiClass classToImport = getClassToPerformStaticImport(element);
    if (classToImport != null) {
      return Presentation.of(JavaBundle.message("intention.add.on.demand.static.import.text", classToImport.getQualifiedName()));
    }
    return null;
  }

  public static boolean invoke(final Project project, PsiFile file, final Editor editor, @NotNull PsiElement element) {
    List<PsiJavaCodeReferenceElement> dequalifiedElements = new ArrayList<>();
    boolean conflicts = addStaticImports(file, element, dequalifiedElements);
    if (editor != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (collectChangedPlaces(project, editor, dequalifiedElements)) {
          WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        }
      }, project.getDisposed());
    }
    return conflicts;
  }

  private static boolean addStaticImports(@NotNull PsiFile file,
                                          @NotNull PsiElement element,
                                          @NotNull List<@NotNull PsiJavaCodeReferenceElement> dequalifiedElements) {
    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    final PsiClass aClass = (PsiClass)refExpr.resolve();
    if (aClass == null) {
      return false;
    }
    final PsiClass containingClass = PsiUtil.getTopLevelClass(refExpr);
    if (aClass != containingClass || !ClassUtils.isInsideClassBody(element, aClass)) {
      PsiJavaFile psiJavaFile = (PsiJavaFile)file;
      PsiImportList importList = psiJavaFile.getImportList();
      if (importList == null) {
        return false;
      }
      boolean alreadyImported = false;
      String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName != null && ImportUtils.createImplicitImportChecker(psiJavaFile).isImplicitlyImported(qualifiedName + ".*", true)) {
        alreadyImported = true;
      }
      if (!alreadyImported) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          if (!statement.isOnDemand()) continue;
          PsiClass staticResolve = statement.resolveTargetClass();
          if (aClass == staticResolve) {
            alreadyImported = true;
            break;
          }
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
        public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement expression) {
          PsiElement qualifierExpression;
          if (!isParameterizedReference(expression) &&
              !(expression instanceof PsiMethodReferenceExpression) &&
              !(expression.getParent() instanceof PsiErrorElement) &&
              (qualifierExpression = expression.getQualifier()) instanceof PsiJavaCodeReferenceElement &&
              ((PsiJavaCodeReferenceElement)qualifierExpression).isReferenceTo(aClass)) {
            try {
              JavaResolveResult[] resolved = expression.multiResolve(false);
              int end = expression.getTextRange().getEndOffset();
              qualifierExpression.delete();
              PsiElement firstChild = expression.getFirstChild();
              if (firstChild instanceof PsiJavaToken && ((PsiJavaToken)firstChild).getTokenType() == JavaTokenType.DOT) {
                firstChild.delete();
              }
              delta += end - expression.getTextRange().getEndOffset();
              JavaResolveResult[] resolvedAfter = expression.multiResolve(false);
              if (resolvesToSame(manager, resolved, resolvedAfter)) {
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
          super.visitReferenceElement(expression);
        }
      });

      for (PsiJavaCodeReferenceElement expression : expressionsToDequalify) {
        new CommentTracker().deleteAndRestoreComments(Objects.requireNonNull(expression.getQualifier()));
      }
      dequalifiedElements.addAll(expressionsToDequalify);
    }
    return conflict.get();
  }

  private static boolean resolvesToSame(@NotNull PsiManager manager, JavaResolveResult @NotNull [] resolved, JavaResolveResult @NotNull [] resolvedAfter) {
    // returns true if there's at least one element from "resolved" which is the same as one of "resolvedAfter"
    for (JavaResolveResult result : resolved) {
      if (!result.isAccessible()) continue;
      for (JavaResolveResult resultAfter : resolvedAfter) {
        if (resultAfter.isAccessible() && manager.areElementsEquivalent(result.getElement(), resultAfter.getElement())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean collectChangedPlaces(Project project, Editor editor, @NotNull List<? extends PsiJavaCodeReferenceElement> expressionsToDequalify) {
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
  protected void invoke(@NotNull ActionContext context, @NotNull PsiIdentifier element, @NotNull ModPsiUpdater updater) {
    List<PsiJavaCodeReferenceElement> dequalifiedElements = new ArrayList<>();
    addStaticImports(element.getContainingFile(), element, dequalifiedElements);
    for (PsiJavaCodeReferenceElement ref : dequalifiedElements) {
      updater.highlight(ref);
    }
  }

  private static boolean isParameterizedReference(final PsiJavaCodeReferenceElement expression) {
    PsiReferenceParameterList parameterList = expression.getParameterList();
    return parameterList != null && parameterList.getFirstChild() != null;
  }
}
