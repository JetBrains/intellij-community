/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefEntity;
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
  CommonProblemDescriptor[] getDescriptions(RefEntity refEntity);

  /**
   * Drops all problems which have been collected for the specified reference graph node.
   *
   * @param refEntity the reference graph node.
   */
  void ignoreElement(RefEntity refEntity);

  /**
   * Registers a problem or several problems, with optional quickfixes, for the specified
   * reference graph node.
   *
   * @param refEntity                the reference graph node.
   * @param commonProblemDescriptors the descriptors for the problems to register.
   */
  void addProblemElement(final RefEntity refEntity, final CommonProblemDescriptor[] commonProblemDescriptors);
}
