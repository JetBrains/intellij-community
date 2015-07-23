package com.intellij.configurationStore;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.StorageData;
import com.intellij.openapi.components.impl.stores.XmlElementStorage;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultProjectStorage extends XmlElementStorage {
  private final DefaultProjectStoreImpl store;
  private final ProjectManagerImpl projectManager;

  public DefaultProjectStorage(DefaultProjectStoreImpl store, PathMacroManager pathMacroManager, ProjectManagerImpl projectManager) {
    super("", RoamingType.DISABLED, pathMacroManager.createTrackingSubstitutor(), "defaultProject", null);

    this.store = store;
    this.projectManager = projectManager;
  }

  @Override
  @Nullable
  protected Element loadLocalData() {
    return store.getStateCopy();
  }

  @NotNull
  @Override
  protected XmlElementStorageSaveSession createSaveSession(@NotNull StorageData storageData) {
    return new XmlElementStorageSaveSession(storageData) {
      @Override
      protected void doSave(@Nullable Element element) {
        // we must set empty element instead of null as indicator - ProjectManager state is ready to save
        projectManager.setDefaultProjectRootElement(element == null ? new Element("empty") : element);
      }

      // we must not collapse paths here, because our solution is just a big hack
      // by default, getElementToSave() returns collapsed paths -> setDefaultProjectRootElement -> project manager writeExternal -> save -> compare old and new - diff because old has expanded, but new collapsed
      // -> needless save
      @Override
      protected boolean isCollapsePathsOnSave() {
        return false;
      }
    };
  }

  @Override
  @NotNull
  protected StorageData createStorageData() {
    return new BaseFileConfigurableStoreImpl.BaseStorageData(myRootElementName);
  }
}
