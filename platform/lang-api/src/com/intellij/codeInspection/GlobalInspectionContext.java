/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * The context for a global inspection run. Provides access to the reference graph
 * and allows to enqueue external usage processing.
 *
 * @author anna
 * @see GlobalInspectionTool#runInspection
 * @see GlobalInspectionTool#queryExternalUsagesRequests
 * @since 6.0
 */
public interface GlobalInspectionContext {
  /**
   * Returns the reference graph for the global inspection run.
   *
   * @return the reference graph instance.
   */
  @NotNull RefManager getRefManager();

  /**
   * Checks if the inspection with the specified ID is suppressed for the
   * specified reference graph node. Should not be called manually in normal case.
   * You need to check suppressions only when you use {@link GlobalInspectionTool#runInspection}
   * @param entity           the reference graph node to check.
   * @param inspectionToolId the ID of the inspection to check.
   * @return true if the inspection is suppressed, false otherwise.
   * @deprecated use #shouldCheck instead
   */
  boolean isSuppressed(RefEntity entity, String inspectionToolId);

  /**
   * Checks if the inspection is suppressed for the specified reference graph node. Should not be called manually in normal case.
   * You need to check suppressions only when you use {@link GlobalInspectionTool#runInspection}
   * @param entity           the reference graph node to check.
   * @param tool             the inspection to check.
   * @return true if the inspection is suppressed, false otherwise.
   */
  boolean shouldCheck(RefEntity entity, GlobalInspectionTool tool);

  /**
   * Checks if the inspection with the specified ID is suppressed for the
   * specified PSI element.
   *
   * @param element          the PSI element to check.
   * @param inspectionToolId the ID of the inspection to check.
   * @return true if the inspection is suppressed, false otherwise.
   */
  boolean isSuppressed(PsiElement element, String inspectionToolId);

  Project getProject();

  <T> T getExtension(Key<T> key);

  void incrementJobDoneAmount(JobDescriptor job, String message);
}
