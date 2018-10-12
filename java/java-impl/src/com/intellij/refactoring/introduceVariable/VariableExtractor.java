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
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

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
  private PsiElement myAnchor;
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
    myOccurrences = occurrences;
    mySettings = settings;
    myContainer = anchorStatement.getParent();
    myIsInsideLoop = RefactoringUtil.isLoopOrIf(myContainer);
    myAnchor = correctAnchor(expression, anchorStatement, myIsInsideLoop);
    myReplaceSelf = settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(expression);
    PsiElement expressionParent = expression.getParent();
    myReplaceLoop = myIsInsideLoop ? expressionParent instanceof PsiExpressionStatement
                                   : myContainer instanceof PsiLambdaExpression && expressionParent == myContainer;
    PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(myContainer, PsiCodeBlock.class, false);
    myFieldConflictsResolver = new FieldConflictsResolver(settings.getEnteredName(), newDeclarationScope);

    myDeleteSelf = shouldDeleteSelf(anchorStatement, expressionParent);
    myPosition = editor != null ? editor.getCaretModel().getLogicalPosition() : null;
    if (myDeleteSelf) {
      if (editor != null) {
        LogicalPosition pos = new LogicalPosition(myPosition.line, myPosition.column);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
    }
  }

  private boolean shouldDeleteSelf(PsiElement origAnchor, PsiElement expressionParent) {
    if (!myIsInsideLoop && myReplaceSelf && expressionParent instanceof PsiExpressionStatement && myAnchor.equals(origAnchor)) {
      PsiElement parent = expressionParent.getParent();
      return parent instanceof PsiCodeBlock ||
             //fabrique
             parent instanceof PsiCodeFragment;
    }
    return false;
  }

  private SmartPsiElementPointer<PsiVariable> extractVariable() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    try {
      PsiElement statement = myExpression.getParent();

      final PsiExpression newExpr = myFieldConflictsResolver.fixInitializer(myExpression);
      PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(newExpr);
      final SmartTypePointer selectedType = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(
        mySettings.getSelectedType());
      initializer = IntroduceVariableBase.simplifyVariableInitializer(initializer, selectedType.getType());
      CommentTracker commentTracker = new CommentTracker();
      commentTracker.markUnchanged(initializer);
      initializer = (PsiExpression)initializer.copy();

      PsiType type = stripNullabilityAnnotationsFromTargetType(selectedType, myProject);
      replaceOccurrences(newExpr);

      PsiElement declaration = createDeclaration(type, mySettings.getEnteredName(), initializer);
      if (!myIsInsideLoop) {
        declaration = addDeclaration(declaration, initializer);
        if (myDeleteSelf) {
          commentTracker.deleteAndRestoreComments(statement);
          if (myEditor != null) {
            LogicalPosition pos = new LogicalPosition(myPosition.line, myPosition.column);
            myEditor.getCaretModel().moveToLogicalPosition(pos);
            myEditor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            myEditor.getSelectionModel().removeSelection();
          }
        }
      }

      PsiVariable var = (PsiVariable)(declaration instanceof PsiDeclarationStatement
                                      ? ((PsiDeclarationStatement)declaration).getDeclaredElements()[0]
                                      : declaration);
      highlight(var);

      if (declaration instanceof PsiDeclarationStatement) {
        declaration = RefactoringUtil.putStatementInLoopBody((PsiStatement)declaration, myContainer, myAnchor,
                                                             myReplaceSelf && myReplaceLoop);
      }
      declaration = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(declaration);
      var = (PsiVariable)(declaration instanceof PsiDeclarationStatement
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

  private void highlight(PsiVariable var) {
    if (myEditor != null) {
      PsiElement[] occurrences =
        PsiTreeUtil.collectElements(myContainer, e -> e instanceof PsiReference && ((PsiReference)e).isReferenceTo(var));
      IntroduceVariableBase.highlightReplacedOccurrences(myProject, myEditor, occurrences);
    }
  }

  private void replaceOccurrences(PsiExpression newExpr) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiExpression ref = elementFactory.createExpressionFromText(mySettings.getEnteredName(), null);
    boolean needReplaceSelf = !myDeleteSelf && myReplaceSelf;
    if (mySettings.isReplaceAllOccurrences()) {
      for (PsiExpression occurrence : myOccurrences) {
        if (myDeleteSelf && occurrence.equals(myExpression)) continue;
        PsiExpression correctedOccurrence = occurrence.equals(myExpression) ? newExpr : occurrence;
        correctedOccurrence = RefactoringUtil.outermostParenthesizedExpression(correctedOccurrence);
        if (mySettings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(correctedOccurrence)) {
          PsiElement replacement = IntroduceVariableBase.replace(correctedOccurrence, ref, myProject);
          if (occurrence.equals(myAnchor)) {
            myAnchor = replacement;
          }
        }
      }

      needReplaceSelf &= newExpr instanceof PsiPolyadicExpression && newExpr.isValid() && !newExpr.isPhysical();
    }
    if (needReplaceSelf) {
      PsiElement replacement = IntroduceVariableBase.replace(newExpr, ref, myProject);
      if (newExpr.equals(myAnchor)) {
        myAnchor = replacement;
      }
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
