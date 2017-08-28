/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckBox;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionMemberAccessInspection extends BaseJavaBatchLocalInspectionTool {

  private static final Set<String> MEMBER_METHOD_NAMES = Collections.unmodifiableSet(
    ContainerUtil.set(GET_FIELD, GET_DECLARED_FIELD,
                      GET_METHOD, GET_DECLARED_METHOD,
                      GET_CONSTRUCTOR, GET_DECLARED_CONSTRUCTOR));

  private final List<String> ignoredClassNames = new ArrayList<>();

  public boolean checkMemberExistsInNonFinalClasses = true;
  public String ignoredClassNamesString = JAVA_LANG_OBJECT + "," + JAVA_LANG_THROWABLE;

  public JavaReflectionMemberAccessInspection() {
    parseSettings();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());

    final ListTable table = new ListTable(
      new ListWrappingTableModel(ignoredClassNames, InspectionsBundle.message(
        "inspection.reflection.member.access.check.exists.exclude")));
    final JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionsBundle.message(
      "inspection.reflection.member.access.check.exists.exclude.chooser"));

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    final CheckBox checkBox = new CheckBox(InspectionsBundle.message("inspection.reflection.member.access.check.exists"),
                                           this, "checkMemberExistsInNonFinalClasses");
    panel.add(checkBox, constraints);

    constraints.weighty = 1.0;
    constraints.gridy = 1;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(tablePanel, constraints);

    return panel;
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseSettings();
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    collectSettings();
    super.writeSettings(element);
  }

  private void parseSettings() {
    ignoredClassNames.clear();
    ContainerUtil.addAll(ignoredClassNames, ignoredClassNamesString.split(","));
  }

  private void collectSettings() {
    ignoredClassNamesString = ignoredClassNames.stream().collect(Collectors.joining(","));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        final String referenceName = expression.getMethodExpression().getReferenceName();
        if (referenceName != null && MEMBER_METHOD_NAMES.contains(referenceName)) {
          final PsiMethod method = expression.resolveMethod();
          if (method != null) {
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && JAVA_LANG_CLASS.equals(containingClass.getQualifiedName())) {
              switch (referenceName) {

                case GET_FIELD:
                  checkField(expression, false, holder);
                  break;
                case GET_DECLARED_FIELD:
                  checkField(expression, true, holder);
                  break;

                case GET_METHOD:
                  checkMethod(expression, false, holder);
                  break;
                case GET_DECLARED_METHOD:
                  checkMethod(expression, true, holder);
                  break;

                case GET_CONSTRUCTOR:
                  checkConstructor(expression, false, holder);
                  break;
                case GET_DECLARED_CONSTRUCTOR: {
                  checkConstructor(expression, true, holder);
                  break;
                }
              }
            }
          }
        }
      }
    };
  }

  private void checkField(@NotNull PsiMethodCallExpression callExpression, boolean isDeclared, @NotNull ProblemsHolder holder) {
    final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 0) {
      final PsiExpression nameExpression = arguments[0];
      final String fieldName = getMemberName(nameExpression);
      if (fieldName != null) {
        final ReflectiveClass ownerClass = getOwnerClass(callExpression);
        if (ownerClass != null && ownerClass.isExact()) {
          final PsiField field = ownerClass.getPsiClass().findFieldByName(fieldName, true);
          if (field == null) {
            if (reportUnresolvedMembersOf(ownerClass)) {
              holder.registerProblem(nameExpression, InspectionsBundle.message(
                "inspection.reflection.member.access.cannot.resolve.field", fieldName));
            }
            return;
          }
          if (isDeclared && field.getContainingClass() != ownerClass.getPsiClass()) {
            holder.registerProblem(nameExpression, InspectionsBundle.message(
              "inspection.reflection.member.access.field.not.in.class", fieldName, ownerClass.getPsiClass().getQualifiedName()));
            return;
          }
          if (!isDeclared && !field.hasModifierProperty(PsiModifier.PUBLIC)) {
            holder.registerProblem(nameExpression, InspectionsBundle.message(
              "inspection.reflection.member.access.field.not.public", fieldName));
          }
        }
      }
    }
  }

  private void checkMethod(@NotNull PsiMethodCallExpression callExpression, boolean isDeclared, @NotNull ProblemsHolder holder) {
    final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 0) {
      final PsiExpression nameExpression = arguments[0];
      final String methodName = getMemberName(nameExpression);
      if (methodName != null) {
        final ReflectiveClass ownerClass = getOwnerClass(callExpression);
        if (ownerClass != null && ownerClass.isExact()) {
          final PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(methodName, true);
          if (methods.length == 0) {
            if (reportUnresolvedMembersOf(ownerClass)) {
              holder.registerProblem(nameExpression, InspectionsBundle.message(
                "inspection.reflection.member.access.cannot.resolve.method", methodName));
            }
            return;
          }
          final PsiMethod matchingMethod = matchMethod(methods, arguments, 1);
          if (matchingMethod == null) {
            if (reportUnresolvedMembersOf(ownerClass)) {
              holder.registerProblem(nameExpression, InspectionsBundle.message(
                "inspection.reflection.member.access.cannot.resolve.method.arguments", methodName));
            }
            return;
          }
          if (isDeclared && matchingMethod.getContainingClass() != ownerClass.getPsiClass()) {
            holder.registerProblem(nameExpression, InspectionsBundle.message(
              "inspection.reflection.member.access.method.not.in.class", methodName, ownerClass.getPsiClass().getQualifiedName()));
            return;
          }
          if (!isDeclared && !matchingMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
            holder.registerProblem(nameExpression, InspectionsBundle.message(
              "inspection.reflection.member.access.method.not.public", methodName));
          }
        }
      }
    }
  }

  private void checkConstructor(@NotNull PsiMethodCallExpression callExpression,
                                boolean isDeclared,
                                @NotNull ProblemsHolder holder) {
    final ReflectiveClass ownerClass = getOwnerClass(callExpression);
    if (ownerClass != null && ownerClass.isExact()) {
      final PsiMethod[] methods = ownerClass.getPsiClass().getConstructors();
      final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
      final PsiModifierListOwner constructorOrClass;
      if (methods.length != 0) {
        constructorOrClass = matchMethod(methods, arguments, 0);
      }
      else {
        // implicit constructor
        constructorOrClass = arguments.length == 0 ? ownerClass.getPsiClass() : null;
      }
      if (constructorOrClass == null) {
        if (reportUnresolvedMembersOf(ownerClass)) {
          holder.registerProblem(callExpression.getArgumentList(), InspectionsBundle.message(
            "inspection.reflection.member.access.cannot.resolve.constructor.arguments"));
        }
        return;
      }
      if (!isDeclared && !constructorOrClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        holder.registerProblem(callExpression.getArgumentList(), InspectionsBundle.message(
          "inspection.reflection.member.access.constructor.not.public"));
      }
    }
  }

  private boolean reportUnresolvedMembersOf(@NotNull ReflectiveClass ownerClass) {
    return (checkMemberExistsInNonFinalClasses || ownerClass.getPsiClass().hasModifierProperty(PsiModifier.FINAL)) &&
           !ignoredClassNames.contains(ownerClass.getPsiClass().getQualifiedName());
  }

  @Contract("null->null")
  @Nullable
  private static String getMemberName(@Nullable PsiExpression memberNameArgument) {
    return computeConstantExpression(memberNameArgument, String.class);
  }

  @Nullable
  private static ReflectiveClass getOwnerClass(@NotNull PsiMethodCallExpression callExpression) {
    return getReflectiveClass(callExpression.getMethodExpression().getQualifierExpression());
  }

  @Nullable
  private static PsiMethod matchMethod(PsiMethod[] methods, PsiExpression[] arguments, int argumentOffset) {
    final JavaReflectionInvocationInspection.Arguments methodArguments =
      JavaReflectionInvocationInspection.getActualMethodArguments(arguments, argumentOffset, true);
    if (methodArguments == null) {
      return null;
    }
    final List<ReflectiveType> argumentTypes =
      ContainerUtil.map(methodArguments.expressions, JavaReflectionReferenceUtil::getReflectiveType);

    return JavaLangClassMemberReference.matchMethod(methods, argumentTypes);
  }
}
