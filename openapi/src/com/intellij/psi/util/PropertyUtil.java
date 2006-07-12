/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Mike
 */
public class PropertyUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PropertyUtil");

  private PropertyUtil() {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isSimplePropertyGetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    PsiType returnType = method.getReturnType();
    if (methodName.startsWith("get") && methodName.length() > "get".length()) {
      if (Character.isLowerCase(methodName.charAt("get".length()))
          && (methodName.length() == "get".length() + 1 || Character.isLowerCase(methodName.charAt("get".length() + 1)))) {
        return false;
      }
      if (returnType != null && returnType == PsiType.VOID) return false;
    }
    else if (methodName.startsWith("is")) {
      if (returnType != null && returnType != PsiType.BOOLEAN) return false;
    }
    else {
      return false;
    }

    if (method.getParameterList().getParameters().length != 0) {
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
    if (Character.isLowerCase(methodName.charAt("set".length()))) return false;

    if (method.getParameterList().getParameters().length != 1) {
      return false;
    }

    if (method.getReturnType() != null && method.getReturnType() != PsiType.VOID) {
      return false;
    }

    return true;
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

  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    @NonNls String methodName = getterMethod.getName();
    return methodName.startsWith("get") ?
           StringUtil.decapitalize(methodName.substring(3)) :
           StringUtil.decapitalize(methodName.substring(2));
  }

  public static String getPropertyNameBySetter(PsiMethod setterMethod) {
    String methodName = setterMethod.getName();
    return Introspector.decapitalize(methodName.substring(3));
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

  @Nullable public static PsiMethod findPropertyGetterWithType(String propertyName, boolean isStatic, PsiType type, Iterator<PsiMethod> methods) {
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

  @Nullable public static PsiMethod findPropertySetterWithType(String propertyName, boolean isStatic, PsiType type, Iterator<PsiMethod> methods) {
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
    if (methodName.startsWith("get")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    else if (methodName.startsWith("is")) {
      return Introspector.decapitalize(methodName.substring(2));
    }
    else if (methodName.startsWith("set")) {
      return Introspector.decapitalize(methodName.substring(3));
    }
    else {
      return null;
    }
  }

  public static String suggestGetterName(String propertyName, PsiType propertyType) {
    return suggestGetterName(propertyName, propertyType, null);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String suggestGetterName(String propertyName, PsiType propertyType, String existingGetterName) {
    StringBuffer name = new StringBuffer(StringUtil.capitalize(propertyName));
    if (propertyType == PsiType.BOOLEAN) {
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

  public static String suggestSetterName(String propertyName) {
    @NonNls StringBuffer name = new StringBuffer(StringUtil.capitalize(propertyName));
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

    return result.toArray(new String[result.size()]);
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

    return result.toArray(new String[result.size()]);
  }

  public static PsiMethod generateGetterPrototype(PsiField field) {
    PsiElementFactory factory = field.getManager().getElementFactory();
    Project project = field.getProject();
    String name = field.getName();
    String getName = suggestGetterName(project, field);
    try {
      PsiMethod getMethod = factory.createMethod(getName, field.getType());
      getMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        getMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }

      PsiAnnotation[] annotations = field.getModifierList().getAnnotations();
      for(PsiAnnotation ann: annotations) {
        if (AnnotationUtil.NULLABLE.equals(ann.getQualifiedName()) ||
            AnnotationUtil.NOT_NULL.equals(ann.getQualifiedName())) {
          final PsiAnnotation annotation = factory.createAnnotationFromText("@" + ann.getQualifiedName(), getMethod);
          getMethod.getModifierList().addAfter(annotation, null);
          break;
        }
      }

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
    Project project = field.getProject();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    PsiElementFactory factory = field.getManager().getElementFactory();

    String name = field.getName();
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    VariableKind kind = codeStyleManager.getVariableKind(field);
    String propertyName = codeStyleManager.variableNameToPropertyName(name, kind);
    String setName = suggestSetterName(project, field);
    try {
      PsiMethod setMethod = factory.createMethod(setName, PsiType.VOID);
      String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter param = factory.createParameter(parameterName, field.getType());

      PsiAnnotation[] annotations = field.getModifierList().getAnnotations();
      for(PsiAnnotation ann: annotations) {
        if (AnnotationUtil.NOT_NULL.equals(ann.getQualifiedName())) {
          final PsiAnnotation annotation = factory.createAnnotationFromText("@" + ann.getQualifiedName(), param);
          param.getModifierList().addAfter(annotation, null);
          break;
        }
      }


      setMethod.getParameterList().add(param);
      setMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
      setMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);

      @NonNls StringBuffer buffer = new StringBuffer();
      buffer.append("{\n");
      if (name.equals(parameterName)) {
        if (!isStatic) {
          buffer.append("this.");
        }
        else {
          String className = field.getContainingClass().getName();
          if (className != null) {
            buffer.append(className);
            buffer.append(".");
          }
        }
      }
      buffer.append(name);
      buffer.append("=");
      buffer.append(parameterName);
      buffer.append(";\n}");
      PsiCodeBlock body = factory.createCodeBlockFromText(buffer.toString(), null);
      setMethod.getBody().replace(body);
      setMethod = (PsiMethod)codeStyleManager.reformat(setMethod);
      return setMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public static String suggestPropertyName(Project project, PsiField field) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
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
  @Nullable public static String suggestPropertyAccessor(String name, PsiMethod accessorTemplate) {
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
}
