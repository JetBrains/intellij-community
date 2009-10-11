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
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ReturnValueBeanBuilder {
    private String className = null;
    private String packageName = null;
    private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
    private Project myProject = null;
    private PsiType valueType = null;
  private boolean myStatic;

  public void setClassName(String className) {
        this.className = className;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setTypeArguments(List<PsiTypeParameter> typeParams) {
        this.typeParams.clear();
        this.typeParams.addAll(typeParams);
    }

    public void setCodeStyleSettings(Project settings) {
        this.myProject = settings;
    }

    public String buildBeanClass() throws IOException {
        @NonNls final StringBuffer out = new StringBuffer(1024);
       
        if (packageName.length() > 0) out.append("package " + packageName + ';');
        out.append('\n');
      out.append("public ");
      if (myStatic) out.append("static ");
      out.append("class ").append(className);
        if (!typeParams.isEmpty()) {
            out.append('<');
            boolean first = true;
            for (PsiTypeParameter typeParam : typeParams) {
                if (!first) {
                    out.append(',');
                }
                final String parameterText = typeParam.getText();
                out.append(parameterText);
                first = false;
            }
            out.append('>');
        }
        out.append('\n');

        out.append('{');
        outputField(out);
        out.append('\n');
        outputConstructor(out);
        out.append('\n');
        outputGetter(out);
        out.append("}\n");
        return out.toString();
    }

    private void outputGetter(@NonNls StringBuffer out) {
        final String typeText = valueType.getCanonicalText();
        @NonNls final String name = "value";
        final String capitalizedName = StringUtil.capitalize(name);
        out.append("\tpublic " + typeText + " get" + capitalizedName + "()\n");
        out.append("\t{\n");
      final String fieldName = getFieldName(name);
      out.append("\t\treturn " + fieldName + ";\n");
        out.append("\t}\n");
        out.append('\n');
    }

    private void outputField(@NonNls StringBuffer out) {
        final String typeText = valueType.getCanonicalText();
      out.append('\t' + "private final " + typeText + ' ' + getFieldName("value") + ";\n");
    }

    private void outputConstructor(@NonNls StringBuffer out) {
        out.append("\tpublic " + className + '(');
        final String typeText = valueType.getCanonicalText();
        @NonNls final String name = "value";
        final String parameterName =
                JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(name, VariableKind.PARAMETER);
        out.append(CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS ? "final " : "");
        out.append(typeText + ' ' + parameterName);
        out.append(")\n");
        out.append("\t{\n");
      final String fieldName = getFieldName(name);
      if (fieldName.equals(parameterName)) {
            out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
        } else {
            out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
        }
        out.append("\t}\n");
        out.append('\n');
    }

  private String getFieldName(final String name) {
    return JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(name, VariableKind.FIELD);
  }

  public void setValueType(PsiType valueType) {
        this.valueType = valueType;
    }

  public void setStatic(final boolean isStatic) {
    myStatic = isStatic;
  }
}

