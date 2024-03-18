/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.JavaLoggingUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class ClassWithMultipleLoggersInspection extends BaseInspection {

  private final List<String> loggerNames = new ArrayList<>();
  /**
   * @noinspection PublicField
   */
  @NonNls
  public String loggerNamesString = StringUtil.join(JavaLoggingUtils.DEFAULT_LOGGERS, ",");

  public ClassWithMultipleLoggersInspection() {
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("loggerNames", InspectionGadgetsBundle.message("logger.class.name"),
                 new JavaClassValidator()
                          .withTitle(InspectionGadgetsBundle.message("choose.logger.class")))
    );
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("multiple.loggers.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerNamesString = formatString(loggerNames);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithMultipleLoggersVisitor();
  }

  private class ClassWithMultipleLoggersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      int numLoggers = 0;
      for (PsiField field : aClass.getFields()) {
        if (isLogger(field)) {
          numLoggers++;
        }
      }
      if (numLoggers <= 1) {
        return;
      }
      registerClassError(aClass);
    }

    private boolean isLogger(PsiVariable variable) {
      return loggerNames.contains(variable.getType().getCanonicalText());
    }
  }
}