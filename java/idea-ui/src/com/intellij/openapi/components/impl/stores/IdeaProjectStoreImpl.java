/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.convertors.Convertor01;
import com.intellij.openapi.project.impl.convertors.Convertor12;
import com.intellij.openapi.project.impl.convertors.Convertor23;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

class IdeaProjectStoreImpl extends ProjectWithModulesStoreImpl {
  public IdeaProjectStoreImpl(@NotNull ProjectImpl project, @NotNull PathMacroManager pathMacroManager) {
    super(project, pathMacroManager);
  }

  @NotNull
  @Override
  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(myPathMacroManager.createTrackingSubstitutor(), myProject) {
      @NotNull
      @Override
      public StorageData createIprStorageData(@NotNull String filePath) {
        return new IdeaIprStorageData(ROOT_TAG_NAME, myProject, filePath);
      }
    };
  }

  private static class IdeaIprStorageData extends IprStorageData {
    private final String myFilePath;

    public IdeaIprStorageData(@NotNull String rootElementName, @NotNull Project project, @NotNull String filePath) {
      super(rootElementName, project);

      myFilePath = filePath;
    }

    private IdeaIprStorageData(@NotNull IdeaIprStorageData storageData) {
      super(storageData);

      myFilePath = storageData.myFilePath;
    }

    @Override
    public StorageData clone() {
      return new IdeaIprStorageData(this);
    }

    @Override
    protected void convert(final Element root, final int originalVersion) {
      if (originalVersion < 1) {
        Convertor01.execute(root);
      }
      if (originalVersion < 2) {
        Convertor12.execute(root);
      }
      if (originalVersion < 3) {
        Convertor23.execute(root);
      }
      if (originalVersion < 4) {
        Convertor34.execute(root, myFilePath, getConversionProblemsStorage());
      }
    }
  }
}
