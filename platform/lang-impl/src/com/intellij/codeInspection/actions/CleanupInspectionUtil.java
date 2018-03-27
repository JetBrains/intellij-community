package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface CleanupInspectionUtil {
  static CleanupInspectionUtil getInstance() {
    return ServiceManager.getService(CleanupInspectionUtil.class);
  }

  AbstractPerformFixesTask applyFixesNoSort(@NotNull Project project,
                                            @NotNull String presentationText,
                                            @NotNull List<ProblemDescriptor> descriptions,
                                            @Nullable Class quickfixClass,
                                            boolean startInWriteAction);

  default AbstractPerformFixesTask applyFixes(@NotNull Project project,
                                              @NotNull String presentationText,
                                              @NotNull List<ProblemDescriptor> descriptions,
                                              @Nullable Class quickfixClass,
                                              boolean startInWriteAction) {
    sortDescriptions(descriptions);
    return applyFixesNoSort(project, presentationText, descriptions, quickfixClass, startInWriteAction);
  }

  default void sortDescriptions(@NotNull List<ProblemDescriptor> descriptions) {
    Collections.sort(descriptions, CommonProblemDescriptor.DESCRIPTOR_COMPARATOR);
  }
}
