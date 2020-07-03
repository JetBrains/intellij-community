// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.conversion.*;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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
import java.util.concurrent.*;

public final class ConversionContextImpl implements ConversionContext {
  private static final Logger LOG = Logger.getInstance(ConversionContextImpl.class);

  private final Map<Path, SettingsXmlFile> mySettingsFiles = new HashMap<>();
  private final StorageScheme myStorageScheme;
  private final Path myProjectBaseDir;
  private final SettingsXmlFile myProjectFile;
  private final SettingsXmlFile myWorkspaceFile;
  private volatile List<Path> myModuleFiles;
  private final List<Path> myNonExistingModuleFiles = new ArrayList<>();
  private final Map<Path, ModuleSettingsImpl> myFile2ModuleSettings = new HashMap<>();
  private final Map<String, ModuleSettingsImpl> myName2ModuleSettings = new HashMap<>();
  private RunManagerSettingsImpl myRunManagerSettings;
  private Path mySettingsBaseDir;
  private ComponentManagerSettings myCompilerManagerSettings;
  private ComponentManagerSettings myProjectRootManagerSettings;
  private SettingsXmlFile myModulesSettings;
  private MultiFilesSettings myProjectLibrariesSettings;
  private MultiFilesSettings myArtifactsSettings;
  private ComponentManagerSettings myProjectFileVersionSettings;

  private final NotNullLazyValue<CachedConversionResult> conversionResult;

  private final Path myModuleListFile;

