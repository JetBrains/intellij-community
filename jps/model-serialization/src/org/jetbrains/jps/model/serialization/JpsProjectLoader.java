// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.TimingLog;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.impl.JpsSerializationFormatException;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationSerializer;
import org.jetbrains.jps.service.SharedThreadPool;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerConfigurationSerializer.BYTECODE_TARGET_LEVEL;

public final class JpsProjectLoader extends JpsLoaderBase {
  public static final String MODULE_MANAGER_COMPONENT = "ProjectModuleManager";
  public static final String MODULES_TAG = "modules";
  public static final String MODULE_TAG = "module";
  public static final String FILE_PATH_ATTRIBUTE = "filepath";
  public static final String FILE_URL_ATTRIBUTE = "fileurl";
  public static final String GROUP_ATTRIBUTE = "group";
  public static final String CLASSPATH_ATTRIBUTE = "classpath";
  public static final String CLASSPATH_DIR_ATTRIBUTE = "classpath-dir";

  private static final Logger LOG = Logger.getInstance(JpsProjectLoader.class);

  private final JpsProject myProject;
  private final Map<String, String> myPathVariables;
  private final JpsPathMapper myPathMapper;
  private final boolean myLoadUnloadedModules;

  private JpsProjectLoader(JpsProject project,
                           Map<String, String> pathVariables,
                           @NotNull JpsPathMapper pathMapper,
                           Path baseDir,
                           boolean loadUnloadedModules) {
    super(createProjectMacroExpander(pathVariables, baseDir));
    myProject = project;
    myPathVariables = pathVariables;
    myPathMapper = pathMapper;
    myProject.getContainer().setChild(JpsProjectSerializationDataExtensionImpl.ROLE, new JpsProjectSerializationDataExtensionImpl(baseDir));
    myLoadUnloadedModules = loadUnloadedModules;
  }

