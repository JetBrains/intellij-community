// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.*;
import java.util.stream.Stream;

public class PropertyUtilBase {

  @NonNls protected static final String GET_PREFIX = PropertyKind.GETTER.prefix;
  @NonNls protected static final String IS_PREFIX = PropertyKind.BOOLEAN_GETTER.prefix;
  @NotNull protected static final String SET_PREFIX = PropertyKind.SETTER.prefix;

  private static final @NonNls @PsiModifier.ModifierConstant String @NotNull [] ONLY_PUBLIC = new String[]{PsiModifier.PUBLIC};

  @Nullable
  public static String getPropertyName(@NonNls @NotNull String methodName) {
    return StringUtil.getPropertyName(methodName);
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(@NotNull final PsiClass psiClass,
                                                        final boolean acceptSetters,
                                                        final boolean acceptGetters) {
    return getAllProperties(psiClass, acceptSetters, acceptGetters, true);
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(@NotNull final PsiClass psiClass,
                                                        final boolean acceptSetters,
                                                        final boolean acceptGetters,
                                                        final boolean includeSuperClass) {
    return getAllProperties(acceptSetters, acceptGetters, includeSuperClass ? psiClass.getAllMethods() : psiClass.getMethods());
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(final boolean acceptSetters,
                                                        final boolean acceptGetters, PsiMethod[] methods) {
    return getAllProperties(acceptSetters, acceptGetters, false, ONLY_PUBLIC, methods);
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(final boolean acceptSetters,
                                                        final boolean acceptGetters,
                                                        final boolean acceptStatic,
                                                        final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels,
                                                        PsiMethod[] methods) {
    return getAllProperties(acceptSetters, acceptGetters, acceptStatic, false, visibilityLevels, methods);
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(boolean acceptSetters,
                                                        boolean acceptGetters,
                                                        boolean acceptStatic,
                                                        boolean acceptBoxedBooleanIsPrefix,
                                                        final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels,
                                                        PsiMethod[] methods) {
    final Map<String, PsiMethod> map = new HashMap<>();

    for (PsiMethod method : methods) {
      if (filterMethods(method, acceptStatic, visibilityLevels)) continue;
      if (acceptSetters && isSimplePropertySetter(method) ||
          acceptGetters && isSimplePropertyGetter(method, acceptBoxedBooleanIsPrefix)) {
        map.put(getPropertyName(method), method);
      }
    }
    return map;
  }

  @NotNull
  public static Pair<Map<String, PsiMethod>, Map<String, PsiMethod>> getAllAccessors(
    final boolean acceptSetters,
    final boolean acceptGetters,
    final boolean acceptStatic,
    final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels,
    PsiMethod @NotNull [] methods
  ) {
    final Map<String, PsiMethod> getters = new HashMap<>();
    final Map<String, PsiMethod> setters = new HashMap<>();

    for (PsiMethod method : methods) {
      if (filterMethods(method, acceptStatic, visibilityLevels)) continue;
      if (acceptGetters && isSimplePropertyGetter(method)) {
        getters.put(getPropertyNameByGetter(method), method);
      }
      if (acceptSetters && isSimplePropertySetter(method)) {
        setters.put(getPropertyNameBySetter(method), method);
      }
    }
    return Pair.create(getters, setters);
  }

  private static boolean filterMethods(final PsiMethod method,
                                       final boolean acceptStatic,
                                       final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels) {
    if ((!acceptStatic && method.hasModifierProperty(PsiModifier.STATIC))
        || !ContainerUtil.exists(visibilityLevels, it -> method.hasModifierProperty(it))) {
      return true;
    }

    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    final String className = psiClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(className);
  }

  @NotNull
  public static List<PsiMethod> getSetters(@NotNull final PsiClass psiClass, final String propertyName) {
    return getSetters(psiClass, propertyName, false, ONLY_PUBLIC);
  }

  @NotNull
  public static List<PsiMethod> getSetters(@NotNull final PsiClass psiClass,
                                           final String propertyName,
                                           final boolean acceptStatic,
                                           final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels) {
    final String setterName = suggestSetterName(propertyName);
    final PsiMethod[] psiMethods = psiClass.findMethodsByName(setterName, true);
    final ArrayList<PsiMethod> list = new ArrayList<>(psiMethods.length);
    for (PsiMethod method : psiMethods) {
      if (filterMethods(method, acceptStatic, visibilityLevels)) continue;
      if (isSimplePropertySetter(method)) {
        list.add(method);
      }
    }
    return list;
  }

  @NotNull
  public static List<PsiMethod> getGetters(@NotNull final PsiClass psiClass, final String propertyName) {
    return getGetters(psiClass, propertyName, false, ONLY_PUBLIC);
  }

  @NotNull
  public static List<PsiMethod> getGetters(@NotNull final PsiClass psiClass,
                                           final String propertyName,
                                           final boolean acceptStatic,
                                           final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels) {
    final String[] names = suggestGetterNames(propertyName);
    final ArrayList<PsiMethod> list = new ArrayList<>();
    for (String name : names) {
      final PsiMethod[] psiMethods = psiClass.findMethodsByName(name, true);
      for (PsiMethod method : psiMethods) {
        if (filterMethods(method, acceptStatic, visibilityLevels)) continue;
        if (isSimplePropertyGetter(method)) {
          list.add(method);
        }
      }
    }
    return list;
  }

  @NotNull
  public static List<PsiMethod> getAccessors(@NotNull final PsiClass psiClass, final String propertyName) {
    return getAccessors(psiClass, propertyName, false, ONLY_PUBLIC);
  }

  @NotNull
  public static List<PsiMethod> getAccessors(@NotNull final PsiClass psiClass,
                                             final String propertyName,
                                             final boolean acceptStatic,
                                             final @NonNls @PsiModifier.ModifierConstant String @NotNull [] visibilityLevels) {
    return ContainerUtil.concat(
      getGetters(psiClass, propertyName, acceptStatic, visibilityLevels),
      getSetters(psiClass, propertyName, acceptStatic, visibilityLevels)
    );
  }

  public static String @NotNull [] getReadableProperties(@NotNull PsiClass aClass, boolean includeSuperClass) {
    List<String> result = new ArrayList<>();

    PsiMethod[] methods = includeSuperClass ? aClass.getAllMethods() : aClass.getMethods();

    for (PsiMethod method : methods) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) continue;

      if (isSimplePropertyGetter(method)) {
        result.add(getPropertyName(method));
      }
    }

    return ArrayUtilRt.toStringArray(result);
  }

  public static String @NotNull [] getWritableProperties(@NotNull PsiClass aClass, boolean includeSuperClass) {
    List<String> result = new ArrayList<>();

    PsiMethod[] methods = includeSuperClass ? aClass.getAllMethods() : aClass.getMethods();

    for (PsiMethod method : methods) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) continue;

      if (isSimplePropertySetter(method)) {
        result.add(getPropertyName(method));
      }
    }

    return ArrayUtilRt.toStringArray(result);
  }

  @Nullable
  public static PsiType getPropertyType(final PsiMember member) {
    if (member instanceof PsiField) {
      return ((PsiField)member).getType();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)member;
      if (isSimplePropertyGetter(psiMethod)) {
        return psiMethod.getReturnType();
      }
      else if (isSimplePropertySetter(psiMethod)) {
        return psiMethod.getParameterList().getParameters()[0].getType();
      }
    }
    return null;
  }


