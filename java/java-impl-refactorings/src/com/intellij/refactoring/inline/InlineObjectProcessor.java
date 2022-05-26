// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Performs inlining of object construction together with a subsequent call.
 * E.g. {@code new Point(12, 34).getX()} could be inlined to {@code 12}.
 */
public final class InlineObjectProcessor extends BaseRefactoringProcessor {
  private final PsiMethod myMethod;
  private final PsiReference myReference;
  private final PsiNewExpression myNewExpression;
  private final PsiMethodCallExpression myNextCall;
  private final PsiMethod myNextMethod;

  private InlineObjectProcessor(PsiMethod method, PsiReference reference) {
    super(method.getProject());
    myMethod = method;
    myReference = reference;
    PsiElement element = myReference.getElement();
    myNewExpression = tryCast(element.getParent(), PsiNewExpression.class);
    assert myNewExpression != null;
    myNextCall = ExpressionUtils.getCallForQualifier(myNewExpression);
    assert myNextCall != null;
    PsiMethod nextMethod = myNextCall.resolveMethod();
    assert nextMethod != null;
    PsiElement nav = nextMethod.getNavigationElement();
    if (nav instanceof PsiMethod) {
      nextMethod = (PsiMethod)nav;
    }
    myNextMethod = nextMethod;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @NotNull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Collections.singletonList(myReference.getElement());
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return new UsageInfo[]{new UsageInfo(myReference)};
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    ChangeContextUtil.encodeContextInfo(myMethod, false);
    PsiMethod ctorCopy = (PsiMethod)myMethod.copy();
    ChangeContextUtil.clearContextInfo(myMethod);
    ChangeContextUtil.encodeContextInfo(myNextMethod, false);
    PsiMethod nextCopy = (PsiMethod)myNextMethod.copy();
    ChangeContextUtil.clearContextInfo(myNextMethod);
    InlineMethodHelper ctorHelper = new InlineMethodHelper(myProject, myMethod, ctorCopy, myNewExpression);
    InlineMethodHelper nextHelper = new InlineMethodHelper(myProject, myNextMethod, nextCopy, myNextCall);
    PsiClass aClass = myMethod.getContainingClass();
    assert aClass != null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    PsiCodeBlock target = factory.createCodeBlock();
    List<PsiLocalVariable> fieldLocals = new ArrayList<>();
    for (PsiField field : aClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiDeclarationStatement declaration =
          factory.createVariableDeclarationStatement(field.getName(), field.getType(), field.getInitializer(), aClass);
        fieldLocals.add((PsiLocalVariable)((PsiDeclarationStatement)target.add(declaration)).getDeclaredElements()[0]);
      }
    }
    PsiLocalVariable[] ctorParameters = ctorHelper.declareParameters();
    InlineTransformer ctorTransformer = InlineTransformer.getSuitableTransformer(myMethod).apply(myReference);
    ctorTransformer.transformBody(ctorCopy, myReference, PsiType.VOID);
    PsiCodeBlock ctorBody = Objects.requireNonNull(ctorCopy.getBody());
    InlineUtil.solveVariableNameConflicts(ctorBody, target, ctorBody);
    updateFieldRefs(ctorCopy, aClass);
    ctorParameters = addRange(target, ctorBody, ctorParameters);

    PsiLocalVariable[] nextParameters = nextHelper.declareParameters();
    InlineTransformer nextTransformer = InlineTransformer.getSuitableTransformer(myNextMethod).apply(myNextCall.getMethodExpression());
    PsiLocalVariable result = nextTransformer.transformBody(nextCopy, myNextCall.getMethodExpression(), myNextCall.getType());
    PsiCodeBlock nextBody = Objects.requireNonNull(nextCopy.getBody());
    InlineUtil.solveVariableNameConflicts(nextBody, target, nextBody);
    updateFieldRefs(nextCopy, aClass);
    if (result != null) {
      PsiLocalVariable[] resultAndParameters = ArrayUtil.prepend(result, nextParameters);
      resultAndParameters = addRange(target, nextBody, resultAndParameters);
      result = resultAndParameters[0];
      nextParameters = Arrays.copyOfRange(resultAndParameters, 1, resultAndParameters.length);
    }
    else {
      nextParameters = addRange(target, nextBody, nextParameters);
    }

    InlineUtil.solveVariableNameConflicts(target, myReference.getElement(), target);
    ctorHelper.initializeParameters(ctorParameters);
    nextHelper.initializeParameters(nextParameters);

    removeRedundantFieldVars(fieldLocals, target);
    ctorHelper.inlineParameters(ctorParameters);
    nextHelper.inlineParameters(nextParameters);

