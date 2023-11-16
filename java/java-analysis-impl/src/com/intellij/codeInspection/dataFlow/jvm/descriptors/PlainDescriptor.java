// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.jvm.FieldChecker;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * A descriptor that represents a PsiVariable (either local, or field -- may have a qualifier)
 */
public final class PlainDescriptor extends PsiVarDescriptor {
  private final @NotNull PsiVariable myVariable;

  public PlainDescriptor(@NotNull PsiVariable variable) {
    myVariable = variable;
  }

  @NotNull
  @Override
  public String toString() {
    return String.valueOf(myVariable.getName());
  }

  @Override
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    PsiType type = myVariable.getType();
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return getSubstitutor(myVariable, qualifier).substitute(type);
  }

  @Override
  public PsiVariable getPsiElement() {
    return myVariable;
  }

  @Override
  public boolean isStable() {
    return PsiUtil.isJvmLocalVariable(myVariable) ||
           (myVariable.hasModifierProperty(PsiModifier.FINAL) && !hasInitializationHacks(myVariable));
  }

  @NotNull
  @Override
  public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (myVariable.hasModifierProperty(PsiModifier.VOLATILE)) {
      PsiType type = getType(ObjectUtils.tryCast(qualifier, DfaVariableValue.class));
      return factory.fromDfType(DfTypes.typedObject(type, DfaPsiUtil.getElementNullability(type, myVariable)));
    }
    if (PsiUtil.isJvmLocalVariable(myVariable) ||
        (myVariable instanceof PsiField && myVariable.hasModifierProperty(PsiModifier.STATIC))) {
      return factory.getVarFactory().createVariableValue(this);
    }
    return super.createValue(factory, qualifier);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myVariable.getName());
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof PlainDescriptor && ((PlainDescriptor)obj).myVariable == myVariable;
  }

  @NotNull
  public static DfaVariableValue createVariableValue(@NotNull DfaValueFactory factory, @NotNull PsiVariable variable) {
    DfaVariableValue qualifier = null;
    if (variable instanceof PsiField && !(variable.hasModifierProperty(PsiModifier.STATIC))) {
      qualifier = ThisDescriptor.createThisValue(factory, ((PsiField)variable).getContainingClass());
    }
    return factory.getVarFactory().createVariableValue(new PlainDescriptor(variable), qualifier);
  }

  @Override
  @NotNull DfaNullability calcCanBeNull(@NotNull DfaVariableValue value, @Nullable PsiElement context) {
    PsiField field = ObjectUtils.tryCast(myVariable, PsiField.class);
    if (field != null && hasInitializationHacks(field)) {
      return DfaNullability.FLUSHED;
    }

    DfaVariableValue qualifier = value.getQualifier();
    if (field != null && context != null) {
      PsiMember member = ObjectUtils.tryCast(context.getParent(), PsiMember.class);
      if (member != null) {
        PsiClass methodClass = member.getContainingClass();
        if (methodClass != null && methodClass.equals(field.getContainingClass())) {
          PsiMethod method = ObjectUtils.tryCast(member, PsiMethod.class);
          VariableDescriptor qualifierDescriptor = qualifier == null ? null : qualifier.getDescriptor();
          if (qualifierDescriptor instanceof ThisDescriptor && ((ThisDescriptor)qualifierDescriptor).getPsiElement().equals(methodClass)) {
            if (member instanceof PsiClassInitializer) {
              return DfaNullability.UNKNOWN;
            }
            if (method != null) {
              if (!method.isConstructor() && isPossiblyNonInitialized(field, method)) {
                return DfaNullability.NULLABLE;
              }
              else if (method.isConstructor()) {
                return DfaNullability.UNKNOWN;
              }
            }
          }
          if (method != null && field.hasModifierProperty(PsiModifier.STATIC) && isPossiblyNonInitialized(field, method)) {
            return DfaNullability.NULLABLE;
          }
        }
      }
    }

    PsiType type = getType(qualifier);
    Nullability nullability = DfaPsiUtil.getElementNullabilityIgnoringParameterInference(type, myVariable);
    if (nullability != Nullability.UNKNOWN) {
      return DfaNullability.fromNullability(nullability);
    }

    if (myVariable instanceof PsiParameter && myVariable.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)myVariable.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaNullability.fromNullability(DfaPsiUtil.getElementNullability(itemType, myVariable));
        }
      }
    }

    if (field != null && FieldChecker.getChecker(context).canTrustFieldInitializer(field)) {
      return DfaNullability.fromNullability(NullabilityUtil.getNullabilityFromFieldInitializers(field).second);
    }
    return DfaNullability.UNKNOWN;
  }

  private static boolean isPossiblyNonInitialized(@NotNull PsiField target, @NotNull PsiMethod placeMethod) {
    if (target.getType() instanceof PsiPrimitiveType) return false;
    PsiClass placeClass = placeMethod.getContainingClass();
    if (placeClass == null || placeClass != target.getContainingClass()) return false;
    if (!placeMethod.hasModifierProperty(PsiModifier.STATIC) && target.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!target.hasModifierProperty(PsiModifier.STATIC) &&
        !placeMethod.hasModifierProperty(PsiModifier.STATIC) &&
        methodCanBeCalledFromConstructorBeforeFieldInitializing(target, placeMethod, placeClass)) {
      return true;
    }
    return getAccessOffset(placeMethod) < getWriteOffset(target);
  }

  private static boolean methodCanBeCalledFromConstructorBeforeFieldInitializing(@NotNull PsiField target,
                                                                                 @NotNull PsiMethod method,
                                                                                 @NotNull PsiClass placeClass) {
     if (target.hasInitializer() || method.isConstructor() ||
        //consider cases with only one constructor to do it faster
        placeClass.getConstructors().length != 1) {
      return false;
    }
    PsiMethod constructor = placeClass.getConstructors()[0];
    if (constructor.getBody() == null ||
        constructor.getBody().getStatements().length == 0 ||
        constructor.getBody().getStatements()[0] instanceof PsiSuperExpression) {
      return false;
    }
    PsiMethodCallExpression methodCallExpression = findCallIn(target, placeClass, method);
    if (methodCallExpression == null) {
      return false;
    }
    if (JavaPsiRecordUtil.isCompactConstructor(constructor)) {
      return true;
    }
    if (!VariableAccessUtils.variableIsAssignedAtPoint(target, constructor, methodCallExpression)) {
      return true;
    }
    return false;
  }

  private record MethodInfo(Map<String, Map<PsiMethod, PsiMethodCallExpression>> methods, boolean hasCallOutside){}

  @Nullable
  private static PsiMethodCallExpression findCallIn(@NotNull PsiField field,
                                                    @NotNull PsiClass contextClass,
                                                    @NotNull PsiMethod method) {
    PsiMethod constructor = contextClass.getConstructors()[0];
    if (!constructor.isPhysical() || constructor.getBody() == null) {
      return null;
    }

    MethodInfo cacheValue = CachedValuesManager.getCachedValue(contextClass, () -> {
      PsiMethod context = contextClass.getConstructors()[0];
      ConcurrentHashMap<String, Map<PsiMethod, PsiMethodCallExpression>> collectedMethods = new ConcurrentHashMap<>();
      Ref<Boolean> callsOutside = new Ref<>(false);
      PsiManager psiManager = context.getManager();
      JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}
        @Override
        public void visitClass(@NotNull PsiClass aClass) {}

        @Override
        public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {}

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression) {
          PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
          if (qualifier == null || qualifier instanceof PsiThisExpression) {
            PsiMethod resolvedMethod = methodCallExpression.resolveMethod();
            if (resolvedMethod != null &&
                resolvedMethod.getContainingClass() != null &&
                psiManager.areElementsEquivalent(resolvedMethod.getContainingClass(), contextClass)) {
              collectedMethods.compute(resolvedMethod.getName(),
                                       (ignoreKey, methodsByCall) -> {
                                         if (methodsByCall == null) {
                                           methodsByCall = new HashMap<>();
                                         }
                                         if (!methodsByCall.containsKey(resolvedMethod)) {
                                           methodsByCall.put(resolvedMethod, methodCallExpression);
                                         }
                                         return methodsByCall;
                                       });
            }
            else {
              callsOutside.set(true);
            }
          }
          else {
            callsOutside.set(true);
          }
          super.visitMethodCallExpression(methodCallExpression);
        }
      };
      context.accept(visitor);
      return CachedValueProvider.Result.create( new MethodInfo(collectedMethods, callsOutside.get()), PsiModificationTracker.MODIFICATION_COUNT);
    });
    PsiManager psiManager = contextClass.getManager();
    if (field.hasModifierProperty(PsiModifier.FINAL) ||
        (!cacheValue.hasCallOutside() &&
         cacheValue.methods().size() == 1 &&
         cacheValue.methods().entrySet().iterator().next().getValue().size() == 1)) {
      Map<PsiMethod, PsiMethodCallExpression> methodsByCall = cacheValue.methods().get(method.getName());
      if (methodsByCall == null) {
        return null;
      }
      for (PsiMethod methodByCall : methodsByCall.keySet()) {
        if (psiManager.areElementsEquivalent(methodByCall, method)) {
          return methodsByCall.get(methodByCall);
        }
      }
    }
    return null;
  }

  private static int getWriteOffset(PsiField target) {
    // Final field: written either in field initializer or in class initializer block which directly writes this field
    // Non-final field: written either in field initializer, in class initializer which directly writes this field or calls any method,
    //    or in other field initializer which directly writes this field or calls any method
    boolean isFinal = target.hasModifierProperty(PsiModifier.FINAL);
    int offset = Integer.MAX_VALUE;
    PsiExpression fieldInitializer = target.getInitializer();
    if (fieldInitializer != null) {
      offset = fieldInitializer.getTextOffset();
      if (isFinal) return offset;
    }
    PsiClass aClass = Objects.requireNonNull(target.getContainingClass());
    PsiClassInitializer[] initializers = aClass.getInitializers();
    Predicate<PsiElement> writesToTarget = element ->
      !PsiTreeUtil.processElements(element, e -> !(e instanceof PsiExpression) ||
                                                 !PsiUtil.isAccessedForWriting((PsiExpression)e) ||
                                                 !ExpressionUtils.isReferenceTo((PsiExpression)e, target));
    Predicate<PsiElement> hasSideEffectCall = element -> !PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class).stream()
      .map(PsiMethodCallExpression::resolveMethod).allMatch(method -> method != null && JavaMethodContractUtil.isPure(method));
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC) != target.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (!isFinal && hasSideEffectCall.test(initializer)) {
        // non-final field could be written indirectly (via method call), so assume it's written in the first applicable initializer
        offset = Math.min(offset, initializer.getTextRange().getStartOffset());
        break;
      }
      if (writesToTarget.test(initializer)) {
        offset = Math.min(offset, initializer.getTextRange().getStartOffset());
        if (isFinal) return offset;
        break;
      }
    }
    if (!isFinal) {
      for (PsiField field : aClass.getFields()) {
        if (field.hasModifierProperty(PsiModifier.STATIC) != target.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (hasSideEffectCall.test(field.getInitializer()) || writesToTarget.test(field)) {
          offset = Math.min(offset, field.getTextRange().getStartOffset());
          break;
        }
      }
    }
    return offset;
  }

  private static int getAccessOffset(PsiMethod referrer) {
    PsiClass aClass = Objects.requireNonNull(referrer.getContainingClass());
    boolean isStatic = referrer.hasModifierProperty(PsiModifier.STATIC);
    for (PsiField field : aClass.getFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      PsiExpression initializer = field.getInitializer();
      Predicate<PsiExpression> callToMethod = (PsiExpression e) -> {
        if (!(e instanceof PsiMethodCallExpression call)) return false;
        return call.getMethodExpression().isReferenceTo(referrer) &&
               (isStatic || ExpressionUtil.isEffectivelyUnqualified(call.getMethodExpression()));
      };
      if (ExpressionUtils.isMatchingChildAlwaysExecuted(initializer, callToMethod)) {
        // current method is definitely called from some field initialization
        return field.getTextRange().getStartOffset();
      }
    }
    return Integer.MAX_VALUE; // accessed after initialization or at unknown moment
  }

  /**
   * @param var variable to check
   * @return true if variable is known to be initialized in a weird way and actual initializer should be taken into account.
   * Currently, reports fields declared inside java.lang.System class (System.out, System.in, System.err)
   */
  public static boolean hasInitializationHacks(@NotNull PsiVariable var) {
    if (!(var instanceof PsiField)) return false;
    PsiClass containingClass = ((PsiField)var).getContainingClass();
    return containingClass != null && System.class.getName().equals(containingClass.getQualifiedName());
  }
}