  @Nullable
  public static PsiMethod findPropertySetter(PsiClass aClass,
                                             @NotNull String propertyName,
                                             boolean isStatic,
                                             boolean checkSuperClasses) {
    if (aClass == null) return null;
    String setterName = suggestSetterName(propertyName);
    PsiMethod[] methods = aClass.findMethodsByName(setterName, checkSuperClasses);

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertySetter(method)) {
        if (getPropertyNameBySetter(method).equals(propertyName)) {
          return method;
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiField findPropertyField(PsiClass aClass, String propertyName, boolean isStatic) {
    PsiField[] fields = aClass.getAllFields();

    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      if (propertyName.equals(suggestPropertyName(field))) return field;
    }

    return null;
  }

  @Nullable
  public static PsiMethod findPropertyGetter(PsiClass aClass,
                                             @NotNull String propertyName,
                                             boolean isStatic,
                                             boolean checkSuperClasses) {
    return findPropertyGetter(aClass, propertyName, isStatic, checkSuperClasses, false);
  }

  @Nullable
  public static PsiMethod findPropertyGetter(PsiClass aClass,
                                             @NotNull String propertyName,
                                             boolean isStatic,
                                             boolean checkSuperClasses,
                                             boolean acceptBoxedBooleanIsPrefix) {
    if (aClass == null) return null;
    String[] getterCandidateNames = suggestGetterNames(propertyName);

    for (String getterCandidateName: getterCandidateNames) {
      PsiMethod[] getterCandidates = aClass.findMethodsByName(getterCandidateName, checkSuperClasses);
      for (PsiMethod method : getterCandidates) {
        if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

        if (isSimplePropertyGetter(method, acceptBoxedBooleanIsPrefix)) {
          if (getPropertyNameByGetter(method).equals(propertyName)) {
            return method;
          }
        }
      }
    }

    return null;
  }

  public static PsiMethod findPropertyGetterWithType(@NotNull String propertyName, boolean isStatic, PsiType type, @NotNull Collection<? extends PsiMethod> methods) {
    return ContainerUtil.find(methods, method ->
      method.hasModifierProperty(PsiModifier.STATIC) == isStatic &&
      isSimplePropertyGetter(method) &&
      getPropertyNameByGetter(method).equals(propertyName) &&
      type.equals(method.getReturnType()));
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method) {
    return isSimplePropertyAccessor(method, false);
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method, boolean acceptBoxedBooleanIsPrefix) {
    return isSimplePropertyGetter(method, acceptBoxedBooleanIsPrefix) || isSimplePropertySetter(method);
  }

  public static PsiMethod findPropertySetterWithType(@NotNull String propertyName, boolean isStatic, PsiType type, @NotNull Collection<? extends PsiMethod> methods) {
    return ContainerUtil.find(methods, method->
      method.hasModifierProperty(PsiModifier.STATIC) == isStatic &&
      isSimplePropertySetter(method) &&
      getPropertyNameBySetter(method).equals(propertyName) && type.equals(method.getParameterList().getParameters()[0].getType()));
  }

  /**
   * Returns the field of current class, which is read by a supplied expression
   * 
   * @param expression the PsiExpression to extract the field from
   * @return the {@link PsiField} of current class returned by the supplied expression, or null if expression does not read the field.
   */
  @Nullable
  public static PsiField getSimplyReturnedField(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return null;
    }

    PsiReferenceExpression reference = (PsiReferenceExpression)expression;
    if (hasSubstantialQualifier(reference)) {
      return null;
    }

    return ObjectUtils.tryCast(reference.resolve(), PsiField.class);
  }

