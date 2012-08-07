package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetLoader;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author nik
 */
public class JpsProjectLoader extends JpsLoaderBase {
  private static final ExecutorService ourThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  private final JpsProject myProject;
  private final Map<String, String> myPathVariables;

  public JpsProjectLoader(JpsProject project, Map<String, String> pathVariables, File baseDir) {
    super(createProjectMacroExpander(pathVariables, baseDir));
    myProject = project;
    myPathVariables = pathVariables;
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

  private void loadFromDirectory(File dir) {
    JpsSdkType<?> projectSdkType = loadProjectRoot(loadRootElement(new File(dir, "misc.xml")));
    loadModules(loadRootElement(new File(dir, "modules.xml")), projectSdkType);
    for (File libraryFile : listXmlFiles(new File(dir, "libraries"))) {
      loadProjectLibraries(loadRootElement(libraryFile));
    }
    for (File artifactFile : listXmlFiles(new File(dir, "artifacts"))) {
      loadArtifacts(loadRootElement(artifactFile));
    }
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
    final Element root = loadRootElement(iprFile);
    JpsSdkType<?> projectSdkType = loadProjectRoot(root);
    loadModules(root, projectSdkType);
    loadProjectLibraries(findComponent(root, "libraryTable"));
    loadArtifacts(findComponent(root, "ArtifactManager"));
  }

  private void loadArtifacts(Element artifactManagerComponent) {
    JpsArtifactSerializer.loadArtifacts(myProject, artifactManagerComponent);
  }

  @Nullable
  private JpsSdkType<?> loadProjectRoot(Element root) {
    JpsSdkType<?> sdkType = null;
    Element rootManagerElement = findComponent(root, "ProjectRootManager");
    if (rootManagerElement != null) {
      String sdkName = rootManagerElement.getAttributeValue("project-jdk-name");
      String sdkTypeId = rootManagerElement.getAttributeValue("project-jdk-type");
      if (sdkName != null && sdkTypeId != null) {
        sdkType = JpsSdkTableSerializer.getSdkType(sdkTypeId);
        JpsSdkTableSerializer.setSdkReference(myProject.getSdkReferencesTable(), sdkName, sdkType);
      }
      for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
        extension.loadProjectRoots(myProject, rootManagerElement);
      }
    }
    return sdkType;
  }

  private void loadProjectLibraries(Element libraryTableElement) {
    JpsLibraryTableSerializer.loadLibraries(libraryTableElement, myProject.getLibraryCollection());
  }

  private void loadModules(Element root, final JpsSdkType<?> projectSdkType) {
    Element componentRoot = findComponent(root, "ProjectModuleManager");
    if (componentRoot == null) return;
    final Element modules = componentRoot.getChild("modules");
    List<Future<JpsModule>> futures = new ArrayList<Future<JpsModule>>();
    for (Element moduleElement : JDOMUtil.getChildren(modules, "module")) {
      final String path = moduleElement.getAttributeValue("filepath");
      futures.add(ourThreadPool.submit(new Callable<JpsModule>() {
        @Override
        public JpsModule call() throws Exception {
          return loadModule(path, projectSdkType);
        }
      }));
    }
    try {
      for (Future<JpsModule> future : futures) {
        myProject.addModule(future.get());
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private JpsModule loadModule(String path, JpsSdkType<?> projectSdkType) {
    final File file = new File(path);
    String name = FileUtil.getNameWithoutExtension(file);
    final JpsMacroExpander expander = createModuleMacroExpander(myPathVariables, file);
    final Element moduleRoot = loadRootElement(file, expander);
    final String typeId = moduleRoot.getAttributeValue("type");
    final JpsModulePropertiesSerializer<?> loader = getModulePropertiesSerializer(typeId);
    final JpsModule module = createModule(name, moduleRoot, loader);
    JpsModuleSerializer.loadRootModel(module, findComponent(moduleRoot, "NewModuleRootManager"), projectSdkType);
    JpsFacetLoader.loadFacets(module, findComponent(moduleRoot, "FacetManager"), FileUtil.toSystemIndependentName(path));
    return module;
  }

  static JpsMacroExpander createModuleMacroExpander(final Map<String, String> pathVariables, File moduleFile) {
    final JpsMacroExpander expander = new JpsMacroExpander(pathVariables);
    expander.addFileHierarchyReplacements("MODULE_DIR", moduleFile.getParentFile());
    return expander;
  }

  private static <P extends JpsElement> JpsModule createModule(String name, Element moduleRoot, JpsModulePropertiesSerializer<P> loader) {
    return JpsElementFactory.getInstance().createModule(name, loader.getType(), loader.loadProperties(moduleRoot));
  }

  private static JpsModulePropertiesSerializer<?> getModulePropertiesSerializer(@NotNull String typeId) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsModulePropertiesSerializer<?> loader : extension.getModulePropertiesSerializers()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return new JpsModulePropertiesSerializer<JpsDummyElement>(JpsJavaModuleType.INSTANCE, "JAVA_MODULE") {
      @Override
      public JpsDummyElement loadProperties(@Nullable Element moduleRootElement) {
        return JpsElementFactory.getInstance().createDummyElement();
      }
    };
  }
}
