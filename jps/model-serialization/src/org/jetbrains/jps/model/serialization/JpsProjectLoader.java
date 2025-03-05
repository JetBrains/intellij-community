// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.impl.JpsSerializationFormatException;
import org.jetbrains.jps.model.serialization.impl.TimingLog;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Use {@link JpsSerializationManager} to load a project.
 */
@ApiStatus.Internal
public final class JpsProjectLoader {
  public static final String MODULE_MANAGER_COMPONENT = "ProjectModuleManager";
  public static final String MODULES_TAG = "modules";
  public static final String MODULE_TAG = "module";
  public static final String FILE_PATH_ATTRIBUTE = "filepath";
  public static final String FILE_URL_ATTRIBUTE = "fileurl";
  public static final String GROUP_ATTRIBUTE = "group";
  public static final String CLASSPATH_ATTRIBUTE = "classpath";
  public static final String CLASSPATH_DIR_ATTRIBUTE = "classpath-dir";

  private static final Logger LOG = Logger.getInstance(JpsProjectLoader.class);
  public static final JpsModulePropertiesSerializer<JpsDummyElement>
    JAVA_MODULE_PROPERTIES_SERIALIZER = new JpsModulePropertiesSerializer<>(JpsJavaModuleType.INSTANCE, "JAVA_MODULE", null) {
    @Override
    public JpsDummyElement loadProperties(@Nullable Element componentElement) {
      return JpsElementFactory.getInstance().createDummyElement();
    }
  };

  private final JpsProject project;
  private final Map<String, String> myPathVariables;
  private final JpsPathMapper myPathMapper;
  private final boolean myLoadUnloadedModules;
  private final JpsComponentLoader myComponentLoader;
  private final @Nullable Path externalConfigurationDirectory;

  private JpsProjectLoader(JpsProject project,
                           Map<String, String> pathVariables,
                           @NotNull JpsPathMapper pathMapper,
                           @NotNull Path baseDir,
                           @Nullable Path externalConfigurationDirectory, 
                           boolean loadUnloadedModules) {
    JpsMacroExpander macroExpander = JpsProjectConfigurationLoading.createProjectMacroExpander(pathVariables, baseDir);
    this.externalConfigurationDirectory = externalConfigurationDirectory;
    myComponentLoader = new JpsComponentLoader(macroExpander, this.externalConfigurationDirectory);
    this.project = project;
    myPathVariables = pathVariables;
    myPathMapper = pathMapper;
    JpsProjectConfigurationLoading.setupSerializationExtension(this.project, baseDir);
    myLoadUnloadedModules = loadUnloadedModules;
  }

