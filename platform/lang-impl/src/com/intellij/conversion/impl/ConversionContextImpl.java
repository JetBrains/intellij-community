package com.intellij.conversion.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.conversion.*;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ConversionContextImpl implements ConversionContext {
  private Map<File, SettingsXmlFile> mySettingsFiles = new HashMap<File, SettingsXmlFile>();
  private StorageScheme myStorageScheme;
  private File myProjectBaseDir;
  private File myProjectFile;
  private File myWorkspaceFile;
  private File[] myModuleFiles;
  private ProjectSettingsImpl myProjectSettings;
  private WorkspaceSettingsImpl myWorkspaceSettings;
  private List<File> myNonExistingModuleFiles = new ArrayList<File>();
  private Map<File, ModuleSettingsImpl> myModuleSettingsMap = new HashMap<File, ModuleSettingsImpl>();
  private RunManagerSettingsImpl myRunManagerSettings;
  private File mySettingsBaseDir;

  public ConversionContextImpl(String projectPath) throws CannotConvertException {
    myProjectFile = new File(projectPath);

    File modulesFile;
    if (myProjectFile.isDirectory()) {
      myStorageScheme = StorageScheme.DIRECTORY_BASED;
      myProjectBaseDir = myProjectFile;
      mySettingsBaseDir = new File(myProjectBaseDir.getAbsolutePath(), ".idea");
      modulesFile = new File(mySettingsBaseDir, "modules.xml");
      myWorkspaceFile = new File(mySettingsBaseDir, "workspace.xml");
    }
    else {
      myStorageScheme = StorageScheme.DEFAULT;
      myProjectBaseDir = myProjectFile.getParentFile();
      modulesFile = myProjectFile;
      myWorkspaceFile = new File(StringUtil.trimEnd(projectPath, ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION);
    }

    myModuleFiles = findModuleFiles(JDomConvertingUtil.loadDocument(modulesFile).getRootElement());

  }

  @NotNull
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

    final ExpandMacroToPathMap macros = createExpandMacroMap();

    List<File> files = new ArrayList<File>();
    for (Element module : JDomConvertingUtil.getChildren(modules, ModuleManagerImpl.ELEMENT_MODULE)) {
      String filePath = module.getAttributeValue(ModuleManagerImpl.ATTRIBUTE_FILEPATH);
      filePath = macros.substitute(filePath, true, null);
      files.add(new File(FileUtil.toSystemDependentName(filePath)));
    }
    return files.toArray(new File[files.size()]);
  }

  @NotNull
  public String expandPath(@NotNull String path, @NotNull ModuleSettingsImpl moduleSettings) {
    final ExpandMacroToPathMap map = createExpandMacroMap();
    final String modulePath = FileUtil.toSystemIndependentName(moduleSettings.getModuleFile().getParentFile().getAbsolutePath());
    map.addMacroExpand(PathMacrosImpl.MODULE_DIR_MACRO_NAME, modulePath);
    return map.substitute(path, true, null);
  }

  @NotNull
  public String collapsePath(@NotNull String path) {
    ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    final String projectDir = FileUtil.toSystemIndependentName(myProjectBaseDir.getAbsolutePath());
    map.addMacroReplacement(projectDir, PathMacrosImpl.PROJECT_DIR_MACRO_NAME);
    PathMacrosImpl.getInstanceEx().addMacroReplacements(map);
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public Collection<File> getLibraryClassRoots(@NotNull String name, @NotNull String level) {
    try {
      Element libraryElement = null;
      if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(level)) {
        libraryElement = findProjectLibraryElement(name);
      }
      else if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(level)) {
        libraryElement = findGlobalLibraryElement(name);
      }

      if (libraryElement != null) {
        //todo[nik] support jar directories
        final Element classesChild = libraryElement.getChild("CLASSES");
        if (classesChild != null) {
          final List<Element> roots = JDomConvertingUtil.getChildren(classesChild, "root");
          List<File> files = new ArrayList<File>();
          final ExpandMacroToPathMap pathMap = createExpandMacroMap();
          for (Element root : roots) {
            final String url = root.getAttributeValue("url");
            final String path = VfsUtil.urlToPath(url);
            files.add(new File(PathUtil.getLocalPath(pathMap.substitute(path, true, null))));
          }
          return files;
        }
      }

      return Collections.emptyList();
    }
    catch (CannotConvertException e) {
      return Collections.emptyList();
    }
  }

  @Nullable
  private Element findGlobalLibraryElement(String name) throws CannotConvertException {
    final File file = PathManager.getOptionsFile("applicationLibraries");
    if (file.exists()) {
      final Element root = JDomConvertingUtil.loadDocument(file).getRootElement();
      final Element libraryTable = JDomConvertingUtil.findComponent(root, "libraryTable");
      if (libraryTable != null) {
        return findLibraryInTable(libraryTable, name);
      }
    }
    return null;
  }

  @Nullable
  private Element findProjectLibraryElement(String name) throws CannotConvertException {
    if (myStorageScheme == StorageScheme.DEFAULT) {
      final Element tableElement = getProjectSettings().getComponentElement("libraryTable");
      if (tableElement != null) {
        return findLibraryInTable(tableElement, name);
      }
    }
    else {
      File libraryFile = new File(new File(mySettingsBaseDir, "libraries"), name + ".xml");
      if (libraryFile.exists()) {
        return JDomConvertingUtil.loadDocument(libraryFile).getRootElement().getChild(LibraryImpl.ELEMENT);
      }
    }
    return null;
  }

  @Nullable
  private Element findLibraryInTable(Element tableElement, String name) {
    final Condition<Element> filter = JDomConvertingUtil.createElementWithAttributeFilter(LibraryImpl.ELEMENT,
                                                                                          LibraryImpl.LIBRARY_NAME_ATTR, name);
    return JDomConvertingUtil.findChild(tableElement, filter);
  }

  private ExpandMacroToPathMap createExpandMacroMap() {
    final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
    final String projectDir = FileUtil.toSystemIndependentName(myProjectBaseDir.getAbsolutePath());
    macros.addMacroExpand(PathMacrosImpl.PROJECT_DIR_MACRO_NAME, projectDir);
    PathMacrosImpl.getInstanceEx().addMacroExpands(macros);
    return macros;
  }

  public File getSettingsBaseDir() {
    return mySettingsBaseDir;
  }

  public File getProjectFile() {
    return myProjectFile;
  }

  public ProjectSettings getProjectSettings() throws CannotConvertException {
    if (myProjectSettings == null) {
      myProjectSettings = new ProjectSettingsImpl(myProjectFile, this);
    }
    return myProjectSettings;
  }

  public RunManagerSettingsImpl getRunManagerSettings() throws CannotConvertException {
    if (myRunManagerSettings == null) {
      if (myStorageScheme == StorageScheme.DEFAULT) {
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, myProjectFile, null, this);
      }
      else {
        final File[] files = new File(mySettingsBaseDir, "runConfigurations").listFiles();
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, null, files, this);
      }
    }
    return myRunManagerSettings;
  }

  public WorkspaceSettings getWorkspaceSettings() throws CannotConvertException {
    if (myWorkspaceSettings == null) {
      myWorkspaceSettings = new WorkspaceSettingsImpl(myWorkspaceFile, this);
    }
    return myWorkspaceSettings;
  }


  public ModuleSettings getModuleSettings(File moduleFile) throws CannotConvertException {
    ModuleSettingsImpl settings = myModuleSettingsMap.get(moduleFile);
    if (settings == null) {
      settings = new ModuleSettingsImpl(moduleFile, this);
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
    if (myRunManagerSettings != null) {
      myRunManagerSettings.save();
    }
  }

  public SettingsXmlFile getOrCreateFile(File file) throws CannotConvertException {
    SettingsXmlFile settingsFile = mySettingsFiles.get(file);
    if (settingsFile == null) {
      settingsFile = new SettingsXmlFile(file);
      mySettingsFiles.put(file, settingsFile);
    }
    return settingsFile;
  }
}
