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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.convertors.Convertor01;
import com.intellij.openapi.project.impl.convertors.Convertor12;
import com.intellij.openapi.project.impl.convertors.Convertor23;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import org.jdom.Element;

/**
 * @author mike
 */
public class IdeaProjectStoreImpl extends ProjectWithModulesStoreImpl {
  public IdeaProjectStoreImpl(final ProjectEx project) {
    super(project);
  }

  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myProject) {
      public XmlElementStorage.StorageData createWsStorageData() {
        return new IdeaWsStorageData(ROOT_TAG_NAME, myProject);
      }

      public XmlElementStorage.StorageData createIprStorageData() {
        return new IdeaIprStorageData(ROOT_TAG_NAME, myProject);
      }
    };
  }

  private class IdeaWsStorageData extends WsStorageData {
    public IdeaWsStorageData(final String rootElementName, final Project project) {
      super(rootElementName, project);
    }

    public IdeaWsStorageData(final WsStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new IdeaWsStorageData(this);
    }
  }

  private class IdeaIprStorageData extends IprStorageData {

    public IdeaIprStorageData(final String rootElementName, Project project) {
      super(rootElementName, project);
    }

    public IdeaIprStorageData(final IprStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new IdeaIprStorageData(this);
    }

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
