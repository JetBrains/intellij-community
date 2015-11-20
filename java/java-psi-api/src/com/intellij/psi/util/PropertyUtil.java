/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.*;

/**
 * @author Mike
 */
public class PropertyUtil {
  @NonNls private static final String IS_PREFIX = "is";
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PropertyUtil");

  private PropertyUtil() {
  }

  public static boolean isSimplePropertyGetter(@NotNull PsiMethod method) {
    return hasGetterName(method) && method.getParameterList().getParametersCount() == 0;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean hasGetterName(final PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    int methodNameLength = methodName.length();
    if (methodName.startsWith("get") && methodNameLength > "get".length()) {
      if (Character.isLowerCase(methodName.charAt("get".length()))
          && (methodNameLength == "get".length() + 1 || Character.isLowerCase(methodName.charAt("get".length() + 1)))) {
        return false;
      }
      PsiType returnType = method.getReturnType();
      if (returnType != null && PsiType.VOID.equals(returnType)) return false;
    }
    else if (methodName.startsWith(IS_PREFIX) && methodNameLength > IS_PREFIX.length()) {
      if (Character.isLowerCase(methodName.charAt(IS_PREFIX.length()))
          && (methodNameLength == IS_PREFIX.length() + 1 || Character.isLowerCase(methodName.charAt(IS_PREFIX.length() + 1)))) {
        return false;
      }
      PsiType returnType = method.getReturnType();
      return isBoolean(returnType);
    }
    else {
      return false;
    }
    return true;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isSimplePropertySetter(@Nullable PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!(methodName.startsWith("set") && methodName.length() > "set".length())) return false;
    if (Character.isLowerCase(methodName.charAt("set".length()))
        && (methodName.length() == "set".length() + 1 || Character.isLowerCase(methodName.charAt("set".length() + 1)))) {
      return false;
    }

    if (method.getParameterList().getParametersCount() != 1) {
      return false;
    }

    final PsiType returnType = method.getReturnType();

    if (returnType == null || PsiType.VOID.equals(returnType)) {
      return true;
    }

    return Comparing.equal(PsiUtil.resolveClassInType(TypeConversionUtil.erasure(returnType)), method.getContainingClass());
  }

  @Nullable
  public static String getPropertyName(@NotNull PsiMethod method) {
    if (isSimplePropertyGetter(method)) {
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
    return methodName.startsWith("get") ?
           StringUtil.decapitalize(methodName.substring(3)) :
           StringUtil.decapitalize(methodName.substring(2));
  }

  @NotNull
  public static String getPropertyNameBySetter(@NotNull PsiMethod setterMethod) {
    String methodName = setterMethod.getName();
    return Introspector.decapitalize(methodName.substring(3));
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
    final Map<String, PsiMethod> map = new HashMap<String, PsiMethod>();

    for (PsiMethod method : methods) {
      if (filterMethods(method)) continue;
      if (acceptSetters && isSimplePropertySetter(method) ||
          acceptGetters && isSimplePropertyGetter(method)) {
        map.put(getPropertyName(method), method);
      }
    }
    return map;
  }


  private static boolean filterMethods(final PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) return true;

    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    final String className = psiClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(className);
  }

  @NotNull
  public static List<PsiMethod> getSetters(@NotNull final PsiClass psiClass, final String propertyName) {
    final String setterName = suggestSetterName(propertyName);
    final PsiMethod[] psiMethods = psiClass.findMethodsByName(setterName, true);
    final ArrayList<PsiMethod> list = new ArrayList<PsiMethod>(psiMethods.length);
    for (PsiMethod method : psiMethods) {
      if (filterMethods(method)) continue;
      if (isSimplePropertySetter(method)) {
        list.add(method);
      }
    }
    return list;
  }

  @NotNull
  public static List<PsiMethod> getGetters(@NotNull final PsiClass psiClass, final String propertyName) {
    final String[] names = suggestGetterNames(propertyName);
    final ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    for (String name : names) {
      final PsiMethod[] psiMethods = psiClass.findMethodsByName(name, true);
      for (PsiMethod method : psiMethods) {
        if (filterMethods(method)) continue;
        if (isSimplePropertyGetter(method)) {
          list.add(method);
        }
      }
    }
    return list;
  }

  @NotNull
  public static List<PsiMethod> getAccessors(@NotNull final PsiClass psiClass, final String propertyName) {
    return ContainerUtil.concat(getGetters(psiClass, propertyName), getSetters(psiClass, propertyName));
  }

  @Nullable
  public static PsiMethod findPropertyGetter(PsiClass aClass,
                                             @NotNull String propertyName,
                                             boolean isStatic,
                                             boolean checkSuperClasses) {
    if (aClass == null) return null;
    String[] getterCandidateNames = suggestGetterNames(propertyName);

    for (String getterCandidateName: getterCandidateNames) {
      PsiMethod[] getterCandidates = aClass.findMethodsByName(getterCandidateName, checkSuperClasses);
      for (PsiMethod method : getterCandidates) {
        if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

        if (isSimplePropertyGetter(method)) {
          if (getPropertyNameByGetter(method).equals(propertyName)) {
            return method;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiMethod findPropertyGetterWithType(String propertyName, boolean isStatic, PsiType type, Iterator<PsiMethod> methods) {
    while (methods.hasNext()) {
      PsiMethod method = methods.next();
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      if (isSimplePropertyGetter(method)) {
        if (getPropertyNameByGetter(method).equals(propertyName)) {
          if (type.equals(method.getReturnType())) return method;
        }
      }
    }
    return null;
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method) {
    return isSimplePropertyGetter(method) || isSimplePropertySetter(method);
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
  public static PsiMethod findPropertySetterWithType(String propertyName, boolean isStatic, PsiType type, Iterator<PsiMethod> methods) {
    while (methods.hasNext()) {
      PsiMethod method = methods.next();
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertySetter(method)) {
        if (getPropertyNameBySetter(method).equals(propertyName)) {
          PsiType methodType = method.getParameterList().getParameters()[0].getType();
          if (type.equals(methodType)) return method;
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
  public static String getPropertyName(@NonNls String methodName) {
    return StringUtil.getPropertyName(methodName);
  }

  public static String suggestGetterName(@NonNls @NotNull String propertyName, @Nullable PsiType propertyType) {
    return suggestGetterName(propertyName, propertyType, null);
  }

  public static String suggestGetterName(@NotNull String propertyName, @Nullable PsiType propertyType, @NonNls String existingGetterName) {
    @NonNls StringBuilder name =
      new StringBuilder(StringUtil.capitalizeWithJavaBeanConvention(StringUtil.sanitizeJavaIdentifier(propertyName)));
    if (isBoolean(propertyType)) {
      if (existingGetterName == null || !existingGetterName.startsWith("get")) {
        name.insert(0, IS_PREFIX);
      }
      else {
        name.insert(0, "get");
      }
    }
    else {
      name.insert(0, "get");
    }

    return name.toString();
  }

  private static boolean isBoolean(@Nullable PsiType propertyType) {
    return PsiType.BOOLEAN.equals(propertyType);
  }

  @NonNls
  @NotNull
  public static String[] suggestGetterNames(@NotNull String propertyName) {
    final String str = StringUtil.capitalizeWithJavaBeanConvention(StringUtil.sanitizeJavaIdentifier(propertyName));
    return new String[]{IS_PREFIX + str, "get" + str};
  }

  public static String suggestSetterName(@NonNls @NotNull String propertyName) {
    return suggestSetterName(propertyName, "set");
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

  @NotNull
  public static String[] getReadableProperties(@NotNull PsiClass aClass, boolean includeSuperClass) {
    List<String> result = new ArrayList<String>();

    PsiMethod[] methods = includeSuperClass ? aClass.getAllMethods() : aClass.getMethods();

    for (PsiMethod method : methods) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) continue;

      if (isSimplePropertyGetter(method)) {
        result.add(getPropertyName(method));
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public static String[] getWritableProperties(@NotNull PsiClass aClass, boolean includeSuperClass) {
    List<String> result = new ArrayList<String>();

    PsiMethod[] methods = includeSuperClass ? aClass.getAllMethods() : aClass.getMethods();

    for (PsiMethod method : methods) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) continue;

      if (isSimplePropertySetter(method)) {
        result.add(getPropertyName(method));
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateGetterPrototype(com.intellij.psi.PsiField)} or
   * {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleGetterPrototype(com.intellij.psi.PsiField)}
   * to add @Override annotation
   */
  @Nullable
  public static PsiMethod generateGetterPrototype(@NotNull PsiField field) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();
    Project project = field.getProject();
    String name = field.getName();
    String getName = suggestGetterName(field);
    try {
      PsiMethod getMethod = factory.createMethod(getName, field.getType());
      PsiUtil.setModifierProperty(getMethod, PsiModifier.PUBLIC, true);
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiUtil.setModifierProperty(getMethod, PsiModifier.STATIC, true);
      }

      NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, getMethod);

      PsiCodeBlock body = factory.createCodeBlockFromText("{\nreturn " + name + ";\n}", null);
      getMethod.getBody().replace(body);
      getMethod = (PsiMethod)CodeStyleManager.getInstance(project).reformat(getMethod);
      return getMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSetterPrototype(com.intellij.psi.PsiField)}
   * or {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleSetterPrototype(com.intellij.psi.PsiField)}
   * to add @Override annotation
   */
  @Nullable
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field) {
    return generateSetterPrototype(field, field.getContainingClass());
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSetterPrototype(com.intellij.psi.PsiField)}
   * or {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleSetterPrototype(com.intellij.psi.PsiField)}
   * to add @Override annotation
   */
  @Nullable
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field, @NotNull PsiClass containingClass) {
    return generateSetterPrototype(field, containingClass, false);
  }

  /**
   * Consider using {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSetterPrototype(com.intellij.psi.PsiField)}
   * or {@link com.intellij.codeInsight.generation.GenerateMembersUtil#generateSimpleSetterPrototype(com.intellij.psi.PsiField)}
   * to add @Override annotation
   */
  @Nullable
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field, @NotNull PsiClass containingClass, boolean returnSelf) {
    Project project = field.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();

    String name = field.getName();
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    VariableKind kind = codeStyleManager.getVariableKind(field);
    String propertyName = codeStyleManager.variableNameToPropertyName(name, kind);
    String setName = suggestSetterName(field);
    try {
      PsiMethod setMethod = factory
        .createMethodFromText(factory.createMethod(setName, returnSelf ? factory.createType(containingClass) : PsiType.VOID).getText(),
                              field);
      String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter param = factory.createParameter(parameterName, field.getType());

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
      setMethod.getBody().replace(body);
      setMethod = (PsiMethod)CodeStyleManager.getInstance(project).reformat(setMethod);
      return setMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  /** @deprecated use {@link NullableNotNullManager#copyNullableOrNotNullAnnotation(PsiModifierListOwner, PsiModifierListOwner)} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public static void annotateWithNullableStuff(@NotNull PsiModifierListOwner field,
                                               @NotNull PsiModifierListOwner listOwner) throws IncorrectOperationException {
    NullableNotNullManager.getInstance(field.getProject()).copyNullableOrNotNullAnnotation(field, listOwner);
  }

  public static String suggestPropertyName(@NotNull PsiField field) {
    return suggestPropertyName(field, field.getName());
  }

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
      if (PsiType.VOID.equals(returnType)) {
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
        if (psiMember.getContainingClass() == field.getContainingClass() ||
            psiMember.getContainingClass().isInheritor(field.getContainingClass(), true)) {
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
   * @return the return value, or null if it doesn't match the condotions.
   */
  @Nullable
  public static PsiExpression getGetterReturnExpression(PsiMethod method) {
    return method != null && hasGetterSignature(method) ? getSingleReturnValue(method) : null;
  }

  private static boolean hasGetterSignature(@NotNull PsiMethod method) {
    return isSimplePropertyGetter(method) && !method.hasModifierProperty(PsiModifier.SYNCHRONIZED);
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

  @Nullable
  public static PsiField getFieldOfGetter(PsiMethod method) {
    PsiField field = getSimplyReturnedField(method, getGetterReturnExpression(method));
    if (field != null) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null && field.getType().equalsToText(returnType.getCanonicalText())) {
        return field;
      }
    }
    return null;
  }

  @Nullable
  public static PsiField getSimplyReturnedField(PsiMethod method, @Nullable PsiExpression value) {
    if (!(value instanceof PsiReferenceExpression)) {
      return null;
    }

    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    if (hasSubstantialQualifier(reference)) {
      return null;
    }

    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiField)) {
      return null;
    }

    final PsiField field = (PsiField)referent;
    return InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), field.getContainingClass(), true) ? field : null;
  }

  private static boolean hasSubstantialQualifier(PsiReferenceExpression reference) {
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier == null) return false;

    if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
      return false;
    }

    if (qualifier instanceof PsiReferenceExpression) {
      return !(((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass);
    }
    return true;
  }

  public static boolean isSimpleGetter(PsiMethod method) {
    return getFieldOfGetter(method) != null;
  }

  @Nullable
  public static PsiField getFieldOfSetter(PsiMethod method) {
    if (method == null) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 1) {
      return null;
    }
    @NonNls final String name = method.getName();
    if (!name.startsWith("set")) {
      return null;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      return null;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return null;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) {
      return null;
    }
    final PsiStatement statement = statements[0];
    if (!(statement instanceof PsiExpressionStatement)) {
      return null;
    }
    final PsiExpressionStatement possibleAssignmentStatement = (PsiExpressionStatement)statement;
    final PsiExpression possibleAssignment = possibleAssignmentStatement.getExpression();
    if (!(possibleAssignment instanceof PsiAssignmentExpression)) {
      return null;
    }
    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)possibleAssignment;
    if (!JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      return null;
    }
    final PsiExpression lhs = assignment.getLExpression();
    if (!(lhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiClass)) {
        return null;
      }
    }
    else if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
      return null;
    }
    final PsiElement referent = reference.resolve();
    if (referent == null) {
      return null;
    }
    if (!(referent instanceof PsiField)) {
      return null;
    }
    final PsiField field = (PsiField)referent;
    final PsiClass fieldContainingClass = field.getContainingClass();
    final PsiClass methodContainingClass = method.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(methodContainingClass, fieldContainingClass, true)) {
      return null;
    }
    final PsiExpression rhs = assignment.getRExpression();
    if (!(rhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression rReference = (PsiReferenceExpression)rhs;
    final PsiExpression rQualifier = rReference.getQualifierExpression();
    if (rQualifier != null) {
      return null;
    }
    final PsiElement rReferent = rReference.resolve();
    if (rReferent == null) {
      return null;
    }
    if (!(rReferent instanceof PsiParameter)) {
      return null;
    }
    final PsiType fieldType = field.getType();
    final PsiType parameterType = ((PsiVariable)rReferent).getType();
    if (fieldType.equalsToText(parameterType.getCanonicalText())) {
      return field;
    }
    return null;
  }

  public static boolean isSimpleSetter(PsiMethod method) {
    return getFieldOfSetter(method) != null;
  }

  @Nullable
  public static PsiMethod getReversePropertyMethod(PsiMethod propertyMethod) {
    if (propertyMethod == null) {
      return null;
    }
    final PsiClass aClass = propertyMethod.getContainingClass();
    if (aClass == null) {
      return null;
    }
    final String methodName = propertyMethod.getName();
    final String prefix;
    if (methodName.startsWith("get")) {
      prefix = "get";
    }
    else if (methodName.startsWith(IS_PREFIX)) {
      prefix = IS_PREFIX;
    }
    else if (methodName.startsWith("set")) {
      prefix = "set";
    }
    else {
      return null;
    }
    final String name = methodName.substring(prefix.length());
    final PsiField field = prefix.equals("set") ? getFieldOfSetter(propertyMethod) : getFieldOfGetter(propertyMethod);
    if (field == null) {
      return null;
    }
    if (prefix.equals("set")) {
      final PsiMethod result = findPropertyMethod(aClass, "get", name, field);
      if (result != null) {
        return result;
      }
      return findPropertyMethod(aClass, IS_PREFIX, name, field);
    }
    else {
      return findPropertyMethod(aClass, "set", name, field);
    }
  }

  private static PsiMethod findPropertyMethod(@NotNull PsiClass aClass,
                                              @NotNull String prefix,
                                              @NotNull String propertyName,
                                              @NotNull PsiField field1) {
    final PsiMethod[] methods = aClass.findMethodsByName(prefix + propertyName, true);
    for (PsiMethod method : methods) {
      final PsiField field2 = prefix.equals("set") ? getFieldOfSetter(method) : getFieldOfGetter(method);
      if (field1.equals(field2)) {
        return method;
      }
    }
    return null;
  }
}
