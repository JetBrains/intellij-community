// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Function;

/**
 * A yes-or-no question that each subject answers on its own, such as whether to delete a parameter in its whole hierarchy: the answer
 * of one subject does not answer the question of another.
 * <p>
 * The answer is the key of the subject in the keys, chosen for a yes and not chosen for a no, see
 * {@link ModPlanOptionValues#withAnswers}. The switch is the general answer of a request: an explicit value answers the question of every
 * subject, and the dialog does not set it. A request can also name the chosen subjects in the keys, as the dialogs do. A subject with no
 * key answers with the switch, so its answer answers every subject.
 */
// TODO IDEA-395594: keeps the answers of each subject as text keys among the up-front options of the request; keep them apart, by subject
@ApiStatus.Experimental
public final class ModPlanSubjectConfirmation implements ModPlanQuestion.Custom {
  private final @NotNull ModPlanOption<Boolean> mySwitch;
  private final @NotNull ModPlanOption<String> myKeys;
  private final @Nullable String myKey;
  private final @NotNull Function<? super Project, @Nullable Boolean> myConfirm;

  /**
   * @param aSwitch the general answer of a request
   * @param keys    the answers of the subjects: chosen for a yes, not chosen for a no
   * @param key     the key of this subject, which tells it from the other subjects of one request; null if it has none
   * @param confirm shows the dialog: true for yes, false for no, null for the cancel
   */
  public ModPlanSubjectConfirmation(@NotNull ModPlanOption<Boolean> aSwitch,
                                    @NotNull ModPlanOption<String> keys,
                                    @Nullable String key,
                                    @NotNull Function<? super Project, @Nullable Boolean> confirm) {
    mySwitch = aSwitch;
    myKeys = keys;
    myKey = key;
    myConfirm = confirm;
  }

  @Override
  public @NotNull @Unmodifiable List<ModPlanOption<?>> options() {
    return List.of(mySwitch, myKeys);
  }

  /** @return whether the switch has an explicit value, or the keys answer this subject */
  @Override
  public boolean isAnswered(@NotNull ModPlanOptionValues currentValues) {
    return currentValues.has(mySwitch) || myKey != null && currentValues.answeredKeys(myKeys).contains(myKey);
  }

  /**
   * @return the answer for this subject. An explicit false is no. Else a chosen key of this subject is yes; a key of it that is not
   * chosen is no; chosen keys of other subjects only are no; with no keys, the switch decides, explicit or by default.
   */
  public boolean isConfirmed(@NotNull ModPlanOptionValues answers) {
    if (answers.has(mySwitch) && !answers.get(mySwitch)) return false;
    if (myKey != null && answers.chosenKeys(myKeys).contains(myKey)) return true;
    if (myKey != null && answers.answeredKeys(myKeys).contains(myKey)) return false;
    if (!answers.chosenKeys(myKeys).isEmpty()) return false;
    return answers.get(mySwitch);
  }

  @RequiresEdt
  @Override
  public @Nullable ModPlanOptionValues ask(@NotNull Project project, @NotNull ModPlanOptionValues currentValues) {
    Boolean confirmed = myConfirm.apply(project);
    if (confirmed == null) return null;
    if (myKey == null) return currentValues.with(mySwitch, confirmed);
    return currentValues.withAnswers(myKeys, List.of(myKey), confirmed ? List.of(myKey) : List.of());
  }
}
