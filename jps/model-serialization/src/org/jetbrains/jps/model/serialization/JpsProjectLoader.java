/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
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
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationSerializer;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * @author nik
 */
public class JpsProjectLoader extends JpsLoaderBase {
  private static final Logger LOG = Logger.getInstance(JpsProjectLoader.class);
  private static final BoundedTaskExecutor ourThreadPool = new BoundedTaskExecutor("JpsProjectLoader pool",SharedThreadPool.getInstance(), Runtime.getRuntime().availableProcessors());
  public static final String CLASSPATH_ATTRIBUTE = "classpath";
  public static final String CLASSPATH_DIR_ATTRIBUTE = "classpath-dir";
  private final JpsProject myProject;
  private final Map<String, String> myPathVariables;
  private final boolean myLoadUnloadedModules;

  private JpsProjectLoader(JpsProject project, Map<String, String> pathVariables, Path baseDir, boolean loadUnloadedModules) {
    super(createProjectMacroExpander(pathVariables, baseDir));
    myProject = project;
    myPathVariables = pathVariables;
    myProject.getContainer().setChild(JpsProjectSerializationDataExtensionImpl.ROLE, new JpsProjectSerializationDataExtensionImpl(baseDir));
    myLoadUnloadedModules = loadUnloadedModules;
  }