  private static boolean hasSubstantialQualifier(PsiReferenceExpression reference) {
    final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression());
    if (qualifier == null) return false;

    if (qualifier instanceof PsiQualifiedExpression) {
      return false;
    }

    if (qualifier instanceof PsiReferenceExpression) {
      return !(((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass);
    }
    return true;
  }

  public enum GetterFlavour {
    BOOLEAN,
    GENERIC,
    NOT_A_GETTER
  }

  @NotNull
  public static GetterFlavour getMethodNameGetterFlavour(@NotNull String methodName) {
    if (checkPrefix(methodName, GET_PREFIX)) {
      return GetterFlavour.GENERIC;
    }
    else if (checkPrefix(methodName, IS_PREFIX)) {
      return GetterFlavour.BOOLEAN;
    }
    return GetterFlavour.NOT_A_GETTER;
  }

  public static boolean hasAccessorName(PsiMethod method) {
    String methodName = method.getName();
    return isSetterName(methodName) || getMethodNameGetterFlavour(methodName) != GetterFlavour.NOT_A_GETTER;
  }

  @Contract("null -> false")
  public static boolean isSimplePropertyGetter(@Nullable PsiMethod method) {
    return isSimplePropertyGetter(method, false);
  }

  /**
   * 'is' prefix is not allowed for java.lang.Boolean getters according to JavaBeans specification,
   * however some frameworks (e.g. Spring Boot) accept such getters.
   */
  @Contract("null, _ -> false")
  public static boolean isSimplePropertyGetter(@Nullable PsiMethod method, boolean acceptBoxedBooleanIsPrefix) {
    return hasGetterName(method, acceptBoxedBooleanIsPrefix) && method.getParameterList().isEmpty();
  }

  public static boolean hasGetterName(final PsiMethod method) {
    return hasGetterName(method, false);
  }

  private static boolean hasGetterName(final PsiMethod method, boolean acceptBoxedBooleanIsPrefix) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    GetterFlavour flavour = getMethodNameGetterFlavour(methodName);
    switch (flavour) {
      case GENERIC:
        PsiType returnType = method.getReturnType();
        return !PsiTypes.voidType().equals(returnType);
      case BOOLEAN:
        PsiType propertyType = method.getReturnType();
        return isBoolean(propertyType) ||
               (acceptBoxedBooleanIsPrefix && propertyType != null &&
                CommonClassNames.JAVA_LANG_BOOLEAN.equals(propertyType.getCanonicalText()));
      case NOT_A_GETTER:
      default:
        return false;
    }
  }


