// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.JavaVarTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JavaPsiPatternUtil {

  private static final String JAVA_MATH_BIG_DECIMAL = "java.math.BigDecimal";

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
      parent instanceof PsiConditionalExpression || parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement ||
      parent instanceof PsiSwitchLabeledRuleStatement; // in guard
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

  /**
   * @return extracted pattern variable or null if the pattern is incomplete or unknown
   */
  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiPatternVariable getPatternVariable(@Nullable PsiCaseLabelElement pattern) {
    if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getPatternVariable();
    }
    if (pattern instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)pattern).getPatternVariable();
    }
    return null;
  }

  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiPrimaryPattern getTypedPattern(@Nullable PsiCaseLabelElement element) {
    if (element instanceof PsiDeconstructionPattern) {
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
   * Checks if the pattern declares one or more pattern variables
   *
   * @param pattern pattern to check
   * @return {@code true} if the pattern declares one or more pattern variables, {@code false} otherwise.
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean containsNamedPatternVariable(@Nullable PsiCaseLabelElement pattern) {
    if (pattern instanceof PsiTypeTestPattern) {
      PsiPatternVariable variable = ((PsiTypeTestPattern)pattern).getPatternVariable();
      return variable != null && !variable.isUnnamed();
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)pattern;
      return deconstructionPattern.getPatternVariable() != null ||
             ContainerUtil.exists(deconstructionPattern.getDeconstructionList().getDeconstructionComponents(),
                                  component -> containsNamedPatternVariable(component));
    }
    return false;
  }

  public static boolean isGuarded(@NotNull PsiCaseLabelElement pattern) {
    PsiElement parent = pattern.getParent();
    if (parent instanceof PsiCaseLabelElementList) {
      PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiSwitchLabelStatementBase) {
        PsiExpression guardExpression = ((PsiSwitchLabelStatementBase)gParent).getGuardExpression();
        if (guardExpression != null && !Boolean.TRUE.equals(evaluateConstant(guardExpression))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return type of variable in pattern, or null if pattern is incomplete
   */
  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiType getPatternType(@Nullable PsiCaseLabelElement pattern) {
    PsiTypeElement typeElement = getPatternTypeElement(pattern);
    if (typeElement == null) return null;
    return typeElement.getType();
  }

  public static @Nullable PsiTypeElement getPatternTypeElement(@Nullable PsiCaseLabelElement pattern) {
    if (pattern == null) return null;
    else if (pattern instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)pattern).getTypeElement();
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getCheckType();
    }
    else if (pattern instanceof PsiUnnamedPattern) {
      return ((PsiUnnamedPattern)pattern).getTypeElement();
    }
    return null;
  }

  @Contract(value = "null, _ -> false", pure = true)
  public static boolean isUnconditionalForType(@Nullable PsiCaseLabelElement pattern, @NotNull PsiType type) {
    return isUnconditionalForType(pattern, type, false) && !isGuarded(pattern);
  }

  public static @Nullable PsiPrimaryPattern findUnconditionalPattern(@Nullable PsiCaseLabelElement pattern) {
    if (pattern == null || isGuarded(pattern)) return null;
    if (pattern instanceof PsiDeconstructionPattern || pattern instanceof PsiTypeTestPattern || pattern instanceof PsiUnnamedPattern) {
      return (PsiPrimaryPattern)pattern;
    }
    return null;
  }

  @Contract("null,_,_ -> false")
  public static boolean isUnconditionalForType(@Nullable PsiCaseLabelElement pattern, @NotNull PsiType type, boolean forDominationOfDeconstructionPatternType) {
    PsiPrimaryPattern unconditionalPattern = findUnconditionalPattern(pattern);
    if (unconditionalPattern == null) return false;
    PsiType patternType = getPatternType(unconditionalPattern);
    if (unconditionalPattern instanceof PsiDeconstructionPattern) {
      return forDominationOfDeconstructionPatternType && dominates(patternType, type);
    }
    else if ((unconditionalPattern instanceof PsiTypeTestPattern ||
              unconditionalPattern instanceof PsiUnnamedPattern)) {
      return isUnconditionallyExactForType(pattern, type, patternType);
    }
    return false;
  }

  /**
   * @param context context PSI element (to check language level and resolve boxed type if necessary)
   * @param type selector type
   * @param patternType pattern type
   * @return true if the supplied pattern type is unconditionally exact, that is cast from type to patternType 
   * always succeeds without data loss
   */
  public static boolean isUnconditionallyExactForType(@NotNull PsiElement context, @NotNull PsiType type, PsiType patternType) {
    type = TypeConversionUtil.erasure(type);
    if ((type instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) &&
        PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, context)) {
      if (type.equals(patternType)) return true;
      if (type instanceof PsiPrimitiveType && patternType instanceof PsiPrimitiveType) {
        return isExactPrimitiveWideningConversion(type, patternType);
      }
      else if (!(type instanceof PsiPrimitiveType)) {
        return false;
      }
      else {
        PsiClassType boxedType = ((PsiPrimitiveType)type).getBoxedType(context);
        return dominates(patternType, boxedType);
      }
    }
    else {
      return dominates(patternType, type);
    }
  }

  /**
   * Returns the promoted type for a given context, type, and pattern type, according to 5.7.1
   *
   * @param context     the context element
   * @param type        the type to be promoted
   * @param patternType the pattern type to compare with
   * @return the promoted type, or the original type if no promotion is necessary
   */
  public static @NotNull PsiType getExactlyPromotedType(@NotNull PsiElement context, @NotNull PsiType type, @NotNull PsiType patternType) {
    if (type.equals(patternType)) return type;
    if ((type.equals(PsiTypes.byteType()) ||
         type.equals(PsiTypes.shortType())) &&
        patternType.equals(PsiTypes.charType())) {
      return PsiTypes.intType();
    }
    else if ((patternType.equals(PsiTypes.byteType()) ||
              patternType.equals(PsiTypes.shortType())) &&
             type.equals(PsiTypes.charType())) {
      return PsiTypes.intType();
    }
    else if ((type.equals(PsiTypes.intType()) &&
              patternType.equals(PsiTypes.floatType())) ||
             (type.equals(PsiTypes.floatType()) &&
              patternType.equals(PsiTypes.intType()))) {
      return PsiTypes.doubleType();
    }
    else if ((type.equals(PsiTypes.longType()) &&
              patternType.equals(PsiTypes.floatType())) ||
             (type.equals(PsiTypes.floatType()) &&
              patternType.equals(PsiTypes.longType())) ||

             (type.equals(PsiTypes.longType()) &&
              patternType.equals(PsiTypes.doubleType())) ||
             (type.equals(PsiTypes.doubleType()) &&
              patternType.equals(PsiTypes.longType()))) {
      return PsiType.getTypeByName(JAVA_MATH_BIG_DECIMAL, context.getProject(), context.getResolveScope());
    }
    else {
      return TypeConversionUtil.isAssignable(patternType, type) ? patternType : type;
    }
  }

  /**
   * Checks if the given type is an exact primitive widening conversion of the pattern type according to 5.1.2
   *
   * @param type         the type to check
   * @param patternType  the pattern type to compare with
   * @return true if the given type is an exact primitive widening conversion of the pattern type, false otherwise
   */
  public static boolean isExactPrimitiveWideningConversion(@NotNull PsiType type, @NotNull PsiType patternType) {
    if (type.equals(PsiTypes.byteType())) {
      return patternType.equals(PsiTypes.shortType()) ||
             patternType.equals(PsiTypes.intType()) ||
             patternType.equals(PsiTypes.longType()) ||
             patternType.equals(PsiTypes.floatType()) ||
             patternType.equals(PsiTypes.doubleType());
    }
    if (type.equals(PsiTypes.shortType())) {
      return patternType.equals(PsiTypes.intType()) ||
             patternType.equals(PsiTypes.longType()) ||
             patternType.equals(PsiTypes.floatType()) ||
             patternType.equals(PsiTypes.doubleType());
    }
    if (type.equals(PsiTypes.charType())) {
      return patternType.equals(PsiTypes.intType()) ||
             patternType.equals(PsiTypes.longType()) ||
             patternType.equals(PsiTypes.floatType()) ||
             patternType.equals(PsiTypes.doubleType());
    }
    if (type.equals(PsiTypes.intType())) {
      return patternType.equals(PsiTypes.longType()) ||
             patternType.equals(PsiTypes.doubleType());
    }
    if (type.equals(PsiTypes.floatType())) {
      return patternType.equals(PsiTypes.doubleType());
    }
    return false;
  }

  /**
   * @param pattern deconstruction pattern to check
   * @return true if all components of a pattern are unconditional
   */
  public static boolean hasUnconditionalComponents(@NotNull PsiDeconstructionPattern pattern) {
    PsiType type = pattern.getTypeElement().getType();
    PsiPattern[] patternComponents = pattern.getDeconstructionList().getDeconstructionComponents();
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (selectorClass == null) return false;
    PsiRecordComponent[] recordComponents = selectorClass.getRecordComponents();
    if (patternComponents.length != recordComponents.length) return false;
    for (int i = 0; i < patternComponents.length; i++) {
      PsiPattern patternComponent = patternComponents[i];
      PsiType componentType = recordComponents[i].getType();
      if (!isUnconditionalForType(patternComponent, componentType)) {
        return false;
      }
    }
    return true;
  }

  private static boolean dominates(@Nullable PsiType who, @Nullable PsiType overWhom) {
    if (who == null || overWhom == null) return false;
    if (who.getCanonicalText().equals(overWhom.getCanonicalText())) return true;
    overWhom = TypeConversionUtil.erasure(overWhom);
    PsiType baseType = TypeConversionUtil.erasure(who);
    if(overWhom.equals(PsiTypes.nullType())) return who instanceof PsiClassType || who instanceof PsiArrayType;
    if (overWhom instanceof PsiArrayType || baseType instanceof PsiArrayType) {
      return baseType != null && TypeConversionUtil.isAssignable(baseType, overWhom);
    }
    PsiClass typeClass = PsiTypesUtil.getPsiClass(overWhom);
    PsiClass baseTypeClass = PsiTypesUtil.getPsiClass(baseType);
    return typeClass != null && baseTypeClass != null && InheritanceUtil.isInheritorOrSelf(typeClass, baseTypeClass, true);
  }

  // TODO
  /**
   * 14.30.3 Pattern Totality and Dominance
   */
  @Contract(value = "null, _ -> false", pure = true)
  public static boolean dominates(@Nullable PsiCaseLabelElement who, @NotNull PsiCaseLabelElement overWhom) {
    if (who == null) return false;
    PsiType overWhomType = getPatternType(overWhom);
    if (overWhomType == null || !isUnconditionalForType(who, overWhomType, true)) {
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
    return ObjectUtils.tryCast(element, PsiDeconstructionPattern.class);
  }

  /**
   * 14.11.1 Switch Blocks
   * @param overWhom - type of constant
   */
  @Contract(value = "_,null -> false", pure = true)
  public static boolean dominatesOverConstant(@NotNull PsiCaseLabelElement who, @Nullable PsiType overWhom) {
    if (overWhom == null) return false;
    who = findUnconditionalPattern(who);
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
  public static @Nullable PsiRecordComponent getRecordComponentForPattern(@NotNull PsiPattern pattern) {
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
    return getDeconstructedImplicitPatternType(parameter.getPattern());
  }

  public static @Nullable PsiType getDeconstructedImplicitPatternType(@NotNull PsiPattern pattern) {
    PsiRecordComponent recordComponent = getRecordComponentForPattern(pattern);
    if (recordComponent != null) {
      PsiDeconstructionList deconstructionList = ObjectUtils.tryCast(pattern.getParent(), PsiDeconstructionList.class);
      if (deconstructionList == null) return null;
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)deconstructionList.getParent();
      PsiType patternType = deconstructionPattern.getTypeElement().getType();
      if (patternType instanceof PsiClassType) {
        patternType = PsiUtil.captureToplevelWildcards(patternType, pattern);
        PsiSubstitutor substitutor = ((PsiClassType)patternType).resolveGenerics().getSubstitutor();
        PsiType recordComponentType = recordComponent.getType();
        return JavaVarTypeUtil.getUpwardProjection(substitutor.substitute(recordComponentType));
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

  private static @NotNull PsiPatternVariable createFakePatternVariable(@NotNull PsiPattern pattern,
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

  /**
   * @param pattern deconstruction pattern to find a context type for
   * @return a context type for the pattern; null, if it cannot be determined. This method can perform 
   * the inference for outer patterns if necessary.
   */
  public static @Nullable PsiType getContextType(@NotNull PsiPattern pattern) {
    PsiElement parent = pattern.getParent();
    if (parent instanceof PsiInstanceOfExpression) {
      return ((PsiInstanceOfExpression)parent).getOperand().getType();
    }
    if (parent instanceof PsiForeachPatternStatement) {
      PsiExpression iteratedValue = ((PsiForeachPatternStatement)parent).getIteratedValue();
      if (iteratedValue == null) {
        return null;
      }
      return JavaGenericsUtil.getCollectionItemType(iteratedValue);
    }
    if (parent instanceof PsiCaseLabelElementList) {
      PsiSwitchLabelStatementBase label = ObjectUtils.tryCast(parent.getParent(), PsiSwitchLabelStatementBase.class);
      if (label != null) {
        PsiSwitchBlock block = label.getEnclosingSwitchBlock();
        if (block != null) {
          PsiExpression expression = block.getExpression();
          if (expression != null) {
            return expression.getType();
          }
        }
      }
    }
    if (parent instanceof PsiDeconstructionList) {
      PsiDeconstructionPattern parentPattern = ObjectUtils.tryCast(parent.getParent(), PsiDeconstructionPattern.class);
      if (parentPattern != null) {
        int index = ArrayUtil.indexOf(((PsiDeconstructionList)parent).getDeconstructionComponents(), pattern);
        if (index < 0) return null;
        PsiType patternType = parentPattern.getTypeElement().getType();
        if (!(patternType instanceof PsiClassType)) return null;
        PsiSubstitutor parentSubstitutor = ((PsiClassType)patternType).resolveGenerics().getSubstitutor();
        PsiClass parentRecord = PsiUtil.resolveClassInClassTypeOnly(parentPattern.getTypeElement().getType());
        if (parentRecord == null) return null;
        PsiRecordComponent[] components = parentRecord.getRecordComponents();
        if (index >= components.length) return null;
        return parentSubstitutor.substitute(components[index].getType());
      }
    }
    return null;
  }

  /**
   * @param selectorType pattern selector type
   * @return list of basic types that contain no intersections or type parameters
   */
  public static List<PsiType> deconstructSelectorType(@NotNull PsiType selectorType) {
    List<PsiType> selectorTypes = new ArrayList<>();
    PsiClass resolvedClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    //T is an intersection type T1& ... &Tn and P covers Ti, for one of the types Ti (1≤i≤n)
    if (resolvedClass instanceof PsiTypeParameter) {
      PsiClassType[] types = resolvedClass.getExtendsListTypes();
      Arrays.stream(types)
        .filter(t -> t != null)
        .forEach(t -> selectorTypes.add(t));
    }
    if (selectorType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)selectorType).getConjuncts()) {
        selectorTypes.addAll(deconstructSelectorType(conjunct));
      }
    }
    if (selectorTypes.isEmpty()) {
      selectorTypes.add(selectorType);
    }
    return selectorTypes;
  }

  /**
   * 
   * @param context context element
   * @param whoType type that should cover the overWhom type
   * @param overWhom type that needs to be covered
   * @return true if whoType overs overWhom type
   */
  public static boolean covers(@NotNull PsiElement context, @NotNull PsiType whoType, @NotNull PsiType overWhom) {
    List<PsiType> whoTypes = deconstructSelectorType(whoType);
    List<PsiType> overWhomTypes = deconstructSelectorType(overWhom);
    for (PsiType currentWhoType : whoTypes) {
      if (!ContainerUtil.exists(overWhomTypes, currentOverWhomType -> {
        boolean unconditionallyExactForType =
          isUnconditionallyExactForType(context, currentOverWhomType, currentWhoType);
        if (unconditionallyExactForType) return true;
        PsiPrimitiveType unboxedOverWhomType = PsiPrimitiveType.getUnboxedType(currentOverWhomType);
        if (unboxedOverWhomType == null) return false;
        return isUnconditionallyExactForType(context, unboxedOverWhomType, currentWhoType);
      })) {
        return false;
      }
    }
    return true;
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

  private static @Nullable Object evaluateConstant(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
      .computeConstantExpression(expression, false);
  }
}
