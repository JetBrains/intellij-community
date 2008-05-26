package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class ApplicationStoreImpl extends ComponentStoreImpl implements IApplicationStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ApplicationStoreImpl");

  @NonNls private static final String XML_EXTENSION = ".xml";

  private ApplicationImpl myApplication;
  private StateStorageManager myStateStorageManager;
  @NonNls private static final String APP_CONFIG_STORAGE_MACRO = "APP_CONFIG";
  @NonNls private static final String OPTIONS_MACRO = "OPTIONS";
  @NonNls private static final String CONFIG_MACRO = "ROOT_CONFIG";
  private DefaultsStateStorage myDefaultsStateStorage;
  @NonNls private static final String ROOT_ELEMENT_NAME = "application";


  @SuppressWarnings({"UnusedDeclaration"}) //picocontainer
  public ApplicationStoreImpl(final ApplicationImpl application, PathMacroManager pathMacroManager) {
    myApplication = application;
    myStateStorageManager = new StateStorageManagerImpl(pathMacroManager.createTrackingSubstitutor(), ROOT_ELEMENT_NAME, application, application.getPicoContainer()) {
      protected XmlElementStorage.StorageData createStorageData(String storageSpec) {
        return new FileBasedStorage.FileStorageData(ROOT_ELEMENT_NAME);
      }

      protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) {
        final String fileName;

        if (component instanceof NamedJDOMExternalizable) {
          fileName = "$" + APP_CONFIG_STORAGE_MACRO + "$/" + ((NamedJDOMExternalizable)component).getExternalFileName() + XML_EXTENSION;
        }
        else {
          fileName = DEFAULT_STORAGE_SPEC;
        }

        return fileName;
      }

      protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
        if (fileSpec.equals("$" + APP_CONFIG_STORAGE_MACRO + "$/" + PathMacrosImpl.EXT_FILE_NAME + XML_EXTENSION)) return null;
        return super.getMacroSubstitutor(fileSpec);
      }
    };
    myDefaultsStateStorage = new DefaultsStateStorage(null);
  }

  public void initStore() {
  }


  public void load() throws IOException {
    myApplication.initComponents();
  }

  public void setOptionsPath(final String path) {
    myStateStorageManager.addMacro(APP_CONFIG_STORAGE_MACRO, path);
    myStateStorageManager.addMacro(OPTIONS_MACRO, path);
  }

  public void setConfigPath(final String configPath) {
    myStateStorageManager.addMacro(CONFIG_MACRO, configPath);
  }

  public static final String DEFAULT_STORAGE_SPEC = "$" + APP_CONFIG_STORAGE_MACRO + "$/" + PathManager.DEFAULT_OPTIONS_FILE_NAME + XML_EXTENSION;

  public StateStorageManager getStateStorageManager() {
    return myStateStorageManager;
  }

  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

}
