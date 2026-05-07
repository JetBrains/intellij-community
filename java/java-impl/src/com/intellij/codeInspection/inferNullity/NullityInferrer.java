// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inferNullity;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("DuplicatedCode") // there is a partial copy in org.jetbrains.kotlin.nj2k.J2KNullityInferrer
public class NullityInferrer {
  private static final int MAX_PASSES = 10;
  private int numAnnotationsAdded;
  private final Map<SmartPsiElementPointer<? extends PsiModifierListOwner>, InferredNullability> inferred = new LinkedHashMap<>();
  private final boolean myAnnotateLocalVariables;
  private final SmartPointerManager myPointerManager;

  public NullityInferrer(boolean annotateLocalVariables, @NotNull Project project) {
    myAnnotateLocalVariables = annotateLocalVariables;
    myPointerManager = SmartPointerManager.getInstance(project);
  }

  private boolean expressionIsNeverNull(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    if (ExpressionUtils.nonStructuralChildren(expression).allMatch(
      expr -> expr instanceof PsiMethodCallExpression && isNotNull(((PsiMethodCallExpression)expr).resolveMethod()))) {
      return true;
    }
    return NullabilityUtil.getExpressionNullability(expression, true) == Nullability.NOT_NULL;
  }

  private boolean expressionIsSometimesNull(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    if (ExpressionUtils.nonStructuralChildren(expression).anyMatch(
      expr -> expr instanceof PsiMethodCallExpression && isNullable(((PsiMethodCallExpression)expr).resolveMethod()))) {
      return true;
    }
    return NullabilityUtil.getExpressionNullability(expression, true) == Nullability.NULLABLE;
  }

  private boolean variableNeverAssignedNull(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      if (!expressionIsNeverNull(initializer)) {
        return false;
      }
    }
    else if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    else if (variable instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiCatchSection) {
      return false;
    }

    final Query<PsiReference> references = ReferencesSearch.search(variable);
    for (final PsiReference reference : references.asIterable()) {
      final PsiElement element = reference.getElement();
      if (!(element instanceof PsiReferenceExpression)) {
        continue;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiAssignmentExpression assignment)) {
        continue;
      }

