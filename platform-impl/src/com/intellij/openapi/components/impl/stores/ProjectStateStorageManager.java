package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.util.Map;
import java.io.IOException;

class ProjectStateStorageManager extends StateStorageManagerImpl {
  protected Project myProject;
  @NonNls protected static final String ROOT_TAG_NAME = "project";

  public ProjectStateStorageManager(final TrackingPathMacroSubstitutor macroSubstitutor, Project project) {
    super(macroSubstitutor, ROOT_TAG_NAME, project, project.getPicoContainer());
    myProject = project;
  }

  protected XmlElementStorage.StorageData createStorageData(String storageSpec) {
    if (storageSpec.equals(ProjectStoreImpl.PROJECT_FILE_STORAGE)) return createIprStorageData();
    if (storageSpec.equals(ProjectStoreImpl.WS_FILE_STORAGE)) return createWsStorageData();
    return new ProjectStoreImpl.ProjectStorageData(ROOT_TAG_NAME, myProject);
  }

  public XmlElementStorage.StorageData createWsStorageData() {
    return new ProjectStoreImpl.WsStorageData(ROOT_TAG_NAME, myProject);
  }

  public XmlElementStorage.StorageData createIprStorageData() {
    return new ProjectStoreImpl.IprStorageData(ROOT_TAG_NAME, myProject);
  }

  protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) throws
                                                                                                                              StateStorage.StateStorageException {
    final ComponentConfig config = myProject.getConfig(component.getClass());
    assert config != null : "Couldn't find old storage for " + component.getClass().getName();

    String macro = ProjectStoreImpl.PROJECT_FILE_MACRO;

    final boolean workspace = isWorkspace(config.options);

    if (workspace) {
      macro = ProjectStoreImpl.WS_FILE_MACRO;
    }

    String name = "$" + macro + "$";

    StateStorage storage = getFileStateStorage(name);

    if (operation == StateStorageOperation.READ && storage != null && workspace && !storage.hasState(component, componentName, Element.class)) {
      name = "$" + ProjectStoreImpl.PROJECT_FILE_MACRO + "$";
    }

    return name;
  }

  private static boolean isWorkspace(final Map options) {
    return options != null && Boolean.parseBoolean((String)options.get(ProjectStoreImpl.OPTION_WORKSPACE));
  }

  private XmlElementStorage.StorageData myAlternativeStorageData = null;
  private boolean  myAlternativeDocumentsLoaded = !"on".equals(System.getProperty("convert.project.mode"));

  @Override
  protected StateStorage createFileStateStorage(final String fileSpec, final String expandedFile, final String rootTagName,
                                                final PicoContainer picoContainer) {
    return new FileBasedStorage(getMacroSubstitutor(fileSpec),this,expandedFile,fileSpec, rootTagName, this, picoContainer,
                                ComponentRoamingManager.getInstance()) {
      @NotNull
      protected StorageData createStorageData() {
        return ProjectStateStorageManager.this.createStorageData(fileSpec);
      }

      @Override
      protected synchronized Element getState(final String componentName) throws StateStorageException {
        Element dataFromSuper = super.getState(componentName);
        if (dataFromSuper == null) {
          ensureAlternativeState();
          if (myAlternativeStorageData != null) {
            Element result = myAlternativeStorageData.getState(componentName);
            if (result != null) {
              myAlternativeStorageData.removeState(componentName);
            }
            return result;
          }
        }
        return dataFromSuper;
      }

      @Override
      public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
        return super
            .hasState(component, componentName, aClass) || hasAlternativeState(componentName);
      }

      private boolean hasAlternativeState(final String componentName) {
        ensureAlternativeState();
        if (myAlternativeStorageData != null) {
          return myAlternativeStorageData.hasState(componentName);
        }

        return false;
      }

      private void ensureAlternativeState() {
        if (!myAlternativeDocumentsLoaded) {
          try {
            myAlternativeStorageData = createStorageData();
            loadData("$PROJECT_CONFIG_DIR$/convert.xml", myAlternativeStorageData);
            //loadData("$PROJECT_CONFIG_DIR$/workspace.xml", myAlternativeStorageData);
          }
          finally {
            myAlternativeDocumentsLoaded = true;
          }
        }
      }

      private void loadData(final String path, final StorageData storageData) {
        String pathToDefaultStorage = expandMacroses(path);
        IFile defaultFile = FILE_SYSTEM.createFile(pathToDefaultStorage);

        try {
          Document document = JDOMUtil.loadDocument(defaultFile);

          if (document != null) {
            Element element = document.getRootElement();
            if (myPathMacroSubstitutor != null) {
              myPathMacroSubstitutor.expandPaths(element);
            }

            JDOMUtil.internElement(element, ourInterner);

            try {
              storageData.load(element);
            }
            catch (IOException e) {
              throw new StateStorageException(e);
            }

          }
        }
        catch (Exception e) {
          //ignore
        }

      }

    };

  }



}
