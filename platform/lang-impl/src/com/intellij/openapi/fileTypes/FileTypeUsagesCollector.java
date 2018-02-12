/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.project.ProjectKt;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public class FileTypeUsagesCollector extends AbstractProjectsUsagesCollector {
  private static final String GROUP_ID = "file-type";

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull final Project project) throws CollectUsagesException {
    final Set<FileType> usedFileTypes = new HashSet<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager == null) {
      throw new CollectUsagesException("Cannot get instance of FileTypeManager");
    }
    final FileType[] registeredFileTypes = fileTypeManager.getRegisteredFileTypes();
    for (final FileType fileType : registeredFileTypes) {
      if (project.isDisposed()) {
        throw new CollectUsagesException("Project is disposed");
      }
      ApplicationManager.getApplication().runReadAction(() -> {
        FileTypeIndex.processFiles(fileType, file -> {
          //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
          if (!ProjectKt.getStateStore(project).isProjectFile(file)) {
            usedFileTypes.add(fileType);
            return false;
          }
          return true;
        }, GlobalSearchScope.projectScope(project));
      });
    }
    return ContainerUtil.map2Set(usedFileTypes, (NotNullFunction<FileType, UsageDescriptor>)fileType -> new UsageDescriptor(fileType.getName(), 1));
  }
}