  public ConversionContextImpl(@NotNull Path projectPath) {
    myProjectFile = new SettingsXmlFile(projectPath);

    if (projectPath.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      myStorageScheme = StorageScheme.DEFAULT;
      myProjectBaseDir = projectPath.getParent();
      myModuleListFile = projectPath;
      myWorkspaceFile = new SettingsXmlFile(projectPath.getParent().resolve(Strings.trimEnd(projectPath.getFileName().toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION));
    }
    else {
      myStorageScheme = StorageScheme.DIRECTORY_BASED;
      myProjectBaseDir = projectPath;
      mySettingsBaseDir = myProjectBaseDir.resolve(Project.DIRECTORY_STORE_FOLDER);
      myModuleListFile = mySettingsBaseDir.resolve("modules.xml");
      myWorkspaceFile = new SettingsXmlFile(mySettingsBaseDir.resolve("workspace.xml"));
    }

    conversionResult = NotNullLazyValue.createValue(() -> {
      try {
        return CachedConversionResult.load(CachedConversionResult.getConversionInfoFile(myProjectFile.getPath()), myProjectBaseDir);
      }
      catch (Exception e) {
        LOG.info(e);
        return CachedConversionResult.createEmpty();
      }
    });
  }

  public void saveConversionResult() throws CannotConvertException, IOException {
    CachedConversionResult.saveConversionResult(getAllProjectFiles(), CachedConversionResult.getConversionInfoFile(myProjectFile.getPath()), myProjectBaseDir);
  }

  public @NotNull Object2LongMap<String> getProjectFileTimestamps() {
    return conversionResult.getValue().projectFilesTimestamps;
  }

  public @NotNull Set<String> getAppliedConverters() {
    return conversionResult.getValue().appliedConverters;
  }

  public @NotNull Object2LongMap<String> getAllProjectFiles() throws CannotConvertException {
    Activity activity = StartUpMeasurer.startActivity("conversion: project files collecting");

    if (myStorageScheme == StorageScheme.DEFAULT) {
      List<Path> moduleFiles = getModulePaths();
      Object2LongMap<String> totalResult = new Object2LongOpenHashMap<>(moduleFiles.size() + 2);
      addLastModifiedTme(myProjectFile.getPath(), totalResult);
      addLastModifiedTme(myWorkspaceFile.getPath(), totalResult);
      addLastModifiedTime(moduleFiles, totalResult);
      return totalResult;
    }

    Path dotIdeaDirectory = mySettingsBaseDir;
    List<Path> dirs = Arrays.asList(dotIdeaDirectory,
      dotIdeaDirectory.resolve("libraries"),
      dotIdeaDirectory.resolve("artifacts"),
      dotIdeaDirectory.resolve("runConfigurations"));

    Executor executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Conversion: Project Files Collecting", 3, false);
    List<CompletableFuture<List<Object2LongMap<String>>>> futures = new ArrayList<>(dirs.size() + 1);
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
        Object2LongMap<String> result = CachedConversionResult.createPathToLastModifiedMap();
        addXmlFilesFromDirectory(subDirName, result);
        return Collections.singletonList(result);
      }, executor));
    }

    Object2LongMap<String> totalResult = CachedConversionResult.createPathToLastModifiedMap();
    try {
      for (CompletableFuture<List<Object2LongMap<String>>> future : futures) {
        for (Object2LongMap<String> result : future.get()) {
          totalResult.putAll(result);
        }
      }
    }
    catch (ExecutionException | InterruptedException e) {
      throw new CannotConvertException(e);
    }

    activity.end();
    return totalResult;
  }

  @NotNull
  private static CompletableFuture<List<Object2LongMap<String>>> computeModuleFilesTimestamp(@NotNull List<Path> moduleFiles, @NotNull Executor executor) {
    return CompletableFuture.supplyAsync(() -> {
      Object2LongMap<String> result = new Object2LongOpenHashMap<>(moduleFiles.size());
      result.defaultReturnValue(-1);
      addLastModifiedTime(moduleFiles, result);
      return Collections.singletonList(result);
    }, executor);
  }

  private static void addLastModifiedTime(@NotNull List<Path> moduleFiles, @NotNull Object2LongMap<String> result) {
    for (Path file : moduleFiles) {
      addLastModifiedTme(file, result);
    }
  }

  private static void addLastModifiedTme(@NotNull Path file, @NotNull Object2LongMap<String> files) {
    try {
      files.put(file.toString(), Files.getLastModifiedTime(file).to(TimeUnit.SECONDS));
    }
    catch (IOException ignore) {
    }
  }

  private static void addXmlFilesFromDirectory(@NotNull Path dir, @NotNull Object2LongMap<String> result) {
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

        result.put(childPath, attributes.lastModifiedTime().to(TimeUnit.SECONDS));
      }
    }
    catch (NotDirectoryException | NoSuchFileException ignore) {
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public boolean isConversionAlreadyPerformed(@NotNull ConverterProvider provider) {
    return getAppliedConverters().contains(provider.getId());
  }

  @Override
  public @NotNull Path getProjectBaseDir() {
    return myProjectBaseDir;
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
  public String expandPath(@NotNull String path, @NotNull ComponentManagerSettings moduleSettings) {
    return createExpandMacroMap(moduleSettings).substitute(path, true);
  }

  private @NotNull ExpandMacroToPathMap createExpandMacroMap(@Nullable ComponentManagerSettings moduleSettings) {
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

  public static String collapsePath(@NotNull String path, @NotNull ComponentManagerSettings moduleSettings) {
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
  public @NotNull Collection<Path> getLibraryClassRoots(@NotNull String name, @NotNull String level) {
    try {
      Element libraryElement = null;
      if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(level)) {
        libraryElement = findProjectLibraryElement(name);
      }
      else if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(level)) {
        libraryElement = findGlobalLibraryElement(name);
      }
      return libraryElement == null ? Collections.emptyList() : ContainerUtil.map(getClassRoots(libraryElement, null), File::toPath);
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

  public @NotNull ComponentManagerSettings getProjectFileVersionSettings() {
    if (myProjectFileVersionSettings == null) {
      myProjectFileVersionSettings = createProjectSettings("misc.xml");
    }
    return myProjectFileVersionSettings;
  }

  @Override
  public @NotNull SettingsXmlFile createProjectSettings(@NotNull String fileName) {
    if (myStorageScheme == StorageScheme.DEFAULT) {
      return myProjectFile;
    }
    else {
      return new SettingsXmlFile(mySettingsBaseDir.resolve(fileName));
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

  @Override
  public @NotNull Path getProjectFile() {
    return myProjectFile.getPath();
  }

  @Override
  public @NotNull ComponentManagerSettings getProjectSettings() {
    return myProjectFile;
  }

  @Override
  public RunManagerSettingsImpl getRunManagerSettings() throws CannotConvertException {
    if (myRunManagerSettings == null) {
      if (myStorageScheme == StorageScheme.DEFAULT) {
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, myProjectFile, null, this);
      }
      else {
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, null, mySettingsBaseDir.resolve("runConfigurations"), this);
      }
    }
    return myRunManagerSettings;
  }

  @Override
  public WorkspaceSettings getWorkspaceSettings() {
    return myWorkspaceFile;
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

  public void saveFiles(@NotNull Collection<Path> files) throws IOException {
    for (Path file : files) {
      SettingsXmlFile xmlFile = mySettingsFiles.get(file);
      if (xmlFile != null) {
        xmlFile.save();
      }
    }
  }

  @NotNull SettingsXmlFile getOrCreateFile(@NotNull Path file) throws CannotConvertException {
    return mySettingsFiles.computeIfAbsent(file, file1 -> new SettingsXmlFile(file1));
  }

  @Override
  public MultiFilesSettings getProjectLibrariesSettings() throws CannotConvertException {
    if (myProjectLibrariesSettings == null) {
      myProjectLibrariesSettings = myStorageScheme == StorageScheme.DEFAULT
                                   ? new MultiFilesSettings(myProjectFile, null, this)
                                   : new MultiFilesSettings(null, mySettingsBaseDir.resolve("libraries"), this);
    }
    return myProjectLibrariesSettings;
  }

  @Override
  public @NotNull MultiFilesSettings getArtifactsSettings() throws CannotConvertException {
    if (myArtifactsSettings == null) {
      myArtifactsSettings = myStorageScheme == StorageScheme.DEFAULT
                            ? new MultiFilesSettings(myProjectFile, null, this)
                            : new MultiFilesSettings(null, mySettingsBaseDir.resolve("artifacts"), this);
    }
    return myArtifactsSettings;
  }
}