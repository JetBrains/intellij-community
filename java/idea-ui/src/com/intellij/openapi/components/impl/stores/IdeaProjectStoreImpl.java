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
