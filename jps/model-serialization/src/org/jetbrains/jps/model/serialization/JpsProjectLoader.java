/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author nik
 */
public class JpsProjectLoader extends JpsLoaderBase {
  private static final Logger LOG = Logger.getInstance(JpsProjectLoader.class);
  private static final BoundedTaskExecutor ourThreadPool = new BoundedTaskExecutor(SharedThreadPool.getInstance(), Runtime.getRuntime().availableProcessors());
  public static final String CLASSPATH_ATTRIBUTE = "classpath";
  public static final String CLASSPATH_DIR_ATTRIBUTE = "classpath-dir";
  private final JpsProject myProject;
  private final Map<String, String> myPathVariables;

  private JpsProjectLoader(JpsProject project, Map<String, String> pathVariables, File baseDir) {
    super(createProjectMacroExpander(pathVariables, baseDir));
    myProject = project;
    myPathVariables = pathVariables;
    myProject.getContainer().setChild(JpsProjectSerializationDataExtensionImpl.ROLE, new JpsProjectSerializationDataExtensionImpl(baseDir));
  }

  static JpsMacroExpander createProjectMacroExpander(Map<String, String> pathVariables, File baseDir) {
    final JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    expander.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, baseDir);
    return expander;
  }

  public static void loadProject(final JpsProject project, Map<String, String> pathVariables, String projectPath) throws IOException {
    File file = new File(FileUtil.toCanonicalPath(projectPath));
    if (file.isFile() && projectPath.endsWith(".ipr")) {
      new JpsProjectLoader(project, pathVariables, file.getParentFile()).loadFromIpr(file);
    }
    else {
      File dotIdea = new File(file, PathMacroUtil.DIRECTORY_STORE_NAME);
      File directory;
      if (dotIdea.isDirectory()) {
        directory = dotIdea;
      }
      else if (file.isDirectory() && file.getName().equals(PathMacroUtil.DIRECTORY_STORE_NAME)) {
        directory = file;
      }
      else {
        throw new IOException("Cannot find IntelliJ IDEA project files at " + projectPath);
      }
      new JpsProjectLoader(project, pathVariables, directory.getParentFile()).loadFromDirectory(directory);
    }
  }

  public static String getDirectoryBaseProjectName(File dir) {
    File nameFile = new File(dir, ".name");
    if (nameFile.isFile()) {
      try {
        return FileUtilRt.loadFile(nameFile).trim();
      }
      catch (IOException ignored) {
      }
    }
    return dir.getParentFile().getName();
  }

  private void loadFromDirectory(File dir) {
    myProject.setName(getDirectoryBaseProjectName(dir));
    JpsSdkType<?> projectSdkType = loadProjectRoot(loadRootElement(new File(dir, "misc.xml")));
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsProjectExtensionSerializer serializer : extension.getProjectExtensionSerializers()) {
        loadComponents(dir, "misc.xml", serializer, myProject);
      }
    }
    loadModules(loadRootElement(new File(dir, "modules.xml")), projectSdkType);

    Runnable timingLog = TimingLog.startActivity("loading project libraries");
    for (File libraryFile : listXmlFiles(new File(dir, "libraries"))) {
      loadProjectLibraries(loadRootElement(libraryFile));
    }
    timingLog.run();

    Runnable artifactsTimingLog = TimingLog.startActivity("loading artifacts");
    for (File artifactFile : listXmlFiles(new File(dir, "artifacts"))) {
      loadArtifacts(loadRootElement(artifactFile));
    }
    artifactsTimingLog.run();

    if (hasRunConfigurationSerializers()) {
      Runnable runConfTimingLog = TimingLog.startActivity("loading run configurations");
      for (File configurationFile : listXmlFiles(new File(dir, "runConfigurations"))) {
        JpsRunConfigurationSerializer.loadRunConfigurations(myProject, loadRootElement(configurationFile));
      }
      File workspaceFile = new File(dir, "workspace.xml");
      if (workspaceFile.exists()) {
        Element runManager = JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "RunManager");
        JpsRunConfigurationSerializer.loadRunConfigurations(myProject, runManager);
      }
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
  private static File[] listXmlFiles(final File dir) {
    File[] files = dir.listFiles(FileFilters.filesWithExtension("xml"));
    return files != null ? files : ArrayUtil.EMPTY_FILE_ARRAY;
  }

  private void loadFromIpr(File iprFile) {
    final Element iprRoot = loadRootElement(iprFile);

    String projectName = FileUtil.getNameWithoutExtension(iprFile);
    myProject.setName(projectName);
    File iwsFile = new File(iprFile.getParent(), projectName + ".iws");
    Element iwsRoot = iwsFile.exists() ? loadRootElement(iwsFile) : null;

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
    loadModules(iprRoot, projectSdkType);
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
  private JpsSdkType<?> loadProjectRoot(Element root) {
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

  private void loadModules(Element root, final @Nullable JpsSdkType<?> projectSdkType) {
    Runnable timingLog = TimingLog.startActivity("loading modules");
    Element componentRoot = JDomSerializationUtil.findComponent(root, "ProjectModuleManager");
    if (componentRoot == null) return;

    List<File> moduleFiles = new ArrayList<File>();
    for (Element moduleElement : JDOMUtil.getChildren(componentRoot.getChild("modules"), "module")) {
      final String path = moduleElement.getAttributeValue("filepath");
      final File file = new File(path);
      if (file.exists()) {
        moduleFiles.add(file);
      }
      else {
        LOG.info("Module '" + FileUtil.getNameWithoutExtension(file) + "' is skipped: " + file.getAbsolutePath() + " doesn't exist");
      }
    }

    List<JpsModule> modules = loadModules(moduleFiles, projectSdkType, myPathVariables);
    for (JpsModule module : modules) {
      myProject.addModule(module);
    }
    timingLog.run();
  }

  @NotNull
  public static List<JpsModule> loadModules(@NotNull List<File> moduleFiles, @Nullable final JpsSdkType<?> projectSdkType,
                                            @NotNull final Map<String, String> pathVariables) {
    List<JpsModule> modules = new ArrayList<JpsModule>();
    List<Future<Pair<File, Element>>> futureModuleFilesContents = new ArrayList<Future<Pair<File, Element>>>();
    for (final File file : moduleFiles) {
      futureModuleFilesContents.add(ourThreadPool.submit(new Callable<Pair<File, Element>>() {
        @Override
        public Pair<File, Element> call() throws Exception {
          final JpsMacroExpander expander = createModuleMacroExpander(pathVariables, file);
          final Element moduleRoot = loadRootElement(file, expander);
          return Pair.create(file, moduleRoot);
        }
      }));
    }

    try {
      final List<String> classpathDirs = new ArrayList<String>();
      for (Future<Pair<File, Element>> moduleFile : futureModuleFilesContents) {
        final String classpathDir = moduleFile.get().getSecond().getAttributeValue(CLASSPATH_DIR_ATTRIBUTE);
        if (classpathDir != null) {
          classpathDirs.add(classpathDir);
        }
      }

      List<Future<JpsModule>> futures = new ArrayList<Future<JpsModule>>();
      for (final Future<Pair<File, Element>> futureModuleFile : futureModuleFilesContents) {
        final Pair<File, Element> moduleFile = futureModuleFile.get();
        futures.add(ourThreadPool.submit(new Callable<JpsModule>() {
          @Override
          public JpsModule call() throws Exception {
            return loadModule(moduleFile.getFirst(), moduleFile.getSecond(), classpathDirs, projectSdkType, pathVariables);
          }
        }));
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

  @Nullable
  private static JpsModule loadModule(@NotNull File file, @NotNull Element moduleRoot, List<String> paths,
                                      @Nullable JpsSdkType<?> projectSdkType, Map<String, String> pathVariables) {
    String name = FileUtil.getNameWithoutExtension(file);
    final String typeId = moduleRoot.getAttributeValue("type");
    final JpsModulePropertiesSerializer<?> serializer = getModulePropertiesSerializer(typeId);
    final JpsModule module = createModule(name, moduleRoot, serializer);
    module.getContainer().setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                   new JpsModuleSerializationDataExtensionImpl(file.getParentFile()));

    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      extension.loadModuleOptions(module, moduleRoot);
    }

    String baseModulePath = FileUtil.toSystemIndependentName(file.getParent());
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

  static JpsMacroExpander createModuleMacroExpander(final Map<String, String> pathVariables, File moduleFile) {
    final JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    String moduleDirPath = PathMacroUtil.getModuleDir(moduleFile.getAbsolutePath());
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