      if (assignment.getLExpression().equals(element) &&
          !expressionIsNeverNull(assignment.getRExpression())) {
        return false;
      }
    }

    return true;
  }

  private boolean variableSometimesAssignedNull(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null && expressionIsSometimesNull(initializer)) {
      return true;
    }

    final Query<PsiReference> references = ReferencesSearch.search(variable);
    for (final PsiReference reference : references.asIterable()) {
      final PsiElement element = reference.getElement();
      if (!(element instanceof PsiReferenceExpression)) {
        continue;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiAssignmentExpression assignment)) {
        continue;
      }

      if (assignment.getLExpression().equals(element) && expressionIsSometimesNull(assignment.getRExpression())) {
        return true;
      }
    }

    return false;
  }

  public void collect(@NotNull PsiFile file) {
    int prevNumAnnotationsAdded;
    int pass = 0;
    do {
      final NullityInferrerVisitor visitor = new NullityInferrerVisitor();
      prevNumAnnotationsAdded = numAnnotationsAdded;
      file.accept(visitor);
      pass++;
    }
    while (prevNumAnnotationsAdded < numAnnotationsAdded && pass < MAX_PASSES);
  }

  public static void nothingFoundMessage(final Project project) {
    SwingUtilities.invokeLater(
      () -> Messages.showInfoMessage(project, JavaBundle.message("dialog.message.no.places.found.to.infer.nullable.notnull"),
                                     JavaBundle.message("dialog.title.infer.nullity.results")));
  }

  public int getCount() {
    return inferred.size();
  }

  public static boolean apply(@NotNull NullableNotNullManager manager, UsageInfo info) {
    return info instanceof InferredNullability inferredNullability && inferredNullability.apply(manager);
  }

  private boolean shouldIgnore(@NotNull PsiModifierListOwner element) {
    if (myAnnotateLocalVariables) return false;
    if (element instanceof PsiLocalVariable) return true;
    if (element instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiForeachStatement) return true;
    return false;
  }

  private void registerNullableAnnotation(@NotNull PsiModifierListOwner declaration) {
    registerAnnotation(declaration, true);
  }

  private void registerNotNullAnnotation(@NotNull PsiModifierListOwner declaration) {
    registerAnnotation(declaration, false);
  }

  private void registerAnnotation(@NotNull PsiModifierListOwner declaration, boolean isNullable) {
    final SmartPsiElementPointer<PsiModifierListOwner> declarationPointer = myPointerManager.createSmartPsiElementPointer(declaration);
    inferred.put(declarationPointer, isNullable ? new NullableInferred(declaration) : new NotNullInferred(declaration));
    numAnnotationsAdded++;
  }

  private void registerAnnotationByNullAssignmentStatus(PsiVariable variable) {
    if (variableNeverAssignedNull(variable)) {
      registerNotNullAnnotation(variable);
    }
    else if (variableSometimesAssignedNull(variable)) {
      registerNullableAnnotation(variable);
    }
  }

  @NotNullByDefault
  private static final class NullableInferred extends InferredNullability {
    NullableInferred(PsiModifierListOwner element) {
      super(element);
    }

    @Override
    public boolean apply(NullableNotNullManager manager) {
      PsiModifierListOwner element = (PsiModifierListOwner)getElement();
      if (element == null) return false;
      invoke(element, AddAnnotationModCommandAction.createAddNullableFix(element));
      return true;
    }
  }

  @NotNullByDefault
  private static final class NotNullInferred extends InferredNullability {
    NotNullInferred(PsiModifierListOwner element) {
      super(element);
    }

    @Override
    public boolean apply(NullableNotNullManager manager) {
      PsiModifierListOwner element = (PsiModifierListOwner)getElement();
      if (element == null || element instanceof PsiField field && field.hasInitializer() && field.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }

      invoke(element, AddAnnotationModCommandAction.createAddNotNullFix(element));
      return true;
    }
  }

  private static void invoke(final @NotNull PsiModifierListOwner element, @Nullable ModCommandAction action) {
    if (action == null) return;
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return;
    ActionContext context = ActionContext.from(null, containingFile).withElement(element);
    ModCommandExecutor.executeInteractively(context, action.getFamilyName(), null, () -> {
      return action.getPresentation(context) == null ? ModCommand.nop() : action.perform(context);
    });
  }

  @NotNullByDefault
  private static abstract class InferredNullability extends UsageInfo {
    protected InferredNullability(PsiModifierListOwner element) {
      super(element);
    }

    public abstract boolean apply(NullableNotNullManager manager);
  }

  public void collect(@NotNull List<? super UsageInfo> usages) {
    inferred.keySet().removeIf((elementPointer) -> {
      return ReadAction.nonBlocking(() -> {
        PsiModifierListOwner element = elementPointer.getElement();
        return element == null || shouldIgnore(element);
      }).executeSynchronously();
    });
    usages.addAll(inferred.values());
  }

  private boolean isNotNull(@Nullable PsiModifierListOwner owner) {
    return owner != null && (NullableNotNullManager.isNotNull(owner) || getInferredNullability(owner) instanceof NotNullInferred);
  }

  private boolean isNullable(@Nullable PsiModifierListOwner owner) {
    return owner != null && (NullableNotNullManager.isNullable(owner) || getInferredNullability(owner) instanceof NullableInferred);
  }

  private boolean hasNullability(@NotNull PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    if (info != null && !info.isInferred() && info.getNullability() != Nullability.UNKNOWN) return true;
    return getInferredNullability(owner) != null;
  }

  private InferredNullability getInferredNullability(@NotNull PsiModifierListOwner owner) {
    return inferred.get(myPointerManager.createSmartPsiElementPointer(owner));
  }

  private class NullityInferrerVisitor extends JavaRecursiveElementWalkingVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor() || method.getReturnType() instanceof PsiPrimitiveType) {
        return;
      }

      final Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(method).findAll();
      for (final PsiMethod overridingMethod : overridingMethods) {
        if (isNullable(overridingMethod)) {
          registerNullableAnnotation(method);
          return;
        }
      }

      final NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
      if (!manager.isNotNull(method, false) && manager.isNotNull(method, true)) {
        registerNotNullAnnotation(method);
        return;
      }

      if (hasNullability(method)) {
        return;
      }

      Nullability nullability = DfaUtil.inferMethodNullability(method);
      switch (nullability) {
        case NULLABLE -> registerNullableAnnotation(method);

        case NOT_NULL -> {
          for (final PsiMethod overridingMethod : overridingMethods) {
            if (!isNotNull(overridingMethod)) {
              return;
            }
          }
          registerNotNullAnnotation(method);
        }
      }
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (variable.getType() instanceof PsiPrimitiveType || hasNullability(variable)) {
        return;
      }
      registerAnnotationByNullAssignmentStatus(variable);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      if (parameter.getType() instanceof PsiPrimitiveType || hasNullability(parameter)) {
        return;
      }

      final PsiElement grandParent = parameter.getDeclarationScope();
      if (grandParent instanceof PsiMethod method) {
        if (method.getBody() != null) {
          if (JavaSourceInference.inferNullability(parameter) == Nullability.NOT_NULL) {
            registerNotNullAnnotation(parameter);
            return;
          }

          for (PsiReferenceExpression expr : VariableAccessUtils.getVariableReferences(parameter, method)) {
            final PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
            if (processParameter(parameter, expr, parent)) return;
            if (isNotNull(method)) {
              PsiElement toReturn = parent;
              if (parent instanceof PsiConditionalExpression &&
                  ((PsiConditionalExpression)parent).getCondition() != expr) {  //todo check conditional operations
                toReturn = parent.getParent();
              }
              if (toReturn instanceof PsiReturnStatement) {
                registerNotNullAnnotation(parameter);
                return;
              }
            }
          }
        }
      }
      else if (grandParent instanceof PsiForeachStatement) {
        for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(grandParent)).asIterable()) {
          final PsiElement place = reference.getElement();
          if (place instanceof PsiReferenceExpression expr) {
            final PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
            if (processParameter(parameter, expr, parent)) return;
          }
        }
      }
      else {
        registerAnnotationByNullAssignmentStatus(parameter);
      }
    }

    private boolean processParameter(@NotNull PsiParameter parameter, @NotNull PsiReferenceExpression expr, PsiElement parent) {
      if (PsiUtil.isAccessedForWriting(expr)) return true;

      if (parent instanceof PsiBinaryExpression binOp) {
        PsiExpression opposite = null;
        final PsiExpression lOperand = binOp.getLOperand();
        final PsiExpression rOperand = binOp.getROperand();
        if (lOperand == expr) {
          opposite = rOperand;
        }
        else if (rOperand == expr) {
          opposite = lOperand;
        }

        if (opposite != null && opposite.getType() == PsiTypes.nullType()) {
          if (DfaPsiUtil.isAssertionEffectively(binOp, binOp.getOperationTokenType() == JavaTokenType.NE)) {
            registerNotNullAnnotation(parameter);
            return true;
          }
          registerNullableAnnotation(parameter);
          return true;
        }
      }
      else if (parent instanceof PsiInstanceOfExpression) {
        return true;
      }
      else if (parent instanceof PsiReferenceExpression ref) {
        final PsiExpression qualifierExpression = ref.getQualifierExpression();
        if (qualifierExpression == expr) {
          registerNotNullAnnotation(parameter);
          return true;
        }
        else {
          PsiElement exprParent = expr.getParent();
          while (exprParent instanceof PsiTypeCastExpression || exprParent instanceof PsiParenthesizedExpression) {
            if (qualifierExpression == exprParent) {
              registerNotNullAnnotation(parameter);
              return true;
            }
            exprParent = exprParent.getParent();
          }
        }
      }
      else if (parent instanceof PsiAssignmentExpression assignment) {
        if (assignment.getRExpression() == expr &&
            assignment.getLExpression() instanceof PsiReferenceExpression ref &&
            ref.resolve() instanceof PsiVariable localVar && isNotNull(localVar)) {
          registerNotNullAnnotation(parameter);
          return true;
        }
      }
      else if (parent instanceof PsiForeachStatement forEach) {
        if (forEach.getIteratedValue() == expr) {
          registerNotNullAnnotation(parameter);
          return true;
        }
      }
      else if (parent instanceof PsiSwitchStatement switchStatement && switchStatement.getExpression() == expr) {
        registerNotNullAnnotation(parameter);
        return true;
      }

      final PsiCall call = PsiTreeUtil.getParentOfType(expr, PsiCall.class);
      if (call != null) {
        final PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          int idx = ArrayUtil.find(args, expr);
          if (idx >= 0) {
            final PsiMethod resolvedMethod = call.resolveMethod();
            if (resolvedMethod != null) {
              final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
              if (idx < parameters.length) { //not vararg
                final PsiParameter resolvedToParam = parameters[idx];
                if (isNotNull(resolvedToParam) && !resolvedToParam.isVarArgs()) {
                  registerNotNullAnnotation(parameter);
                  return true;
                }
              }
            }
          }
        }
      }

      return false;
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field instanceof PsiEnumConstant || field.getType() instanceof PsiPrimitiveType || hasNullability(field)) {
        return;
      }
      registerAnnotationByNullAssignmentStatus(field);
    }
  }
}
