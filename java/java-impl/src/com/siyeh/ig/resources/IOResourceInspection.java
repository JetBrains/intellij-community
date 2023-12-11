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
package com.siyeh.ig.resources;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.stringList;

public final class IOResourceInspection extends ResourceInspection {
  final List<String> ignoredTypes = new ArrayList<>();
  @SuppressWarnings("PublicField")
  public String ignoredTypesString = "java.io.ByteArrayOutputStream" +
                                     ',' + "java.io.ByteArrayInputStream" +
                                     ',' + "java.io.StringBufferInputStream" +
                                     ',' + "java.io.CharArrayWriter" +
                                     ',' + "java.io.CharArrayReader" +
                                     ',' + "java.io.StringWriter" +
                                     ',' + "java.io.StringReader";

  public IOResourceInspection() {
    parseString(ignoredTypesString, ignoredTypes);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return super.getOptionsPane()
      .prepend(stringList("ignoredTypes", InspectionGadgetsBundle.message("ignored.io.resource.types.label"),
                          new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.io.resource.type.to.ignore"))
                          .withSuperClass("java.io.Closeable")));
  }

  @Override
  @NotNull
  public String getID() {
    return "IOResourceOpenedButNotSafelyClosed";
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(ignoredTypesString, ignoredTypes);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    ignoredTypesString = formatString(ignoredTypes);
    super.writeSettings(element);
  }

  @Override
  public boolean isResourceCreation(PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      return TypeUtils.expressionHasTypeOrSubtype(expression, "java.io.Closeable") && !isIgnoredType(expression);
    }
    else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!"getResourceAsStream".equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null ||
          TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_CLASS, "java.lang.ClassLoader") == null) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(expression, "java.io.InputStream");
    }
    return false;
  }

  private boolean isIgnoredType(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes);
  }
}
