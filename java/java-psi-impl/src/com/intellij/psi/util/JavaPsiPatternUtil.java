// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JavaPsiPatternUtil {
  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression.
   */
  @Contract(pure = true)
  public static @NotNull List<PsiPatternVariable> getExposedPatternVariables(@NotNull PsiExpression expression) {
    List<PatternVariableWrapper> list = collectPatternVariableWrappers(expression);
    return StreamEx.of(list).filter(base -> !base.isFake()).map(PatternVariableWrapper::getVariable).toList();
  }

  /**
   * @param expression expression to collect pattern variable wrappers for
   * @return list of pattern variable wrappers for:
   * <ul>
   *   <li>pattern variables declared within an expression that could be visible outside of given expression</li>
   *   <li>fake pattern variables. They are needed for extracting nested patterns from expression.</li>
   * <p>
   * {@code if (obj instanceof Point(double x, doubly y))  } - to extract x and y from the condition
   * we need a fake pattern variable for Point
   * </ul>
   */
  @Contract(pure = true)
  public static @NotNull List<PatternVariableWrapper> collectPatternVariableWrappers(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    boolean parentMayAccept =
      parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(JavaTokenType.EXCL) ||
      parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.ANDAND) ||
      parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.OROR) ||
      parent instanceof PsiConditionalExpression || parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement;
    if (!parentMayAccept) {
      return Collections.emptyList();
    }
    List<PatternVariableWrapper> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list, false);
    return list;
  }

  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression
   * under some other parent (e.g. under PsiIfStatement).
   */
  @Contract(pure = true)
  public static @NotNull List<PsiPatternVariable> getExposedPatternVariablesIgnoreParent(@NotNull PsiExpression expression) {
    List<PatternVariableWrapper> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list, true);
    return StreamEx.of(list).filter(base -> !base.isFake()).map(PatternVariableWrapper::getVariable).toList();
  }

  /**
   * @param variable pattern variable
   * @return effective initializer expression for the variable; null if cannot be determined.
   * Returns null for inner record patterns because an instanceof operand may not be safely recomputable expression
   * @see com.siyeh.ig.psiutils.ExpressionUtils#isSafelyRecomputableExpression(PsiExpression)
   * For inner record patterns consider using
   * @see JavaPsiPatternUtil#collectPatternVariableWrappers(PsiExpression)
   * @see DestructionComponent#getEffectiveInitializerText()
   *
   */
  public static @Nullable String getEffectiveInitializerText(@NotNull PsiPatternVariable variable) {
    PsiPattern pattern = variable.getPattern();
    PsiInstanceOfExpression instanceOf = ObjectUtils.tryCast(pattern.getParent(), PsiInstanceOfExpression.class);
    if (instanceOf == null) return null;
    PsiExpression operand = instanceOf.getOperand();
    PsiTypeElement checkType;
    if (pattern instanceof PsiTypeTestPattern) {
      checkType = ((PsiTypeTestPattern)pattern).getCheckType();
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      checkType = ((PsiDeconstructionPattern)pattern).getTypeElement();
    }
    else {
      checkType = null;
    }
    if (checkType == null) return null;
    if (checkType.getType().equals(operand.getType())) {
      return operand.getText();
    }
    return "(" + checkType.getText() + ")" + operand.getText();
  }

  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiPattern skipParenthesizedPatternDown(PsiPattern pattern) {
    while (pattern instanceof PsiParenthesizedPattern) {
      pattern = ((PsiParenthesizedPattern)pattern).getPattern();
    }
    return pattern;
  }

  public static PsiElement skipParenthesizedPatternUp(PsiElement parent) {
    while (parent instanceof PsiParenthesizedPattern) {
      parent = parent.getParent();
    }
    return parent;
  }

  /**
   * @return extracted pattern variable or null if the pattern is incomplete or unknown
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiPatternVariable getPatternVariable(@Nullable PsiCaseLabelElement pattern) {
    if (pattern instanceof PsiGuardedPattern) {
      return getPatternVariable(((PsiGuardedPattern)pattern).getPrimaryPattern());
    }
    if (pattern instanceof PsiPatternGuard) {
      return getPatternVariable(((PsiPatternGuard)pattern).getPattern());
    }
    if (pattern instanceof PsiParenthesizedPattern) {
      return getPatternVariable(((PsiParenthesizedPattern)pattern).getPattern());
    }
    if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getPatternVariable();
    }
    if (pattern instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)pattern).getPatternVariable();
    }
    return null;
  }

  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiPrimaryPattern getTypedPattern(@Nullable PsiCaseLabelElement element) {
    if (element instanceof PsiGuardedPattern) {
      return getTypedPattern(((PsiGuardedPattern)element).getPrimaryPattern());
    }
    else if (element instanceof PsiPatternGuard) {
      return getTypedPattern(((PsiPatternGuard)element).getPattern());
    }
    else if (element instanceof PsiParenthesizedPattern) {
      return getTypedPattern(((PsiParenthesizedPattern)element).getPattern());
    }
    else if (element instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)element);
    }
    else if (element instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)element);
    }
    else {
      return null;
    }
  }

  /**
   * @return type of variable in pattern, or null if pattern is incomplete
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiType getPatternType(@Nullable PsiCaseLabelElement pattern) {
    PsiTypeElement typeElement = getPatternTypeElement(pattern);
    if (typeElement == null) return null;
    return typeElement.getType();
  }

  public static @Nullable PsiTypeElement getPatternTypeElement(@Nullable PsiCaseLabelElement pattern) {
    if (pattern == null) return null;
    if (pattern instanceof PsiGuardedPattern) {
      return getPatternTypeElement(((PsiGuardedPattern)pattern).getPrimaryPattern());
    }
    else if (pattern instanceof PsiPatternGuard) {
      return getPatternTypeElement(((PsiPatternGuard)pattern).getPattern());
    }
    else if (pattern instanceof PsiParenthesizedPattern) {
      return getPatternTypeElement(((PsiParenthesizedPattern)pattern).getPattern());
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)pattern).getTypeElement();
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getCheckType();
    }
    return null;
  }

  @Contract(value = "null, _ -> false", pure = true)
  public static boolean isTotalForType(@Nullable PsiCaseLabelElement pattern, @NotNull PsiType type) {
    return isTotalForType(pattern, type, false);
  }

  public static boolean isTotalForType(@Nullable PsiCaseLabelElement pattern, @NotNull PsiType type, boolean forDomination) {
    if (pattern == null) return false;
    if (pattern instanceof PsiPatternGuard) {
      PsiPatternGuard guarded = (PsiPatternGuard)pattern;
      Object constVal = evaluateConstant(guarded.getGuardingExpression());
      return isTotalForType(guarded.getPattern(), type, forDomination) && Boolean.TRUE.equals(constVal);
    }
    if (pattern instanceof PsiGuardedPattern) {
      PsiGuardedPattern guarded = (PsiGuardedPattern)pattern;
      Object constVal = evaluateConstant(guarded.getGuardingExpression());
      return isTotalForType(guarded.getPrimaryPattern(), type, forDomination) && Boolean.TRUE.equals(constVal);
    }
    else if (pattern instanceof PsiParenthesizedPattern) {
      return isTotalForType(((PsiParenthesizedPattern)pattern).getPattern(), type, forDomination);
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      return forDomination && dominates(getPatternType(pattern), type);
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      return dominates(getPatternType(pattern), type);
    }
    return false;
  }

  /**
   * @param pattern deconstruction pattern to check
   * @return true if all components of a pattern are total
   */
  public static boolean hasTotalComponents(@NotNull PsiDeconstructionPattern pattern) {
    PsiType type = pattern.getTypeElement().getType();
    PsiPattern[] patternComponents = pattern.getDeconstructionList().getDeconstructionComponents();
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (selectorClass == null) return false;
    PsiRecordComponent[] recordComponents = selectorClass.getRecordComponents();
    if (patternComponents.length != recordComponents.length) return false;
    for (int i = 0; i < patternComponents.length; i++) {
      PsiPattern patternComponent = patternComponents[i];
      PsiType componentType = recordComponents[i].getType();
      if (!isTotalForType(patternComponent, componentType)) {
        return false;
      }
    }
    return true;
  }

  public static boolean dominates(@Nullable PsiType who, @Nullable PsiType overWhom) {
    if (who == null || overWhom == null) return false;
    if (who.getCanonicalText().equals(overWhom.getCanonicalText())) return true;
    overWhom = TypeConversionUtil.erasure(overWhom);
    PsiType baseType = TypeConversionUtil.erasure(who);
    if (overWhom instanceof PsiArrayType || baseType instanceof PsiArrayType) {
      return baseType != null && TypeConversionUtil.isAssignable(baseType, overWhom);
    }
    PsiClass typeClass = PsiTypesUtil.getPsiClass(overWhom);
    PsiClass baseTypeClass = PsiTypesUtil.getPsiClass(baseType);
    return typeClass != null && baseTypeClass != null && InheritanceUtil.isInheritorOrSelf(typeClass, baseTypeClass, true);
  }

  /**
   * 14.30.3 Pattern Totality and Dominance
   */
  @Contract(value = "null, _ -> false", pure = true)
  public static boolean dominates(@Nullable PsiCaseLabelElement who, @NotNull PsiCaseLabelElement overWhom) {
    if (who == null) return false;
    PsiType overWhomType = getPatternType(overWhom);
    if (overWhomType == null || !isTotalForType(who, overWhomType, true)) {
      return false;
    }
    PsiDeconstructionPattern whoDeconstruction = findDeconstructionPattern(who);
    if (whoDeconstruction != null) {
      PsiDeconstructionPattern overWhomDeconstruction = findDeconstructionPattern(overWhom);
      return dominatesComponents(whoDeconstruction, overWhomDeconstruction);
    }
    return true;
  }

  private static boolean dominatesComponents(@NotNull PsiDeconstructionPattern who, @Nullable PsiDeconstructionPattern overWhom) {
    if (overWhom == null) return false;
    PsiPattern[] whoComponents = who.getDeconstructionList().getDeconstructionComponents();
    PsiPattern[] overWhomComponents = overWhom.getDeconstructionList().getDeconstructionComponents();
    if (whoComponents.length != overWhomComponents.length) return false;
    for (int i = 0; i < whoComponents.length; i++) {
      PsiPattern whoComponent = whoComponents[i];
      PsiPattern overWhomComponent = overWhomComponents[i];
      if (!dominates(whoComponent, overWhomComponent)) return false;
    }
    return true;
  }

  public static @Nullable PsiDeconstructionPattern findDeconstructionPattern(@Nullable PsiCaseLabelElement element) {
    if (element instanceof PsiParenthesizedPattern) {
      return findDeconstructionPattern(((PsiParenthesizedPattern)element).getPattern());
    }
    else if (element instanceof PsiPatternGuard) {
      return findDeconstructionPattern(((PsiPatternGuard)element).getPattern());
    }
    else if (element instanceof PsiDeconstructionPattern) {
      return (PsiDeconstructionPattern)element;
    }
    else {
      return null;
    }
  }

  /**
   * 14.11.1 Switch Blocks
   */
  @Contract(value = "_,null -> false", pure = true)
  public static boolean dominates(@NotNull PsiCaseLabelElement who, @Nullable PsiType overWhom) {
    if (overWhom == null) return false;
    PsiType whoType = TypeConversionUtil.erasure(getPatternType(who));
    if (whoType == null) return false;
    PsiType overWhomType = null;
    if (overWhom instanceof PsiPrimitiveType) {
      overWhomType = ((PsiPrimitiveType)overWhom).getBoxedType(who);
    }
    else if (overWhom instanceof PsiClassType) {
      overWhomType = overWhom;
    }
    return overWhomType != null && TypeConversionUtil.areTypesConvertible(overWhomType, whoType);
  }

  @Contract(pure = true)
  @Nullable
  public static PsiRecordComponent getRecordComponentForPattern(@NotNull PsiPattern pattern) {
    PsiDeconstructionList deconstructionList = ObjectUtils.tryCast(pattern.getParent(), PsiDeconstructionList.class);
    if (deconstructionList == null) return null;
    @NotNull PsiPattern @NotNull [] patterns = deconstructionList.getDeconstructionComponents();
    int index = ArrayUtil.indexOf(patterns, pattern);
    PsiDeconstructionPattern deconstructionPattern = ObjectUtils.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
    if (deconstructionPattern == null) return null;
    PsiClassType classType = ObjectUtils.tryCast(deconstructionPattern.getTypeElement().getType(), PsiClassType.class);
    if (classType == null) return null;
    PsiClass aClass = classType.resolve();
    if (aClass == null) return null;
    PsiRecordComponent[] components = aClass.getRecordComponents();
    if (components.length <= index) return null;
    return components[index];
  }

  public static @Nullable PsiType getDeconstructedImplicitPatternVariableType(@NotNull PsiPatternVariable parameter) {
    PsiRecordComponent recordComponent = getRecordComponentForPattern(parameter.getPattern());
    if (recordComponent != null) {
      PsiTypeTestPattern pattern = (PsiTypeTestPattern)parameter.getParent();
      PsiDeconstructionList deconstructionList = ObjectUtils.tryCast(pattern.getParent(), PsiDeconstructionList.class);
      if (deconstructionList == null) return null;
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)deconstructionList.getParent();
      PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
      PsiClassType patternType = (PsiClassType)typeElement.getType();
      PsiType[] parameters = patternType.getParameters();
      PsiType recordComponentType = recordComponent.getType();
      PsiClass recordClass = recordComponent.getContainingClass();
      if (recordClass != null) {
        PsiTypeParameter[] typeParameters = recordClass.getTypeParameters();
        HashMap<PsiTypeParameter, PsiType> substitutor = new HashMap<>();
        int index = Math.min(typeParameters.length, parameters.length);
        for (int i = 0; i < index; i++) {
          PsiTypeParameter typeParameter = typeParameters[i];
          PsiType param = parameters[i];
          substitutor.put(typeParameter, param);
        }
        for (int i = index; i < typeParameters.length; i++) {
          PsiTypeParameter typeParam = typeParameters[i];
          substitutor.put(typeParam, null);
        }
        return PsiSubstitutor.createSubstitutor(substitutor).substitute(recordComponentType);
      }
    }
    return null;
  }

  private static void collectPatternVariableCandidates(@NotNull PsiExpression scope, @NotNull PsiExpression expression,
                                                       Collection<PatternVariableWrapper> candidates, boolean strict) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
      else if (expression instanceof PsiPrefixExpression &&
               ((PsiPrefixExpression)expression).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        expression = ((PsiPrefixExpression)expression).getOperand();
      }
      else {
        break;
      }
    }
    if (expression instanceof PsiInstanceOfExpression) {
      PsiPattern pattern = ((PsiInstanceOfExpression)expression).getPattern();
      if (pattern instanceof PsiTypeTestPattern || pattern instanceof PsiDeconstructionPattern) {
        collectPatternVariableCandidates(pattern, scope, null, candidates, strict);
      }
    }
    if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          collectPatternVariableCandidates(scope, operand, candidates, strict);
        }
      }
    }
  }

  private static void collectPatternVariableCandidates(@NotNull PsiPattern pattern,
                                                       @NotNull PsiExpression scope,
                                                       @Nullable Pair<PsiPatternVariable, PsiRecordComponent> parent,
                                                       Collection<PatternVariableWrapper> candidates,
                                                       boolean strict) {
    if (pattern instanceof PsiTypeTestPattern) {
      PsiPatternVariable variable = ((PsiTypeTestPattern)pattern).getPatternVariable();
      if (variable != null && !PsiTreeUtil.isAncestor(scope, variable.getDeclarationScope(), strict)) {
        if (parent == null) {
          candidates.add(new PatternVariableWrapper(variable, false));
        }
        else {
          candidates.add(new DestructionComponent(variable, parent.getFirst(), parent.getSecond(), false));
        }
      }
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      PsiDeconstructionPattern deconstruction = (PsiDeconstructionPattern)pattern;
      PsiTypeElement typeElement = deconstruction.getTypeElement();
      PsiType type = typeElement.getType();
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      PsiPatternVariable variable = deconstruction.getPatternVariable();
      boolean isFake = variable == null;
      if (aClass != null) {
        PsiRecordComponent[] recordComponents = aClass.getRecordComponents();
        PsiPattern[] components = deconstruction.getDeconstructionList().getDeconstructionComponents();
        if (recordComponents.length == components.length && recordComponents.length != 0) {
          if (isFake) {
            variable = createFakePatternVariable(pattern, typeElement, type);
          }
          PatternVariableWrapper patternVariableWrapper =
            parent == null
            ? new PatternVariableWrapper(variable, isFake)
            : new DestructionComponent(variable, parent.getFirst(), parent.getSecond(), isFake);
          candidates.add(patternVariableWrapper);
          for (int i = 0; i < components.length; i++) {
            collectPatternVariableCandidates(components[i], scope, Pair.pair(variable, recordComponents[i]), candidates, strict);
          }
        }
      }
    }
  }

  @NotNull
  private static PsiPatternVariable createFakePatternVariable(@NotNull PsiPattern pattern,
                                                              @NotNull PsiTypeElement typeElement,
                                                              @NotNull PsiType type) {
    Project project = pattern.getProject();
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String name = styleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type).names[0];
    name = styleManager.suggestUniqueVariableName(name, pattern, true);
    /*
     If the pattern is not nested, we use the text of the operand matched against the pattern because the operand text
     of the created instanceof expression will be used in com.intellij.psi.util.JavaPsiPatternUtil.getEffectiveInitializerText.
     If the pattern is nested, we can use random text as operand text.
    */
    PsiElement parent = pattern.getParent();
    String operand = parent instanceof PsiInstanceOfExpression ? ((PsiInstanceOfExpression)parent).getOperand().getText() : "x";
    String text = operand + " instanceof " + typeElement.getText() + " " + name;
    PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)factory.createExpressionFromText(text, null);
    PsiPatternVariable variable = ((PsiTypeTestPattern)Objects.requireNonNull(instanceOf.getPattern())).getPatternVariable();
    assert variable != null;
    return variable;
  }

  public static class PatternVariableWrapper {
    private final @NotNull PsiPatternVariable myVariable;
    private final boolean myIsFake;

    PatternVariableWrapper(@NotNull PsiPatternVariable variable, boolean isFake) {
      myVariable = variable;
      myIsFake = isFake;
    }

    public @NotNull PsiPatternVariable getVariable() {
      return myVariable;
    }

    boolean isFake() {
      return myIsFake;
    }

    public String getEffectiveInitializerText() {
      return JavaPsiPatternUtil.getEffectiveInitializerText(myVariable);
    }
  }

  public static class DestructionComponent extends PatternVariableWrapper {
    private final @NotNull PsiPatternVariable myParent;
    private final @NotNull PsiRecordComponent myRecordComponent;

    DestructionComponent(@NotNull PsiPatternVariable variable,
              @NotNull PsiPatternVariable parent,
              @NotNull PsiRecordComponent recordComponent,
              boolean isFake) {
      super(variable, isFake);
      myParent = parent;
      myRecordComponent = recordComponent;
    }

    @Override
    public String getEffectiveInitializerText() {
      String text = myParent.getName() + "." + myRecordComponent.getName() + "()";
      PsiType type = getVariable().getType();
      if (!type.equals(myRecordComponent.getType())) {
        return "(" + type.getCanonicalText() + ")" + text;
      }
      return text;
    }
  }

  @Nullable
  private static Object evaluateConstant(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
      .computeConstantExpression(expression, false);
  }
}