    PsiElement anchor = CommonJavaRefactoringUtil.getParentStatement(myNextCall, true);
    assert anchor != null;
    PsiElement anchorParent = anchor.getParent();
    PsiStatement[] statements = target.getStatements();
    PsiElement firstBodyElement = target.getFirstBodyElement();
    if (firstBodyElement instanceof PsiWhiteSpace) firstBodyElement = PsiTreeUtil.skipWhitespacesForward(firstBodyElement);
    PsiElement firstAdded = null;
    if (firstBodyElement != null && firstBodyElement != target.getRBrace()) {
      int last = statements.length - 1;

      final PsiElement rBraceOrReturnStatement =
        last >= 0 ? PsiTreeUtil.skipWhitespacesAndCommentsForward(statements[last]) : target.getLastBodyElement();
      assert rBraceOrReturnStatement != null;
      final PsiElement beforeRBraceStatement = rBraceOrReturnStatement.getPrevSibling();
      assert beforeRBraceStatement != null;

      firstAdded = anchorParent.addRangeBefore(firstBodyElement, beforeRBraceStatement, anchor);
      ChangeContextUtil.decodeContextInfo(anchorParent, null, null);
    }

    PsiReferenceExpression resultUsage = InlineMethodProcessor.replaceCall(factory, myNextCall, firstAdded, result);
    if (resultUsage != null) {
      PsiLocalVariable resultVar = ExpressionUtils.resolveLocalVariable(resultUsage);
      if (resultVar != null) {
        InlineUtil.tryInlineResultVariable(resultVar, resultUsage);
      }
    }
  }

  private static void removeRedundantFieldVars(List<PsiLocalVariable> vars, PsiCodeBlock block) {
    for (PsiLocalVariable var : vars) {
      List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(var, block);
      PsiAssignmentExpression firstAssignment = null;
      List<PsiAssignmentExpression> assignments = new ArrayList<>();
      for (PsiReferenceExpression reference : references) {
        PsiAssignmentExpression assignment = tryCast(PsiUtil.skipParenthesizedExprUp(reference.getParent()), PsiAssignmentExpression.class);
        if (assignment != null && assignment.getOperationTokenType().equals(JavaTokenType.EQ) &&
            PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()) == reference &&
            assignment.getParent() instanceof PsiExpressionStatement) {
          assignments.add(assignment);
          if (firstAssignment == null && assignment.getParent().getParent() == block) {
            firstAssignment = assignment;
          }
        }
        else {
          assignments = null;
          break;
        }
      }
      if (assignments != null) {
        for (PsiAssignmentExpression assignment : assignments) {
          PsiExpressionStatement statement = (PsiExpressionStatement)assignment.getParent();
          PsiExpression expression = assignment.getRExpression();
          CommentTracker ct = new CommentTracker();
          if (expression != null) {
            List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
            sideEffects.forEach(ct::markUnchanged);
            PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
            if (statements.length > 0) {
              BlockUtils.addBefore(statement, statements);
            }
          }
          ct.deleteAndRestoreComments(statement);
        }
        new CommentTracker().deleteAndRestoreComments(var);
      }
      else if (firstAssignment != null) {
        var = DeclarationJoinLinesHandler.joinDeclarationAndAssignment(var, firstAssignment);
        InlineUtil.tryInlineGeneratedLocal(var, false);
      }
    }
  }

  private static PsiLocalVariable[] addRange(PsiCodeBlock target, PsiCodeBlock body, PsiLocalVariable[] declaredVars) {
    PsiElement firstBodyElement = body.getFirstBodyElement();
    PsiElement lastBodyElement = body.getLastBodyElement();
    if (firstBodyElement == null || lastBodyElement == null) return declaredVars;
    PsiElement firstAdded = target.addRange(firstBodyElement, lastBodyElement);
    PsiLocalVariable[] updatedVars = new PsiLocalVariable[declaredVars.length];
    int index = 0;
    for (PsiElement e = firstAdded; index < updatedVars.length && e != null; e = e.getNextSibling()) {
      if (e instanceof PsiDeclarationStatement) {
        PsiElement[] elements = ((PsiDeclarationStatement)e).getDeclaredElements();
        if (elements.length == 1) {
          PsiLocalVariable var = tryCast(elements[0], PsiLocalVariable.class);
          if (var != null) {
            if (var.getName().equals(declaredVars[index].getName())) {
              updatedVars[index++] = var;
            }
          }
        }
      }
    }
    assert index == updatedVars.length;
    return updatedVars;
  }

  private static void updateFieldRefs(PsiMethod method, PsiClass aClass) {
    PsiCodeBlock body = method.getBody();
    assert body != null;
    for (PsiThisExpression thisExpression : PsiTreeUtil.findChildrenOfType(body, PsiThisExpression.class)) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(thisExpression.getParent());
      if (parent instanceof PsiReferenceExpression) {
        PsiField field = tryCast(((PsiReferenceExpression)parent).resolve(), PsiField.class);
        if (field != null && field.getContainingClass() == aClass) {
          thisExpression.delete();
        }
      }
    }
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usagesIn = refUsages.get();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final ReferencedElementsCollector collector = new ReferencedElementsCollector();
    myMethod.accept(collector);
    myNextMethod.accept(collector);

    final Map<PsiMember, Set<PsiMember>> containersToReferenced = InlineMethodProcessor
      .getInaccessible(collector.myReferencedMembers, usagesIn, myMethod);

    containersToReferenced.forEach((container, referencedInaccessible) -> {
      for (PsiMember referenced : referencedInaccessible) {
        if (referenced instanceof PsiField && !referenced.hasModifierProperty(PsiModifier.STATIC) &&
            referenced.getContainingClass() == myMethod.getContainingClass()) {
          // Instance fields will be inlined
          continue;
        }
        final String referencedDescription = RefactoringUIUtil.getDescription(referenced, true);
        final String containerDescription = RefactoringUIUtil.getDescription(container, true);
        String message = RefactoringBundle.message("0.that.is.used.in.inlined.method.is.not.accessible.from.call.site.s.in.1",
                                                   referencedDescription, containerDescription);
        conflicts.putValue(container, StringUtil.capitalize(message));
      }
    });
    return showConflicts(conflicts, usagesIn);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return JavaRefactoringBundle.message("inline.object.command.name");
  }

  @Nullable
  public static InlineObjectProcessor create(PsiReference reference, PsiMethod method) {
    if (!canInlineConstructorAndChainCall(reference, method)) {
      return null;
    }
    return new InlineObjectProcessor(method, reference);
  }

  @Contract("null, _ -> false")
  private static boolean canInlineConstructorAndChainCall(PsiReference reference, PsiMethod method) {
    if (reference == null) return false;
    PsiElement element = reference.getElement();
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
    PsiNewExpression expression = tryCast(element.getParent(), PsiNewExpression.class);
    if (expression == null) return false;
    PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
    if (call == null) return false;
    if (CommonJavaRefactoringUtil.getParentStatement(call, true) == null) return false;
    PsiMethod nextMethod = call.resolveMethod();
    if (nextMethod == null) return false;
    PsiElement nav = nextMethod.getNavigationElement();
    if (nav instanceof PsiMethod) {
      nextMethod = (PsiMethod)nav;
    }
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) return false;

    PsiClassType[] supers = aClass.getExtendsListTypes();
    if (supers.length > 1) return false;
    if (supers.length == 1 && !isStatelessSuperClass(supers[0], new HashSet<>())) return false;
    for (PsiField field : aClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiExpression initializer = field.getInitializer();
        if (initializer != null && mayLeakThis(initializer)) return false;
      }
    }
    for (PsiClassInitializer initializer : aClass.getInitializers()) {
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
    }
    return !mayLeakThis(method) && !mayLeakThis(nextMethod);
  }

  private static boolean isStatelessSuperClass(PsiClassType psiType, Set<PsiClass> checked) {
    if (TypeUtils.isJavaLangObject(psiType)) return true;
    PsiClass psiClass = psiType.resolve();
    if (psiClass == null || !checked.add(psiClass)) return false;
    PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().isEmpty()) {
        PsiElement nav = constructor.getNavigationElement();
        if (nav instanceof PsiMethod) {
          constructor = (PsiMethod)nav;
        }
        PsiCodeBlock body = constructor.getBody();
        if (body == null || !ControlFlowUtils.isEmptyCodeBlock(body)) return false;
      }
    }
    for (PsiField field : psiClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    }
    PsiClassType[] supers = psiClass.getExtendsListTypes();
    return supers.length == 0 || supers.length == 1 && isStatelessSuperClass(supers[0], checked);
  }

  private static boolean mayLeakThis(PsiMethod method) {
    if (method == null) return true;
    PsiCodeBlock body = method.getBody();
    if (body == null) return true;
    return mayLeakThis(body);
  }

  private static boolean mayLeakThis(PsiElement body) {
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      boolean leak = false;

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
        if (qualifier instanceof PsiQualifiedExpression) {
          leak = true;
          stopWalking();
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        if (expression.getQualifier() == null) {
          PsiJavaCodeReferenceElement reference = expression.getClassReference();
          if (reference != null) {
            PsiClass target = tryCast(reference.resolve(), PsiClass.class);
            if (target != null && target.getContainingClass() != null && !target.hasModifierProperty(PsiModifier.STATIC)) {
              leak = true;
              stopWalking();
            }
          }
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (!(parent instanceof PsiReferenceExpression)) {
          leak = true;
          stopWalking();
        }
      }
    }
    Visitor visitor = new Visitor();
    body.accept(visitor);
    return visitor.leak;
  }
}
