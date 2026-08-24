// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Headless application service that performs the "Introduce Field" and
 * "Introduce Constant" Java refactorings without showing any UI.
 */
@ApiStatus.Internal
public abstract class JavaIntroduceFieldModCommandService {

  /** @return the application-level service instance, or {@code null} if not registered. */
  public static @Nullable JavaIntroduceFieldModCommandService getInstance() {
    return ApplicationManager.getApplication().getService(JavaIntroduceFieldModCommandService.class);
  }

  /**
   * The expression or local variable of {@code range} to extract, or an {@link ToFieldContext.Error} if there is
   * nothing to extract there, or no class the new field could be created in.
   *
   * @param isConstant whether a constant is being introduced rather than a field
   */
  public abstract @NotNull ToFieldContext getContext(@NotNull PsiFile psiFile, @NotNull TextRange range, boolean isConstant);

  /** Same, for an expression that has already been found, e.g. inside a copy of a file with different offsets. */
  public abstract @NotNull ToFieldContext getContext(@NotNull PsiExpression expression, boolean isConstant);

  /**
   * A command introducing a field (or a constant, if {@code isConstant}) from what {@code site} finds, asking the user
   * to pick the target class and then the initialization place whenever more than one is available, and renaming the
   * created field inline afterwards. An {@code analysis} that is an {@link ToFieldContext.Error} yields a command
   * reporting it, so a caller that would rather not offer the refactoring there checks for it itself.
   *
   * @param site       how every round of the command finds the element again in the copy being updated
   * @param isConstant must be the same value {@code analysis} was retrieved with
   * @param analysis   what to offer, retrieved by {@link #getContext}
   * @param familyName the {@link ModCommandAction#getFamilyName()} of the chooser entries, their own text if
   *                   {@code null}
   */
  public abstract @NotNull ModCommand introduceFieldCommand(@NotNull ActionContext context,
                                                            @NotNull ToFieldSite site,
                                                            boolean isConstant,
                                                            @NotNull ToFieldContext analysis,
                                                            @Nls @Nullable String familyName);

  /**
   * How {@link #introduceFieldCommand} finds what to extract inside the copy of the file being updated.
   * <p>
   * A command is re-entered once per chooser round, each time from a fresh {@link ActionContext} on the physical file,
   * so an implementation must be immutable and must not keep PSI of a previous round's copy, except as a tree-position
   * token for {@link com.intellij.psi.util.PsiTreeUtil#findSameElementInCopy}.
   */
  public interface ToFieldSite {
    /**
     * Resolves what to extract in the copy being updated. Making the resolved PSI replaceable there is the command's
     * job, so an implementation only has to find the element again.
     *
     * @return the context, a {@link ToFieldContext.Error} if the element to extract is gone from the copy
     */
    @NotNull ToFieldContext resolve(@NotNull ModPsiUpdater updater);

    /**
     * Runs {@code action} on a writable copy of the file of {@code context}. Overridden by a caller that has to
     * pre-process the copy, like a postfix template removing its template key.
     */
    default @NotNull ModCommand psiUpdate(@NotNull ActionContext context, @NotNull Consumer<@NotNull ModPsiUpdater> action) {
      return ModCommand.psiUpdate(context, action);
    }
  }

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

  /** What the refactoring found for a selection: an {@link Error}, or something {@link Available} to extract. */
  public sealed interface ToFieldContext {
    /** The refactoring cannot proceed; {@link #message} explains why. */
    record Error(@NlsContexts.DialogMessage @NotNull String message) implements ToFieldContext {
    }

    /** Something was found to extract: an expression, or a local variable to promote. */
    sealed interface Available extends ToFieldContext {
      /**
       * The classes the new field may be created in, innermost first; empty if there is no class that may hold it.
       */
      @NotNull List<@NotNull PsiClass> candidateClasses();

      /** The class the new field will be created in, or {@code null} if no class may hold it. */
      @Nullable PsiClass targetClass();

