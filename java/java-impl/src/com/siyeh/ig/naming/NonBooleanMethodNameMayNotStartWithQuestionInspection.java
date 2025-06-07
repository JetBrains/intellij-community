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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public class NonBooleanMethodNameMayNotStartWithQuestionInspection extends BaseInspection {
  public static final @NonNls String DEFAULT_QUESTION_WORDS =
    "are,can,check,contains,could,endsWith,equals,has,is,matches,must,shall,should,startsWith,was,were,will,would";

  @SuppressWarnings("PublicField") public @NonNls String questionString = DEFAULT_QUESTION_WORDS;
  @SuppressWarnings("PublicField")
  public boolean ignoreBooleanMethods = false;
  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnBaseMethods = true;
  List<String> questionList = new ArrayList<>(32);

  public NonBooleanMethodNameMayNotStartWithQuestionInspection() {
    parseString(questionString, questionList);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("questionList", InspectionGadgetsBundle.message("boolean.method.name.must.start.with.question.table.label")),
      checkbox("ignoreBooleanMethods", InspectionGadgetsBundle.message("ignore.methods.with.boolean.return.type.option")),
      checkbox("onlyWarnOnBaseMethods", InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"))
    );
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    final LocalQuickFix suppressFix = SuppressForTestsScopeFix.build(this, context);
    if (suppressFix == null) {
      return new InspectionGadgetsFix[]{new RenameFix()};
    }
    return new LocalQuickFix[]{new RenameFix(), suppressFix};
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.boolean.method.name.must.not.start.with.question.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(questionString, questionList);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    questionString = formatString(questionList);
    super.writeSettings(element);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonBooleanMethodNameMayNotStartWithQuestionVisitor();
  }

  private class NonBooleanMethodNameMayNotStartWithQuestionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.equals(PsiTypes.booleanType())) {
        return;
      }
      if (ignoreBooleanMethods && returnType.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
        return;
      }
      if (!startsWithQuestionWord(method.getName())) {
        return;
      }
      if (onlyWarnOnBaseMethods) {
        if (MethodUtils.hasSuper(method)) {
          return;
        }
      }
      else if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method, method);
    }
  }

  protected boolean startsWithQuestionWord(String name) {
    for (String question : questionList) {
      if (name.startsWith(question)) {
        if (name.length() == question.length()) {
          return true;
        }
        final char nextChar = name.charAt(question.length());
        if (Character.isUpperCase(nextChar) || nextChar == '_') {
          return true;
        }
      }
    }
    return false;
  }
}
