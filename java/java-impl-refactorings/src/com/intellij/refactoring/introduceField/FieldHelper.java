// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;


import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Strategy that encapsulates the variant-specific decisions for the
 * "Introduce Field" / "Introduce Constant" Java refactorings.
 * <p>
 * Two implementations are provided in this package:
 * <ul>
 *   <li>{@link IntroduceFieldHelper} – introduces a regular instance/static field;</li>
 *   <li>{@link IntroduceConstantHelper} – introduces a {@code public static final} constant.</li>
 * </ul>
 * <p>
 * <b>This type does not use or invoke any UI</b> (no Swing dialogs, no
 * {@code Messages}, no popups): all decisions are taken from
 * {@link com.intellij.refactoring.JavaRefactoringSettings} / persistent properties or
 * computed from PSI. Therefore the helper can be safely used from non-interactive
 * contexts such as {@code ModCommand} actions and the LSP language server, where
 * showing a dialog is not possible.
 */
sealed interface FieldHelper permits IntroduceFieldHelper, IntroduceConstantHelper {
  /**
   * Decides whether this helper is applicable to the given element.
   *
   * @param elementToWorkOn the local variable or expression chosen as the refactoring target
   * @return {@code true} if this helper can process the element; {@code false} otherwise
   *         (e.g. {@link IntroduceConstantHelper} returns {@code false} when the
   *         initializer is not a compile-time constant)
   */
  boolean accept(@NotNull ElementToWorkOn elementToWorkOn);

  /**
   * @return {@code true} if this helper performs "Introduce Constant"
   *         (i.e. produces a {@code static final} field), {@code false} for the
   *         regular "Introduce Field" refactoring. Affects target-class resolution
   *         (see {@link #getParentClass(PsiExpression)}) and various PSI checks.
   */
  boolean isConstant();

  /**
   * @return the localized refactoring name, suitable for dialog titles and error
   *         messages (e.g. "Introduce Field" or "Introduce Constant")
   */
  @NlsContexts.DialogTitle
  @NotNull String getRefactoringName();

  /**
   * Checks the occurrences of a given local variable in the code.
   *
   * @param variable the local variable to be checked; must not be null.
   * @return a localized message describing the occurrences of the variable, or null if no issues are found.
   */
  @Nullable @Nls
  String checkOccurrences(@NotNull PsiLocalVariable variable);

  /**
   * Checks the occurrences of the specified expression within the given class.
   *
   * @param expr   the expression to be analyzed; must not be null.
   * @param aClass the class within which to analyze occurrences of the expression; must not be null.
   * @return a localized message describing the occurrences of the expression within the class,
   *         or null if no issues are found.
   */
  @Nullable @Nls
  String checkOccurrences(@NotNull PsiExpression expr, @NotNull PsiClass aClass);

  /**
   * Resolves the class in which the new field/constant must be declared.
   * The default implementation delegates to {@link FieldExtractor#getParentClass}
   * which, for constants, walks up to the enclosing static container.
   *
   * @param initializerExpression the expression that will become the field initializer
   * @return the target class, or {@code null} if no suitable class is found
   */
  default @Nullable PsiClass getParentClass(@NotNull PsiExpression initializerExpression) {
    return FieldExtractor.getParentClass(initializerExpression, isConstant());
  }

  /**
   * Validates the chosen target class against the selected expression.
   * Typical checks include: interfaces cannot host new fields, fields of unknown
   * type (lambda/method-reference types, {@code null}) cannot be introduced,
   * local classes that are not visible from the target class are forbidden, etc.
   *
   * @param parentClass  the candidate target class returned by {@link #getParentClass(PsiExpression)}
   * @param selectedExpr the expression selected by the user
   * @return {@code null} if the target class is acceptable, otherwise a localized
   *         error message
   */
  @NlsContexts.DialogMessage @Nullable String checkClass(@NotNull PsiClass parentClass,
                                                         @NotNull PsiExpression selectedExpr);

