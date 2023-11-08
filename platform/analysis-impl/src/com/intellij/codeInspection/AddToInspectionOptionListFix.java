// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * Fix to add a new value to inspection option list
 * 
 * @param <T> inspection class
 */
public class AddToInspectionOptionListFix<T extends InspectionProfileEntry> extends ModCommandQuickFix implements LowPriorityAction {
  private final String myItemToAdd;
  private final @IntentionName String myFixName;
  private final @NotNull T myInspection;
  private final @NotNull Function<@NotNull T, @NotNull List<String>> myExtractor;

  /**
   * @param inspection inspection object
   * @param fixName name of the quick-fix
   * @param itemToAdd item to add
   * @param listExtractor a function that retrieves the option
   */
  public AddToInspectionOptionListFix(@NotNull T inspection, @IntentionName String fixName, @NotNull String itemToAdd, 
                                      @NotNull Function<@NotNull T, @NotNull List<String>> listExtractor) {
    myInspection = inspection;
    myExtractor = listExtractor;
    myItemToAdd = itemToAdd;
    myFixName = fixName;
  }

  @Override
  public @NotNull String getName() {
    return myFixName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFixName;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    return ModCommand.updateOption(descriptor.getStartElement(), myInspection,
                                    inspection -> {
                                      List<String> list = myExtractor.apply(inspection);
                                      list.add(myItemToAdd);
                                      list.sort(null);
                                    });
  }
}
