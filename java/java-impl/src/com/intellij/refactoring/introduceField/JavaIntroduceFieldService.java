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

/**
 * Headless application service that performs the "Introduce Field" and
 * "Introduce Constant" Java refactorings without showing any UI.
 */
@ApiStatus.Internal
public abstract class JavaIntroduceFieldService {

  /** @return the application-level service instance, or {@code null} if not registered. */
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
   * @return available settings.
   * Available initialization places for an already resolved expression context;
   * empty list means a field cannot be introduced for this expression.
   */
  public abstract @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull ToFieldContext.ExpressionContext context);

  /**
   * @return available settings.
   * Available initialization places when converting a local variable to a field;
   * empty list means the variable cannot be promoted.
   */
  public abstract @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull ToFieldContext.VariableContext context);

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

  /**
   * Introduces a field/constant for the selection at {@code range} inside {@code psiJavaFile}.
   *
   * @param isConstant {@code true} to create a {@code public static final} constant,
   *                   {@code false} for a regular field
   * @return the created field, or {@code null} if the refactoring cannot be performed
   */
  public abstract @Nullable PsiField introduceField(@NotNull PsiJavaFile psiJavaFile,
                                           @NotNull TextRange range,
                                           boolean isConstant,
                                           @NotNull JavaIntroduceFieldService.InitializationPlace place);

  /**
   * Where the new field's initializer is placed.
   */
  public enum InitializationPlace {
    /** Inside the method that contains the selected expression. */
    IN_CURRENT_METHOD,
    /** Directly on the field declaration ({@code Type field = expr;}). */
    IN_FIELD_DECLARATION,
    /** In the enclosing class's constructor(s). */
    IN_CONSTRUCTOR,
    /** In the JUnit {@code setUp()} method (for tests). */
    IN_SETUP_METHOD;

    /** @return the localized display name of the place, or {@code null} if {@code place} is {@code null}. */
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

    /** @return the localized short display name of the place, or {@code null} if {@code place} is {@code null}. */
    @Nls
    @Nullable
    public static String getShortPresentableText(@Nullable InitializationPlace place) {
      return switch (place) {
        case IN_CURRENT_METHOD -> JavaBundle.message("introduce.field.initialization.place.current.method.short");
        case IN_FIELD_DECLARATION -> JavaBundle.message("introduce.field.initialization.place.field.declaration.short");
        case IN_CONSTRUCTOR -> JavaBundle.message("introduce.field.initialization.place.constructor.short");
        case IN_SETUP_METHOD -> JavaBundle.message("introduce.field.initialization.place.setup.method.short");
        case null -> null;
      };
    }
  }

  /**
   * Result of {@link #getContext}: either an {@link Error}, a resolved expression
   * to extract ({@link ExpressionContext}), or a local variable that can be
   * promoted to a field ({@link VariableContext}).
   */
  sealed public interface ToFieldContext {
    /** The refactoring cannot proceed; {@link #message} explains why. */
    record Error(@NlsContexts.DialogMessage @NotNull String message) implements ToFieldContext {
    }

    /**
     * The selection resolves to an expression that can be extracted into a field.
     *
     * @param selectedExpr    the expression the user selected
     * @param element         the PSI element used as anchor for the new field
     * @param psiFile         the file containing {@code selectedExpr}
     * @param tempType        the inferred type of the future field
     * @param parentClass     the default target class for the new field
     * @param proposedClasses candidate target classes (the user / caller may pick another)
     */
    record ExpressionContext(@NotNull PsiExpression selectedExpr,
                   @NotNull PsiElement element,
                   @NotNull PsiFile psiFile,
                   @NotNull PsiType tempType,
                   @NotNull PsiClass parentClass,
                   @NotNull List<@NotNull PsiClass> proposedClasses) implements ToFieldContext {
    }

    /**
     * The selection resolves to a local variable that can be promoted to a field.
     *
     * @param localVariable                  the local variable to convert
     * @param variableToFieldCandidatesContext target-class candidates and the
     *                                       inferred {@code static} flag
     */
    record VariableContext(@NotNull PsiLocalVariable localVariable,
                           @NotNull VariableToFieldCandidatesContext variableToFieldCandidatesContext) implements ToFieldContext {
    }
  }

  /**
   * Information collected while analysing a local variable promotion candidate.
   *
   * @param tempIsStatic whether the new field should be declared {@code static}
   * @param classes      candidate target classes, ordered from innermost outwards
   */
  public record VariableToFieldCandidatesContext(boolean tempIsStatic, List<PsiClass> classes) { }

  /**
   * Initialization places the caller may choose from; an empty list means
   * "Introduce Field" is not available for the analysed expression/variable.
   */
  public record AvailableSettings(@NotNull List<@NotNull InitializationPlace> places) {
  }
}
