// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JavaIntroduceFieldModCommandServiceImpl extends JavaIntroduceFieldModCommandService {
  private static final FieldExtractor myFieldExtractor = new FieldExtractor(new IntroduceFieldHelper());
  private static final FieldExtractor myConstantExtractor = new FieldExtractor(new IntroduceConstantHelper());

  @Override
  public @NotNull ToFieldContext getContext(@NotNull PsiFile psiFile, @NotNull TextRange range, boolean isConstant) {
    return available(extractor(isConstant).getContext(psiFile, range), isConstant);
  }

  @Override
  public @NotNull ToFieldContext getContext(@NotNull PsiExpression expression, boolean isConstant) {
    return available(extractor(isConstant).getContext(expression.getContainingFile(), expression), isConstant);
  }

  @Override
  public @NotNull ModCommand introduceFieldCommand(@NotNull ActionContext context,
                                                   @NotNull ToFieldSite site,
                                                   boolean isConstant,
                                                   @NotNull ToFieldContext analysis,
                                                   @Nls @Nullable String familyName) {
    return chooseTargetClass(context, site, isConstant, analysis, familyName);
  }

  /** {@code context} itself, or an {@link ToFieldContext.Error} if no class it offers may hold the new field. */
  private static @NotNull ToFieldContext available(@NotNull ToFieldContext context, boolean isConstant) {
    return context instanceof ToFieldContext.Available found && targetClasses(found, isConstant).isEmpty()
           ? cannotExtract()
           : context;
  }

  /** The classes of {@code context} that may hold the new field, in the order of its {@code candidateClasses()}. */
  private static @NotNull List<@NotNull TargetClassChoice> targetClasses(@NotNull ToFieldContext.Available context,
                                                                         boolean isConstant) {
    List<PsiClass> candidates = context.candidateClasses();
    List<TargetClassChoice> targetClasses = new ArrayList<>(candidates.size());
    for (int index = 0; index < candidates.size(); index++) {
      PsiClass candidate = candidates.get(index);
      List<InitializationPlace> places;
      if (isConstant) {
        places = List.of(InitializationPlace.IN_FIELD_DECLARATION);
      }
      else {
        places = availablePlaces(context.withTargetClass(candidate));
        if (places.isEmpty()) continue;
      }
      targetClasses.add(new TargetClassChoice(index, ClassPresentationUtil.getNameForClass(candidate, false), places));
    }
    return targetClasses;
  }

  /** What is reported when there is nothing to extract, or no class the field could be created in. */
  private static @NotNull ToFieldContext.Error cannotExtract() {
    return new ToFieldContext.Error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
  }

  /** A command asking the user to pick the target class, when the field may be created in more than one. */
  private static @NotNull ModCommand chooseTargetClass(@NotNull ActionContext context,
                                                       @NotNull ToFieldSite site,
                                                       boolean isConstant,
                                                       @NotNull ToFieldContext analysis,
                                                       @Nls @Nullable String familyName) {
    if (!(analysis instanceof ToFieldContext.Available available)) {
      return ModCommand.error(((ToFieldContext.Error)analysis).message());
    }
    List<TargetClassChoice> targetClasses = targetClasses(available, isConstant);
    if (targetClasses.isEmpty()) {
      return ModCommand.error(cannotExtract().message());
    }
    if (targetClasses.size() == 1) {
      return chooseInitializationPlace(context, site, isConstant, available, targetClasses.getFirst(), familyName);
    }
    return ModCommand.chooseAction(
      JavaRefactoringBundle.message(isConstant ? "popup.title.choose.class.to.introduce.constant"
                                               : "popup.title.choose.class.to.introduce.field"),
      ContainerUtil.map(targetClasses, targetClass -> ModCommandAction
        .of(targetClass.presentableName(), familyName,
            pickedContext -> chooseInitializationPlace(pickedContext, site, isConstant, available, targetClass, familyName))
        .withPresentation(presentation -> presentation.withHighlighting(highlightRanges(available)))));
  }

  /** A command creating the field in {@code targetClass}, asking where to initialize it if there is a choice. */
  private static @NotNull ModCommand chooseInitializationPlace(@NotNull ActionContext context,
                                                               @NotNull ToFieldSite site,
                                                               boolean isConstant,
                                                               @NotNull ToFieldContext.Available analysis,
                                                               @NotNull TargetClassChoice targetClass,
                                                               @Nls @Nullable String familyName) {
    List<InitializationPlace> places = ContainerUtil.filter(targetClass.places(),
                                                            place -> InitializationPlace.getPresentableText(place) != null);
    if (places.isEmpty()) {
      return ModCommand.error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    }
    if (places.size() == 1) {
      return createFieldCommand(context, site, isConstant, places.getFirst(), targetClass.index());
    }
    return ModCommand.chooseAction(
      JavaBundle.message("introduce.field.initialize.in.scope"),
      ContainerUtil.map(places, place -> ModCommandAction
        .of(Objects.requireNonNull(InitializationPlace.getPresentableText(place)), familyName,
            pickedContext -> createFieldCommand(pickedContext, site, isConstant, place, targetClass.index()))
        .withPresentation(presentation -> presentation.withHighlighting(highlightRanges(analysis)))));
  }

  /** The ranges of {@code analysis} to highlight while a chooser entry of the refactoring is selected. */
  private static @NotNull TextRange @NotNull [] highlightRanges(@NotNull ToFieldContext.Available analysis) {
    return analysis.highlightRanges().toArray(TextRange.EMPTY_ARRAY);
  }

  /**
   * A command creating the field initialized at {@code place}, in the class at {@code classIndex} of
   * {@link ToFieldContext.Available#candidateClasses()}, and renaming it inline afterwards.
   */
  private static @NotNull ModCommand createFieldCommand(@NotNull ActionContext context,
                                                        @NotNull ToFieldSite site,
                                                        boolean isConstant,
                                                        @NotNull InitializationPlace place,
                                                        int classIndex) {
    return site.psiUpdate(context, updater -> {
      if (!(site.resolve(updater) instanceof ToFieldContext.Available resolved)) return;
      markForReplacement(resolved);
      List<PsiClass> candidates = resolved.candidateClasses();
      if (classIndex < 0 || classIndex >= candidates.size()) return;
      PsiField field = introduceField(resolved.withTargetClass(candidates.get(classIndex)), isConstant, place);
      if (field == null) return;
      updater.rename(field, new VariableNameGenerator(field, isConstant ? VariableKind.STATIC_FINAL_FIELD : VariableKind.FIELD)
        .byExpression(field.getInitializer())
        .byType(field.getType())
        .generateAll(true));
    });
  }

  /**
   * Marks every element the refactoring is about to replace to be replaced despite living in the non-physical copy a
   * ModCommand updates, which it would leave alone otherwise: the extracted expression, or, for a variable being
   * promoted, its references, which become references to the new field whenever its name differs.
   */
  private static void markForReplacement(@NotNull ToFieldContext.Available context) {
    switch (context) {
      case ToFieldContext.ExpressionContext expressionContext -> markForReplacement(expressionContext.selectedExpr());
      case ToFieldContext.VariableContext variableContext -> {
        PsiLocalVariable local = variableContext.localVariable();
        for (PsiReference reference : ReferencesSearch.search(local, GlobalSearchScope.projectScope(local.getProject()), false)) {
          markForReplacement(reference.getElement());
        }
      }
    }
  }

  /**
   * Marks {@code element} unless it carries an {@link ElementToWorkOn#PARENT} — a part of a literal, or the varargs
   * arguments of a call — which is replaced by rebuilding the text of that parent around the new reference instead, see
   * {@link ElementToWorkOn#getWritable}.
   */
  private static void markForReplacement(@NotNull PsiElement element) {
    if (element.getUserData(ElementToWorkOn.PARENT) == null) {
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(element, true);
    }
  }

  private static @Nullable PsiField introduceField(@NotNull ToFieldContext.Available context,
                                                   boolean isConstant,
                                                   @NotNull InitializationPlace place) {
    FieldExtractor extractor = extractor(isConstant);
    return switch (context) {
      case ToFieldContext.ExpressionContext expressionContext -> extractor.extractField(expressionContext, place);
      case ToFieldContext.VariableContext variableContext -> extractor.extractField(variableContext, place);
    };
  }

  private static @NotNull List<@NotNull InitializationPlace> availablePlaces(@NotNull ToFieldContext.Available context) {
    return switch (context) {
      case ToFieldContext.ExpressionContext expressionContext -> myFieldExtractor.getAvailableSettings(expressionContext).places();
      case ToFieldContext.VariableContext variableContext -> myFieldExtractor.getAvailableSettings(variableContext).places();
    };
  }

  private static @NotNull FieldExtractor extractor(boolean isConstant) {
    return isConstant ? myConstantExtractor : myFieldExtractor;
  }

  /**
   * One entry of the target class chooser.
   *
   * @param index           the index of the class in {@link ToFieldContext.Available#candidateClasses()}, which is how
   *                        it is found again in the copy being updated
   * @param presentableName the name of the class, as the chooser entry shows it
   * @param places          the initialization places available in that class
   */
  private record TargetClassChoice(int index,
                                   @Nls @NotNull String presentableName,
                                   @NotNull List<@NotNull InitializationPlace> places) {
  }
}
