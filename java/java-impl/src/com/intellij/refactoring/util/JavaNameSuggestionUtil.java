// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaNameSuggestionUtil {
  public static NameSuggestionsGenerator createFieldNameGenerator(final boolean willBeDeclaredStatic,
                                                                  final PsiLocalVariable localVariable,
                                                                  final PsiExpression initializerExpression,
                                                                  final boolean isInvokedOnDeclaration,
                                                                  @Nullable final String enteredName,
                                                                  final PsiClass parentClass,
                                                                  final Project project) {
    return new NameSuggestionsGenerator() {
      private final JavaCodeStyleManager myCodeStyleManager = JavaCodeStyleManager.getInstance(project);

      @Override
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        VariableKind variableKind = willBeDeclaredStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;

        String propertyName = null;
        if (isInvokedOnDeclaration) {
          propertyName = myCodeStyleManager.variableNameToPropertyName(localVariable.getName(), VariableKind.LOCAL_VARIABLE);
        }
        final SuggestedNameInfo nameInfo = myCodeStyleManager.suggestVariableName(variableKind, propertyName, initializerExpression, type);
        if (initializerExpression != null) {
          String[] names = nameInfo.names;
          for (int i = 0, namesLength = names.length; i < namesLength; i++) {
            String name = names[i];
            if (parentClass.findFieldByName(name, false) != null) {
              names[i] = myCodeStyleManager.suggestUniqueVariableName(name, initializerExpression, true);
            }
          }
        }
        final String[] strings = appendUnresolvedExprName(
          JavaCompletionUtil.completeVariableNameForRefactoring(myCodeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo),
          initializerExpression);
        return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings) : strings,
                                              nameInfo);
      }
    };
  }

  public static String[] appendUnresolvedExprName(String[] names, final PsiExpression expr) {
    if (expr instanceof PsiReferenceExpression && ((PsiReferenceExpression)expr).resolve() == null) {
      final String name = expr.getText();
      if (PsiNameHelper.getInstance(expr.getProject()).isIdentifier(name, LanguageLevel.HIGHEST)) {
        names = ArrayUtil.mergeArrays(new String[]{name}, names);
      }
    }
    return names;
  }

  public static SuggestedNameInfo suggestFieldName(@Nullable PsiType defaultType,
                                                   @Nullable final PsiLocalVariable localVariable,
                                                   final PsiExpression initializer,
                                                   final boolean forStatic,
                                                   @NotNull final PsiClass parentClass) {
    return createFieldNameGenerator(forStatic, localVariable, initializer, localVariable != null, null,
                                    parentClass, parentClass.getProject()).getSuggestedNameInfo(defaultType);
  }
}
