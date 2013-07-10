/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 6/5/13 8:11 PM
 */
public class ExternalSystemTaskLocation extends PsiLocation<PsiFile> {

  @NotNull private final ExternalTaskExecutionInfo myTaskInfo;

  public ExternalSystemTaskLocation(@NotNull Project project, @NotNull PsiFile psiElement, @NotNull ExternalTaskExecutionInfo taskInfo) {
    super(project, psiElement);
    myTaskInfo = taskInfo;
  }

  @NotNull
  public ExternalTaskExecutionInfo getTaskInfo() {
    return myTaskInfo;
  }
}
