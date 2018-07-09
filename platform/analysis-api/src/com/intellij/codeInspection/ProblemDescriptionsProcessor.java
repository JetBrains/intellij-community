/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects the results of a global inspection.
 *
 * @author anna
 * @since 6.0
 * @see GlobalInspectionTool#runInspection
 */
public interface ProblemDescriptionsProcessor {
  /**
   * Returns the problems which have been collected for the specified reference graph node.
   *
   * @param refEntity the reference graph node.
   * @return the problems found for the specified node.
   */
  @Nullable
  default CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
    return CommonProblemDescriptor.EMPTY_ARRAY;
  }

  /**
   * Drops all problems which have been collected for the specified reference graph node.
   *
   * @param refEntity the reference graph node.
   */
  default void ignoreElement(@NotNull RefEntity refEntity) {}

  default void resolveProblem(@NotNull CommonProblemDescriptor descriptor) {}

  /**
   * Registers a problem or several problems, with optional quickfixes, for the specified
   * reference graph node.
   *
   * @param refEntity                the reference graph node.
   * @param commonProblemDescriptors the descriptors for the problems to register.
   */
  default void addProblemElement(@Nullable RefEntity refEntity, @NotNull CommonProblemDescriptor... commonProblemDescriptors) {
  }

  default RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
    return null;
  }

  static void resolveAllProblemsInElement(@NotNull ProblemDescriptionsProcessor processor, @NotNull RefEntity element) {
    CommonProblemDescriptor[] descriptors = processor.getDescriptions(element);
    if (descriptors != null) {
      for (CommonProblemDescriptor descriptor : descriptors) {
        processor.resolveProblem(descriptor);
      }
    }
  }
}
