package com.intellij.conversion.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ModuleSettings;
import com.intellij.conversion.ProjectSettings;
import com.intellij.conversion.WorkspaceSettings;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ConversionContextImpl {
  private StorageScheme myStorageScheme;
  private File myProjectBaseDir;
  private File myProjectFile;
  private File myWorkspaceFile;
  private File[] myModuleFiles;
  private ProjectSettingsImpl myProjectSettings;
  private WorkspaceSettingsImpl myWorkspaceSettings;
  private List<File> myNonExistingModuleFiles = new ArrayList<File>();
  private Map<File, ModuleSettingsImpl> myModuleSettingsMap = new HashMap<File, ModuleSettingsImpl>();

  public ConversionContextImpl(String projectPath) throws CannotConvertException {
    myProjectFile = new File(projectPath);

    File modulesFile;
    if (myProjectFile.isDirectory()) {
      myStorageScheme = StorageScheme.DIRECTORY_BASED;
      myProjectBaseDir = myProjectFile;
      final File settingsBaseDir = new File(myProjectBaseDir.getAbsolutePath(), ".idea");
      modulesFile = new File(settingsBaseDir, "modules.xml");
      myWorkspaceFile = new File(settingsBaseDir, "workspace.xml");
    }
    else {
      myStorageScheme = StorageScheme.DEFAULT;
      myProjectBaseDir = myProjectFile.getParentFile();
      modulesFile = myProjectFile;
      myWorkspaceFile = new File(StringUtil.trimEnd(projectPath, ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION);
    }

    myModuleFiles = findModuleFiles(JDomConvertingUtil.loadDocument(modulesFile).getRootElement());

  }

  public File getProjectBaseDir() {
    return myProjectBaseDir;
  }

  public File[] getModuleFiles() {
    return myModuleFiles;
  }

  private File[] findModuleFiles(final Element root) {
    final Element modulesManager = JDomConvertingUtil.findComponent(root, ModuleManagerImpl.COMPONENT_NAME);
    if (modulesManager == null) return new File[0];

    final Element modules = modulesManager.getChild(ModuleManagerImpl.ELEMENT_MODULES);
    if (modules == null) return new File[0];

    final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
    final String projectDir = FileUtil.toSystemIndependentName(myProjectBaseDir.getAbsolutePath());
    macros.addMacroExpand(PathMacrosImpl.PROJECT_DIR_MACRO_NAME, projectDir);
    PathMacrosImpl.getInstanceEx().addMacroExpands(macros);

    List<File> files = new ArrayList<File>();
    for (Element module : JDomConvertingUtil.getChildren(modules, ModuleManagerImpl.ELEMENT_MODULE)) {
      String filePath = module.getAttributeValue(ModuleManagerImpl.ATTRIBUTE_FILEPATH);
      filePath = macros.substitute(filePath, true, null);
      files.add(new File(FileUtil.toSystemDependentName(filePath)));
    }
    return files.toArray(new File[files.size()]);
  }

  public File getProjectFile() {
    return myProjectFile;
  }

  public ProjectSettings getProjectSettings() throws CannotConvertException {
    if (myProjectSettings == null) {
      myProjectSettings = new ProjectSettingsImpl(myProjectFile);
    }
    return myProjectSettings;
  }

  public WorkspaceSettings getWorkspaceSettings() throws CannotConvertException {
    if (myWorkspaceSettings == null) {
      myWorkspaceSettings = new WorkspaceSettingsImpl(myWorkspaceFile);
    }
    return myWorkspaceSettings;
  }


  public ModuleSettings getModuleSettings(File moduleFile) throws CannotConvertException {
    ModuleSettingsImpl settings = myModuleSettingsMap.get(moduleFile);
    if (settings == null) {
      settings = new ModuleSettingsImpl(moduleFile);
      myModuleSettingsMap.put(moduleFile, settings);
    }
    return settings;
  }

  public List<File> getNonExistingModuleFiles() {
    return myNonExistingModuleFiles;
  }

  public StorageScheme getStorageScheme() {
    return myStorageScheme;
  }

  public File getWorkspaceFile() {
    return myWorkspaceFile;
  }

  public void saveFiles() throws IOException {
    if (myWorkspaceSettings != null) {
      myWorkspaceSettings.save();
    }
    if (myProjectSettings != null) {
      myProjectSettings.save();
    }
    for (ModuleSettingsImpl settings : myModuleSettingsMap.values()) {
      settings.save();
    }
  }
}
