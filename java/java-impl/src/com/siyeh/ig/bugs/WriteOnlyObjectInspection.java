// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class WriteOnlyObjectInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher OBJECT_CLONE =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_OBJECT, "clone").parameterCount(0);
  public boolean ignoreImpureConstructors = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreImpureConstructors", InspectionGadgetsBundle.message("write.only.object.option.ignore.impure.constructors")));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        if (!(variable instanceof PsiResourceVariable)) {
          processVariable(variable, PsiUtil.getVariableCodeBlock(variable, null));
        }
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
          processVariable(field, PsiUtil.getTopLevelClass(field));
        }
      }

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(expression);
        PsiJavaCodeReferenceElement anchor = expression.getClassOrAnonymousClassReference();
        if (anchor != null && nextCall != null && isNewExpression(expression) && isWriteOnlyCall(nextCall, true)) {
          holder.registerProblem(anchor, InspectionGadgetsBundle.message("write.only.object.display.name"));
        }
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(call);
        PsiElement anchor = call.getMethodExpression().getReferenceNameElement();
        if (anchor != null && nextCall != null && isNewExpression(call) && isWriteOnlyCall(nextCall, false)) {
          holder.registerProblem(anchor, InspectionGadgetsBundle.message("write.only.object.display.name"));
        }
      }

      private void processVariable(PsiVariable variable, PsiElement block) {
        if (block == null) return;
        PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier != null && isNewExpression(initializer)) {
          boolean exactType = PsiUtil.skipParenthesizedExprDown(initializer) instanceof PsiNewExpression;
          PsiType type = variable.getType();
          if (!(type instanceof PsiClassType)) return;
          if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
              InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP) ||
              InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
            // will be processed by other inspections
            return;
          }
          List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable, block);
          if (references.isEmpty()) return;
          for (PsiReferenceExpression ref : references) {
            if (!isWriteWithoutRead(ref, exactType)) {
              return;
            }
          }
          holder.registerProblem(identifier, InspectionGadgetsBundle.message("write.only.object.display.name"));
        }
      }
    };
  }

  private static boolean isWriteWithoutRead(@NotNull PsiReferenceExpression ref, boolean exactType) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)parent)) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiExpression &&
          ExpressionUtils.isVoidContext((PsiExpression)grandParent)) {
        return true;
      }
    }
    PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(ref);
    return isWriteOnlyCall(call, exactType);
  }

  @Contract("null, _ -> false")
  private static boolean isWriteOnlyCall(@Nullable PsiMethodCallExpression call, boolean exactType) {
    while (call != null) {
      if (!isMutatorMethod(call, exactType)) break;
      if (ExpressionUtils.isVoidContext(call)) return true;
      ContractReturnValue value = JavaMethodContractUtil.getNonFailingReturnValue(JavaMethodContractUtil.getMethodCallContracts(call));
      if (!ContractReturnValue.returnThis().equals(value)) break;
      call = ExpressionUtils.getCallForQualifier(call);
    }
    return false;
  }

  private static boolean isMutatorMethod(PsiMethodCallExpression call, boolean exactType) {
    MutationSignature sig = MutationSignature.fromCall(call);
    if (sig.equals(MutationSignature.pure().alsoMutatesThis())) return true;
    if (exactType) {
      PsiMethod method = call.resolveMethod();
      if (PropertyUtil.isSimpleSetter(method)) return true;
    }
    return false;
  }

  private boolean isNewExpression(PsiExpression initializer) {
    if (initializer == null) return false;
    return ExpressionUtils.nonStructuralChildren(initializer).allMatch(
      expr -> {
        if (OBJECT_CLONE.matches(expr)) return true;
        if (expr instanceof PsiNewExpression ||
            expr instanceof PsiMethodCallExpression &&
            ContractReturnValue.returnNew().equals(JavaMethodContractUtil.getNonFailingReturnValue(
              JavaMethodContractUtil.getMethodCallContracts((PsiMethodCallExpression)expr)))) {
          PsiMethod method = ((PsiCallExpression)expr).resolveMethod();
          if (method != null && Mutability.getMutability(method).isUnmodifiable()) {
            // Probably we are expecting some exception when calling the modification method
            return false;
          }
          if (ignoreImpureConstructors) {
            return MutationSignature.fromCall((PsiCallExpression)expr).isPure();
          }
          return true;
        }
        return false;
      });
  }
}