  static JpsMacroExpander createProjectMacroExpander(Map<String, String> pathVariables, @NotNull Path baseDir) {
    final JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    expander.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, baseDir.toFile());
    return expander;
  }

  public static void loadProject(final JpsProject project,
                                 Map<String, String> pathVariables,
                                 String projectPath) throws IOException {
    loadProject(project, pathVariables, projectPath, false);
  }

  public static void loadProject(final JpsProject project, Map<String, String> pathVariables, String projectPath,
                                 boolean loadUnloadedModules) throws IOException {
    Path file = Paths.get(FileUtil.toCanonicalPath(projectPath));
    if (Files.isRegularFile(file) && projectPath.endsWith(".ipr")) {
      new JpsProjectLoader(project, pathVariables, file.getParent(), loadUnloadedModules).loadFromIpr(file);
    }
    else {
      Path dotIdea = file.resolve(PathMacroUtil.DIRECTORY_STORE_NAME);
      Path directory;
      if (Files.isDirectory(dotIdea)) {
        directory = dotIdea;
      }
      else if (Files.isDirectory(file) && file.endsWith(PathMacroUtil.DIRECTORY_STORE_NAME)) {
        directory = file;
      }
      else {
        throw new IOException("Cannot find IntelliJ IDEA project files at " + projectPath);
      }
      new JpsProjectLoader(project, pathVariables, directory.getParent(), loadUnloadedModules).loadFromDirectory(directory);
    }
  }

  @NotNull
  public static String getDirectoryBaseProjectName(@NotNull Path dir) {
    try (Stream<String> stream = Files.lines(dir.resolve(".name"))) {
      String value = stream.findFirst().map(String::trim).orElse(null);
      if (value != null) {
        return value;
      }
    }
    catch (IOException ignored) {
    }
    return dir.getParent().getFileName().toString();
  }

  @Nullable
  @Override
  protected Element loadRootElement(@NotNull Path file) {
    return super.loadRootElement(file);
  }

  @Nullable
  @Override
  protected <E extends JpsElement> Element loadComponentData(@NotNull JpsElementExtensionSerializerBase<E> serializer, @NotNull Path configFile) {
    Path externalConfigDir = resolveExternalProjectConfig("project");
    Element data = super.loadComponentData(serializer, configFile);
    if (externalConfigDir != null && serializer.getComponentName().equals("CompilerConfiguration")) {
      Element externalData = JDomSerializationUtil.findComponent(loadRootElement(externalConfigDir.resolve(configFile.getFileName())), "External" + serializer.getComponentName());
      if (data == null) {
        return externalData;
      }
      else if (externalData != null) {
        return JDOMUtil.deepMerge(data, externalData);
      }
    }
    return data;
  }

  private void loadFromDirectory(@NotNull Path dir) {
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

    Element moduleData = JDomSerializationUtil.findComponent(loadRootElement(dir.resolve("modules.xml")), "ProjectModuleManager");
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
    loadModules(moduleData, projectSdkType, workspaceFile);

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

  @NotNull
  private static List<Path> listXmlFiles(@NotNull Path dir) {
    try {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, it -> it.getFileName().toString().endsWith(".xml") && Files.isRegularFile(it))) {
        return ContainerUtil.collect(stream.iterator());
      }
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private void loadFromIpr(@NotNull Path iprFile) {
    final Element iprRoot = loadRootElement(iprFile);

    String projectName = FileUtil.getNameWithoutExtension(iprFile.getFileName().toString());
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
    loadModules(JDomSerializationUtil.findComponent(iprRoot, "ProjectModuleManager"), projectSdkType, iwsFile);
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

  @Nullable
  private JpsSdkType<?> loadProjectRoot(@Nullable Element root) {
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
    JpsLibraryTableSerializer.loadLibraries(libraryTableElement, myProject.getLibraryCollection());
  }

  private void loadModules(@Nullable Element componentElement, final @Nullable JpsSdkType<?> projectSdkType, @NotNull Path workspaceFile) {
    Runnable timingLog = TimingLog.startActivity("loading modules");
    if (componentElement == null) {
      return;
    }

    Set<String> unloadedModules = new HashSet<>();
    if (!myLoadUnloadedModules && Files.exists(workspaceFile)) {
      Element unloadedModulesList = JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "UnloadedModulesList");
      for (Element element : JDOMUtil.getChildren(unloadedModulesList, "module")) {
        unloadedModules.add(element.getAttributeValue("name"));
      }
    }

    final Set<Path> foundFiles = new THashSet<>();
    final List<Path> moduleFiles = new ArrayList<>();
    for (Element moduleElement : JDOMUtil.getChildren(componentElement.getChild("modules"), "module")) {
      final String path = moduleElement.getAttributeValue("filepath");
      final Path file = Paths.get(path);
      if (foundFiles.add(file) && !unloadedModules.contains(getModuleName(file))) {
        moduleFiles.add(file);
      }
    }

    List<JpsModule> modules = loadModules(moduleFiles, projectSdkType, myPathVariables);
    for (JpsModule module : modules) {
      myProject.addModule(module);
    }
    timingLog.run();
  }

  @Nullable
  private static Path resolveExternalProjectConfig(@NotNull String subDirName) {
    String externalProjectConfigDir = System.getProperty("external.project.config");
    return StringUtil.isEmptyOrSpaces(externalProjectConfigDir) ? null : Paths.get(externalProjectConfigDir, subDirName);
  }

  @NotNull
  public static List<JpsModule> loadModules(@NotNull List<Path> moduleFiles, @Nullable final JpsSdkType<?> projectSdkType,
                                            @NotNull final Map<String, String> pathVariables) {
    List<JpsModule> modules = new ArrayList<>();
    List<Future<Pair<Path, Element>>> futureModuleFilesContents = new ArrayList<>();
    Path externalModuleDir = resolveExternalProjectConfig("modules");
    if (externalModuleDir != null) {
      LOG.info("External project config dir is used for modules: " + externalModuleDir);
    }

    for (Path file : moduleFiles) {
      futureModuleFilesContents.add(ourThreadPool.submit(() -> {
        final JpsMacroExpander expander = createModuleMacroExpander(pathVariables, file);

        Element data = loadRootElement(file, expander);
        Path externalPath = externalModuleDir == null ? null : externalModuleDir.resolve(FileUtilRt.getNameWithoutExtension(file.getFileName().toString()) + ".xml");
        Element externalData = externalPath == null ? null : loadRootElement(externalPath, expander);
        if (externalData != null) {
          if (data == null) {
            data = externalData;
          }
          else {
            JDOMUtil.merge(data, externalData);
          }
        }

        if (data == null) {
          LOG.info("Module '" + getModuleName(file) + "' is skipped: " + file.toAbsolutePath() + " doesn't exist");
        }

        return Pair.create(file, data);
      }));
    }

    try {
      final List<String> classpathDirs = new ArrayList<>();
      for (Future<Pair<Path, Element>> moduleFile : futureModuleFilesContents) {
        Element rootElement = moduleFile.get().getSecond();
        if (rootElement != null) {
          final String classpathDir = rootElement.getAttributeValue(CLASSPATH_DIR_ATTRIBUTE);
          if (classpathDir != null) {
            classpathDirs.add(classpathDir);
          }
        }
      }

      List<Future<JpsModule>> futures = new ArrayList<>();
      for (final Future<Pair<Path, Element>> futureModuleFile : futureModuleFilesContents) {
        final Pair<Path, Element> moduleFile = futureModuleFile.get();
        if (moduleFile.getSecond() != null) {
          futures.add(ourThreadPool.submit(
            () -> loadModule(moduleFile.getFirst(), moduleFile.getSecond(), classpathDirs, projectSdkType, pathVariables)));
        }
      }
      for (Future<JpsModule> future : futures) {
        JpsModule module = future.get();
        if (module != null) {
          modules.add(module);
        }
      }
      return modules;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static JpsModule loadModule(@NotNull Path file, @NotNull Element moduleRoot, List<String> paths,
                                      @Nullable JpsSdkType<?> projectSdkType, Map<String, String> pathVariables) {
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
      JpsModuleRootModelSerializer.loadRootModel(module, JDomSerializationUtil.findComponent(moduleRoot, "NewModuleRootManager"),
                                                 projectSdkType);
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
    JpsFacetSerializer.loadFacets(module, JDomSerializationUtil.findComponent(moduleRoot, "FacetManager"));
    return module;
  }

  @NotNull
  private static String getModuleName(@NotNull Path file) {
    return FileUtil.getNameWithoutExtension(file.getFileName().toString());
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
    return new JpsModulePropertiesSerializer<JpsDummyElement>(JpsJavaModuleType.INSTANCE, "JAVA_MODULE", null) {
      @Override
      public JpsDummyElement loadProperties(@Nullable Element componentElement) {
        return JpsElementFactory.getInstance().createDummyElement();
      }

      @Override
      public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element componentElement) {
      }
    };
  }
}
