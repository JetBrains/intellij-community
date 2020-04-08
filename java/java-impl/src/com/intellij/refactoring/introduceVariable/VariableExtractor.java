// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Performs actual write action (see {@link #extractVariable()}) which introduces new variable and replaces all occurrences.
 * No user interaction is performed here.
 */
class VariableExtractor {
  private static final Logger LOG = Logger.getInstance(VariableExtractor.class);

  private final Project myProject;
  private final Editor myEditor;
  private final IntroduceVariableSettings mySettings;
  private final PsiExpression myExpression;
  private @NotNull PsiElement myAnchor;
  private final PsiElement myContainer;
  private final PsiExpression[] myOccurrences;
  private final boolean myReplaceSelf;
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
    myAnchor = correctAnchor(expression, anchorStatement, occurrences);
    myReplaceSelf = settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(expression);
    PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(myContainer, PsiCodeBlock.class, false);
    myFieldConflictsResolver = new FieldConflictsResolver(settings.getEnteredName(), newDeclarationScope);
    myPosition = editor != null ? editor.getCaretModel().getLogicalPosition() : null;
  }

  @NotNull
  private SmartPsiElementPointer<PsiVariable> extractVariable() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final PsiExpression newExpr = myFieldConflictsResolver.fixInitializer(myExpression);
    if (myAnchor == myExpression) {
      myAnchor = newExpr;
    }
    PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(newExpr);
    final SmartTypePointer selectedType = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(
      mySettings.getSelectedType());
    initializer = IntroduceVariableBase.simplifyVariableInitializer(initializer, selectedType.getType());
    CommentTracker commentTracker = new CommentTracker();
    commentTracker.markUnchanged(initializer);
    initializer = (PsiExpression)initializer.copy();

    PsiType type = stripNullabilityAnnotationsFromTargetType(selectedType, newExpr);
    PsiElement declaration = createDeclaration(type, mySettings.getEnteredName(), initializer);

    replaceOccurrences(newExpr);

    ensureCodeBlock();

    PsiVariable var = addVariable(declaration, initializer);

    if (myAnchor instanceof PsiExpressionStatement &&
        ExpressionUtils.isReferenceTo(((PsiExpressionStatement)myAnchor).getExpression(), var)) {
      commentTracker.deleteAndRestoreComments(myAnchor);
      if (myEditor != null) {
        myEditor.getCaretModel().moveToLogicalPosition(myPosition);
        myEditor.getCaretModel().moveToOffset(var.getTextRange().getEndOffset());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        myEditor.getSelectionModel().removeSelection();
      }
    }

    highlight(var);

    if (!(var instanceof PsiPatternVariable)) {
      PsiUtil.setModifierProperty(var, PsiModifier.FINAL, mySettings.isDeclareFinal());
      if (mySettings.isDeclareVarType()) {
        PsiTypeElement typeElement = var.getTypeElement();
        LOG.assertTrue(typeElement != null);
        IntroduceVariableBase.expandDiamondsAndReplaceExplicitTypeWithVar(typeElement, var);
      }
    }
    myFieldConflictsResolver.fix();
    return SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(var);
  }

  private void ensureCodeBlock() {
    if (myAnchor instanceof PsiStatement && RefactoringUtil.isLoopOrIf(myAnchor.getParent())) {
      myAnchor = BlockUtils.expandSingleStatementToBlockStatement((PsiStatement)myAnchor);
    }
    if (myAnchor instanceof PsiInstanceOfExpression && PsiUtil.skipParenthesizedExprDown(myExpression) instanceof PsiTypeCastExpression) {
      return;
    }
    if (myAnchor instanceof PsiExpression) {
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression((PsiExpression)myAnchor);
      if (surrounder == null) {
        throw new RuntimeExceptionWithAttachments(
          "Cannot ensure code block: myAnchor type is " + myAnchor.getClass() + "; parent type is " + myAnchor.getParent().getClass(),
          new Attachment("context.txt", myContainer.getText()));
      }
      CodeBlockSurrounder.SurroundResult result = surrounder.surround();
      myAnchor = result.getAnchor();
    }
  }

  private void highlight(PsiVariable var) {
    if (myEditor != null) {
      PsiElement[] occurrences =
        PsiTreeUtil.collectElements(myContainer, e -> e instanceof PsiReference && ((PsiReference)e).isReferenceTo(var));
      IntroduceVariableBase.highlightReplacedOccurrences(myProject, myEditor, occurrences);
    }
  }

  private void replaceOccurrences(PsiExpression newExpr) {
    assert myAnchor.isValid();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiExpression ref = elementFactory.createExpressionFromText(mySettings.getEnteredName(), null);
    boolean needReplaceSelf = myReplaceSelf;
    if (mySettings.isReplaceAllOccurrences()) {
      for (PsiExpression occurrence : myOccurrences) {
        PsiExpression correctedOccurrence = occurrence.equals(myExpression) ? newExpr : occurrence;
        correctedOccurrence = RefactoringUtil.outermostParenthesizedExpression(correctedOccurrence);
        if (mySettings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(correctedOccurrence)) {
          PsiElement replacement = IntroduceVariableBase.replace(correctedOccurrence, ref, myProject);
          if (!myAnchor.isValid()) {
            myAnchor = replacement;
          }
        }
      }

      needReplaceSelf &= newExpr instanceof PsiPolyadicExpression && newExpr.isValid() && !newExpr.isPhysical();
    }
    if (needReplaceSelf) {
      PsiElement replacement = IntroduceVariableBase.replace(newExpr, ref, myProject);
      if (!myAnchor.isValid()) {
        myAnchor = replacement;
      }
    }
  }

  private PsiVariable addVariable(PsiElement declaration, PsiExpression initializer) {
    declaration = addDeclaration(declaration, initializer, myAnchor);
    declaration = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(declaration);
    return (PsiVariable)(declaration instanceof PsiDeclarationStatement
                         ? ((PsiDeclarationStatement)declaration).getDeclaredElements()[0]
                         : declaration);
  }

  @NotNull
  private PsiElement createDeclaration(@NotNull PsiType type, @NotNull String name, PsiExpression initializer) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    if (myAnchor instanceof PsiInstanceOfExpression && initializer instanceof PsiTypeCastExpression) {
      PsiTypeElement castType = Objects.requireNonNull(((PsiTypeCastExpression)initializer).getCastType());
      return elementFactory.createExpressionFromText(
        ((PsiInstanceOfExpression)myAnchor).getOperand().getText() + " instanceof " + castType.getText() + " " + name, myContainer);
    }
    if (myContainer instanceof PsiClass) {
      PsiField declaration = elementFactory.createField(name, type);
      declaration.setInitializer(initializer);
      return declaration;
    }
    return elementFactory.createVariableDeclarationStatement(name, type, initializer, myContainer);
  }

  private static PsiElement addDeclaration(PsiElement declaration, PsiExpression initializer, @NotNull PsiElement anchor) {
    if (anchor instanceof PsiDeclarationStatement) {
      final PsiElement[] declaredElements = ((PsiDeclarationStatement)anchor).getDeclaredElements();
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
          return anchor.getParent().addAfter(declaration, parDeclarationStatement);
        }
      }
    }
    if (anchor instanceof PsiInstanceOfExpression && declaration instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression newInstanceOf = (PsiInstanceOfExpression)anchor.replace(declaration);
      return ((PsiTypeTestPattern)Objects.requireNonNull(newInstanceOf.getPattern())).getPatternVariable();
    }
    if (anchor instanceof PsiResourceListElement) {
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)declaration;
      PsiLocalVariable localVariable = (PsiLocalVariable)declarationStatement.getDeclaredElements()[0];
      PsiResourceVariable resourceVariable = JavaPsiFacade.getElementFactory(anchor.getProject())
        .createResourceVariable(localVariable.getName(), localVariable.getType(), initializer, anchor);
      return anchor.replace(resourceVariable);
    }
    PsiElement parent = anchor.getParent();
    if (parent == null) {
      throw new IllegalStateException("Unexpectedly anchor has no parent. Anchor class: " + anchor.getClass());
    }
    return parent.addBefore(declaration, anchor);
  }

  @NotNull
  private static PsiType stripNullabilityAnnotationsFromTargetType(SmartTypePointer selectedType, final PsiExpression expression) {
    PsiType type = selectedType.getType();
    if (type == null) {
      throw new IncorrectOperationException("Unexpected empty type pointer");
    }

    PsiDeclarationStatement probe = JavaPsiFacade.getElementFactory(expression.getProject())
      .createVariableDeclarationStatement("x", TypeUtils.getObjectType(expression), null, expression);
    Project project = expression.getProject();
    NullabilityAnnotationInfo nullabilityAnnotationInfo = 
      NullableNotNullManager.getInstance(project).findExplicitNullability((PsiLocalVariable)probe.getDeclaredElements()[0]);

    final PsiAnnotation[] annotations = type.getAnnotations();
    return type.annotate(new TypeAnnotationProvider() {
      @Override
      public PsiAnnotation @NotNull [] getAnnotations() {
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        final Set<String> nullables = new HashSet<>();
        Nullability nullability = nullabilityAnnotationInfo != null ? nullabilityAnnotationInfo.getNullability() : Nullability.UNKNOWN;
        if (nullability == Nullability.UNKNOWN) {
          nullables.addAll(manager.getNotNulls());
          nullables.addAll(manager.getNullables());
        }
        else if (nullability == Nullability.NOT_NULL) {
          nullables.addAll(manager.getNotNulls());
        }
        else if (nullability == Nullability.NULLABLE){
          nullables.addAll(manager.getNullables());
        }
        return Arrays.stream(annotations)
          .filter(annotation -> !nullables.contains(annotation.getQualifiedName()))
          .toArray(PsiAnnotation[]::new);
      }
    });
  }

  @NotNull
  private static PsiElement correctAnchor(PsiExpression expr,
                                          @NotNull PsiElement anchor,
                                          PsiExpression[] occurrences) {
    if (!expr.isPhysical()) {
      expr = ObjectUtils.tryCast(expr.getUserData(ElementToWorkOn.PARENT), PsiExpression.class);
      if (expr == null) return anchor;
    }
    if (anchor instanceof PsiSwitchLabelStatementBase) {
      PsiSwitchBlock block = ((PsiSwitchLabelStatementBase)anchor).getEnclosingSwitchBlock();
      if (block == null) return anchor;
      anchor = block;
      if (anchor instanceof PsiExpression) {
        expr = (PsiExpression)anchor;
      }
    }
    Set<PsiExpression> allOccurrences = StreamEx.of(occurrences).filter(PsiElement::isPhysical).append(expr).toSet();
    PsiExpression firstOccurrence = Collections.min(allOccurrences, Comparator.comparing(e -> e.getTextRange().getStartOffset()));
    if (HighlightingFeature.PATTERNS.isAvailable(anchor)) {
      PsiTypeCastExpression cast = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(firstOccurrence), PsiTypeCastExpression.class);
      if (cast != null && !(cast.getType() instanceof PsiPrimitiveType) &&
          !(PsiUtil.skipParenthesizedExprUp(firstOccurrence.getParent()) instanceof PsiExpressionStatement)) {
        PsiInstanceOfExpression candidate = InstanceOfUtils.findPatternCandidate(cast);
        if (candidate != null && allOccurrences.stream()
          .map(occ -> ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(occ), PsiTypeCastExpression.class))
          .allMatch(occ -> occ != null && (occ == firstOccurrence || InstanceOfUtils.findPatternCandidate(occ) == candidate))) {
          return candidate;
        }
      }
    }
    if (anchor instanceof PsiWhileStatement) {
      PsiWhileStatement whileStatement = (PsiWhileStatement)anchor;
      PsiExpression condition = whileStatement.getCondition();
      if (condition != null && allOccurrences.stream().allMatch(occurrence -> PsiTreeUtil.isAncestor(whileStatement, occurrence, true))) {
        if (firstOccurrence != null && PsiTreeUtil.isAncestor(condition, firstOccurrence, false)) {
          PsiPolyadicExpression polyadic = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(condition), PsiPolyadicExpression.class);
          if (polyadic != null && JavaTokenType.ANDAND.equals(polyadic.getOperationTokenType())) {
            PsiExpression operand = ContainerUtil.find(polyadic.getOperands(), op -> PsiTreeUtil.isAncestor(op, firstOccurrence, false));
            operand = PsiUtil.skipParenthesizedExprDown(operand);
            LOG.assertTrue(operand != null);
            return operand;
          }
          return condition;
        }
      }
    }
    if (firstOccurrence != null && CodeBlockSurrounder.canSurround(firstOccurrence) && 
        !PsiUtil.isAccessedForWriting(firstOccurrence)) {
      PsiExpression ancestorCandidate = ExpressionUtils.getTopLevelExpression(firstOccurrence);
      if (PsiTreeUtil.isAncestor(anchor, ancestorCandidate, false)) {
        PsiElement statement = RefactoringUtil.getParentStatement(ancestorCandidate, false);
        PsiElement extractable = statement == null ? PsiTreeUtil.getParentOfType(ancestorCandidate, PsiField.class) : statement;
        if (allOccurrences.stream().allMatch(occurrence ->
                                               PsiTreeUtil.isAncestor(extractable, occurrence, false) &&
                                               (!PsiTreeUtil.isAncestor(ancestorCandidate, occurrence, false) ||
                                                ReorderingUtils.canExtract(ancestorCandidate, occurrence) == ThreeState.NO))) {
          return firstOccurrence;
        }
      }
    }
    if (anchor instanceof PsiTryStatement && firstOccurrence != null) {
      PsiResourceList resourceList = ((PsiTryStatement)anchor).getResourceList();
      PsiElement parent = firstOccurrence.getParent();
      if (resourceList != null && parent instanceof PsiResourceExpression && parent.getParent() == resourceList
          && InheritanceUtil.isInheritor(firstOccurrence.getType(), CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE)) {
        return parent;
      }
    }
    if (anchor.getParent() instanceof PsiSwitchLabeledRuleStatement) {
      return ExpressionUtils.getTopLevelExpression(expr);
    }
    if (RefactoringUtil.isLoopOrIf(anchor.getParent())) return anchor;
    PsiElement child = locateAnchor(anchor);
    if (IntroduceVariableBase.isFinalVariableOnLHS(expr)) {
      child = child.getNextSibling();
    }
    return child == null ? anchor : child;
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

  @Nullable
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
