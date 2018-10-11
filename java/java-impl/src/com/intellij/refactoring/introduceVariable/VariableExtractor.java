// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Performs actual write action (see {@link #extractVariable()}) which introduces new variable and replaces all occurrences.
 * No user interaction is performed here.
 */
class VariableExtractor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceVariable.VariableExtractor");

  private final Project myProject;
  private final Editor myEditor;
  private final IntroduceVariableSettings mySettings;
  private final PsiExpression myExpression;
  private final PsiElement myExpressionParent;
  private final PsiElement myAnchor;
  private final PsiElement myAnchorStatement;
  private final PsiElement myContainer;
  private final PsiExpression[] myOccurrences;
  private final boolean myIsInsideLoop;
  private final boolean myReplaceSelf;
  private final boolean myDeleteSelf;
  private final boolean myReplaceLoop;
  private final FieldConflictsResolver myFieldConflictsResolver;
  private final LogicalPosition myPosition;

  private VariableExtractor(final Project project,
                            final PsiExpression expression,
                            final Editor editor,
                            final PsiElement anchorStatement,
                            final PsiExpression[] occurrences,
                            final IntroduceVariableSettings settings) {
    myProject = project;
    myExpression = expression;
    myEditor = editor;
    myAnchorStatement = anchorStatement;
    myOccurrences = occurrences;
    mySettings = settings;
    myContainer = anchorStatement.getParent();
    myIsInsideLoop = RefactoringUtil.isLoopOrIf(myContainer);
    myAnchor = correctAnchor(expression, anchorStatement, myIsInsideLoop);
    myReplaceSelf = settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(expression);
    myExpressionParent = expression.getParent();
    myReplaceLoop = myIsInsideLoop ? myExpressionParent instanceof PsiExpressionStatement
                                   : myContainer instanceof PsiLambdaExpression && myExpressionParent == myContainer;
    PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(myContainer, PsiCodeBlock.class, false);
    myFieldConflictsResolver = new FieldConflictsResolver(settings.getEnteredName(), newDeclarationScope);

    myDeleteSelf = shouldDeleteSelf();
    myPosition = editor != null ? editor.getCaretModel().getLogicalPosition() : null;
    if (myDeleteSelf) {
      if (editor != null) {
        LogicalPosition pos = new LogicalPosition(myPosition.line, myPosition.column);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
    }
  }

  private boolean shouldDeleteSelf() {
    if (!myIsInsideLoop && myReplaceSelf && myExpressionParent instanceof PsiExpressionStatement && myAnchor.equals(myAnchorStatement)) {
      PsiElement parent = myExpressionParent.getParent();
      return parent instanceof PsiCodeBlock ||
             //fabrique
             parent instanceof PsiCodeFragment;
    }
    return false;
  }

  private SmartPsiElementPointer<PsiVariable> extractVariable() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    try {
      final PsiExpression newExpr = myFieldConflictsResolver.fixInitializer(myExpression);
      PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(newExpr);
      final SmartTypePointer selectedType = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(
        mySettings.getSelectedType());
      initializer = IntroduceVariableBase.simplifyVariableInitializer(initializer, selectedType.getType());

      PsiType type = stripNullabilityAnnotationsFromTargetType(selectedType, myProject);
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
      PsiElement declaration = createDeclaration(type, mySettings.getEnteredName(), initializer);
      if (!myIsInsideLoop) {
        declaration = addDeclaration(declaration, initializer);
        LOG.assertTrue(newExpr.isValid());
        if (myDeleteSelf) {
          CommentTracker commentTracker = new CommentTracker();
          commentTracker.markUnchanged(initializer);
          commentTracker.deleteAndRestoreComments(myExpressionParent);
          if (myEditor != null) {
            LogicalPosition pos = new LogicalPosition(myPosition.line, myPosition.column);
            myEditor.getCaretModel().moveToLogicalPosition(pos);
            myEditor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            myEditor.getSelectionModel().removeSelection();
          }
        }
      }

      PsiExpression ref = elementFactory.createExpressionFromText(mySettings.getEnteredName(), null);
      if (mySettings.isReplaceAllOccurrences()) {
        replaceOccurrences(ref, myOccurrences, myExpression, newExpr);
      }
      else if (!myDeleteSelf && myReplaceSelf) {
        IntroduceVariableBase.replace(newExpr, ref, myProject);
      }

      if (declaration instanceof PsiDeclarationStatement) {
        declaration = RefactoringUtil.putStatementInLoopBody((PsiStatement)declaration, myContainer, myAnchorStatement, myReplaceSelf &&
                                                                                                                        myReplaceLoop);
      }
      declaration = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(declaration);
      PsiVariable var = (PsiVariable)(declaration instanceof PsiDeclarationStatement
                                      ? ((PsiDeclarationStatement)declaration).getDeclaredElements()[0]
                                      : declaration);
      PsiUtil.setModifierProperty(var, PsiModifier.FINAL, mySettings.isDeclareFinal());
      myFieldConflictsResolver.fix();
      return SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(var);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  private void replaceOccurrences(@NotNull PsiExpression ref,
                                  @NotNull PsiExpression[] occurrences,
                                  @NotNull PsiExpression origExpr,
                                  @NotNull PsiExpression newExpr) {
    ArrayList<PsiElement> array = new ArrayList<>();
    for (PsiExpression occurrence : occurrences) {
      if (myDeleteSelf && occurrence.equals(origExpr)) continue;
      if (occurrence.equals(origExpr)) {
        occurrence = newExpr;
      }
      occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
      if (mySettings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(occurrence)) {
        array.add(IntroduceVariableBase.replace(occurrence, ref, myProject));
      }
    }

    if (!myDeleteSelf && myReplaceSelf && newExpr instanceof PsiPolyadicExpression && newExpr.isValid() && !newExpr.isPhysical()) {
      array.add(IntroduceVariableBase.replace(newExpr, ref, myProject));
    }

    if (myEditor != null) {
      final PsiElement[] replacedOccurrences = PsiUtilCore.toPsiElementArray(array);
      IntroduceVariableBase.highlightReplacedOccurrences(myProject, myEditor, replacedOccurrences);
    }
  }

  @NotNull
  private PsiElement createDeclaration(@NotNull PsiType type, @NotNull String name, PsiExpression initializer) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    if (myContainer instanceof PsiClass) {
      PsiField declaration = elementFactory.createField(name, type);
      declaration.setInitializer(initializer);
      return declaration;
    }
    return elementFactory.createVariableDeclarationStatement(name, type, initializer, myContainer);
  }

  private PsiElement addDeclaration(PsiElement declaration, PsiExpression initializer) {
    if (myAnchor instanceof PsiDeclarationStatement) {
      final PsiElement[] declaredElements = ((PsiDeclarationStatement)myAnchor).getDeclaredElements();
      if (declaredElements.length > 1) {
        final int[] usedFirstVar = new int[]{-1};
        initializer.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            final int i = ArrayUtilRt.find(declaredElements, expression.resolve());
            if (i > -1) {
              usedFirstVar[0] = Math.max(i, usedFirstVar[0]);
            }
            super.visitReferenceExpression(expression);
          }
        });
        if (usedFirstVar[0] > -1) {
          final PsiVariable psiVariable = (PsiVariable)declaredElements[usedFirstVar[0]];
          psiVariable.normalizeDeclaration();
          final PsiDeclarationStatement parDeclarationStatement = PsiTreeUtil.getParentOfType(psiVariable, PsiDeclarationStatement.class);
          return myContainer.addAfter(declaration, parDeclarationStatement);
        }
      }
    }
    return myContainer.addBefore(declaration, myAnchor);
  }

  @NotNull
  private static PsiType stripNullabilityAnnotationsFromTargetType(SmartTypePointer selectedType, final Project project) {
    PsiType type = selectedType.getType();
    if (type == null) {
      throw new IncorrectOperationException("Unexpected empty type pointer");
    }
    final PsiAnnotation[] annotations = type.getAnnotations();
    return type.annotate(new TypeAnnotationProvider() {
      @NotNull
      @Override
      public PsiAnnotation[] getAnnotations() {
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        final Set<String> nullables = new HashSet<>();
        nullables.addAll(manager.getNotNulls());
        nullables.addAll(manager.getNullables());
        return Arrays.stream(annotations)
          .filter(annotation -> !nullables.contains(annotation.getQualifiedName()))
          .toArray(PsiAnnotation[]::new);
      }
    });
  }

  @NotNull
  private static PsiElement correctAnchor(PsiExpression expr, @NotNull PsiElement anchorStatement, boolean isInsideLoop) {
    PsiElement child = anchorStatement;
    if (!isInsideLoop) {
      child = locateAnchor(child);
      if (IntroduceVariableBase.isFinalVariableOnLHS(expr)) {
        child = child.getNextSibling();
      }
    }
    return child == null ? anchorStatement : child;
  }

  private static PsiElement locateAnchor(PsiElement child) {
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (prev instanceof PsiStatement) break;
      if (PsiUtil.isJavaToken(prev, JavaTokenType.LBRACE)) break;
      child = prev;
    }

    while (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
      child = child.getNextSibling();
    }
    return child;
  }

  public static PsiVariable introduce(final Project project,
                                      final PsiExpression expr,
                                      final Editor editor,
                                      final PsiElement anchorStatement,
                                      final PsiExpression[] occurrences,
                                      final IntroduceVariableSettings settings) {
    Computable<SmartPsiElementPointer<PsiVariable>> computation =
      new VariableExtractor(project, expr, editor, anchorStatement, occurrences, settings)::extractVariable;
    SmartPsiElementPointer<PsiVariable> pointer = ApplicationManager.getApplication().runWriteAction(computation);
    return pointer != null ? pointer.getElement() : null;
  }
}
