package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.io.FileFilter;
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
    expander.addFileHierarchyReplacements("PROJECT_DIR", baseDir);
    return expander;
  }

  public static void loadProject(final JpsProject project, Map<String, String> pathVariables, String projectPath) throws IOException {
    File file = new File(projectPath).getCanonicalFile();
    if (file.isFile() && projectPath.endsWith(".ipr")) {
      new JpsProjectLoader(project, pathVariables, file.getParentFile()).loadFromIpr(file);
    }
    else {
      File directory;
      if (file.isDirectory() && file.getName().equals(".idea")) {
        directory = file;
      }
      else {
        directory = new File(file, ".idea");
        if (!directory.isDirectory()) {
          throw new IOException("Cannot find IntelliJ IDEA project files at " + projectPath);
        }
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
    return StringUtil.replace(dir.getParentFile().getName(), ":", "");
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
    for (File libraryFile : listXmlFiles(new File(dir, "libraries"))) {
      loadProjectLibraries(loadRootElement(libraryFile));
    }
    for (File artifactFile : listXmlFiles(new File(dir, "artifacts"))) {
      loadArtifacts(loadRootElement(artifactFile));
    }

    if (hasRunConfigurationSerializers()) {
      for (File configurationFile : listXmlFiles(new File(dir, "runConfigurations"))) {
        JpsRunConfigurationSerializer.loadRunConfigurations(myProject, loadRootElement(configurationFile));
      }
      File workspaceFile = new File(dir, "workspace.xml");
      if (workspaceFile.exists()) {
        Element runManager = JDomSerializationUtil.findComponent(loadRootElement(workspaceFile), "RunManager");
        JpsRunConfigurationSerializer.loadRunConfigurations(myProject, runManager);
      }
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
    File[] files = dir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File file) {
        return isXmlFile(file);
      }
    });
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
      if (sdkName != null && sdkTypeId != null) {
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
    Element componentRoot = JDomSerializationUtil.findComponent(root, "ProjectModuleManager");
    if (componentRoot == null) return;
    final Element modules = componentRoot.getChild("modules");
    List<Future<JpsModule>> futures = new ArrayList<Future<JpsModule>>();
    final List<String> paths = new ArrayList<String>(); 
    for (Element moduleElement : JDOMUtil.getChildren(modules, "module")) {
      final String path = moduleElement.getAttributeValue("filepath");
      paths.add(path);
    }
    for (final String path : paths) {
      futures.add(ourThreadPool.submit(new Callable<JpsModule>() {
        @Override
        public JpsModule call() throws Exception {
          return loadModule(path, paths, projectSdkType);
        }
      }));
    }
    try {
      for (Future<JpsModule> future : futures) {
        JpsModule module = future.get();
        if (module != null) {
          myProject.addModule(module);
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  private JpsModule loadModule(@NotNull String path, List<String> paths, @Nullable JpsSdkType<?> projectSdkType) {
    final File file = new File(path);
    String name = FileUtil.getNameWithoutExtension(file);
    if (!file.exists()) {
      LOG.info("Module '" + name + "' is skipped: " + file.getAbsolutePath() + " doesn't exist");
      return null;
    }

    final JpsMacroExpander expander = createModuleMacroExpander(myPathVariables, file);
    final Element moduleRoot = loadRootElement(file, expander);
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
          classpathSerializer.loadClasspath(module, classpathDir, baseModulePath, expander, paths);
        }
      }
    }
    JpsFacetSerializer.loadFacets(module, JDomSerializationUtil.findComponent(moduleRoot, "FacetManager"), baseModulePath);
    return module;
  }

  static JpsMacroExpander createModuleMacroExpander(final Map<String, String> pathVariables, File moduleFile) {
    final JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    expander.addFileHierarchyReplacements("MODULE_DIR", moduleFile.getParentFile());
    return expander;
  }

  private static <P extends JpsElement> JpsModule createModule(String name, Element moduleRoot, JpsModulePropertiesSerializer<P> loader) {
    String componentName = loader.getComponentName();
    Element component = componentName != null ? JDomSerializationUtil.findComponent(moduleRoot, componentName) : null;
    return JpsElementFactory.getInstance().createModule(name, loader.getType(), loader.loadProperties(component));
  }

  private static JpsModulePropertiesSerializer<?> getModulePropertiesSerializer(@NotNull String typeId) {
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