      /**
       * @param targetClass the class to create the field in, must be one of {@link #candidateClasses()}
       * @return a copy of this context creating the field in {@code targetClass}
       */
      @NotNull Available withTargetClass(@NotNull PsiClass targetClass);

      /** The ranges of the element being extracted, highlighted while a chooser entry is selected. */
      @NotNull List<@NotNull TextRange> highlightRanges();
    }

    /**
     * The selection resolves to an expression that can be extracted into a field.
     *
     * @param selectedExpr    the expression the user selected
     * @param element         the PSI element used as anchor for the new field
     * @param psiFile         the file containing {@code selectedExpr}
     * @param tempType        the inferred type of the future field
     * @param parentClass     the target class for the new field, the innermost enclosing one by default, which is not
     *                        necessarily one of {@code proposedClasses}
     * @param proposedClasses candidate target classes (the user / caller may pick another)
     */
    record ExpressionContext(@NotNull PsiExpression selectedExpr,
                             @NotNull PsiElement element,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiType tempType,
                             @NotNull PsiClass parentClass,
                             @NotNull List<@NotNull PsiClass> proposedClasses) implements Available {
      @Override
      public @NotNull List<@NotNull PsiClass> candidateClasses() {
        return proposedClasses;
      }

      @Override
      public @NotNull PsiClass targetClass() {
        return parentClass;
      }

      @Override
      public @NotNull Available withTargetClass(@NotNull PsiClass targetClass) {
        if (!proposedClasses.contains(targetClass)) {
          throw new IllegalArgumentException("The target class must be one of the proposed ones");
        }
        return new ExpressionContext(selectedExpr, element, psiFile, tempType, targetClass, proposedClasses);
      }

      @Override
      public @NotNull List<@NotNull TextRange> highlightRanges() {
        return List.of(selectedExpr.getTextRange());
      }
    }

    /**
     * The selection resolves to a local variable that can be promoted to a field.
     *
     * @param localVariable                    the local variable to convert
     * @param variableToFieldCandidatesContext target-class candidates and the
     *                                         inferred {@code static} flag
     */
    record VariableContext(@NotNull PsiLocalVariable localVariable,
                           @NotNull VariableToFieldCandidatesContext variableToFieldCandidatesContext) implements Available {
      @Override
      public @NotNull List<@NotNull PsiClass> candidateClasses() {
        return variableToFieldCandidatesContext.classes();
      }

      @Override
      public @Nullable PsiClass targetClass() {
        List<PsiClass> classes = candidateClasses();
        return classes.isEmpty() ? null : classes.getFirst();
      }

      @Override
      public @NotNull Available withTargetClass(@NotNull PsiClass targetClass) {
        List<PsiClass> classes = candidateClasses();
        if (!classes.contains(targetClass)) {
          throw new IllegalArgumentException("The target class must be one of the candidate ones");
        }
        List<PsiClass> reordered = new ArrayList<>(classes.size());
        reordered.add(targetClass);
        for (PsiClass candidate : classes) {
          if (candidate != targetClass) {
            reordered.add(candidate);
          }
        }
        return new VariableContext(localVariable,
                                   new VariableToFieldCandidatesContext(variableToFieldCandidatesContext.tempIsStatic(), reordered));
      }

      @Override
      public @NotNull List<@NotNull TextRange> highlightRanges() {
        return List.of(localVariable.getTextRange());
      }
    }
  }

  /**
   * Information collected while analysing a local variable promotion candidate.
   *
   * @param tempIsStatic whether the new field should be declared {@code static}
   * @param classes      candidate target classes, ordered from innermost outwards, except that the first one is
   *                     where the field is created, see {@link ToFieldContext.Available#withTargetClass}
   */
  public record VariableToFieldCandidatesContext(boolean tempIsStatic, List<PsiClass> classes) {
  }

  /**
   * Initialization places the caller may choose from; an empty list means
   * "Introduce Field" is not available for the analysed expression/variable.
   */
  public record AvailableSettings(@NotNull List<@NotNull InitializationPlace> places) {
  }
}