  private static final class DefaultExecutorHolder {
    static final ExecutorService threadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "JpsProjectLoader Pool", SharedThreadPool.getInstance(), Runtime.getRuntime().availableProcessors());
  }

  private static JpsMacroExpander createProjectMacroExpander(Map<String, String> pathVariables, @NotNull Path baseDir) {
    JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    expander.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, baseDir.toFile());
    return expander;
  }

  public static void loadProject(JpsProject project, Map<String, String> pathVariables, Path projectPath) throws IOException {
    loadProject(project, pathVariables, JpsPathMapper.IDENTITY, projectPath, false);
  }

  public static void loadProject(JpsProject project,
                                 Map<String, String> pathVariables,
                                 @NotNull JpsPathMapper pathMapper,
                                 Path projectPath,
                                 boolean loadUnloadedModules) throws IOException {
    loadProject(project, pathVariables, pathMapper, projectPath, DefaultExecutorHolder.threadPool, loadUnloadedModules);
  }

  public static void loadProject(JpsProject project,
                                 Map<String, String> pathVariables,
                                 @NotNull JpsPathMapper pathMapper,
                                 Path projectPath,
                                 @NotNull Executor executor,
                                 boolean loadUnloadedModules) throws IOException {
    if (Files.isRegularFile(projectPath) && projectPath.toString().endsWith(".ipr")) {
      new JpsProjectLoader(project, pathVariables, pathMapper, projectPath.getParent(), loadUnloadedModules)
        .loadFromIpr(projectPath, executor);
    }
    else {
      Path dotIdea = projectPath.resolve(PathMacroUtil.DIRECTORY_STORE_NAME);
      Path directory;
      if (Files.isDirectory(dotIdea)) {
        directory = dotIdea;
      }
      else if (Files.isDirectory(projectPath) && projectPath.endsWith(PathMacroUtil.DIRECTORY_STORE_NAME)) {
        directory = projectPath;
      }
      else {
        throw new IOException("Cannot find IntelliJ IDEA project files at " + projectPath);
      }
      new JpsProjectLoader(project, pathVariables, pathMapper, directory.getParent(), loadUnloadedModules)
        .loadFromDirectory(directory, executor);
    }
  }

  public static @NotNull String getDirectoryBaseProjectName(@NotNull Path dir) {
    String name = JpsPathUtil.readProjectName(dir);
    return name != null ? name : JpsPathUtil.getDefaultProjectName(dir);
  }

  @Override
  protected @Nullable <E extends JpsElement> Element loadComponentData(@NotNull JpsElementExtensionSerializerBase<E> serializer, @NotNull Path configFile) {
    Path externalConfigDir = resolveExternalProjectConfig("project");
    Element data = super.loadComponentData(serializer, configFile);
    String componentName = serializer.getComponentName();
    if (externalConfigDir == null || !(componentName.equals("CompilerConfiguration"))) {
      return data;
    }

    String prefixedComponentName = "External" + componentName;
    Element externalData = null;
    for (Element child : JDOMUtil.getChildren(loadRootElement(externalConfigDir.resolve(configFile.getFileName())))) {
      // be ready to handle both original name and prefixed
      if (child.getName().equals(prefixedComponentName) || JDomSerializationUtil.isComponent(prefixedComponentName, child) ||
          child.getName().equals(componentName) || JDomSerializationUtil.isComponent(componentName, child)) {
        externalData = child;
        break;
      }
    }
    return deepMergeCompilerConfigurations(data, externalData);
  }

  private static @Nullable Element deepMergeCompilerConfigurations(@Nullable Element data, @Nullable Element externalData) {
    if (data == null) return externalData;
    if (externalData == null) return data;
    JDOMUtil.deepMerge(data, externalData);
    JDOMUtil.reduceChildren(BYTECODE_TARGET_LEVEL, data);
    return data;
  }

  private void loadFromDirectory(@NotNull Path dir, @NotNull Executor executor) {
    myProject.setName(getDirectoryBaseProjectName(dir));
    Path defaultConfigFile = dir.resolve("misc.xml");
    JpsSdkType<?> projectSdkType = loadProjectRoot(loadRootElement(defaultConfigFile));
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsProjectExtensionSerializer serializer : extension.getProjectExtensionSerializers()) {
        loadComponents(dir, defaultConfigFile, serializer, myProject);
      }
    }

    Path externalConfigDir = resolveExternalProjectConfig("project");
    if (externalConfigDir != null) {
      LOG.info("External project config dir is used: " + externalConfigDir);
    }

    Element moduleData = JDomSerializationUtil.findComponent(loadRootElement(dir.resolve("modules.xml")), MODULE_MANAGER_COMPONENT);
    Element externalModuleData;
    if (externalConfigDir == null) {
      externalModuleData = null;
    }
    else {
      Element rootElement = loadRootElement(externalConfigDir.resolve("modules.xml"));
      if (rootElement == null) {
        externalModuleData = null;
      }
      else {
        externalModuleData = JDomSerializationUtil.findComponent(rootElement, "ExternalProjectModuleManager");
        if (externalModuleData == null) {
          externalModuleData = JDomSerializationUtil.findComponent(rootElement, "ExternalModuleListStorage");
        }
        // old format (root tag is "component")
        if (externalModuleData == null && rootElement.getName().equals(JDomSerializationUtil.COMPONENT_ELEMENT)) {
          externalModuleData = rootElement;
        }
      }
    }
    if (externalModuleData != null) {
      String componentName = externalModuleData.getAttributeValue("name");
      LOG.assertTrue(componentName != null && componentName.startsWith("External"));
      externalModuleData.setAttribute("name", componentName.substring("External".length()));
      if (moduleData == null) {
        moduleData = externalModuleData;
      }
      else {
        JDOMUtil.deepMerge(moduleData, externalModuleData);
      }
    }

    Path workspaceFile = dir.resolve("workspace.xml");
    loadModules(moduleData, projectSdkType, workspaceFile, executor);

    Runnable timingLog = TimingLog.startActivity("loading project libraries");
    for (Path libraryFile : listXmlFiles(dir.resolve("libraries"))) {
      loadProjectLibraries(loadRootElement(libraryFile));
    }

    if (externalConfigDir != null) {
      loadProjectLibraries(loadRootElement(externalConfigDir.resolve("libraries.xml")));
    }

    timingLog.run();

    Runnable artifactsTimingLog = TimingLog.startActivity("loading artifacts");
    for (Path artifactFile : listXmlFiles(dir.resolve("artifacts"))) {
      loadArtifacts(loadRootElement(artifactFile));
    }
    if (externalConfigDir != null) {
      loadArtifacts(loadRootElement(externalConfigDir.resolve("artifacts.xml")));
    }
    artifactsTimingLog.run();

    if (hasRunConfigurationSerializers()) {
      Runnable runConfTimingLog = TimingLog.startActivity("loading run configurations");
      for (Path configurationFile : listXmlFiles(dir.resolve("runConfigurations"))) {
        JpsRunConfigurationSerializer.loadRunConfigurations(myProject, loadRootElement(configurationFile));
      }
      JpsRunConfigurationSerializer.loadRunConfigurations(myProject, JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "RunManager"));
      runConfTimingLog.run();
    }
  }

  private static boolean hasRunConfigurationSerializers() {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      if (!extension.getRunConfigurationPropertiesSerializers().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull List<Path> listXmlFiles(@NotNull Path dir) {
    try {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, it -> it.getFileName().toString().endsWith(".xml") && Files.isRegularFile(it))) {
        return ContainerUtil.collect(stream.iterator());
      }
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private void loadFromIpr(@NotNull Path iprFile, @NotNull Executor executor) {
    final Element iprRoot = loadRootElement(iprFile);

    String projectName = FileUtilRt.getNameWithoutExtension(iprFile.getFileName().toString());
    myProject.setName(projectName);
    Path iwsFile = iprFile.getParent().resolve(projectName + ".iws");
    Element iwsRoot = loadRootElement(iwsFile);

    JpsSdkType<?> projectSdkType = loadProjectRoot(iprRoot);
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsProjectExtensionSerializer serializer : extension.getProjectExtensionSerializers()) {
        Element rootTag = JpsProjectExtensionSerializer.WORKSPACE_FILE.equals(serializer.getConfigFileName()) ? iwsRoot : iprRoot;
        Element component = JDomSerializationUtil.findComponent(rootTag, serializer.getComponentName());
        if (component != null) {
          serializer.loadExtension(myProject, component);
        }
        else {
          serializer.loadExtensionWithDefaultSettings(myProject);
        }
      }
    }
    loadModules(JDomSerializationUtil.findComponent(iprRoot, "ProjectModuleManager"), projectSdkType, iwsFile, executor);
    loadProjectLibraries(JDomSerializationUtil.findComponent(iprRoot, "libraryTable"));
    loadArtifacts(JDomSerializationUtil.findComponent(iprRoot, "ArtifactManager"));
    if (hasRunConfigurationSerializers()) {
      JpsRunConfigurationSerializer.loadRunConfigurations(myProject, JDomSerializationUtil.findComponent(iprRoot, "ProjectRunConfigurationManager"));
      JpsRunConfigurationSerializer.loadRunConfigurations(myProject, JDomSerializationUtil.findComponent(iwsRoot, "RunManager"));
    }
  }

  private void loadArtifacts(@Nullable Element artifactManagerComponent) {
    JpsArtifactSerializer.loadArtifacts(myProject, artifactManagerComponent);
  }

  private @Nullable JpsSdkType<?> loadProjectRoot(@Nullable Element root) {
    JpsSdkType<?> sdkType = null;
    Element rootManagerElement = JDomSerializationUtil.findComponent(root, "ProjectRootManager");
    if (rootManagerElement != null) {
      String sdkName = rootManagerElement.getAttributeValue("project-jdk-name");
      String sdkTypeId = rootManagerElement.getAttributeValue("project-jdk-type");
      if (sdkName != null) {
        sdkType = JpsSdkTableSerializer.getSdkType(sdkTypeId);
        JpsSdkTableSerializer.setSdkReference(myProject.getSdkReferencesTable(), sdkName, sdkType);
      }
    }
    return sdkType;
  }

  private void loadProjectLibraries(@Nullable Element libraryTableElement) {
    JpsLibraryTableSerializer.loadLibraries(libraryTableElement, myPathMapper, myProject.getLibraryCollection());
  }

  private void loadModules(@Nullable Element componentElement,
                           @Nullable JpsSdkType<?> projectSdkType,
                           @NotNull Path workspaceFile,
                           @NotNull Executor executor) {
    Runnable timingLog = TimingLog.startActivity("loading modules");
    if (componentElement == null) {
      return;
    }

    Set<String> unloadedModules = new HashSet<>();
    if (!myLoadUnloadedModules && workspaceFile.toFile().exists()) {
      Element unloadedModulesList = JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "UnloadedModulesList");
      for (Element element : JDOMUtil.getChildren(unloadedModulesList, "module")) {
        unloadedModules.add(element.getAttributeValue("name"));
      }
    }

    final Set<Path> foundFiles = CollectionFactory.createSmallMemoryFootprintSet();
    final List<Path> moduleFiles = new ArrayList<>();
    for (Element moduleElement : JDOMUtil.getChildren(componentElement.getChild(MODULES_TAG), MODULE_TAG)) {
      final String path = moduleElement.getAttributeValue(FILE_PATH_ATTRIBUTE);
      if (path != null) {
        final Path file = Paths.get(path);
        if (foundFiles.add(file) && !unloadedModules.contains(getModuleName(file))) {
          moduleFiles.add(file);
        }
      }
    }

    List<JpsModule> modules = loadModules(moduleFiles, projectSdkType, myPathVariables, myPathMapper, executor);
    for (JpsModule module : modules) {
      myProject.addModule(module);
    }
    timingLog.run();
  }

  private static @Nullable Path resolveExternalProjectConfig(@NotNull String subDirName) {
    String externalProjectConfigDir = System.getProperty("external.project.config");
    return StringUtil.isEmptyOrSpaces(externalProjectConfigDir) ? null : Paths.get(externalProjectConfigDir, subDirName);
  }

  public static @NotNull List<JpsModule> loadModules(@NotNull List<? extends Path> moduleFiles,
                                                     @Nullable JpsSdkType<?> projectSdkType,
                                                     @NotNull Map<String, String> pathVariables,
                                                     @NotNull JpsPathMapper pathMapper,
                                                     @Nullable Executor executor) {
    if (executor == null) {
      executor = DefaultExecutorHolder.threadPool;
    }

    List<JpsModule> modules = new ArrayList<>();
    List<CompletableFuture<Pair<Path, Element>>> futureModuleFilesContents = new ArrayList<>();
    Path externalModuleDir = resolveExternalProjectConfig("modules");
    if (externalModuleDir != null) {
      LOG.info("External project config dir is used for modules: " + externalModuleDir);
    }

    for (Path file : moduleFiles) {
      futureModuleFilesContents.add(CompletableFuture.supplyAsync(() -> {
        JpsMacroExpander expander = createModuleMacroExpander(pathVariables, file);

        Element data = loadRootElement(file, expander);
        if (externalModuleDir != null) {
          String externalName = FileUtilRt.getNameWithoutExtension(file.getFileName().toString()) + ".xml";
          Element externalData = loadRootElement(externalModuleDir.resolve(externalName), expander);
          if (externalData != null) {
            if (data == null) {
              data = externalData;
            }
            else {
              JDOMUtil.deepMergeWithAttributes(data, externalData,
                                               List.of(
                                    new JDOMUtil.MergeAttribute("content", "url"),
                                    new JDOMUtil.MergeAttribute("component", "name")
                                  ));
            }
          }
        }

        if (data == null) {
          LOG.info("Module '" + getModuleName(file) + "' is skipped: " + file.toAbsolutePath() + " doesn't exist");
        }
        else {
          // Copy the content roots that are defined in a separate tag, to a general content root component
          List<Element> components = data.getChildren("component");
          Element rootManager = null;
          Element additionalElements = null;
          for (Element component : components) {
            String attributeValue = component.getAttributeValue("name");
            if (attributeValue.equals("NewModuleRootManager")) rootManager = component;
            if (attributeValue.equals("AdditionalModuleElements")) additionalElements = component;
            if (rootManager != null && additionalElements != null) break;
          }
          if (rootManager != null && additionalElements != null) {
            // Cleanup attributes that aren't needed
            additionalElements.removeAttribute("name");
            additionalElements.getChildren().forEach(o -> o.removeAttribute("dumb"));
            JDOMUtil.deepMerge(rootManager, additionalElements);
          }
        }

        return new Pair<>(file, data);
      }, executor));
    }

    try {
      List<String> classpathDirs = new ArrayList<>();
      for (CompletableFuture<Pair<Path, Element>> moduleFile : futureModuleFilesContents) {
        Element rootElement = moduleFile.join().getSecond();
        if (rootElement != null) {
          String classpathDir = rootElement.getAttributeValue(CLASSPATH_DIR_ATTRIBUTE);
          if (classpathDir != null) {
            classpathDirs.add(classpathDir);
          }
        }
      }

      List<CompletableFuture<JpsModule>> futures = new ArrayList<>();
      for (CompletableFuture<Pair<Path, Element>> futureModuleFile : futureModuleFilesContents) {
        Pair<Path, Element> moduleFile = futureModuleFile.join();
        if (moduleFile.getSecond() != null) {
          futures.add(CompletableFuture.supplyAsync(() -> {
            return loadModule(moduleFile.getFirst(), moduleFile.getSecond(), classpathDirs, projectSdkType, pathVariables, pathMapper);
          }, executor));
        }
      }
      for (CompletableFuture<JpsModule> future : futures) {
        JpsModule module = future.join();
        if (module != null) {
          modules.add(module);
        }
      }
      return modules;
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static @NotNull JpsModule loadModule(@NotNull Path file, @NotNull Element moduleRoot, List<String> paths,
                                               @Nullable JpsSdkType<?> projectSdkType, Map<String, String> pathVariables, @NotNull JpsPathMapper pathMapper) {
    String name = getModuleName(file);
    final String typeId = moduleRoot.getAttributeValue("type");
    final JpsModulePropertiesSerializer<?> serializer = getModulePropertiesSerializer(typeId);
    final JpsModule module = createModule(name, moduleRoot, serializer);
    module.getContainer().setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                   new JpsModuleSerializationDataExtensionImpl(file.getParent()));

    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.loadModuleOptions(module, moduleRoot);
    }

    String baseModulePath = FileUtil.toSystemIndependentName(file.getParent().toString());
    String classpath = moduleRoot.getAttributeValue(CLASSPATH_ATTRIBUTE);
    if (classpath == null) {
      try {
        JpsModuleRootModelSerializer.loadRootModel(module, JDomSerializationUtil.findComponent(moduleRoot, "NewModuleRootManager"),
                                                   projectSdkType, pathMapper);
      }
      catch (JpsSerializationFormatException e) {
        LOG.warn("Failed to load module configuration from " + file + ": " + e.getMessage(), e);
      }
    }
    else {
      for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
        JpsModuleClasspathSerializer classpathSerializer = extension.getClasspathSerializer();
        if (classpathSerializer != null && classpathSerializer.getClasspathId().equals(classpath)) {
          String classpathDir = moduleRoot.getAttributeValue(CLASSPATH_DIR_ATTRIBUTE);
          final JpsMacroExpander expander = createModuleMacroExpander(pathVariables, file);
          classpathSerializer.loadClasspath(module, classpathDir, baseModulePath, expander, paths, projectSdkType);
        }
      }
    }
    Element facetsTag = JDomSerializationUtil.findComponent(moduleRoot, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME);
    Element externalFacetsTag = JDomSerializationUtil.findComponent(moduleRoot, "ExternalFacetManager");
    Element mergedFacetsTag;
    if (facetsTag == null) {
      mergedFacetsTag = externalFacetsTag;
    }
    else if (externalFacetsTag != null) {
      mergedFacetsTag = JDOMUtil.deepMerge(facetsTag, externalFacetsTag);
    }
    else {
      mergedFacetsTag = facetsTag;
    }
    JpsFacetSerializer.loadFacets(module, mergedFacetsTag);
    return module;
  }

  private static @NotNull String getModuleName(@NotNull Path file) {
    return FileUtilRt.getNameWithoutExtension(file.getFileName().toString());
  }

  static JpsMacroExpander createModuleMacroExpander(final Map<String, String> pathVariables, @NotNull Path moduleFile) {
    final JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    String moduleDirPath = PathMacroUtil.getModuleDir(moduleFile.toAbsolutePath().toString());
    if (moduleDirPath != null) {
      expander.addFileHierarchyReplacements(PathMacroUtil.MODULE_DIR_MACRO_NAME, new File(FileUtil.toSystemDependentName(moduleDirPath)));
    }
    return expander;
  }

  private static <P extends JpsElement> JpsModule createModule(String name, Element moduleRoot, JpsModulePropertiesSerializer<P> loader) {
    String componentName = loader.getComponentName();
    Element component = componentName != null ? JDomSerializationUtil.findComponent(moduleRoot, componentName) : null;
    return JpsElementFactory.getInstance().createModule(name, loader.getType(), loader.loadProperties(component));
  }

  private static JpsModulePropertiesSerializer<?> getModulePropertiesSerializer(@Nullable String typeId) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsModulePropertiesSerializer<?> loader : extension.getModulePropertiesSerializers()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return new JpsModulePropertiesSerializer<>(JpsJavaModuleType.INSTANCE, "JAVA_MODULE", null) {
      @Override
      public JpsDummyElement loadProperties(@Nullable Element componentElement) {
        return JpsElementFactory.getInstance().createDummyElement();
      }
    };
  }
}