  private static boolean isBoolean(@Nullable PsiType propertyType) {
    return PsiTypes.booleanType().equals(propertyType);
  }


  public static String suggestPropertyName(@NotNull PsiField field) {
    return suggestPropertyName(field, field.getName());
  }

  @NotNull
  public static String suggestPropertyName(@NotNull PsiField field, @NotNull String fieldName) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(field.getProject());
    VariableKind kind = codeStyleManager.getVariableKind(field);
    String name = codeStyleManager.variableNameToPropertyName(fieldName, kind);
    if (!field.hasModifierProperty(PsiModifier.STATIC) && isBoolean(field.getType())) {
      if (name.startsWith(IS_PREFIX) && name.length() > IS_PREFIX.length() && Character.isUpperCase(name.charAt(IS_PREFIX.length()))) {
        name = Introspector.decapitalize(name.substring(IS_PREFIX.length()));
      }
    }
    return name;
  }

  public static String suggestGetterName(PsiField field) {
    String propertyName = suggestPropertyName(field);
    return suggestGetterName(propertyName, field.getType());
  }

  public static String suggestSetterName(PsiField field) {
    String propertyName = suggestPropertyName(field);
    return suggestSetterName(propertyName);
  }

  @Nullable
  public static String getPropertyName(final PsiMember member) {
    if (member instanceof PsiMethod) {
      return getPropertyName((PsiMethod)member);
    }
    if (member instanceof PsiField) {
      return member.getName();
    }
    return null;
  }


  public static boolean isSimplePropertySetter(@Nullable PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!isSetterName(methodName)) return false;

    if (method.getParameterList().getParametersCount() != 1) {
      return false;
    }

    final PsiType returnType = method.getReturnType();

    if (returnType == null || PsiTypes.voidType().equals(returnType)) {
      return true;
    }

    return Comparing.equal(PsiUtil.resolveClassInType(TypeConversionUtil.erasure(returnType)), method.getContainingClass());
  }

  public static boolean isSetterName(@NotNull String methodName) {
    return checkPrefix(methodName, SET_PREFIX);
  }

  public static boolean isGetterName(@NotNull String methodName) {
    return checkPrefix(methodName, GET_PREFIX);
  }

  public static boolean isIsGetterName(@NotNull String methodName) {
    return checkPrefix(methodName, IS_PREFIX);
  }

  @Nullable
  public static String getPropertyName(@NotNull PsiMethod method) {
    return getPropertyName(method, false);
  }

  @Nullable
  public static String getPropertyName(@NotNull PsiMethod method, boolean acceptBoxedBooleanIsPrefix) {
    if (isSimplePropertyGetter(method, acceptBoxedBooleanIsPrefix)) {
      return getPropertyNameByGetter(method);
    }
    if (isSimplePropertySetter(method)) {
      return getPropertyNameBySetter(method);
    }
    return null;
  }

  @NotNull
  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    @NonNls String methodName = getterMethod.getName();
    if (methodName.startsWith(GET_PREFIX)) return StringUtil.decapitalize(methodName.substring(3));
    if (methodName.startsWith(IS_PREFIX)) return StringUtil.decapitalize(methodName.substring(2));
    return methodName;
  }

  @NotNull
  public static String getPropertyNameBySetter(@NotNull PsiMethod setterMethod) {
    String methodName = setterMethod.getName();
    return Introspector.decapitalize(methodName.substring(3));
  }

  private static boolean checkPrefix(@NotNull String methodName, @NotNull String prefix) {
    boolean hasPrefix = methodName.startsWith(prefix) && methodName.length() > prefix.length();
    return hasPrefix && !(Character.isLowerCase(methodName.charAt(prefix.length())) &&
                          (methodName.length() == prefix.length() + 1 || Character.isLowerCase(methodName.charAt(prefix.length() + 1))));
  }

  @NonNls
  public static String @NotNull [] suggestGetterNames(@NotNull String propertyName) {
    final String str = StringUtil.capitalizeWithJavaBeanConvention(StringUtil.sanitizeJavaIdentifier(propertyName));
    return new String[]{IS_PREFIX + str, GET_PREFIX + str};
  }

  public static @NotNull String suggestGetterName(@NonNls @NotNull String propertyName, @Nullable PsiType propertyType) {
    return suggestGetterName(propertyName, propertyType, null);
  }

  public static @NotNull String suggestGetterName(@NotNull String propertyName, @Nullable PsiType propertyType, @NonNls String existingGetterName) {
    @NonNls StringBuilder name =
      new StringBuilder(StringUtil.capitalizeWithJavaBeanConvention(StringUtil.sanitizeJavaIdentifier(propertyName)));
    if (isBoolean(propertyType)) {
      if (existingGetterName == null || !existingGetterName.startsWith(GET_PREFIX)) {
        name.insert(0, IS_PREFIX);
      }
      else {
        name.insert(0, GET_PREFIX);
      }
    }
    else {
      name.insert(0, GET_PREFIX);
    }

    return name.toString();
  }


  public static String suggestSetterName(@NonNls @NotNull String propertyName) {
    return suggestSetterName(propertyName, SET_PREFIX);
  }

  public static String suggestSetterName(@NonNls @NotNull String propertyName, String setterPrefix) {
    final String sanitizeJavaIdentifier = StringUtil.sanitizeJavaIdentifier(propertyName);
    if (StringUtil.isEmpty(setterPrefix)) {
      return sanitizeJavaIdentifier;
    }
    @NonNls StringBuilder name = new StringBuilder(StringUtil.capitalizeWithJavaBeanConvention(sanitizeJavaIdentifier));
    name.insert(0, setterPrefix);
    return name.toString();
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateGetterPrototype(PsiField)} or
   * {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleGetterPrototype(PsiField)}
   * to add @Override annotation
   */
  @NotNull
  public static PsiMethod generateGetterPrototype(@NotNull PsiField field) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());
    Project project = field.getProject();
    String name = field.getName();
    String getName = suggestGetterName(field);
    PsiMethod getMethod = factory.createMethod(getName, AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(field.getModifierList(), field.getType()));
    PsiUtil.setModifierProperty(getMethod, PsiModifier.PUBLIC, true);
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      PsiUtil.setModifierProperty(getMethod, PsiModifier.STATIC, true);
    }

    NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, getMethod);

    PsiCodeBlock body = factory.createCodeBlockFromText("{\nreturn " + name + ";\n}", null);
    Objects.requireNonNull(getMethod.getBody()).replace(body);
    getMethod = (PsiMethod)CodeStyleManager.getInstance(project).reformat(getMethod);
    return getMethod;
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSetterPrototype(PsiField)}
   * or {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleSetterPrototype(PsiField)}
   * to add @Override annotation
   */
  @NotNull
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field) {
    return generateSetterPrototype(field, field.getContainingClass());
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSetterPrototype(PsiField)}
   * or {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleSetterPrototype(PsiField)}
   * to add @Override annotation
   */
  @NotNull
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field, @NotNull PsiClass containingClass) {
    return generateSetterPrototype(field, containingClass, false);
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSetterPrototype(PsiField)}
   * or {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleSetterPrototype(PsiField)}
   * to add @Override annotation
   */
  @NotNull
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field, @NotNull PsiClass containingClass, boolean returnSelf) {
    Project project = field.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());

    String name = field.getName();
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    VariableKind kind = codeStyleManager.getVariableKind(field);
    String propertyName = codeStyleManager.variableNameToPropertyName(name, kind);
    String setName = suggestSetterName(field);
    
    PsiMethod setMethod;
    if (returnSelf) {
      PsiType[] typeArguments = Stream.of(containingClass.getTypeParameters())
        .map(factory::createType)
        .toArray(PsiType[]::new);
      setMethod = factory.createMethod(setName, factory.createType(containingClass, typeArguments));
    }
    else {
      setMethod = factory.createMethod(setName, PsiTypes.voidType());
    }
    String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
    PsiParameter param = factory.createParameter(parameterName, AnnotationTargetUtil.keepStrictlyTypeUseAnnotations(field.getModifierList(), field.getType()));

    NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, param);

    setMethod.getParameterList().add(param);
    PsiUtil.setModifierProperty(setMethod, PsiModifier.PUBLIC, true);
    PsiUtil.setModifierProperty(setMethod, PsiModifier.STATIC, isStatic);

    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("{\n");
    if (name.equals(parameterName)) {
      if (!isStatic) {
        buffer.append("this.");
      }
      else {
        String className = containingClass.getName();
        if (className != null) {
          buffer.append(className);
          buffer.append(".");
        }
      }
    }
    buffer.append(name);
    buffer.append("=");
    buffer.append(parameterName);
    buffer.append(";\n");
    if (returnSelf) {
      buffer.append("return this;\n");
    }
    buffer.append("}");
    PsiCodeBlock body = factory.createCodeBlockFromText(buffer.toString(), null);
    Objects.requireNonNull(setMethod.getBody()).replace(body);
    setMethod = (PsiMethod)CodeStyleManager.getInstance(project).reformat(setMethod);
    return setMethod;
  }

  @Nullable
  public static PsiTypeElement getPropertyTypeElement(final PsiMember member) {
    if (member instanceof PsiField) {
      return ((PsiField)member).getTypeElement();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)member;
      if (isSimplePropertyGetter(psiMethod)) {
        return psiMethod.getReturnTypeElement();
      }
      else if (isSimplePropertySetter(psiMethod)) {
        return psiMethod.getParameterList().getParameters()[0].getTypeElement();
      }
    }
    return null;
  }

  @Nullable
  public static PsiIdentifier getPropertyNameIdentifier(final PsiMember member) {
    if (member instanceof PsiField) {
      return ((PsiField)member).getNameIdentifier();
    }
    if (member instanceof PsiMethod) {
      return ((PsiMethod)member).getNameIdentifier();
    }
    return null;
  }

  @Nullable
  public static PsiField findPropertyFieldByMember(final PsiMember psiMember) {
    if (psiMember instanceof PsiField) {
      return (PsiField)psiMember;
    }
    if (psiMember instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)psiMember;
      final PsiType returnType = psiMethod.getReturnType();
      if (returnType == null) return null;
      final PsiCodeBlock body = psiMethod.getBody();
      final PsiStatement[] statements = body == null ? null : body.getStatements();
      final PsiStatement statement = statements == null || statements.length != 1 ? null : statements[0];
      final PsiElement target;
      if (PsiTypes.voidType().equals(returnType)) {
        final PsiExpression expression =
          statement instanceof PsiExpressionStatement ? ((PsiExpressionStatement)statement).getExpression() : null;
        target = expression instanceof PsiAssignmentExpression ? ((PsiAssignmentExpression)expression).getLExpression() : null;
      }
      else {
        target = statement instanceof PsiReturnStatement ? ((PsiReturnStatement)statement).getReturnValue() : null;
      }
      final PsiElement resolved = target instanceof PsiReferenceExpression ? ((PsiReferenceExpression)target).resolve() : null;
      if (resolved instanceof PsiField) {
        final PsiField field = (PsiField)resolved;
        PsiClass memberClass = psiMember.getContainingClass();
        PsiClass fieldClass = field.getContainingClass();
        if (memberClass != null && fieldClass != null && (memberClass == fieldClass || memberClass.isInheritor(fieldClass, true))) {
          return field;
        }
      }
    }
    return null;
  }

  public static PsiMethod findSetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final String propertyName = suggestPropertyName(field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return findPropertySetter(containingClass, propertyName, isStatic, true);
  }

  public static PsiMethod findGetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final String propertyName = suggestPropertyName(field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return findPropertyGetter(containingClass, propertyName, isStatic, true);
  }

  /**
   * If the name of the method looks like a getter and the body consists of a single return statement,
   * returns the returned expression. Otherwise, returns null.
   *
   * @param method the method to check
   * @return the return value, or null if it doesn't match the conditions.
   */
  @Nullable
  public static PsiExpression getGetterReturnExpression(@Nullable PsiMethod method) {
    return method != null && hasGetterSignature(method) ? getSingleReturnValue(method) : null;
  }

  private static boolean hasGetterSignature(@NotNull PsiMethod method) {
    return isSimplePropertyGetter(method);
  }

  @Nullable
  public static PsiExpression getSingleReturnValue(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return null;
    }
    final PsiStatement[] statements = body.getStatements();
    final PsiStatement statement = statements.length != 1 ? null : statements[0];
    return statement instanceof PsiReturnStatement ? ((PsiReturnStatement)statement).getReturnValue() : null;
  }

  @Contract(pure = true)
  @Nullable
  public static PropertyKind getPropertyKind(@NotNull String accessorName) {
    for (PropertyKind kind : PropertyKind.values()) {
      String prefix = kind.prefix;
      int prefixLength = prefix.length();
      if (accessorName.startsWith(prefix) && accessorName.length() > prefixLength) {
        return kind;
      }
    }
    return null;
  }

  /**
   * @see StringUtil#getPropertyName(String)
   * @see Introspector
   */
  @Contract(pure = true)
  @Nullable
  public static Pair<String, PropertyKind> getPropertyNameAndKind(@NotNull String accessorName) {
    PropertyKind kind = getPropertyKind(accessorName);
    if (kind == null) {
      return null;
    }
    String baseName = accessorName.substring(kind.prefix.length());
    String propertyName = Introspector.decapitalize(baseName);
    return Pair.create(propertyName, kind);
  }

  @Contract(pure = true)
  @NotNull
  public static String getAccessorName(@NotNull String propertyName, @NotNull PropertyKind kind) {
    return kind.prefix + StringUtil.capitalizeWithJavaBeanConvention(propertyName);
  }
}
