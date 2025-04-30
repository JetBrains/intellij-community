// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.magicConstant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class MagicConstantUtils {
  private static AllowedValues getAllowedValuesFromMagic(@NotNull PsiType type,
                                                         @NotNull PsiAnnotation magic,
                                                         @NotNull PsiManager manager,
                                                         @Nullable PsiElement context) {
    PsiAnnotationMemberValue[] allowedValues = PsiAnnotationMemberValue.EMPTY_ARRAY;
    boolean values = false;
    boolean flags = false;
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue("intValues");
      if (intValues instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)intValues).getInitializers();
        if (initializers.length != 0) {
          allowedValues = initializers;
          values = true;
        }
      }
      if (!values) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue("flags");
        if (orValue instanceof PsiArrayInitializerMemberValue) {
          final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)orValue).getInitializers();
          if (initializers.length != 0) {
            allowedValues = initializers;
            flags = true;
          }
        }
      }
    }
    else if (type.equals(PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject())))) {
      PsiAnnotationMemberValue strValuesAttr = magic.findAttributeValue("stringValues");
      if (strValuesAttr instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)strValuesAttr).getInitializers();
        if (initializers.length != 0) {
          allowedValues = initializers;
          values = true;
        }
      }
    }
    else {
      return null; //other types not supported
    }

    PsiAnnotationMemberValue[] valuesFromClass = readFromClass("valuesFromClass", magic, type, manager, context);
    if (valuesFromClass != null) {
      allowedValues = ArrayUtil.mergeArrays(allowedValues, valuesFromClass, PsiAnnotationMemberValue.ARRAY_FACTORY);
      values = true;
    }
    PsiAnnotationMemberValue[] flagsFromClass = readFromClass("flagsFromClass", magic, type, manager, context);
    if (flagsFromClass != null) {
      allowedValues = ArrayUtil.mergeArrays(allowedValues, flagsFromClass, PsiAnnotationMemberValue.ARRAY_FACTORY);
      flags = true;
    }
    if (allowedValues.length == 0) {
      return null;
    }
    if (values && flags) {
      throw new IncorrectOperationException(
        "Misconfiguration of @MagicConstant annotation: 'flags' and 'values' shouldn't be used at the same time");
    }
    return new AllowedValues(allowedValues, flags);
  }

  private static PsiAnnotationMemberValue[] readFromClass(@NonNls @NotNull String attributeName,
                                                          @NotNull PsiAnnotation magic,
                                                          @NotNull PsiType type,
                                                          @NotNull PsiManager manager,
                                                          @Nullable PsiElement context) {
    PsiAnnotationMemberValue fromClassAttr = magic.findAttributeValue(attributeName);
    PsiType fromClassType = fromClassAttr instanceof PsiClassObjectAccessExpression
                            ? ((PsiClassObjectAccessExpression)fromClassAttr).getOperand().getType()
                            : null;
    PsiClass fromClass = fromClassType instanceof PsiClassType ? ((PsiClassType)fromClassType).resolve() : null;
    if (fromClass == null) return null;
    String fqn = fromClass.getQualifiedName();
    if (fqn == null) return null;
    List<PsiAnnotationMemberValue> constants = new ArrayList<>();
    for (PsiField field : fromClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) continue;
      if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
        if (context == null ||
            !JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper().isAccessible(field, context, null)) {
          continue;
        }
      }
      PsiType fieldType = field.getType();
      if (!Comparing.equal(fieldType, type)) continue;
      PsiAssignmentExpression e = (PsiAssignmentExpression)JavaPsiFacade.getElementFactory(manager.getProject())
        .createExpressionFromText("x=" + fqn + "." + field.getName(), field);
      PsiReferenceExpression refToField = (PsiReferenceExpression)e.getRExpression();
      constants.add(refToField);
    }
    if (constants.isEmpty()) return null;

    return constants.toArray(PsiAnnotationMemberValue.EMPTY_ARRAY);
  }

  /**
   * Generates a user-friendly textual representation of a value based on magic constant annotations, if possible.
   * Must be run inside the read action
   * 
   * @param val   value (number or string) which may have a magic constant representation
   * @param owner an owner that produced this value (either the variable which stores it, or a method which returns it)
   * @return a textual representation of the magic constant; null if non-applicable
   */
  @RequiresReadLock
  public static @Nullable String getPresentableText(Object val, @NotNull PsiModifierListOwner owner) {
    if (!(val instanceof String) &&
        !(val instanceof Integer) &&
        !(val instanceof Long) &&
        !(val instanceof Short) &&
        !(val instanceof Byte)) {
      return null;
    }
    PsiType type = PsiUtil.getTypeByPsiElement(owner);
    if (type == null) return null;
    AllowedValues allowedValues = getAllowedValues(owner, type, owner);
    if (allowedValues == null) return null;

    if (!allowedValues.isFlagSet()) {
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression expression) {
          Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
          if (val.equals(constantValue)) {
            return expression instanceof PsiReferenceExpression ref ? ref.getReferenceName() : expression.getText();
          }
        }
      }
    }
    else {
      if (!(val instanceof Number number)) return null;

      // try to find ored flags
      long remainingFlags = number.longValue();
      List<PsiAnnotationMemberValue> flags = new ArrayList<>();
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression expression) {
          Long constantValue = evaluateLongConstant(expression);
          if (constantValue == null) {
            continue;
          }
          if ((remainingFlags & constantValue) == constantValue) {
            flags.add(value);
            remainingFlags &= ~constantValue;
          }
        }
      }
      if (remainingFlags == 0) {
        // found flags to combine with OR, suggest the fix
        if (flags.size() > 1) {
          for (int i = flags.size() - 1; i >= 0; i--) {
            PsiAnnotationMemberValue flag = flags.get(i);
            Long flagValue = evaluateLongConstant((PsiExpression)flag);
            if (flagValue != null && flagValue == 0) {
              // no sense in ORing with '0'
              flags.remove(i);
            }
          }
        }
        if (!flags.isEmpty()) {
          return StreamEx.of(flags)
            .map(flag -> flag instanceof PsiReferenceExpression ref ? ref.getReferenceName() : flag.getText())
            .joining(" | ");
        }
      }
    }
    return null;
  }

  /**
   * @param element element with possible MagicConstant annotation
   * @param type    element type
   * @param context context where annotation is applied (to check the accessibility of magic constant)
   * @return possible allowed values to be used instead of constant literal; null if no MagicConstant annotation found
   */
  public static @Nullable AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element,
                                               @Nullable PsiType type,
                                               @Nullable PsiElement context) {
    return getAllowedValues(element, type, context, null);
  }

  static @Nullable AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element,
                                                  @Nullable PsiType type,
                                                  @Nullable PsiElement context,
                                                  @Nullable Set<? super PsiModifierListOwner> visited) {
    if (visited != null && visited.size() > 5) return null; // Avoid too deep traversal
    PsiManager manager = element.getManager();
    for (PsiAnnotation annotation : getAllAnnotations(element)) {
      if (type != null && MagicConstant.class.getName().equals(annotation.getQualifiedName())) {
        AllowedValues values = getAllowedValuesFromMagic(type, annotation, manager, context);
        if (values != null) return values;
      }

      PsiClass aClass = annotation.resolveAnnotationType();
      if (aClass == null) continue;

      if (visited == null) {
        visited = new HashSet<>();
      }
      if (!visited.add(aClass)) {
        continue;
      }
      AllowedValues values = getAllowedValues(aClass, type, context, visited);
      if (values != null) {
        return values;
      }
    }
    
    if (element instanceof PsiLocalVariable localVariable) {
      PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
      if (initializer != null) {
        PsiModifierListOwner target = null;
        if (initializer instanceof PsiMethodCallExpression call) {
          target = call.resolveMethod();
        } else if (initializer instanceof PsiReferenceExpression ref) {
          target = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
        }
        if (target != null) {
          PsiElement block = PsiUtil.getVariableCodeBlock(localVariable, null);
          if (block != null && ControlFlowUtil.isEffectivelyFinal(localVariable, block)) {
            if (visited == null) {
              visited = new HashSet<>();
            }
            if (visited.add(target)) {
              return getAllowedValues(target, type, context, visited);
            }
          }
        }
      }
    }

    return parseBeanInfo(element, manager);
  }

  private static PsiAnnotation @NotNull [] getAllAnnotations(@NotNull PsiModifierListOwner element) {
    PsiModifierListOwner realElement = getSourceElement(element);
    return CachedValuesManager.getCachedValue(realElement, () ->
      CachedValueProvider.Result.create(AnnotationUtil.getAllAnnotations(realElement, true, null, false),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static PsiModifierListOwner getSourceElement(@NotNull PsiModifierListOwner element) {
    if (element instanceof PsiCompiledElement) {
      PsiElement navigationElement = element.getNavigationElement();
      if (navigationElement instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner)navigationElement;
      }
    }
    return element;
  }

  private static AllowedValues parseBeanInfo(@NotNull PsiModifierListOwner owner, @NotNull PsiManager manager) {
    PsiUtilCore.ensureValid(owner);
    PsiFile containingFile = owner.getContainingFile();
    if (containingFile != null) {
      PsiUtilCore.ensureValid(containingFile);
      if (!containsBeanInfoText((PsiFile)containingFile.getNavigationElement())) {
        return null;
      }
    }
    PsiMethod method = null;
    if (owner instanceof PsiParameter parameter) {
      PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod)) return null;
      PsiElement nav = scope.getNavigationElement();
      if (!(nav instanceof PsiMethod)) return null;
      method = (PsiMethod)nav;
      if (method.isConstructor()) {
        // not a property, try the @ConstructorProperties({"prop"})
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "java.beans.ConstructorProperties");
        if (annotation == null) return null;
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (!(value instanceof PsiArrayInitializerMemberValue)) return null;
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        PsiElement parent = parameter.getParent();
        if (!(parent instanceof PsiParameterList)) return null;
        int index = ((PsiParameterList)parent).getParameterIndex(parameter);
        if (index >= initializers.length) return null;
        PsiAnnotationMemberValue initializer = initializers[index];
        if (!(initializer instanceof PsiLiteralExpression)) return null;
        Object val = ((PsiLiteralExpression)initializer).getValue();
        if (!(val instanceof String)) return null;
        PsiMethod setter = PropertyUtilBase.findPropertySetter(method.getContainingClass(), (String)val, false, false);
        if (setter == null) return null;
        // try the @beaninfo of the corresponding setter
        PsiElement navigationElement = setter.getNavigationElement();
        if (!(navigationElement instanceof PsiMethod)) return null;
        method = (PsiMethod)navigationElement;
      }
    }
    else if (owner instanceof PsiMethod) {
      PsiElement nav = owner.getNavigationElement();
      if (!(nav instanceof PsiMethod)) return null;
      method = (PsiMethod)nav;
    }
    if (method == null) return null;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      List<PsiMethod> setters = PropertyUtilBase.getSetters(aClass, PropertyUtilBase.getPropertyNameByGetter(method));
      if (setters.size() != 1) return null;
      method = setters.get(0);
    }
    if (!PropertyUtilBase.isSimplePropertySetter(method)) return null;
    PsiDocComment doc = method.getDocComment();
    if (doc == null) return null;
    PsiDocTag beaninfo = doc.findTagByName("beaninfo");
    if (beaninfo == null) return null;
    String data = StringUtil.join(beaninfo.getDataElements(), PsiElement::getText, "\n");
    int enumIndex = StringUtil.indexOfSubstringEnd(data, "enum:");
    if (enumIndex == -1) return null;
    data = data.substring(enumIndex);
    int colon = data.indexOf(':');
    int last = colon == -1 ? data.length() : data.substring(0,colon).lastIndexOf('\n');
    data = data.substring(0, last);

    List<PsiAnnotationMemberValue> values = new ArrayList<>();
    for (String line : StringUtil.splitByLines(data)) {
      List<String> words = StringUtil.split(line, " ", true, true);
      if (words.size() != 2) continue;
      String ref = words.get(1);
      PsiExpression constRef = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(ref, aClass);
      if (!(constRef instanceof PsiReferenceExpression expr)) continue;
      values.add(expr);
    }
    if (values.isEmpty()) return null;
    PsiAnnotationMemberValue[] array = values.toArray(PsiAnnotationMemberValue.EMPTY_ARRAY);
    return new AllowedValues(array, false);
  }

  private static boolean containsBeanInfoText(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () ->
      CachedValueProvider.Result.create(PsiSearchHelper.getInstance(file.getProject()).hasIdentifierInFile(file, "beaninfo") &&
                                        PsiSearchHelper.getInstance(file.getProject()).hasIdentifierInFile(file, "enum"),
                                        file));
  }

  static boolean same(@NotNull PsiElement e1, @NotNull PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression && e2 instanceof PsiLiteralExpression) {
      return Comparing.equal(((PsiLiteralExpression)e1).getValue(), ((PsiLiteralExpression)e2).getValue());
    }
    if (e1 instanceof PsiPrefixExpression && e2 instanceof PsiPrefixExpression && ((PsiPrefixExpression)e1).getOperationTokenType() == ((PsiPrefixExpression)e2).getOperationTokenType()) {
      PsiExpression lOperand = ((PsiPrefixExpression)e1).getOperand();
      PsiExpression rOperand = ((PsiPrefixExpression)e2).getOperand();
      return lOperand != null && rOperand != null && same(lOperand, rOperand, manager);
    }
    if (e1 instanceof PsiReference && e2 instanceof PsiReference) {
      e1 = ((PsiReference)e1).resolve();
      e2 = ((PsiReference)e2).resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  static Long evaluateLongConstant(@NotNull PsiExpression expression) {
    Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
    if (constantValue instanceof Long ||
        constantValue instanceof Integer ||
        constantValue instanceof Short ||
        constantValue instanceof Byte) {
      return ((Number)constantValue).longValue();
    }
    return null;
  }

  public static class AllowedValues {
    private final PsiAnnotationMemberValue @NotNull [] values;
    private final boolean canBeOred;
    private final boolean resolvesToZero; //true if one if the values resolves to literal 0, e.g. "int PLAIN = 0"

    AllowedValues(PsiAnnotationMemberValue @NotNull [] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
      resolvesToZero = resolvesToZero();
    }

    private boolean resolvesToZero() {
      for (PsiAnnotationMemberValue value : values) {
        if (value instanceof PsiExpression) {
          Object evaluated = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)value, null, false);
          if (evaluated instanceof Integer && ((Integer)evaluated).intValue() == 0) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues)o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = ContainerUtil.newHashSet(values);
      Set<PsiAnnotationMemberValue> v2 = ContainerUtil.newHashSet(a2.values);
      if (v1.size() != v2.size()) {
        return false;
      }
      for (PsiAnnotationMemberValue value : v1) {
        for (PsiAnnotationMemberValue value2 : v2) {
          if (same(value, value2, value.getManager())) {
            v2.remove(value2);
            break;
          }
        }
      }
      return v2.isEmpty();
    }
    @Override
    public int hashCode() {
      int result = Arrays.hashCode(values);
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }

    boolean isSubsetOf(@NotNull AllowedValues other, @NotNull PsiManager manager) {
      return Arrays.stream(values).allMatch(
        value -> Arrays.stream(other.values).anyMatch(otherValue -> same(value, otherValue, manager)));
    }

    public PsiAnnotationMemberValue @NotNull [] getValues() {
      return values;
    }

    /**
     * @return true if values represent a flag set, so can be combined via bitwise or
     */
    public boolean isFlagSet() {
      return canBeOred;
    }

    /**
     * @return true if at least one of values equals to integer 0
     */
    public boolean hasZeroValue() {
      return resolvesToZero;
    }
  }
}
