// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ParameterObjectBuilder {
  private final PsiElement myContext;
  private String className;
  private String packageName;
  private final List<ParameterSpec> fields = new ArrayList<>(5);
  private final List<PsiTypeParameter> typeParams = new ArrayList<>();
  private String myVisibility;

  ParameterObjectBuilder(@NotNull PsiElement context) {
    myContext = context;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public void addField(PsiParameter variable, String name, PsiType type, boolean setterRequired) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myContext.getProject());
    final String propertyName = codeStyleManager.variableNameToPropertyName(name, VariableKind.PARAMETER);
    String variableName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD);
    PsiType normalizedType = type instanceof PsiEllipsisType ellipsisType ? ellipsisType.toArrayType() : type;
    fields.add(new ParameterSpec(variable, variableName, normalizedType, setterRequired));
  }

  public void setTypeArguments(Collection<PsiTypeParameter> typeParams) {
    this.typeParams.clear();
    this.typeParams.addAll(typeParams);
  }

  public String buildText() {
    boolean recordsAvailable =
      PsiUtil.isAvailable(JavaFeature.RECORDS, myContext) && !ContainerUtil.exists(fields, ParameterSpec::setterRequired);
    final @NonNls StringBuilder out = new StringBuilder(1024);
    if (!packageName.isEmpty()) out.append("package ").append(packageName).append(";\n");
    out.append(myVisibility).append(" ");
    out.append(recordsAvailable ? JavaKeywords.RECORD : JavaKeywords.CLASS);
    out.append(" ").append(className);
    if (!typeParams.isEmpty()) {
      out.append('<');
      StringUtil.join(typeParams, PsiElement::getText, ",", out);
      out.append('>');
    }

    if (recordsAvailable) {
      out.append("(");
      StringUtil.join(fields, param -> {
        PsiType type = param.type();
        if (param.parameter().isVarArgs() && type instanceof PsiArrayType arrayType) {
          type = new PsiEllipsisType(arrayType.getComponentType(), type.getAnnotations());
        }
        return type.getCanonicalText(true) + " " + param.name();
      }, ",", out);
      out.append("){}");
    }
    else {
      out.append("\n{");
      for (ParameterSpec field : fields) {
        outputField(field, out);
      }
      outputConstructor(out);
      for (ParameterSpec field : fields) {
        outputGetter(field, out);
      }
      for (ParameterSpec field : fields) {
        outputSetter(field, out);
      }
      out.append("}\n");
    }
    return out.toString();
  }

  private void outputSetter(ParameterSpec spec, @NonNls StringBuilder out) {
    if (!spec.setterRequired()) {
      return;
    }
    PsiField field = JavaPsiFacade.getElementFactory(myContext.getProject()).createField(spec.name(), spec.type());
    out.append(GenerateMembersUtil.generateSetterPrototype(field).getText());
  }

  private static void generateFieldAssignment(@NonNls StringBuilder out, String parameterName, String fieldName) {
    if (fieldName.equals(parameterName)) {
      out.append("this.");
    }
    out.append(fieldName).append('=').append(parameterName).append(";\n");
  }

  private void outputGetter(ParameterSpec spec, @NonNls StringBuilder out) {
    PsiField field = JavaPsiFacade.getElementFactory(myContext.getProject()).createField(spec.name(), spec.type());
    out.append(GenerateMembersUtil.generateGetterPrototype(field).getText());
  }

  private void outputConstructor(@NonNls StringBuilder out) {
    out.append(myVisibility).append(" ").append(className).append('(');
    boolean first = true;
    for (ParameterSpec field : fields) {
      if (!first) out.append(',');
      first = false;
      final PsiParameter parameter = field.parameter();
      outputAnnotationString(parameter, out);
      CodeStyleSettings settings = CodeStyle.getSettings(myContext.getContainingFile());
      if (settings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
        out.append(" final ");
      }
      final PsiType type = field.type();
      final PsiType fieldType = parameter.isVarArgs() && type instanceof PsiArrayType arrayType ?
                                new PsiEllipsisType(arrayType.getComponentType()) : type;
      out.append(' ').append(fieldType.getCanonicalText()).append(' ').append(parameter.getName());
    }
    out.append("){\n");
    for (ParameterSpec field : fields) {
      generateFieldAssignment(out, field.parameter().getName(), field.name());
    }
    out.append("}\n");
  }

  private static void outputField(ParameterSpec field, StringBuilder out) {
    final PsiParameter parameter = field.parameter();
    final PsiDocComment docComment = getJavadocForVariable(parameter);
    if (docComment != null) {
      out.append(docComment.getText()).append('\n');
    }
    out.append("private ");
    if (!field.setterRequired()) {
      out.append("final ");
    }
    outputAnnotationString(parameter, out);
    out.append(field.type().getCanonicalText()).append(' ').append(field.name()).append(";\n");
  }

  private static void outputAnnotationString(PsiParameter parameter, StringBuilder out) {
    final PsiModifierList modifierList = parameter.getModifierList();
    assert modifierList != null;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      final PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
      if (reference == null) {
        continue;
      }
      final PsiClass annotationClass = (PsiClass)reference.resolve();
      if (annotationClass != null) {
        out.append('@').append(annotationClass.getQualifiedName()).append(annotation.getParameterList().getText());
      }
    }
  }

  private static PsiDocComment getJavadocForVariable(PsiVariable variable) {
    for (PsiElement child : variable.getChildren()) {
      if (child instanceof PsiDocComment comment) {
        return comment;
      }
    }
    return null;
  }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }

  private record ParameterSpec(PsiParameter parameter, String name, PsiType type, boolean setterRequired) {}
}
