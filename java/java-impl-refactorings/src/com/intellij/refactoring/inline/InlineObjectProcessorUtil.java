// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiQualifiedExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Util class that provides steps for "Inline Object" refactoring that can be accessed outside of
 * {@link com.intellij.refactoring.BaseRefactoringProcessor}
 */
@ApiStatus.Internal
public final class InlineObjectProcessorUtil {
  private InlineObjectProcessorUtil() { }

  @Contract("null, _ -> false")
  static boolean canInlineConstructorAndChainCall(PsiReference reference, PsiMethod method) {
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
    if (TypeUtils.isJavaLangObject(psiType) || TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_RECORD, psiType)) return true;
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
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
        if (qualifier instanceof PsiQualifiedExpression) {
          leak = true;
          stopWalking();
        }
      }

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
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
      public void visitThisExpression(@NotNull PsiThisExpression expression) {
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

  public static UsageInfo @NotNull [] findUsages(@NotNull InlineObjectContext context) {
    return new UsageInfo[]{new UsageInfo(context.reference())};
  }

  public static void collectConflicts(@NotNull InlineObjectContext context,
                                      UsageInfo @NotNull [] usages,
                                      @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    PsiMethod method = context.method();
    final ReferencedElementsCollector collector = new ReferencedElementsCollector();
    method.accept(collector);
    context.nextMethod().accept(collector);

    final Map<PsiMember, Set<PsiMember>> containersToReferenced =
      InlineMethodProcessorUtil.getInaccessible(collector.myReferencedMembers, usages, method);

    containersToReferenced.forEach((container, referencedInaccessible) -> {
      for (PsiMember referenced : referencedInaccessible) {
        if (referenced instanceof PsiField && !referenced.hasModifierProperty(PsiModifier.STATIC) &&
            referenced.getContainingClass() == method.getContainingClass()) {
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
  }

  public static void performRefactoring(@NotNull InlineObjectContext context) {
    PsiMethod method = context.method();
    PsiReference reference = context.reference();
    PsiMethod nextMethod = context.nextMethod();
    PsiMethodCallExpression nextCall = context.nextCall();
    Project project = context.project();
    ChangeContextUtil.encodeContextInfo(method, false);
    PsiMethod ctorCopy = (PsiMethod)method.copy();
    ChangeContextUtil.clearContextInfo(method);
    ChangeContextUtil.encodeContextInfo(nextMethod, false);
    PsiMethod nextCopy = (PsiMethod)nextMethod.copy();
    ChangeContextUtil.clearContextInfo(nextMethod);
    InlineMethodHelper ctorHelper = new InlineMethodHelper(project, method, ctorCopy, context.newExpression());
    InlineMethodHelper nextHelper = new InlineMethodHelper(project, nextMethod, nextCopy, nextCall);
    PsiClass aClass = method.getContainingClass();
    assert aClass != null;
    PsiElementFactory factory = context.factory();
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
    ctorHelper.substituteTypes(ctorParameters);
    InlineTransformer ctorTransformer = InlineTransformer.getSuitableTransformer(method).apply(reference);
    ctorTransformer.transformBody(ctorCopy, reference, PsiTypes.voidType());
    PsiCodeBlock ctorBody = Objects.requireNonNull(ctorCopy.getBody());
    InlineUtil.solveLocalNameConflicts(ctorBody, target, ctorBody);
    updateFieldRefs(ctorCopy, aClass);
    ctorParameters = addRange(target, ctorBody, ctorParameters);

    PsiLocalVariable[] nextParameters = nextHelper.declareParameters();
    nextHelper.substituteTypes(nextParameters);
    InlineTransformer nextTransformer = InlineTransformer.getSuitableTransformer(nextMethod).apply(nextCall.getMethodExpression());
    PsiLocalVariable result = nextTransformer.transformBody(nextCopy, nextCall.getMethodExpression(), nextCall.getType());
    PsiCodeBlock nextBody = Objects.requireNonNull(nextCopy.getBody());
    InlineUtil.solveLocalNameConflicts(nextBody, target, nextBody);
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

    InlineUtil.solveLocalNameConflicts(target, reference.getElement(), target);
    ctorHelper.initializeParameters(ctorParameters);
    nextHelper.initializeParameters(nextParameters);

    removeRedundantFieldVars(fieldLocals, target);
    ctorHelper.inlineParameters(ctorParameters);
    nextHelper.inlineParameters(nextParameters);

    PsiElement anchor = CommonJavaRefactoringUtil.getParentStatement(nextCall, true);
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

    PsiReferenceExpression resultUsage = InlineMethodProcessorUtil.replaceCall(factory, nextCall, firstAdded, result);
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

  /**
   * Holds the values that stay the same during one "Inline Object" refactoring.
   *
   * @param method The constructor to be inlined.
   * @param reference The reference to the {@link method} to be inlined.
   * @param newExpression The object creation expression that holds the {@link reference}.
   * @param nextCall The call that is chained to the {@link newExpression}, for example {@code getX()} in {@code new Point().getX()}.
   * @param nextMethod The method that the {@link nextCall} resolves to.
   */
  @ApiStatus.Internal
  public record InlineObjectContext(@NotNull PsiMethod method,
                                    @NotNull PsiReference reference,
                                    @NotNull PsiNewExpression newExpression,
                                    @NotNull PsiMethodCallExpression nextCall,
                                    @NotNull PsiMethod nextMethod) {
    public @NotNull PsiElementFactory factory() {
      return JavaPsiFacade.getElementFactory(project());
    }

    public @NotNull Project project() {
      return method.getProject();
    }

    public static @NotNull InlineObjectContext create(@NotNull PsiMethod method, @NotNull PsiReference reference) {
      PsiElement element = reference.getElement();
      PsiNewExpression newExpression = tryCast(element.getParent(), PsiNewExpression.class);
      assert newExpression != null;
      PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(newExpression);
      assert nextCall != null;
      PsiMethod nextMethod = nextCall.resolveMethod();
      assert nextMethod != null;
      PsiElement nav = nextMethod.getNavigationElement();
      if (nav instanceof PsiMethod) {
        nextMethod = (PsiMethod)nav;
      }
      return new InlineObjectContext(method, reference, newExpression, nextCall, nextMethod);
    }
  }
}