  /**
   * Creates an {@link OccurrenceManager} that finds further occurrences of the
   * selected expression inside the target class. The manager is used to power the
   * "Replace all occurrences" option of the refactoring.
   *
   * @param expr   the originally selected expression
   * @param aClass the class to search within (returned by {@link #getParentClass(PsiExpression)})
   * @return an occurrence manager tailored to this refactoring's filters
   *         (for instance, {@link IntroduceFieldHelper} excludes occurrences inside
   *         {@code this(...)}/{@code super(...)} calls)
   */
  @NotNull OccurrenceManager createOccurrenceManager(@NotNull PsiExpression expr, @NotNull PsiClass aClass);

  /**
   * Builds the complete {@link BaseExpressionToFieldHandler.Settings} for a
   * "convert local variable to field" flow, without any user interaction.
   * Visibility, name, initialization place, static-ness and other options are
   * taken from persisted refactoring settings and from PSI analysis.
   *
   * @param context     a snapshot of the local variable being converted plus candidate target classes
   * @param place       where the field has to be initialised (declaration / current method / constructor / setUp)
   * @param occurrences pre-computed occurrences that should be replaced by the new field reference
   * @return ready-to-execute settings consumed by {@link BaseExpressionToFieldHandler}
   */
  @NotNull
  BaseExpressionToFieldHandler.Settings getSettings(@NotNull JavaIntroduceFieldService.ToFieldContext.VariableContext context,
                                                    @NotNull JavaIntroduceFieldService.InitializationPlace place,
                                                    @NotNull PsiExpression @NotNull[] occurrences);

  /**
   * @return the default visibility modifier ({@link PsiModifier#PRIVATE},
   *         {@link PsiModifier#PUBLIC}, etc.) for the introduced field.
   *         For "Introduce Field" this is taken from
   *         {@link com.intellij.refactoring.JavaRefactoringSettings#INTRODUCE_FIELD_VISIBILITY};
   *         "Introduce Constant" always returns {@link PsiModifier#PUBLIC}.
   */
  @PsiModifier.ModifierConstant @NotNull String getVisibility();

  /**
   * Produces name candidates for the new field/constant based on the expression
   * being extracted, its type and the target class. The first name in the
   * returned info is used as the default in non-interactive scenarios.
   *
   * @param context    the expression that will be extracted, its inferred type and parent class
   * @param parameters auxiliary parameters such as the {@code declareStatic} flag
   * @return suggested field names compliant with the project code style
   */
  @NotNull SuggestedNameInfo getSuggestedNameInfo(@NotNull JavaIntroduceFieldService.ToFieldContext.ExpressionContext context,
                                                  @NotNull FieldExtractor.SettingParameters parameters);

  /**
   * Validates that the given expression and/or local variable can serve as a
   * valid initializer for the field/constant being introduced.
   * <p>
   * "Introduce Constant" requires the initializer to be eligible as a
   * {@code static final} initializer (no references to local variables /
   * parameters / instance state). "Introduce Field" has no such restriction,
   * so the default implementation accepts everything.
   *
   * @param expr          the expression selected by the user, or {@code null}
   *                      when a local variable is being promoted
   * @param localVariable the local variable to convert, or {@code null} when
   *                      an expression is being extracted directly
   * @return a description of the validation failure containing a localized
   *         dialog-ready message and the offending PSI element to highlight,
   *         or {@code null} if the input is acceptable
   */
  default @Nullable InvalidInitializer checkInitializer(@Nullable PsiExpression expr,
                                                        @Nullable PsiLocalVariable localVariable) {
    return null;
  }

  /**
   * Outcome of {@link #checkInitializer}: the value is rejected because it cannot
   * serve as the initializer for the field/constant being introduced.
   *
   * @param message      localized, dialog-ready explanation of why the
   *                     initializer is rejected
   * @param errorElement the specific PSI element to highlight in the editor;
   *                     {@code null} when no precise sub-element can be
   *                     pinpointed (e.g. a local variable with no initializer)
   */
  record InvalidInitializer(@NlsContexts.DialogMessage @NotNull String message,
                            @Nullable PsiElement errorElement) {
  }
}
