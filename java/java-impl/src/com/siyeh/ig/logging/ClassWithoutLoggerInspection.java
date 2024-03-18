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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.JavaLoggingUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.codeInspection.options.OptPane.*;

public final class ClassWithoutLoggerInspection extends BaseInspection {

  private final List<String> loggerNames = new ArrayList<>();
  /**
   * @noinspection PublicField
   */
  @NonNls
  public String loggerNamesString = StringUtil.join(JavaLoggingUtils.DEFAULT_LOGGERS, ",");
  /**
   * @noinspection PublicField
   */
  public boolean ignoreSuperLoggers = false;

  @SuppressWarnings("PublicField") public final ExternalizableStringSet annotations = new ExternalizableStringSet();
  @SuppressWarnings("PublicField") public final ExternalizableStringSet ignoredClasses = new ExternalizableStringSet(CommonClassNames.JAVA_LANG_THROWABLE);

  public ClassWithoutLoggerInspection() {
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      tabs(
        tab(InspectionGadgetsBundle.message("class.without.logger.loggers.tab"),
            stringList("loggerNames", InspectionGadgetsBundle.message("logger.class.name"),
                       new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.logger.class")))),
        tab(InspectionGadgetsBundle.message("options.title.ignored.classes"),
            stringList("ignoredClasses", InspectionGadgetsBundle.message("ignored.class.hierarchies.border.title"),
                       new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.class.hierarchy.to.ignore.title"))),
            checkbox("ignoreSuperLoggers", InspectionGadgetsBundle.message("super.class.logger.option"))),
        tab(InspectionGadgetsBundle.message("class.without.logger.annotations.tab"),
            stringList("annotations", InspectionGadgetsBundle.message("ignore.classes.annotated.by"),
                       new JavaClassValidator().annotationsOnly()))
      )
    );
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("no.logger.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerNamesString = formatString(loggerNames);
    defaultWriteSettings(element, "annotations", "ignoredClasses");
    annotations.writeSettings(element, "annotations");
    ignoredClasses.writeSettings(element, "ignoredClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutLoggerVisitor();
  }

  private class ClassWithoutLoggerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType() || aClass.getContainingClass() != null) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (ignoredClasses.stream().anyMatch(ignoredClass -> InheritanceUtil.isInheritor(aClass, ignoredClass))) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, annotations, AnnotationUtil.CHECK_EXTERNAL | AnnotationUtil.CHECK_HIERARCHY)) {
        return;
      }
      final PsiField[] fields = ignoreSuperLoggers ? aClass.getAllFields() : aClass.getFields();
      if (Stream.of(fields).anyMatch(field -> isLogger(field) && PsiUtil.isAccessible(field, aClass, aClass))) {
        return;
      }
      registerClassError(aClass);
    }

    private boolean isLogger(PsiVariable variable) {
      final PsiType type = variable.getType();
      final String text = type.getCanonicalText();
      return loggerNames.contains(text);
    }
  }
}