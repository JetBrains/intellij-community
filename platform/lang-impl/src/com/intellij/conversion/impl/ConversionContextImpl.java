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

package com.intellij.conversion.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.conversion.*;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ConversionContextImpl implements ConversionContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.conversion.impl.ConversionContextImpl");
  private final Map<File, SettingsXmlFile> mySettingsFiles = new HashMap<File, SettingsXmlFile>();
  private final StorageScheme myStorageScheme;
  private final File myProjectBaseDir;
  private final File myProjectFile;
  private final File myWorkspaceFile;
  private final File[] myModuleFiles;
  private ProjectSettingsImpl myProjectSettings;
  private WorkspaceSettingsImpl myWorkspaceSettings;
  private final List<File> myNonExistingModuleFiles = new ArrayList<File>();
  private final Map<File, ModuleSettingsImpl> myFile2ModuleSettings = new HashMap<File, ModuleSettingsImpl>();
  private final Map<String, ModuleSettingsImpl> myName2ModuleSettings = new HashMap<String, ModuleSettingsImpl>();
  private RunManagerSettingsImpl myRunManagerSettings;
  private File mySettingsBaseDir;
  private ComponentManagerSettings myCompilerManagerSettings;
  private ComponentManagerSettings myProjectRootManagerSettings;

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

  public Set<File> getAllProjectFiles() {
    final HashSet<File> files = new HashSet<File>(Arrays.asList(myModuleFiles));
    if (myStorageScheme == StorageScheme.DEFAULT) {
      files.add(myProjectFile);
      files.add(myWorkspaceFile);
    }
    else {
      addFilesRecursively(mySettingsBaseDir, files);
    }
    return files;
  }

  private static void addFilesRecursively(File file, Set<File> files) {
    if (file.isDirectory()) {
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          addFilesRecursively(child, files);
        }
      }
    }
    else if (StringUtil.endsWithIgnoreCase(file.getName(), ".xml") && !file.getName().startsWith(".")) {
      files.add(file);
    }
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
    for (Element module : JDOMUtil.getChildren(modules, ModuleManagerImpl.ELEMENT_MODULE)) {
      String filePath = module.getAttributeValue(ModuleManagerImpl.ATTRIBUTE_FILEPATH);
      filePath = macros.substitute(filePath, true);
      files.add(new File(FileUtil.toSystemDependentName(filePath)));
    }
    return files.toArray(new File[files.size()]);
  }

  @NotNull
  public String expandPath(@NotNull String path, @NotNull ModuleSettingsImpl moduleSettings) {
    return createExpandMacroMap(moduleSettings).substitute(path, true);
  }

  private ExpandMacroToPathMap createExpandMacroMap(@Nullable ModuleSettingsImpl moduleSettings) {
    final ExpandMacroToPathMap map = createExpandMacroMap();
    if (moduleSettings != null) {
      final String modulePath = FileUtil.toSystemIndependentName(moduleSettings.getModuleFile().getParentFile().getAbsolutePath());
      map.addMacroExpand(PathMacrosImpl.MODULE_DIR_MACRO_NAME, modulePath);
    }
    return map;
  }

  @NotNull
  public String collapsePath(@NotNull String path) {
    ReplacePathToMacroMap map = createCollapseMacroMap(PathMacrosImpl.PROJECT_DIR_MACRO_NAME, myProjectBaseDir);
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public String collapsePath(@NotNull String path, @NotNull ModuleSettingsImpl moduleSettings) {
    final ReplacePathToMacroMap map = createCollapseMacroMap(PathMacrosImpl.MODULE_DIR_MACRO_NAME, moduleSettings.getModuleFile().getParentFile());
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  private static ReplacePathToMacroMap createCollapseMacroMap(final String macroName, final File dir) {
    ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    map.addMacroReplacement(FileUtil.toSystemIndependentName(dir.getAbsolutePath()), macroName);
    PathMacrosImpl.getInstanceEx().addMacroReplacements(map);
    return map;
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
        return getClassRoots(libraryElement, null);
      }

      return Collections.emptyList();
    }
    catch (CannotConvertException e) {
      return Collections.emptyList();
    }
  }

  @NotNull
  public List<File> getClassRoots(Element libraryElement, ModuleSettingsImpl moduleSettings) {
    List<File> files = new ArrayList<File>();
    //todo[nik] support jar directories
    final Element classesChild = libraryElement.getChild("CLASSES");
    if (classesChild != null) {
      final List<Element> roots = JDOMUtil.getChildren(classesChild, "root");
      final ExpandMacroToPathMap pathMap = createExpandMacroMap(moduleSettings);
      for (Element root : roots) {
        final String url = root.getAttributeValue("url");
        final String path = VfsUtil.urlToPath(url);
        files.add(new File(PathUtil.getLocalPath(pathMap.substitute(path, true))));
      }
    }
    return files;
  }

  public ComponentManagerSettings getCompilerSettings() {
    if (myCompilerManagerSettings == null) {
      myCompilerManagerSettings = createProjectSettings("compiler.xml");
    }
    return myCompilerManagerSettings;
  }

  public ComponentManagerSettings getProjectRootManagerSettings() {
    if (myProjectRootManagerSettings == null) {
      myProjectRootManagerSettings = createProjectSettings("misc.xml");
    }
    return myProjectRootManagerSettings;
  }

  @Nullable
  private ComponentManagerSettingsImpl createProjectSettings(final String fileName) {
    try {
      File file;
      if (myStorageScheme == StorageScheme.DEFAULT) {
        file = myProjectFile;
      }
      else {
        file = new File(mySettingsBaseDir, fileName);
      }
      return new ComponentManagerSettingsImpl(file, this);
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      return null;
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
        final File[] files = new File(mySettingsBaseDir, "runConfigurations").listFiles(new FileFilter() {
          public boolean accept(File file) {
            return !file.isDirectory() && file.getName().endsWith(".xml");
          }
        });
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
    ModuleSettingsImpl settings = myFile2ModuleSettings.get(moduleFile);
    if (settings == null) {
      settings = new ModuleSettingsImpl(moduleFile, this);
      myFile2ModuleSettings.put(moduleFile, settings);
      myName2ModuleSettings.put(settings.getModuleName(), settings);
    }
    return settings;
  }

  public ModuleSettings getModuleSettings(@NotNull String moduleName) {
    if (!myName2ModuleSettings.containsKey(moduleName)) {
      for (File moduleFile : myModuleFiles) {
        try {
          getModuleSettings(moduleFile);
        }
        catch (CannotConvertException ignored) {
        }
      }
    }
    return myName2ModuleSettings.get(moduleName);
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

  public void saveFiles(Collection<File> files) throws IOException {
    for (File file : files) {
      final SettingsXmlFile xmlFile = mySettingsFiles.get(file);
      if (xmlFile != null) {
        xmlFile.save();
      }
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
