// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CleanupInspectionUtil {
  static CleanupInspectionUtil getInstance() {
    return ApplicationManager.getApplication().getService(CleanupInspectionUtil.class);
  }

  AbstractPerformFixesTask applyFixesNoSort(@NotNull Project project,
                                            @NotNull @NlsContexts.DialogTitle String presentationText,
                                            @NotNull List<? extends ProblemDescriptor> descriptions,
                                            @Nullable Class<?> quickfixClass,
                                            boolean startInWriteAction);

  default AbstractPerformFixesTask applyFixesNoSort(@NotNull Project project,
                                                    @NotNull @NlsContexts.DialogTitle String presentationText,
                                                    @NotNull List<? extends ProblemDescriptor> descriptions,
                                                    @Nullable Class<?> quickfixClass,
                                                    boolean startInWriteAction,
                                                    boolean markGlobal) {
    return applyFixesNoSort(project, presentationText, descriptions, quickfixClass, startInWriteAction);
  }

  default AbstractPerformFixesTask applyFixes(@NotNull Project project,
                                              @NotNull @NlsContexts.DialogTitle String presentationText,
                                              @NotNull List<ProblemDescriptor> descriptions,
                                              @Nullable Class<?> quickfixClass,
                                              boolean startInWriteAction) {
    sortDescriptions(descriptions);
    return applyFixesNoSort(project, presentationText, descriptions, quickfixClass, startInWriteAction, true);
  }

  default void sortDescriptions(@NotNull List<ProblemDescriptor> descriptions) {
    descriptions.sort(CommonProblemDescriptor.DESCRIPTOR_COMPARATOR);
  }
}
