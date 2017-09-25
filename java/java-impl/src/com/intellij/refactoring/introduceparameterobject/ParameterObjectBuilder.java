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

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NonNls;

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
  private JavaCodeStyleManager myJavaCodeStyleManager ;
  private String myVisibility;

  public void setClassName(String className) {
        this.className = className;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void addField(PsiParameter variable, String name, PsiType type, boolean setterRequired) {
      final String propertyName = myJavaCodeStyleManager.variableNameToPropertyName(name, VariableKind.PARAMETER);
      final ParameterSpec field = new ParameterSpec(variable, myJavaCodeStyleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD), 
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

  public String buildBeanClass() {
        @NonNls final StringBuffer out = new StringBuffer(1024);
        if (packageName.length() > 0) out.append("package " + packageName + ';');
        out.append('\n');
        out.append(myVisibility + " class " + className);
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
        out.append('\n');

        out.append('{');
        outputFields(out);
        outputConstructor(out);
        outputGetters(out);
        outputSetters(out);
        out.append("}\n");
        return out.toString();
    }

    private void outputSetters(StringBuffer out) {
        for (final ParameterSpec field : fields) {
            outputSetter(field, out);
        }
    }

    private void outputGetters(StringBuffer out) {
        for (final ParameterSpec field : fields) {
            outputGetter(field, out);
        }
    }

    private void outputFields(StringBuffer out) {
        for (final ParameterSpec field : fields) {
            outputField(field, out);
        }
    }

    private void outputSetter(ParameterSpec field, @NonNls StringBuffer out) {
        if (!field.isSetterRequired()) {
            return;
        }
      out.append(GenerateMembersUtil.generateSetterPrototype(JavaPsiFacade.getElementFactory(myProject).createField(field.getName(), field.getType())).getText());
    }

  private static void generateFieldAssignment(final StringBuffer out, final String parameterName, final String fieldName) {
    if (fieldName.equals(parameterName)) {
        out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
    } else {
        out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
    }
  }

  private void outputGetter(ParameterSpec field, @NonNls StringBuffer out) {
      out.append(GenerateMembersUtil.generateGetterPrototype(JavaPsiFacade.getElementFactory(myProject).createField(field.getName(), field.getType())).getText());
    }

    private void outputConstructor(@NonNls StringBuffer out) {
        out.append("\t" + myVisibility + " " + className + '(');
        for (Iterator<ParameterSpec> iterator = fields.iterator(); iterator.hasNext();) {
          final ParameterSpec field = iterator.next();
          final PsiParameter parameter = field.getParameter();
            outputAnnotationString(parameter, out);
          out.append(
            CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS ?
            " final " : "");
            final String parameterName = parameter.getName();
          final PsiType type = field.getType();
          final PsiType fieldType = parameter.isVarArgs() && type instanceof PsiArrayType ?
                                    new PsiEllipsisType(((PsiArrayType)type).getComponentType()) : type;
            out.append(' ' + fieldType.getCanonicalText() + ' ' + parameterName);
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

    private void outputField(ParameterSpec field, StringBuffer out) {
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
        out.append('\t' + modifierString + typeText + ' ' + name + ";\n");
    }

    private void outputAnnotationString(PsiParameter parameter, StringBuffer out) {
        final PsiModifierList modifierList = parameter.getModifierList();
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            final PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            if (reference == null) {
                continue;
            }
            final PsiClass annotationClass = (PsiClass) reference.resolve();
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
                return (PsiDocComment) child;
            }
        }
        return null;
    }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }
}