  private static final class DefaultExecutorHolder {
    static final ExecutorService threadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "JpsProjectLoader Pool", SharedThreadPool.getInstance(), Runtime.getRuntime().availableProcessors());
  }

  public static void loadProject(JpsProject project, Map<String, String> pathVariables, Path projectPath) throws IOException {
    loadProject(project, pathVariables, JpsPathMapper.IDENTITY, projectPath, false);
  }

  public static void loadProject(JpsProject project,
                                 Map<String, String> pathVariables,
                                 @NotNull JpsPathMapper pathMapper,
                                 Path projectPath,
                                 boolean loadUnloadedModules) throws IOException {
    Path externalConfigurationDirectory = JpsProjectConfigurationLoading.getExternalConfigurationDirectoryFromSystemProperty();
    loadProject(project, pathVariables, pathMapper, projectPath, loadUnloadedModules, externalConfigurationDirectory);
  }

  public static void loadProject(@NotNull JpsProject project,
                                 @NotNull Map<String, String> pathVariables,
                                 @NotNull JpsPathMapper pathMapper,
                                 @NotNull Path projectPath,
                                 boolean loadUnloadedModules,
                                 @Nullable Path externalConfigurationDirectory) throws IOException {
    loadProject(project, pathVariables, pathMapper, projectPath, externalConfigurationDirectory, DefaultExecutorHolder.threadPool,
                loadUnloadedModules);
  }

  public static void loadProject(JpsProject project,
                                 Map<String, String> pathVariables,
                                 @NotNull JpsPathMapper pathMapper,
                                 @NotNull Path projectPath,
                                 @Nullable Path externalConfigurationDirectory,
                                 @NotNull Executor executor,
                                 boolean loadUnloadedModules) throws IOException {
    if (Files.isRegularFile(projectPath) && projectPath.toString().endsWith(".ipr")) {
      new JpsProjectLoader(project, pathVariables, pathMapper, projectPath.getParent(), null, loadUnloadedModules)
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
      new JpsProjectLoader(project, pathVariables, pathMapper, directory.getParent(), externalConfigurationDirectory, loadUnloadedModules)
        .loadFromDirectory(directory, executor);
    }
  }

  private void loadFromDirectory(@NotNull Path dir, @NotNull Executor executor) {
    project.setName(JpsProjectConfigurationLoading.getDirectoryBaseProjectName(dir.getParent(), dir));
    Path defaultConfigFile = dir.resolve("misc.xml");
    JpsSdkType<?> projectSdkType = loadProjectRoot(myComponentLoader.loadRootElement(defaultConfigFile));
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsProjectExtensionSerializer serializer : extension.getProjectExtensionSerializers()) {
        myComponentLoader.loadComponents(dir, defaultConfigFile, serializer, project);
      }
    }

    Path externalConfigDir;
    if (externalConfigurationDirectory == null) {
      externalConfigDir = null;
    }
    else {
      externalConfigDir = externalConfigurationDirectory.resolve("project");
      LOG.info("External project config dir is used: " + externalConfigDir);
    }

    Element moduleData = myComponentLoader.loadComponent(dir.resolve("modules.xml"), MODULE_MANAGER_COMPONENT);
    if (externalConfigDir != null) {
      Element externalModuleData;
      Element rootElement = myComponentLoader.loadRootElement(externalConfigDir.resolve("modules.xml"));
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
    }

    Path workspaceFile = dir.resolve("workspace.xml");
    loadModules(moduleData, projectSdkType, workspaceFile, executor);

    Runnable timingLog = TimingLog.startActivity("loading project libraries");
    for (Path libraryFile : JpsProjectConfigurationLoading.listXmlFiles(dir.resolve("libraries"))) {
      loadProjectLibraries(myComponentLoader.loadRootElement(libraryFile));
    }

    if (externalConfigDir != null) {
      loadProjectLibraries(myComponentLoader.loadRootElement(externalConfigDir.resolve("libraries.xml")));
    }
    timingLog.run();

    JpsProjectConfigurationLoading.loadArtifactsFromDirectory(project, myComponentLoader, dir, externalConfigDir);
    JpsProjectConfigurationLoading.loadRunConfigurationsFromDirectory(project, myComponentLoader, dir, workspaceFile);
  }

  private void loadFromIpr(@NotNull Path iprFile, @NotNull Executor executor) {
    final Element iprRoot = myComponentLoader.loadRootElement(iprFile);

    String projectName = FileUtilRt.getNameWithoutExtension(iprFile.getFileName().toString());
    project.setName(projectName);
    Path iwsFile = iprFile.getParent().resolve(projectName + ".iws");
    Element iwsRoot = myComponentLoader.loadRootElement(iwsFile);

    JpsSdkType<?> projectSdkType = loadProjectRoot(iprRoot);
    JpsProjectConfigurationLoading.loadProjectExtensionsFromIpr(project, iprRoot, iwsRoot);
    loadModules(JDomSerializationUtil.findComponent(iprRoot, "ProjectModuleManager"), projectSdkType, iwsFile, executor);
    loadProjectLibraries(JDomSerializationUtil.findComponent(iprRoot, "libraryTable"));
    JpsProjectConfigurationLoading.loadArtifactsFromIpr(project, iprRoot);
    JpsProjectConfigurationLoading.loadRunConfigurationsFromIpr(project, iprRoot, iwsRoot);
  }

  private @Nullable JpsSdkType<?> loadProjectRoot(@Nullable Element root) {
    Pair<String, String> sdkTypeIdAndName = JpsProjectConfigurationLoading.readProjectSdkTypeAndName(root);
    if (sdkTypeIdAndName != null) {
      JpsSdkType<?> sdkType = JpsSdkTableSerializer.getSdkType(sdkTypeIdAndName.first);
      JpsSdkTableSerializer.setSdkReference(project.getSdkReferencesTable(), sdkTypeIdAndName.second, sdkType);
      return sdkType;
    }
    return null;
  }

  private void loadProjectLibraries(@Nullable Element libraryTableElement) {
    JpsLibraryTableSerializer.loadLibraries(libraryTableElement, myPathMapper, project.getLibraryCollection());
  }

  private void loadModules(@Nullable Element componentElement,
                           @Nullable JpsSdkType<?> projectSdkType,
                           @NotNull Path workspaceFile,
                           @NotNull Executor executor) {
    Runnable timingLog = TimingLog.startActivity("loading modules");
    if (componentElement == null) {
      return;
    }

    Set<String> unloadedModules = myLoadUnloadedModules
                                  ? Set.of()
                                  : JpsProjectConfigurationLoading.readNamesOfUnloadedModules(workspaceFile, myComponentLoader);

    final Set<Path> foundFiles = new HashSet<>();
    final List<Path> moduleFiles = new ArrayList<>();
    for (Element moduleElement : JDOMUtil.getChildren(componentElement.getChild(MODULES_TAG), MODULE_TAG)) {
      final String path = moduleElement.getAttributeValue(FILE_PATH_ATTRIBUTE);
      if (path != null) {
        final Path file = Path.of(path);
        if (foundFiles.add(file) && !unloadedModules.contains(getModuleName(file))) {
          moduleFiles.add(file);
        }
      }
    }

    List<JpsModule> modules = loadModules(moduleFiles, projectSdkType, myPathVariables, myPathMapper, executor);
    for (JpsModule module : modules) {
      project.addModule(module);
    }
    timingLog.run();
  }

  private @NotNull List<JpsModule> loadModules(@NotNull List<? extends Path> moduleFiles,
                                               @Nullable JpsSdkType<?> projectSdkType,
                                               @NotNull Map<String, String> pathVariables,
                                               @NotNull JpsPathMapper pathMapper,
                                               @Nullable Executor executor) {
    if (executor == null) {
      executor = DefaultExecutorHolder.threadPool;
    }

    List<JpsModule> modules = new ArrayList<>();
    List<CompletableFuture<Pair<Path, Element>>> futureModuleFilesContents = new ArrayList<>();
    Path externalModuleDir;
    if (externalConfigurationDirectory == null) {
      externalModuleDir = null;
    }
    else {
      externalModuleDir = externalConfigurationDirectory.resolve("modules");
      LOG.info("External project config dir is used for modules: " + externalModuleDir);
    }

    for (Path file : moduleFiles) {
      futureModuleFilesContents.add(CompletableFuture.supplyAsync(() -> {
        JpsMacroExpander expander = JpsProjectConfigurationLoading.createModuleMacroExpander(pathVariables, file);

        Element data = JpsComponentLoader.loadRootElement(file, expander);
        if (externalModuleDir != null) {
          String externalName = FileUtilRt.getNameWithoutExtension(file.getFileName().toString()) + ".xml";
          Element externalData = JpsComponentLoader.loadRootElement(externalModuleDir.resolve(externalName), expander);
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
          // copy the content roots that are defined in a separate tag, to a general content root component
          List<Element> components = data.getChildren("component");
          Element rootManager = null;
          Element additionalElements = null;
          for (Element component : components) {
            String attributeValue = component.getAttributeValue("name");
            if (attributeValue.equals("NewModuleRootManager")) {
              rootManager = component;
            }
            if (attributeValue.equals("AdditionalModuleElements")) {
              additionalElements = component;
            }
            if (rootManager != null && additionalElements != null) {
              break;
            }
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

  private static @NotNull JpsModule loadModule(@NotNull Path file,
                                               @NotNull Element moduleRoot,
                                               List<String> paths,
                                               @Nullable JpsSdkType<?> projectSdkType,
                                               Map<String, String> pathVariables,
                                               @NotNull JpsPathMapper pathMapper) {
    String name = getModuleName(file);
    final String typeId = moduleRoot.getAttributeValue("type");
    final JpsModulePropertiesSerializer<?> serializer = getModulePropertiesSerializer(typeId);
    final JpsModule module = createModule(name, moduleRoot, serializer);
    module.getContainer().setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                   new JpsModuleSerializationDataExtensionImpl(file.getParent()));

    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.loadModuleOptions(module, moduleRoot);
    }

    String baseModulePath = FileUtilRt.toSystemIndependentName(file.getParent().toString());
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
          final JpsMacroExpander expander = JpsProjectConfigurationLoading.createModuleMacroExpander(pathVariables, file);
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
    return JAVA_MODULE_PROPERTIES_SERIALIZER;
  }
}