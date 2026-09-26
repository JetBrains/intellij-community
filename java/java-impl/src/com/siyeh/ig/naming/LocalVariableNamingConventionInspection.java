/*
 * Copyright 2003-2026 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class LocalVariableNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 1;
  private static final int DEFAULT_MAX_LENGTH = 20;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreForLoopParameters = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCatchParameters = false;
  public boolean ignorePatternVariables = false;

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignorePatternVariables");
    writeBooleanOption(node, "ignorePatternVariables", false);
  }

  @Override
  public OptRegularComponent @NotNull [] createExtraOptions() {
    return new OptRegularComponent[] {
      OptPane.checkbox("m_ignoreForLoopParameters", InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.option")),
      OptPane.checkbox("m_ignoreCatchParameters", InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.catch.option")),
      OptPane.checkbox("ignorePatternVariables", InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.pattern.variables.option"))
    };
  }

  @Override
  protected String getElementDescription() {
    return InspectionGadgetsBundle.message("local.variable.naming.convention.element.description");
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (variable.isUnnamed()) return;
      if (m_ignoreForLoopParameters) {
        final PsiElement parent = variable.getParent();
        if (parent != null) {
          final PsiElement grandparent = parent.getParent();
          if (grandparent instanceof PsiForStatement forLoop) {
            final PsiStatement initialization = forLoop.getInitialization();
            if (parent.equals(initialization)) {
              return;
            }
          }
        }
      }
      final String name = variable.getName();
      if (isValid(name)) {
        return;
      }
      registerVariableError(variable, name);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      if (variable.isUnnamed()) return;
      final PsiElement scope = variable.getDeclarationScope();
      final boolean isCatchParameter = scope instanceof PsiCatchSection;
      final boolean isForeachParameter = scope instanceof PsiForeachStatement;
      final boolean isPatternVariable = variable instanceof PsiPatternVariable;
      if (!isCatchParameter && !isForeachParameter && !isPatternVariable) {
        return;
      }
      if (m_ignoreCatchParameters && isCatchParameter) {
        return;
      }
      if (m_ignoreForLoopParameters && isForeachParameter) {
        return;
      }
      if (ignorePatternVariables && isPatternVariable) {
        return;
      }
      final String name = variable.getName();
      if (isValid(name)) {
        return;
      }
      registerVariableError(variable, name);
    }
  }
}