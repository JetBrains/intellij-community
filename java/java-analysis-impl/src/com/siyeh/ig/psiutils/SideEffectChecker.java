/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.dataFlow.ContractValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

public final class SideEffectChecker {
  private static final Set<String> ourSideEffectFreeClasses = Set.of(
    Object.class.getName(),
    Short.class.getName(),
    Character.class.getName(),
    Byte.class.getName(),
    Integer.class.getName(),
    Long.class.getName(),
    Float.class.getName(),
    Double.class.getName(),
    String.class.getName(),
    StringBuffer.class.getName(),
    Boolean.class.getName(),

    ArrayList.class.getName(),
    Date.class.getName(),
    HashMap.class.getName(),
    HashSet.class.getName(),
    Hashtable.class.getName(),
    LinkedHashMap.class.getName(),
    LinkedHashSet.class.getName(),
    LinkedList.class.getName(),
    Stack.class.getName(),
    TreeMap.class.getName(),
    TreeSet.class.getName(),
    Vector.class.getName(),
    WeakHashMap.class.getName());

  private SideEffectChecker() {
  }

  public static boolean mayHaveSideEffects(@NotNull PsiExpression exp) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null, exp);
    exp.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  /**
   * @param exp expression to test
   * @return whether expression produces side effect. {@link ThreeState#UNSURE} means 
   * that unannotated method is called, which may or may not produce side effect.
   */
  public static @NotNull ThreeState getSideEffectStatus(@NotNull PsiExpression exp) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null, exp);
    exp.accept(visitor);
    return visitor.getSideEffectStatus();
  }

  public static boolean mayHaveSideEffects(@NotNull PsiElement element, @NotNull Predicate<? super PsiElement> shouldIgnoreElement) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null, element, shouldIgnoreElement);
    element.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static boolean mayHaveNonLocalSideEffects(@NotNull PsiElement element, @NotNull Predicate<PsiElement> shouldIgnoreElement) {
    return mayHaveSideEffects(element, shouldIgnoreElement.or(e -> isLocalSideEffect(e)));
  }

  /**
   * Returns true if element execution may cause non-local side-effect. Side-effects like control flow within method; throw/return or
   * local variable declaration or update are considered as local side-effects.
   *
   * @param element element to check
   * @return true if element execution may cause non-local side-effect.
   */
  public static boolean mayHaveNonLocalSideEffects(@NotNull PsiElement element) {
    return mayHaveSideEffects(element, SideEffectChecker::isLocalSideEffect);
  }

  private static boolean isLocalSideEffect(PsiElement e) {
    if (e instanceof PsiContinueStatement ||
        e instanceof PsiReturnStatement ||
        e instanceof PsiThrowStatement) {
      return true;
    }
    if (e instanceof PsiLocalVariable) return true;

    PsiReferenceExpression ref = null;
    if (e instanceof PsiAssignmentExpression assignment) {
      ref = tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()), PsiReferenceExpression.class);
    }
    if (e instanceof PsiUnaryExpression) {
      PsiExpression operand = ((PsiUnaryExpression)e).getOperand();
      ref = tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiReferenceExpression.class);
    }
    if (ref != null) {
      PsiElement target = ref.resolve();
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) return true;
    }
    return false;
  }

  public static boolean checkSideEffects(@NotNull PsiExpression element, @Nullable List<? super PsiElement> sideEffects) {
    return checkSideEffects(element, sideEffects, e -> false);
  }

  public static boolean checkSideEffects(@NotNull PsiExpression element,
                                         @Nullable List<? super PsiElement> sideEffects,
                                         @NotNull Predicate<? super PsiElement> ignoreElement) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(sideEffects, element, ignoreElement);
    element.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static @Unmodifiable List<PsiExpression> extractSideEffectExpressions(@NotNull PsiExpression element) {
    return extractSideEffectExpressions(element, e -> false);
  }

  public static @Unmodifiable List<PsiExpression> extractSideEffectExpressions(@NotNull PsiExpression element,
                                                                               @NotNull Predicate<? super PsiElement> ignoreElement) {
    List<PsiElement> list = new SmartList<>();
    element.accept(new SideEffectsVisitor(list, element, ignoreElement));
    return ContainerUtil.filterIsInstance(list, PsiExpression.class);
  }

  private static class SideEffectsVisitor extends JavaRecursiveElementWalkingVisitor {
    private final @Nullable List<? super PsiElement> mySideEffects;
    private final @NotNull PsiElement myStartElement;
    private final @NotNull Predicate<? super PsiElement> myIgnorePredicate;
    private @NotNull ThreeState found = ThreeState.NO;

    SideEffectsVisitor(@Nullable List<? super PsiElement> sideEffects, @NotNull PsiElement startElement) {
      this(sideEffects, startElement, call -> false);
    }

    SideEffectsVisitor(@Nullable List<? super PsiElement> sideEffects, @NotNull PsiElement startElement, @NotNull Predicate<? super PsiElement> predicate) {
      myStartElement = startElement;
      myIgnorePredicate = predicate;
      mySideEffects = sideEffects;
    }

    private boolean addSideEffect(PsiElement element) {
      return addSideEffect(element, ThreeState.YES);
    }

    private boolean addSideEffect(PsiElement element, ThreeState state) {
      if (state == ThreeState.NO || myIgnorePredicate.test(element)) return false;
      if (state == ThreeState.YES || state == ThreeState.UNSURE && found == ThreeState.NO) {
        found = state;
      }
      if(mySideEffects != null) {
        mySideEffects.add(element);
      } else if (found == ThreeState.YES) {
        stopWalking();
      }
      return true;
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      if (addSideEffect(expression)) return;
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      ThreeState sideEffect = getMethodSideEffect(method);
      if (addSideEffect(expression, sideEffect)) return;
      super.visitMethodCallExpression(expression);
    }

    private static @NotNull ThreeState getMethodSideEffect(PsiMethod method) {
      if (method == null) {
        return ThreeState.UNSURE;
      }
      PsiField field = PropertyUtil.getFieldOfGetter(method);
      if (field != null) {
        return ThreeState.fromBoolean(field.hasModifierProperty(PsiModifier.VOLATILE));
      }
      if (mayHaveExceptionalSideEffect(method)) {
        return ThreeState.UNSURE;
      }
      MutationSignature mutationSignature = MutationSignature.fromMethod(method);
      if (mutationSignature.isPure()) {
        return ThreeState.NO;
      }
      if (mutationSignature.mutatesAnything() || PropertyUtil.getFieldOfSetter(method) != null) {
        return ThreeState.YES;
      }
      return ThreeState.UNSURE;
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      if (addSideEffect(expression, getConstructorSideEffect(expression))) return;
      super.visitNewExpression(expression);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS)) {
        if (addSideEffect(expression)) return;
      }
      super.visitUnaryExpression(expression);
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      List<PsiPatternVariable> variables = JavaPsiPatternUtil.getExposedPatternVariables(expression);
      if (!variables.isEmpty() &&
          !PsiTreeUtil.isAncestor(myStartElement, variables.get(0).getDeclarationScope(), false)) {
        if (addSideEffect(expression)) return;
      }
      super.visitInstanceOfExpression(expression);
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      if (variable instanceof PsiPatternVariable) return;
      if (addSideEffect(variable)) return;
      super.visitVariable(variable);
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null || !PsiTreeUtil.isAncestor(myStartElement, exitedStatement, false)) {
        if (addSideEffect(statement)) return;
      }
      super.visitBreakStatement(statement);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // local or anonymous class declaration is not side effect per se (unless it's instantiated)
    }

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      PsiStatement exitedStatement = statement.findContinuedStatement();
      if (exitedStatement != null && PsiTreeUtil.isAncestor(myStartElement, exitedStatement, false)) return;
      if (addSideEffect(statement)) return;
      super.visitContinueStatement(statement);
    }

    @Override
    public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
      final PsiSwitchExpression enclosingExpression = statement.findEnclosingExpression();
      if (enclosingExpression == null || !PsiTreeUtil.isAncestor(myStartElement, enclosingExpression, false)) {
        if (addSideEffect(statement)) return;
      }
      super.visitYieldStatement(statement);
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      if (!(myStartElement.getParent() instanceof PsiParameterListOwner)) {
        if (addSideEffect(statement)) return;
      }
      super.visitReturnStatement(statement);
    }

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      if (addSideEffect(statement)) return;
      super.visitThrowStatement(statement);
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      // lambda is not side effect per se (unless it's called)
    }

    public boolean mayHaveSideEffects() {
      return found != ThreeState.NO;
    }
    
    public @NotNull ThreeState getSideEffectStatus() {
      return found;
    }
  }

  /**
   * Returns true if given method function is likely to throw an exception (e.g. "assertEquals"). In some cases this means that
   * the method call should be preserved in source code even if it's pure (i.e. does not change the program state).
   *
   * @param method a method to check
   * @return true if the method has exceptional side effect
   */
  public static boolean mayHaveExceptionalSideEffect(PsiMethod method) {
    String name = method.getName();
    if (name.startsWith("assert") || name.startsWith("check") || name.startsWith("require")) return true;
    PsiClass aClass = method.getContainingClass();
    if (InheritanceUtil.isInheritor(aClass, "org.assertj.core.api.Descriptable")) {
      // See com.intellij.codeInsight.DefaultInferredAnnotationProvider#getHardcodedContractAnnotation
      return true;
    }
    return ContainerUtil.exists(JavaMethodContractUtil.getMethodCallContracts(method, null),
                                mc -> !ContainerUtil.exists(mc.getConditions(), ContractValue::isBoundCheckingCondition)
                                      && mc.getReturnValue().isFail());
  }

  private static @NotNull ThreeState getConstructorSideEffect(@NotNull PsiNewExpression newExpression) {
    if (newExpression.isArrayCreation()) return ThreeState.NO;
    PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    if (anonymousClass != null && anonymousClass.getInitializers().length == 0) {
      PsiClass baseClass = anonymousClass.getBaseClassType().resolve();
      if (baseClass != null && baseClass.isInterface()) {
        return ThreeState.NO;
      }
    }
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    PsiClass aClass = classReference == null ? null : tryCast(classReference.resolve(), PsiClass.class);
    String qualifiedName = aClass == null ? null : aClass.getQualifiedName();
    if (qualifiedName == null) return ThreeState.UNSURE;
    if (ourSideEffectFreeClasses.contains(qualifiedName)) return ThreeState.NO;
    PsiMethod method = newExpression.resolveConstructor();
    if (method != null) {
      MutationSignature signature = MutationSignature.fromMethod(method);
      if (signature.isPure()) {
        return ThreeState.NO;
      }
      if (signature.mutatesAnything()) {
        return ThreeState.YES;
      }
    }

    PsiFile file = aClass.getContainingFile();
    PsiDirectory directory = file.getContainingDirectory();
    PsiPackage classPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
    String packageName = classPackage == null ? null : classPackage.getQualifiedName();

    // all Throwable descendants from java.lang are side effects free
    if (CommonClassNames.DEFAULT_PACKAGE.equals(packageName) || "java.io".equals(packageName)) {
      PsiClass throwableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, aClass.getResolveScope());
      if (throwableClass != null && com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf(aClass, throwableClass, true)) {
        return ThreeState.NO;
      }
    }
    if (method == null) {
      PsiClass superClass = aClass.getSuperClass();
      if (superClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        for (PsiClassInitializer initializer : aClass.getInitializers()) {
          if (!initializer.hasModifierProperty(PsiModifier.STATIC)) return ThreeState.UNSURE;
        }
        for (PsiField field : aClass.getFields()) {
          if (!field.hasModifierProperty(PsiModifier.STATIC) && field.hasInitializer()) return ThreeState.UNSURE;
        }
        return ThreeState.NO;
      }
    }
    return ThreeState.UNSURE;
  }
}
