/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.*;

/**
 * @author Mike
 */
public class PropertyUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PropertyUtil");

  private PropertyUtil() {
  }

  public static boolean isSimplePropertyGetter(PsiMethod method) {
    return hasGetterName(method) && method.getParameterList().getParametersCount() == 0;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean hasGetterName(final PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    PsiType returnType = method.getReturnType();
    int methodNameLength = methodName.length();
    if (methodName.startsWith("get") && methodNameLength > "get".length()) {
      if (Character.isLowerCase(methodName.charAt("get".length()))
          && (methodNameLength == "get".length() + 1 || Character.isLowerCase(methodName.charAt("get".length() + 1)))) {
        return false;
      }
      if (returnType != null && PsiType.VOID.equals(returnType)) return false;
    }
    else if (methodName.startsWith("is") && methodNameLength > "is".length()) {
      if (Character.isLowerCase(methodName.charAt("is".length()))
          && (methodNameLength == "is".length() + 1 || Character.isLowerCase(methodName.charAt("is".length() + 1)))) {
        return false;
      }
      return isBoolean(returnType);
    }
    else {
      return false;
    }
    return true;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isSimplePropertySetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!(methodName.startsWith("set") && methodName.length() > "set".length())) return false;
    if (Character.isLowerCase(methodName.charAt("set".length()))
      && (methodName.length() == "set".length() + 1 || Character.isLowerCase(methodName.charAt("set".length() + 1)))) return false;

    if (method.getParameterList().getParametersCount() != 1) {
      return false;
    }

    final PsiType returnType = method.getReturnType();

    if (returnType == null || PsiType.VOID.equals(returnType)) {
      return true;
    }

    return Comparing.equal(PsiUtil.resolveClassInType(returnType), method.getContainingClass());
  }

  @Nullable public static String getPropertyName(PsiMethod method) {
    if (isSimplePropertyGetter(method)) {
      return getPropertyNameByGetter(method);
    }
    else if (isSimplePropertySetter(method)) {
      return getPropertyNameBySetter(method);
    }
    else {
      return null;
    }
  }

  @NotNull
  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    @NonNls String methodName = getterMethod.getName();
    return methodName.startsWith("get") ?
           StringUtil.decapitalize(methodName.substring(3)) :
           StringUtil.decapitalize(methodName.substring(2));
  }

  @NotNull
  public static String getPropertyNameBySetter(PsiMethod setterMethod) {
    String methodName = setterMethod.getName();
    return Introspector.decapitalize(methodName.substring(3));
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(@NotNull final PsiClass psiClass, final boolean acceptSetters, final boolean acceptGetters) {
    return getAllProperties(psiClass, acceptSetters, acceptGetters, true);
  }

  @NotNull
  public static Map<String, PsiMethod> getAllProperties(@NotNull final PsiClass psiClass, final boolean acceptSetters, final boolean acceptGetters, final boolean includeSuperClass) {
    final Map<String, PsiMethod> map = new HashMap<String, PsiMethod>();
    final PsiMethod[] methods = includeSuperClass ? psiClass.getAllMethods() : psiClass.getMethods();

    for (PsiMethod method : methods) {
      if (filterMethods(method)) continue;
      if (acceptSetters && isSimplePropertySetter(method)||
          acceptGetters && isSimplePropertyGetter(method)) {
        map.put(getPropertyName(method), method);
      }
    }
    return map;
  }


  private static boolean filterMethods(final PsiMethod method) {
    if(method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) return true;

    final String className = method.getContainingClass().getQualifiedName();
    return className != null && className.equals(CommonClassNames.JAVA_LANG_OBJECT);
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

  @Nullable public static PsiMethod findPropertyGetter(PsiClass aClass,
                                                       String propertyName,
                                                       boolean isStatic,
                                                       boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertyGetter(method)) {
        if (getPropertyNameByGetter(method).equals(propertyName)) {
          return method;
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

  @Nullable public static PsiMethod findPropertySetter(PsiClass aClass,
                                             String propertyName,
                                             boolean isStatic,
                                             boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

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

  @Nullable public static PsiField findPropertyField(Project project, PsiClass aClass, String propertyName, boolean isStatic) {
    PsiField[] fields = aClass.getAllFields();

    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      if (propertyName.equals(suggestPropertyName(project, field))) return field;
    }

    return null;
  }

  @Nullable public static PsiField findPropertyFieldWithType(Project project, String propertyName,
                                                   boolean isStatic, PsiType type, Iterator<PsiField> fields) {
    while (fields.hasNext()) {
      PsiField field = fields.next();
      if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      if (propertyName.equals(suggestPropertyName(project, field))) {
        if (type.equals(field.getType())) return field;
      }
    }

    return null;
  }

  @Nullable public static String getPropertyName(@NonNls String methodName) {
    return StringUtil.getPropertyName(methodName);
  }

  public static String suggestGetterName(@NonNls @NotNull String propertyName, @Nullable PsiType propertyType) {
    return suggestGetterName(propertyName, propertyType, null);
  }

  public static String suggestGetterName(@NotNull String propertyName, @Nullable PsiType propertyType, @NonNls String existingGetterName) {
    @NonNls StringBuffer name = new StringBuffer(StringUtil.capitalizeWithJavaBeanConvention(propertyName));
    if (isBoolean(propertyType)) {
      if (existingGetterName == null || !existingGetterName.startsWith("get")) {
        name.insert(0, "is");
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
  public static String[] suggestGetterNames(String propertyName) {
    final String str = StringUtil.capitalizeWithJavaBeanConvention(propertyName);
    return new String[] { "is" + str, "get" + str };
  }

  public static String suggestSetterName(@NonNls String propertyName) {
    @NonNls StringBuffer name = new StringBuffer(StringUtil.capitalizeWithJavaBeanConvention(propertyName));
    name.insert(0, "set");
    return name.toString();
  }

  public static String[] getReadableProperties(PsiClass aClass, boolean includeSuperClass) {
    List<String> result = new ArrayList<String>();

    PsiMethod[] methods;
    if (includeSuperClass) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if ("java.lang.Object".equals(method.getContainingClass().getQualifiedName())) continue;

      if (isSimplePropertyGetter(method)) {
        result.add(getPropertyName(method));
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  public static String[] getWritableProperties(PsiClass aClass, boolean includeSuperClass) {
    List<String> result = new ArrayList<String>();

    PsiMethod[] methods;

    if (includeSuperClass) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if ("java.lang.Object".equals(method.getContainingClass().getQualifiedName())) continue;

      if (isSimplePropertySetter(method)) {
        result.add(getPropertyName(method));
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  public static PsiMethod generateGetterPrototype(PsiField field) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();
    Project project = field.getProject();
    String name = field.getName();
    String getName = suggestGetterName(project, field);
    try {
      PsiMethod getMethod = factory.createMethod(getName, field.getType());
      PsiUtil.setModifierProperty(getMethod, PsiModifier.PUBLIC, true);
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiUtil.setModifierProperty(getMethod, PsiModifier.STATIC, true);
      }

      annotateWithNullableStuff(field, factory, getMethod);

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

  public static PsiMethod generateSetterPrototype(PsiField field) {
    return generateSetterPrototype(field, field.getContainingClass());
  }

  public static PsiMethod generateSetterPrototype(PsiField field, final PsiClass containingClass) {
    return generateSetterPrototype(field, containingClass, false);
  }

  public static PsiMethod generateSetterPrototype(PsiField field, final PsiClass containingClass, boolean returnSelf) {
    Project project = field.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();

    String name = field.getName();
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    VariableKind kind = codeStyleManager.getVariableKind(field);
    String propertyName = codeStyleManager.variableNameToPropertyName(name, kind);
    String setName = suggestSetterName(project, field);
    try {
      PsiMethod setMethod = factory.createMethod(setName, returnSelf ? factory.createType(containingClass) : PsiType.VOID);
      String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter param = factory.createParameter(parameterName, field.getType());

      annotateWithNullableStuff(field, factory, param);

      setMethod.getParameterList().add(param);
      PsiUtil.setModifierProperty(setMethod, PsiModifier.PUBLIC, true);
      PsiUtil.setModifierProperty(setMethod, PsiModifier.STATIC, isStatic);

      @NonNls StringBuffer buffer = new StringBuffer();
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

  private static void annotateWithNullableStuff(final PsiModifierListOwner field, final PsiElementFactory factory, final PsiModifierListOwner listOwner)
    throws IncorrectOperationException {
    if (AnnotationUtil.isAnnotated(field, AnnotationUtil.NOT_NULL, false)) {
      annotate(factory, listOwner, AnnotationUtil.NOT_NULL);
    }
    else if (AnnotationUtil.isAnnotated(field, AnnotationUtil.NULLABLE, false)) {
      annotate(factory, listOwner, AnnotationUtil.NULLABLE);
    }
  }

  private static void annotate(final PsiElementFactory factory, final PsiModifierListOwner listOwner, final String annotationQName)
    throws IncorrectOperationException {
    final PsiModifierList modifierList = listOwner.getModifierList();
    LOG.assertTrue(modifierList != null);
    modifierList.addAfter(factory.createAnnotationFromText("@" + annotationQName, listOwner), null);
  }

  public static String suggestPropertyName(Project project, PsiField field) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    VariableKind kind = codeStyleManager.getVariableKind(field);
    return codeStyleManager.variableNameToPropertyName(field.getName(), kind);
  }

  public static String suggestGetterName(Project project, PsiField field) {
    String propertyName = suggestPropertyName(project, field);
    return suggestGetterName(propertyName, field.getType());
  }

  public static String suggestSetterName(Project project, PsiField field) {
    String propertyName = suggestPropertyName(project, field);
    return suggestSetterName(propertyName);
  }

  /**
   *  "xxx", "void setMyProperty(String pp)" -> "setXxx"
   */
  @Nullable
  public static String suggestPropertyAccessor(String name, PsiMethod accessorTemplate) {
    if (isSimplePropertyGetter(accessorTemplate)) {
      PsiType type = accessorTemplate.getReturnType();
      return suggestGetterName(name, type, accessorTemplate.getName());
    }
    if (isSimplePropertySetter(accessorTemplate)) {
      return suggestSetterName(name);
    }
    return null;
  }

  @Nullable
  public static String getPropertyName(final PsiMember member) {
    if (member instanceof PsiMethod) {
      return getPropertyName((PsiMethod)member);
    }
    else if (member instanceof PsiField) {
      return member.getName();
    }
    else return null;
  }

  @Nullable
  public static PsiType getPropertyType(final PsiMember member) {
    if (member instanceof PsiField) {
      return ((PsiField)member).getType();
    }
    else if (member instanceof PsiMethod) {
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
    else if (member instanceof PsiMethod) {
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
    else if (member instanceof PsiMethod) {
      return ((PsiMethod)member).getNameIdentifier();
    }
    return null;
  }

  @Nullable
  public static PsiField findPropertyFieldByMember(final PsiMember psiMember) {
    if (psiMember instanceof PsiField) {
      return (PsiField)psiMember;
    }
    else if (psiMember instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)psiMember;
      final PsiType returnType = psiMethod.getReturnType();
      if (returnType == null) return null;
      final PsiCodeBlock body = psiMethod.getBody();
      final PsiStatement[] statements = body == null? null : body.getStatements();
      final PsiStatement statement = statements == null || statements.length != 1? null : statements[0];
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
               psiMember.getContainingClass().isInheritor(field.getContainingClass(), true)) return field;
      }
    }
    return null;
  }
}
