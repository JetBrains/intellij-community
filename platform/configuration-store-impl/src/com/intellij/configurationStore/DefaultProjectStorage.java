package com.intellij.configurationStore;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.FileBasedStorage;
import com.intellij.openapi.components.impl.stores.StorageData;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class DefaultProjectStorage extends FileBasedStorage {
  public DefaultProjectStorage(@NotNull File file, @NotNull String fileSpec, PathMacroManager pathMacroManager) {
    super(file, fileSpec, RoamingType.DISABLED, pathMacroManager.createTrackingSubstitutor(), "defaultProject", null);
  }

  @Nullable
  @Override
  protected Element loadLocalData() {
    Element element = super.loadLocalData();
    if (element == null) {
      return null;
    }

    try {
      return element.getChild("component").getChild("defaultProject");
    }
    catch (NullPointerException e) {
      LOG.warn("Cannot read default project");
      return null;
    }
  }

  @NotNull
  @Override
  protected XmlElementStorageSaveSession createSaveSession(@NotNull StorageData storageData) {
    return new FileSaveSession(storageData) {
      @Override
      protected void doSave(@Nullable Element element) throws IOException {
        super.doSave(new Element("application").addContent(new Element("component").setAttribute("name", "ProjectManager").addContent(element)));
      }
    };
  }
}