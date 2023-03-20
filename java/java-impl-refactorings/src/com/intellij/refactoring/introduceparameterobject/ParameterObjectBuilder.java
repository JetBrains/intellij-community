/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

class ParameterObjectBuilder {
  private String className;
  private String packageName;
  private final List<ParameterSpec> fields = new ArrayList<>(5);
  private final List<PsiTypeParameter> typeParams = new ArrayList<>();
  private Project myProject;
  private PsiFile myFile;
  private JavaCodeStyleManager myJavaCodeStyleManager;
  private String myVisibility;

  public void setClassName(String className) {
    this.className = className;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public void addField(PsiParameter variable, String name, PsiType type, boolean setterRequired) {
    final String propertyName = myJavaCodeStyleManager.variableNameToPropertyName(name, VariableKind.PARAMETER);
    final ParameterSpec field =
      new ParameterSpec(variable, myJavaCodeStyleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD),
                        type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type, setterRequired);
    fields.add(field);
  }

  public void setTypeArguments(Collection<PsiTypeParameter> typeParams) {
    this.typeParams.clear();
    this.typeParams.addAll(typeParams);
  }

  public void setProject(final Project project) {
    myProject = project;
    myJavaCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
  }

  public void setFile(@NotNull PsiFile file) {
    myFile = file;
  }

  public String buildBeanClass() {
    boolean recordsAvailable = HighlightingFeature.RECORDS.isAvailable(myFile) &&
                               !ContainerUtil.exists(fields, ParameterSpec::isSetterRequired);
    @NonNls final StringBuilder out = new StringBuilder(1024);
    if (packageName.length() > 0) out.append("package ").append(packageName).append(';');
    out.append('\n');
    out.append(myVisibility).append(" ");
    out.append(recordsAvailable ? PsiKeyword.RECORD : PsiKeyword.CLASS);
    out.append(" ").append(className);
    if (!typeParams.isEmpty()) {
      out.append('<');
      boolean first = true;
      for (PsiTypeParameter typeParam : typeParams) {
        if (!first) {
          out.append(',');
        }
        out.append(typeParam.getText());
        first = false;
      }
      out.append('>');
    }


    if (recordsAvailable) {
      out.append("(");
      fields.stream().map(param -> param.getType().getCanonicalText(true) + " " + param.getName());
      StringUtil.join(fields, param -> {
        PsiType type = param.getType();
        if (param.getParameter().isVarArgs() && type instanceof PsiArrayType) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType(), type.getAnnotations());
        }
        return type.getCanonicalText(true) + " " + param.getName();
      }, ", ", out);
      out.append("){}");
    }
    else {

      out.append('\n');

      out.append('{');
      outputFields(out);
      outputConstructor(out);
      outputGetters(out);
      outputSetters(out);
      out.append("}\n");
    }
    return out.toString();
  }

  private void outputSetters(@NonNls StringBuilder out) {
    for (final ParameterSpec field : fields) {
      outputSetter(field, out);
    }
  }

  private void outputGetters(@NonNls StringBuilder out) {
    for (final ParameterSpec field : fields) {
      outputGetter(field, out);
    }
  }

  private void outputFields(StringBuilder out) {
    for (final ParameterSpec field : fields) {
      outputField(field, out);
    }
  }

  private void outputSetter(ParameterSpec field, @NonNls StringBuilder out) {
    if (!field.isSetterRequired()) {
      return;
    }
    out.append(
      GenerateMembersUtil.generateSetterPrototype(JavaPsiFacade.getElementFactory(myProject).createField(field.getName(), field.getType()))
        .getText());
  }

  private static void generateFieldAssignment(final @NonNls StringBuilder out, final String parameterName, final String fieldName) {
    if (fieldName.equals(parameterName)) {
      out.append("\t\tthis.").append(fieldName).append(" = ").append(parameterName).append(";\n");
    }
    else {
      out.append("\t\t").append(fieldName).append(" = ").append(parameterName).append(";\n");
    }
  }

  private void outputGetter(ParameterSpec field, @NonNls StringBuilder out) {
    out.append(
      GenerateMembersUtil.generateGetterPrototype(JavaPsiFacade.getElementFactory(myProject).createField(field.getName(), field.getType()))
        .getText());
  }

  @NotNull
  private CodeStyleSettings getSettings() {
    return myFile != null ? CodeStyle.getSettings(myFile) : CodeStyle.getProjectOrDefaultSettings(myProject);
  }

  private void outputConstructor(@NonNls StringBuilder out) {
    out.append("\t").append(myVisibility).append(" ").append(className).append('(');
    for (Iterator<ParameterSpec> iterator = fields.iterator(); iterator.hasNext(); ) {
      final ParameterSpec field = iterator.next();
      final PsiParameter parameter = field.getParameter();
      outputAnnotationString(parameter, out);
      out.append(getSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS ?
                 " final " : "");
      final String parameterName = parameter.getName();
      final PsiType type = field.getType();
      final PsiType fieldType = parameter.isVarArgs() && type instanceof PsiArrayType ?
                                new PsiEllipsisType(((PsiArrayType)type).getComponentType()) : type;
      out.append(' ').append(fieldType.getCanonicalText()).append(' ').append(parameterName);
      if (iterator.hasNext()) {
        out.append(", ");
      }
    }
    out.append(")\n");
    out.append("\t{\n");
    for (final ParameterSpec field : fields) {
      generateFieldAssignment(out, field.getParameter().getName(), field.getName());
    }
    out.append("\t}\n");
  }

  private static void outputField(ParameterSpec field, StringBuilder out) {
    final PsiParameter parameter = field.getParameter();
    final PsiDocComment docComment = getJavadocForVariable(parameter);
    if (docComment != null) {
      out.append(docComment.getText());
      out.append('\n');
    }
    final PsiType type = field.getType();
    final String typeText = type.getCanonicalText();
    final String name = field.getName();
    @NonNls String modifierString = "private ";
    if (!field.isSetterRequired()) {
      modifierString += "final ";
    }
    outputAnnotationString(parameter, out);
    out.append('\t').append(modifierString).append(typeText).append(' ').append(name).append(";\n");
  }

  private static void outputAnnotationString(PsiParameter parameter, StringBuilder out) {
    final PsiModifierList modifierList = parameter.getModifierList();
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
      if (reference == null) {
        continue;
      }
      final PsiClass annotationClass = (PsiClass)reference.resolve();
      if (annotationClass != null) {
        final PsiAnnotationParameterList parameterList = annotation.getParameterList();
        final String annotationText = '@' + annotationClass.getQualifiedName() + parameterList.getText();
        out.append(annotationText);
      }
    }
  }

  private static PsiDocComment getJavadocForVariable(PsiVariable variable) {
    final PsiElement[] children = variable.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocComment) {
        return (PsiDocComment)child;
      }
    }
    return null;
  }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }
}

