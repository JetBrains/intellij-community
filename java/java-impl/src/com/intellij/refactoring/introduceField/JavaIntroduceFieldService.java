// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public abstract class JavaIntroduceFieldService {

  public static @Nullable JavaIntroduceFieldService getInstance() {
    return ApplicationManager.getApplication().getService(JavaIntroduceFieldService.class);
  }

  /**
   * Available initialization places for the given expression; empty list means field cannot be introduced.
   */
  public @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull PsiExpression expression) {
    return new AvailableSettings(List.of());
  }

  /**
   * Retrieves the context for converting a specific code expression into a field within a Java class.
   *
   * @param psiFile the psi file containing the code expression to be converted, must not be null
   * @param range the text range specifying the location of the expression within the file, must not be null
   * @param isConstant represents if it is a constant or not
   * @return a non-null context object that provides details required for the field introduction process
   */
  public abstract @NotNull JavaIntroduceFieldService.ToFieldContext getContext(@NotNull PsiFile psiFile,
                                                                               @NotNull TextRange range,
                                                                               boolean isConstant);

  /**
   * Introduce field for expression at the given initialization place.
   *
   * @return created field, or {@code null} if the refactoring cannot be performed
   */
  public abstract @Nullable PsiField introduceField(@NotNull PsiExpression expression,
                                           @NotNull JavaIntroduceFieldService.InitializationPlace place);

  public abstract @Nullable PsiField introduceField(@NotNull PsiJavaFile psiJavaFile,
                                           @NotNull TextRange range,
                                           boolean isConstant,
                                           @NotNull JavaIntroduceFieldService.InitializationPlace place);

  public enum InitializationPlace {
    IN_CURRENT_METHOD,
    IN_FIELD_DECLARATION,
    IN_CONSTRUCTOR,
    IN_SETUP_METHOD;

    @Nls
    @Nullable
    public static String getPresentableText(@Nullable InitializationPlace place) {
      return switch (place) {
        case IN_CURRENT_METHOD -> JavaBundle.message("introduce.field.initialization.place.current.method");
        case IN_FIELD_DECLARATION -> JavaBundle.message("introduce.field.initialization.place.field.declaration");
        case IN_CONSTRUCTOR -> JavaBundle.message("introduce.field.initialization.place.constructor");
        case IN_SETUP_METHOD -> JavaBundle.message("introduce.field.initialization.place.setup.method");
        case null -> null;
      };
    }
  }

  sealed public interface ToFieldContext {
    record Error(@NlsContexts.DialogMessage @NotNull String message) implements ToFieldContext {
    }

    record ExpressionContext(@NotNull PsiExpression selectedExpr,
                   @NotNull PsiElement element,
                   @NotNull PsiFile psiFile,
                   @NotNull PsiType tempType,
                   @NotNull PsiClass parentClass,
                   @NotNull List<@NotNull PsiClass> proposedClasses) implements ToFieldContext {
    }

    record VariableContext(@NotNull PsiLocalVariable localVariable,
                           @NotNull VariableToFieldCandidatesContext variableToFieldCandidatesContext) implements ToFieldContext {
    }
  }

  public record VariableToFieldCandidatesContext(boolean tempIsStatic, List<PsiClass> classes) { }

  public record AvailableSettings(@NotNull List<@NotNull InitializationPlace> places) {
  }
}
