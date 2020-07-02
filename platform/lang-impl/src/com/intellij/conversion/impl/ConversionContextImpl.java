// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public final class ConversionContextImpl implements ConversionContext {
  private static final Logger LOG = Logger.getInstance(ConversionContextImpl.class);
  private static final String PROJECT_FILE_VERSION_COMPONENT_NAME = "ProjectFileVersion";

  private final Map<Path, SettingsXmlFile> mySettingsFiles = new HashMap<>();
  private final StorageScheme myStorageScheme;
  private final Path myProjectBaseDir;
  private final Path myProjectFile;
  private final Path myWorkspaceFile;
  private volatile List<Path> myModuleFiles;
  private ComponentManagerSettings myProjectSettings;
  private WorkspaceSettings myWorkspaceSettings;
  private final List<Path> myNonExistingModuleFiles = new ArrayList<>();
  private final Map<Path, ModuleSettingsImpl> myFile2ModuleSettings = new HashMap<>();
  private final Map<String, ModuleSettingsImpl> myName2ModuleSettings = new HashMap<>();
  private RunManagerSettingsImpl myRunManagerSettings;
  private Path mySettingsBaseDir;
  private ComponentManagerSettings myCompilerManagerSettings;
  private ComponentManagerSettings myProjectRootManagerSettings;
  private ComponentManagerSettingsImpl myModulesSettings;
  private ProjectLibrariesSettingsImpl myProjectLibrariesSettings;
  private ArtifactsSettingsImpl myArtifactsSettings;
  private ComponentManagerSettings myProjectFileVersionSettings;
  private final Set<String> myPerformedConversionIds;
  private final Path myModuleListFile;

  public ConversionContextImpl(@NotNull Path projectPath) {
    myProjectFile = projectPath;

    if (projectPath.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      myStorageScheme = StorageScheme.DEFAULT;
      myProjectBaseDir = projectPath.getParent();
      myModuleListFile = projectPath;
      myWorkspaceFile = projectPath.getParent().resolve(Strings.trimEnd(projectPath.getFileName().toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION);
    }
    else {
      myStorageScheme = StorageScheme.DIRECTORY_BASED;
      myProjectBaseDir = projectPath;
      mySettingsBaseDir = myProjectBaseDir.resolve(Project.DIRECTORY_STORE_FOLDER);
      myModuleListFile = mySettingsBaseDir.resolve("modules.xml");
      myWorkspaceFile = mySettingsBaseDir.resolve("workspace.xml");
    }

    myPerformedConversionIds = loadPerformedConversionIds();
  }

  @NotNull
  public ObjectLongHashMap<String> getAllProjectFiles() throws CannotConvertException {
    if (myStorageScheme == StorageScheme.DEFAULT) {
      List<Path> moduleFiles = getModulePaths();
      ObjectLongHashMap<String> totalResult = new ObjectLongHashMap<>(moduleFiles.size() + 2);
      addLastModifiedTme(myProjectFile, totalResult);
      addLastModifiedTme(myWorkspaceFile, totalResult);
      addLastModifiedTime(moduleFiles, totalResult);
      return totalResult;
    }

    Path dotIdeaDirectory = mySettingsBaseDir;
    List<Path> dirs = Arrays.asList(dotIdeaDirectory,
      dotIdeaDirectory.resolve("libraries"),
      dotIdeaDirectory.resolve("artifacts"),
      dotIdeaDirectory.resolve("runConfigurations"));

    Executor executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Conversion: Project Files Collecting", 3, false);
    List<CompletableFuture<List<ObjectLongHashMap<String>>>> futures = new ArrayList<>(dirs.size() + 1);
    futures.add(CompletableFuture.supplyAsync(() -> {
      List<Path> moduleFiles = myModuleFiles;
      if (moduleFiles == null) {
        try {
          moduleFiles = findModuleFiles(JDOMUtil.load(myModuleListFile));
        }
        catch (NoSuchFileException ignore) {
          moduleFiles = Collections.emptyList();
        }
        catch (JDOMException | IOException e) {
          throw new CompletionException(e);
        }

        myModuleFiles = moduleFiles;
      }
      return moduleFiles;
    }, executor)
    .thenComposeAsync(moduleFiles -> {
      int moduleCount = moduleFiles.size();
      if (moduleCount < 50) {
        return computeModuleFilesTimestamp(moduleFiles, executor);
      }

      int secondOffset = moduleCount / 2;
      return computeModuleFilesTimestamp(moduleFiles.subList(0, secondOffset), executor)
        .thenCombine(computeModuleFilesTimestamp(moduleFiles.subList(secondOffset, moduleCount), executor), (v1, v2) -> ContainerUtil.concat(v1, v2));
    }, executor));

    for (Path subDirName : dirs) {
      futures.add(CompletableFuture.supplyAsync(() -> {
        ObjectLongHashMap<String> result = new ObjectLongHashMap<>();
        addXmlFilesFromDirectory(subDirName, result);
        return Collections.singletonList(result);
      }, executor));
    }

    ObjectLongHashMap<String> totalResult = new ObjectLongHashMap<>();
    try {
      for (CompletableFuture<List<ObjectLongHashMap<String>>> future : futures) {
        for (ObjectLongHashMap<String> result : future.get()) {
          totalResult.putAll(result);
        }
      }
    }
    catch (ExecutionException | InterruptedException e) {
      throw new CannotConvertException(e.getMessage(), e);
    }
    return totalResult;
  }

  @NotNull
  private static CompletableFuture<List<ObjectLongHashMap<String>>> computeModuleFilesTimestamp(@NotNull List<Path> moduleFiles, @NotNull Executor executor) {
    return CompletableFuture.supplyAsync(() -> {
      ObjectLongHashMap<String> result = new ObjectLongHashMap<>();
      addLastModifiedTime(moduleFiles, result);
      return Collections.singletonList(result);
    }, executor);
  }

  private static void addLastModifiedTime(@NotNull List<Path> moduleFiles, @NotNull ObjectLongHashMap<String> result) {
    for (Path file : moduleFiles) {
      addLastModifiedTme(file, result);
    }
  }

  private static void addLastModifiedTme(@NotNull Path file, @NotNull ObjectLongHashMap<String> files) {
    try {
      files.put(file.toString(), Files.getLastModifiedTime(file).toMillis());
    }
    catch (IOException ignore) {
    }
  }

  private static void addXmlFilesFromDirectory(@NotNull Path dir, @NotNull ObjectLongHashMap<String> result) {
    try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
      for (Path child : children) {
        String childPath = child.toString();
        if (!childPath.endsWith(".xml") || child.getFileName().toString().startsWith(".")) {
          continue;
        }

        BasicFileAttributes attributes;
        try {
          attributes = Files.readAttributes(child, BasicFileAttributes.class);
          if (attributes.isDirectory()) {
            continue;
          }
        }
        catch (IOException ignore) {
          continue;
        }

        result.put(childPath, attributes.lastModifiedTime().toMillis());
      }
    }
    catch (NotDirectoryException | NoSuchFileException ignore) {
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public boolean isConversionAlreadyPerformed(ConverterProvider provider) {
    return myPerformedConversionIds.contains(provider.getId());
  }

  @Override
  @NotNull
  public File getProjectBaseDir() {
    return myProjectBaseDir.toFile();
  }

  @Override
  public @NotNull List<Path> getModulePaths() throws CannotConvertException {
    List<Path> result = myModuleFiles;
    if (result == null) {
      try {
        result = findModuleFiles(JDOMUtil.load(myModuleListFile));
      }
      catch (NoSuchFileException e) {
        result = Collections.emptyList();
      }
      catch (JDOMException | IOException e) {
        throw new CannotConvertException(myModuleListFile + ": " + e.getMessage(), e);
      }
      myModuleFiles = result;
    }
    return result;
  }

  private @NotNull List<Path> findModuleFiles(@NotNull Element root) {
    Element moduleManager = JDomSerializationUtil.findComponent(root, JpsProjectLoader.MODULE_MANAGER_COMPONENT);
    Element modules = moduleManager == null ? null : moduleManager.getChild(JpsProjectLoader.MODULES_TAG);
    if (modules == null) {
      return Collections.emptyList();
    }

    ExpandMacroToPathMap macros = createExpandMacroMap();
    List<Path> files = new ArrayList<>();
    for (Element module : modules.getChildren(JpsProjectLoader.MODULE_TAG)) {
      String filePath = module.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE);
      if (filePath != null) {
        filePath = macros.substitute(filePath, true);
        files.add(Paths.get(filePath));
      }
    }
    return files;
  }

  @NotNull
  public String expandPath(@NotNull String path, @NotNull XmlBasedSettings moduleSettings) {
    return createExpandMacroMap(moduleSettings).substitute(path, true);
  }

  private @NotNull ExpandMacroToPathMap createExpandMacroMap(@Nullable XmlBasedSettings moduleSettings) {
    ExpandMacroToPathMap map = createExpandMacroMap();
    if (moduleSettings != null) {
      String modulePath = FileUtil.toSystemIndependentName(moduleSettings.getPath().getParent().toAbsolutePath().toString());
      map.addMacroExpand(PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath);
    }
    return map;
  }

  @Override
  @NotNull
  public String expandPath(@NotNull String path) {
    ExpandMacroToPathMap map = createExpandMacroMap(null);
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public @NotNull String collapsePath(@NotNull String path) {
    ReplacePathToMacroMap map = createCollapseMacroMap(PathMacroUtil.PROJECT_DIR_MACRO_NAME, myProjectBaseDir);
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public static String collapsePath(@NotNull String path, @NotNull XmlBasedSettings moduleSettings) {
    ReplacePathToMacroMap map = createCollapseMacroMap(PathMacroUtil.MODULE_DIR_MACRO_NAME, moduleSettings.getPath().getParent());
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  private static ReplacePathToMacroMap createCollapseMacroMap(final String macroName, @NotNull Path dir) {
    ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    map.addMacroReplacement(FileUtil.toSystemIndependentName(dir.toAbsolutePath().toString()), macroName);
    PathMacrosImpl.getInstanceEx().addMacroReplacements(map);
    return map;
  }

  @Override
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
  public List<File> getClassRoots(Element libraryElement, @SuppressWarnings("TypeMayBeWeakened") @Nullable ModuleSettingsImpl moduleSettings) {
    List<File> files = new ArrayList<>();
    //todo[nik] support jar directories
    final Element classesChild = libraryElement.getChild("CLASSES");
    if (classesChild != null) {
      final ExpandMacroToPathMap pathMap = createExpandMacroMap(moduleSettings);
      for (Element root : classesChild.getChildren("root")) {
        final String url = root.getAttributeValue("url");
        final String path = VfsUtilCore.urlToPath(url);
        files.add(new File(PathUtil.getLocalPath(pathMap.substitute(path, true))));
      }
    }
    return files;
  }

  @Override
  public ComponentManagerSettings getCompilerSettings() {
    if (myCompilerManagerSettings == null) {
      myCompilerManagerSettings = createProjectSettings("compiler.xml");
    }
    return myCompilerManagerSettings;
  }

  @Override
  public ComponentManagerSettings getProjectRootManagerSettings() {
    if (myProjectRootManagerSettings == null) {
      myProjectRootManagerSettings = createProjectSettings("misc.xml");
    }
    return myProjectRootManagerSettings;
  }

  @Override
  public ComponentManagerSettings getModulesSettings() {
    if (myModulesSettings == null) {
      myModulesSettings = createProjectSettings("modules.xml");
    }
    return myModulesSettings;
  }

  @Nullable
  public ComponentManagerSettings getProjectFileVersionSettings() {
    if (myProjectFileVersionSettings == null) {
      myProjectFileVersionSettings = createProjectSettings("misc.xml");
    }
    return myProjectFileVersionSettings;
  }

  @Override
  @Nullable
  public ComponentManagerSettingsImpl createProjectSettings(@NotNull String fileName) {
    try {
      Path file;
      if (myStorageScheme == StorageScheme.DEFAULT) {
        file = myProjectFile;
      }
      else {
        file = mySettingsBaseDir.resolve(fileName);
        if (!Files.exists(file)) {
          return null;
        }
      }
      return new ComponentManagerSettingsImpl(file, this);
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  private static Element findGlobalLibraryElement(String name) throws CannotConvertException {
    final File file = PathManager.getOptionsFile("applicationLibraries");
    if (file.exists()) {
      final Element root = JDomConvertingUtil.load(file.toPath());
      final Element libraryTable = JDomSerializationUtil.findComponent(root, "libraryTable");
      if (libraryTable != null) {
        return findLibraryInTable(libraryTable, name);
      }
    }
    return null;
  }

  @Nullable
  private Element findProjectLibraryElement(String name) throws CannotConvertException {
    final Collection<? extends Element> libraries = getProjectLibrariesSettings().getProjectLibraries();
    final Condition<Element> filter = JDomConvertingUtil.createElementWithAttributeFilter(JpsLibraryTableSerializer.LIBRARY_TAG,
                                                                                          JpsLibraryTableSerializer.NAME_ATTRIBUTE, name);
    return ContainerUtil.find(libraries, filter);
  }

  @Nullable
  private static Element findLibraryInTable(Element tableElement, String name) {
    final Condition<Element> filter = JDomConvertingUtil.createElementWithAttributeFilter(JpsLibraryTableSerializer.LIBRARY_TAG,
                                                                                          JpsLibraryTableSerializer.NAME_ATTRIBUTE, name);
    return JDomConvertingUtil.findChild(tableElement, filter);
  }

  private ExpandMacroToPathMap createExpandMacroMap() {
    final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
    final String projectDir = FileUtil.toSystemIndependentName(myProjectBaseDir.toAbsolutePath().toString());
    macros.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectDir);
    PathMacrosImpl.getInstanceEx().addMacroExpands(macros);
    return macros;
  }

  @Override
  public File getSettingsBaseDir() {
    return mySettingsBaseDir != null ? mySettingsBaseDir.toFile() : null;
  }

  @NotNull
  @Override
  public File getProjectFile() {
    return myProjectFile.toFile();
  }

  @Override
  public @NotNull ComponentManagerSettings getProjectSettings() throws CannotConvertException {
    if (myProjectSettings == null) {
      myProjectSettings = new ComponentManagerSettingsImpl(myProjectFile, this);
    }
    return myProjectSettings;
  }

  @Override
  public RunManagerSettingsImpl getRunManagerSettings() throws CannotConvertException {
    if (myRunManagerSettings == null) {
      if (myStorageScheme == StorageScheme.DEFAULT) {
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, myProjectFile, null, this);
      }
      else {
        File[] files = mySettingsBaseDir.resolve("runConfigurations").toFile().listFiles(FileFilters.filesWithExtension("xml"));
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, null, files, this);
      }
    }
    return myRunManagerSettings;
  }

  @Override
  public WorkspaceSettings getWorkspaceSettings() throws CannotConvertException {
    if (myWorkspaceSettings == null) {
      myWorkspaceSettings = new WorkspaceSettingsImpl(myWorkspaceFile, this);
    }
    return myWorkspaceSettings;
  }

  @Override
  public ModuleSettings getModuleSettings(@NotNull Path moduleFile) throws CannotConvertException {
    ModuleSettingsImpl settings = myFile2ModuleSettings.get(moduleFile);
    if (settings == null) {
      settings = new ModuleSettingsImpl(moduleFile, this);
      myFile2ModuleSettings.put(moduleFile, settings);
      myName2ModuleSettings.put(settings.getModuleName(), settings);
    }
    return settings;
  }

  @Override
  public ModuleSettings getModuleSettings(@NotNull String moduleName) {
    if (!myName2ModuleSettings.containsKey(moduleName)) {
      for (Path moduleFile : myModuleFiles) {
        try {
          getModuleSettings(moduleFile);
        }
        catch (CannotConvertException ignored) {
        }
      }
    }
    return myName2ModuleSettings.get(moduleName);
  }

  public List<Path> getNonExistingModuleFiles() {
    return myNonExistingModuleFiles;
  }

  @NotNull
  @Override
  public StorageScheme getStorageScheme() {
    return myStorageScheme;
  }

  public Path getWorkspaceFile() {
    return myWorkspaceFile;
  }

  public void saveFiles(Collection<? extends Path> files, List<ConversionRunner> usedRunners) throws IOException {
    Set<String> performedConversions = new HashSet<>();
    for (ConversionRunner runner : usedRunners) {
      final ConverterProvider provider = runner.getProvider();
      if (!provider.canDetermineIfConversionAlreadyPerformedByProjectFiles()) {
        performedConversions.add(provider.getId());
      }
    }
    if (!performedConversions.isEmpty()) {
      performedConversions.addAll(myPerformedConversionIds);
      ComponentManagerSettings settings = getProjectFileVersionSettings();
      if (settings != null) {
        List<String> performedConversionsList = new ArrayList<>(performedConversions);
        performedConversionsList.sort(String.CASE_INSENSITIVE_ORDER);
        Element element = JDomSerializationUtil.findOrCreateComponentElement(settings.getRootElement(), PROJECT_FILE_VERSION_COMPONENT_NAME);
        XmlSerializer.serializeInto(new ProjectFileVersionState(performedConversionsList), element);
      }
    }

    for (Path file : files) {
      final SettingsXmlFile xmlFile = mySettingsFiles.get(file);
      if (xmlFile != null) {
        xmlFile.save();
      }
    }
  }

  @NotNull
  private Set<String> loadPerformedConversionIds() {
    ComponentManagerSettings component = getProjectFileVersionSettings();
    if (component != null) {
      Element componentElement = component.getComponentElement(PROJECT_FILE_VERSION_COMPONENT_NAME);
      if (componentElement != null) {
        return new HashSet<>(XmlSerializer.deserialize(componentElement, ProjectFileVersionState.class).performedConversionIds);
      }
    }
    return Collections.emptySet();
  }

  public @NotNull SettingsXmlFile getOrCreateFile(@NotNull Path file) throws CannotConvertException {
    return mySettingsFiles.computeIfAbsent(file, file1 -> new SettingsXmlFile(file1));
  }

  @Override
  public ProjectLibrariesSettingsImpl getProjectLibrariesSettings() throws CannotConvertException {
    if (myProjectLibrariesSettings == null) {
      myProjectLibrariesSettings = myStorageScheme == StorageScheme.DEFAULT
                                   ? new ProjectLibrariesSettingsImpl(myProjectFile, null, this)
                                   : new ProjectLibrariesSettingsImpl(null, getSettingsXmlFiles("libraries"), this);
    }
    return myProjectLibrariesSettings;
  }

  @Override
  public ArtifactsSettingsImpl getArtifactsSettings() throws CannotConvertException {
    if (myArtifactsSettings == null) {
      myArtifactsSettings = myStorageScheme == StorageScheme.DEFAULT
                            ? new ArtifactsSettingsImpl(myProjectFile, null, this)
                            : new ArtifactsSettingsImpl(null, getSettingsXmlFiles("artifacts"), this);
    }
    return myArtifactsSettings;
  }

  private File @NotNull [] getSettingsXmlFiles(@NotNull String dirName) {
    Path librariesDir = mySettingsBaseDir.resolve(dirName);
    return ObjectUtils.notNull(librariesDir.toFile().listFiles(FileFilters.filesWithExtension("xml")), ArrayUtilRt.EMPTY_FILE_ARRAY);
  }
}

final class ProjectFileVersionState {
  @XCollection(propertyElementName = "performed-conversions", elementName = "converter", valueAttributeName = "id")
  final List<String> performedConversionIds;

  @SuppressWarnings({"unused", "RedundantSuppression"})
  ProjectFileVersionState() {
    performedConversionIds = new ArrayList<>();
  }

  ProjectFileVersionState(@NotNull List<String> value) {
    performedConversionIds = value;
  }
}