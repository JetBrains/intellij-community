// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.IntroduceChoiceAction;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.IntroduceSite;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase.IntroduceVariableResult;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase.JavaReplaceChoice;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableModCommandService.ToVariableContext.OccurrenceChoice;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class JavaIntroduceVariableModCommandServiceImpl extends JavaIntroduceVariableModCommandService {
  @Override
  public @Nullable TextRange adjustSelection(@NotNull PsiFile psiFile, int offset, @NotNull TextRange selection) {
    if (!IntroduceHandlerBase.isAvailableForQuickList(psiFile, selection)) return null;
    return super.adjustSelection(psiFile, offset, selection);
  }

  @Override
  public @NotNull ToVariableContext getContext(@Nullable PsiExpression expression) {
    if (expression == null) return new ToVariableContext.Error(null);
    IntroduceVariableResult result = IntroduceVariableBase.getIntroduceVariableContext(expression.getProject(), expression, null);
    if (!(result instanceof IntroduceVariableResult.Context context)) {
      return new ToVariableContext.Error(((IntroduceVariableResult.Error)result).message);
    }
    List<OccurrenceChoice> choices = new ArrayList<>();
    for (Map.Entry<JavaReplaceChoice, List<PsiExpression>> entry : occurrencesMap(context).entrySet()) {
      List<PsiExpression> occurrences = entry.getValue();
      choices.add(new OccurrenceChoice(choices.size(),
                                       entry.getKey().formatDescription(occurrences.size()),
                                       ContainerUtil.map(occurrences, PsiElement::getTextRange)));
    }
    if (choices.isEmpty()) return new ToVariableContext.Error(null);
    return new ToVariableContext.Available(chooserTitle(context), choices);
  }

  @Override
  public @NotNull ModCommand introduceVariableCommand(@NotNull ActionContext context,
                                                      @NotNull IntroduceSite site,
                                                      @NotNull ToVariableContext analysis,
                                                      @Nls @Nullable String familyName) {
    if (!(analysis instanceof ToVariableContext.Available(@Nls String title, List<OccurrenceChoice> choices))) {
      return errorCommand(analysis);
    }
    if (choices.size() == 1) {
      return introduceVariableCommand(context, site, choices.getFirst().index());
    }
    //noinspection DialogTitleCapitalization
    return ModCommand.chooseAction(
      title != null ? title : RefactoringBundle.message("replace.multiple.occurrences.found"),
      ContainerUtil.map(choices, choice -> new IntroduceChoiceAction(
        choice.description(), familyName, choice.occurrences(),
        pickedContext -> introduceVariableCommand(pickedContext, site, choice.index()))));
  }

  /**
   * A command introducing a variable at {@code site} replacing the occurrences of the choice at {@code choiceIndex} of
   * {@link ToVariableContext.Available#choices()}, and starting an inline rename of it afterwards.
   */
  private static @NotNull ModCommand introduceVariableCommand(@NotNull ActionContext context,
                                                              @NotNull IntroduceSite site,
                                                              int choiceIndex) {
    Consumer<@NotNull ModPsiUpdater> action = updater -> {
      PsiExpression expression = site.locate(updater);
      if (expression == null) return;
      PsiVariable variable = introduceVariable(expression, choiceIndex);
      if (variable == null) return;
      updater.rename(variable, new VariableNameGenerator(variable, VariableKind.LOCAL_VARIABLE)
        .byExpression(variable.getInitializer())
        .byType(variable.getType())
        .generateAll(true));
    };
    return site.psiUpdate(context, action);
  }

  /**
   * Introduces a variable for {@code expression}, replacing the occurrences of the choice at {@code choiceIndex} of
   * {@link ToVariableContext.Available#choices()}.
   *
   * @return the created variable, or {@code null} if the refactoring cannot be performed
   */
  private static @Nullable PsiVariable introduceVariable(@NotNull PsiExpression expression, int choiceIndex) {
    Project project = expression.getProject();
    IntroduceVariableResult result = IntroduceVariableBase.getIntroduceVariableContext(project, expression, null);
    if (!(result instanceof IntroduceVariableResult.Context context)) return null;

    List<JavaReplaceChoice> choices = List.copyOf(occurrencesMap(context).keySet());
    if (choiceIndex < 0 || choiceIndex >= choices.size()) return null;
    JavaReplaceChoice choice = choices.get(choiceIndex);

    PsiExpression[] occurrences = choice.filter(context.occurrenceManager());
    if (occurrences.length == 0) return null;
    PsiElement anchor = IntroduceVariableBase.getAnchor(occurrences);
    if (anchor == null) anchor = context.anchorStatement();

    if (expression.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) == Boolean.TRUE) {
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(anchor, true);
      Arrays.stream(occurrences).forEach(occurrence -> ElementToWorkOn.REPLACE_NON_PHYSICAL.set(occurrence, true));
    }
    IntroduceVariableSettings settings = IntroduceVariableBase.headlessSettings(context, choice, anchor, occurrences);
    // Extracts without a write action of its own, as the file being modified is already under one.
    return VariableExtractor.introduceInReadAction(project, context.expression(), anchor, occurrences, settings);
  }

  /**
   * The ways to replace the occurrences of {@code context}, in the order they are offered in.
   * <p>
   * The choices extracting a method chain are left out: they need a further interactive step, which no headless caller
   * can take.
   */
  private static @NotNull LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap(
    @NotNull IntroduceVariableResult.Context context) {
    LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> map =
      context.occurrencesInfo().buildOccurrencesMap(context.expression());
    map.keySet().removeIf(JavaReplaceChoice::isChain);
    return map;
  }

  /** The title of the occurrences chooser, as {@link IntroduceVariableBase} shows it. */
  private static @Nls @NotNull String chooserTitle(@NotNull IntroduceVariableResult.Context context) {
    return context.occurrencesInfo().myChainMethodName != null && context.occurrenceManager().getOccurrences().length == 1
           ? JavaRefactoringBundle.message("replace.lambda.chain.detected")
           : RefactoringBundle.message("replace.multiple.occurrences.found");
  }
}
