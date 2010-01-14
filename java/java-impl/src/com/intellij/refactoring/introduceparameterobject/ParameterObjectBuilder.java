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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class ParameterObjectBuilder {
    private String className = null;
    private String packageName = null;
    private final List<ParameterSpec> fields = new ArrayList<ParameterSpec>(5);
    private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
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
        final ParameterSpec field = new ParameterSpec(variable, name, type, setterRequired);
        fields.add(field);
    }

    public void setTypeArguments(List<PsiTypeParameter> typeParams) {
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
      final PsiParameter parameter = field.getParameter();
      final PsiType type = field.getType();
        final String typeText;
        if (parameter.isVarArgs()) {
            typeText = ((PsiArrayType) type).getComponentType().getCanonicalText() + "...";
        } else {
            typeText = type.getCanonicalText();
        }
        final String name = calculateStrippedName(field.getName());
        final String capitalizedName = StringUtil.capitalize(name);
        final String parameterName =
                myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);

        out.append("\tpublic void set" + capitalizedName + '(');
        outputAnnotationString(parameter, out);
        out.append(CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS?"final " : "");
        out.append(' ' +typeText + ' ' + parameterName + ")\n");
        out.append("\t{\n");
        final String fieldName = myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.FIELD);
      generateFieldAssignment(out, parameterName, fieldName);
      out.append("\t}\n");
    }

  private static void generateFieldAssignment(final StringBuffer out, final String parameterName, final String fieldName) {
    if (fieldName.equals(parameterName)) {
        out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
    } else {
        out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
    }
  }

  @NonNls
    private String calculateStrippedName(String name) {
        return myJavaCodeStyleManager.variableNameToPropertyName(name, VariableKind.PARAMETER);
    }

    private void outputGetter(ParameterSpec field, @NonNls StringBuffer out) {
        final PsiParameter parameter = field.getParameter();
        final PsiType type = field.getType();
        final String typeText;
        if (parameter.isVarArgs()) {
            typeText = ((PsiArrayType) type).getComponentType().getCanonicalText() + "[]";
        } else {
            typeText = type.getCanonicalText();
        }
        final String name = calculateStrippedName(field.getName());
        final String capitalizedName = StringUtil.capitalize(name);
        if (PsiType.BOOLEAN.equals(type)) {
            out.append('\t');
            outputAnnotationString(parameter, out);
            out.append(" public "+ typeText + " is" + capitalizedName + "()\n");
        } else {
            out.append('\t');
            outputAnnotationString(parameter, out);
            out.append(" public " +typeText + " get" + capitalizedName + "()\n");
        }
        out.append("\t{\n");
        final String fieldName = myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.FIELD);
        out.append("\t\treturn " + fieldName + ";\n");
        out.append("\t}\n");
    }

    private void outputConstructor(@NonNls StringBuffer out) {
        out.append("\t" + myVisibility + " " + className + '(');
        for (Iterator<ParameterSpec> iterator = fields.iterator(); iterator.hasNext();) {
            final ParameterSpec field = iterator.next();
            final PsiParameter parameter = field.getParameter();
            outputAnnotationString(parameter, out);
            out.append(CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS ? " final " : "");

            final PsiType type = field.getType();
            final String typeText;
            if (parameter.isVarArgs()) {
              typeText = ((PsiArrayType) type).getComponentType().getCanonicalText() + "...";
            } else {
              typeText = type.getCanonicalText();
            }
            final String name = calculateStrippedName(field.getName());
            final String parameterName =
              myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
            out.append(' ' +typeText + ' ' + parameterName);
            if (iterator.hasNext()) {
                out.append(", ");
            }
        }
        out.append(")\n");
        out.append("\t{\n");
        for (final ParameterSpec field : fields) {
            final String name = calculateStrippedName(field.getName());
            final String fieldName = myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.FIELD);
            final String parameterName =
            myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
          generateFieldAssignment(out, parameterName, fieldName);
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
        final String typeText;
        if (parameter.isVarArgs()) {
            final PsiType componentType = ((PsiArrayType) type).getComponentType();
            typeText = componentType.getCanonicalText() + "[]";
        } else {
            typeText = type.getCanonicalText();
        }
        final String name = calculateStrippedName(field.getName());
        @NonNls String modifierString = "private ";
        if (!field.isSetterRequired()) {
            modifierString += "final ";
        }
        outputAnnotationString(parameter, out);
        out.append('\t' + modifierString + typeText + ' ' + myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.FIELD) + ";\n");
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

