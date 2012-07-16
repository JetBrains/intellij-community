package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.facet.JpsFacetLoader;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
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
  private static final ExecutorService ourThreadPool = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());
  private final JpsProject myProject;
  private final Map<String, String> myPathVariables;

  public JpsProjectLoader(JpsProject project, Map<String, String> pathVariables, File baseDir) {
    super(createMacroExpander(pathVariables, baseDir));
    myProject = project;
    myPathVariables = pathVariables;
  }

  private static JpsMacroExpander createMacroExpander(Map<String, String> pathVariables, File baseDir) {
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
    loadProjectRoot(loadRootElement(new File(dir, "misc.xml")));
    loadModules(loadRootElement(new File(dir, "modules.xml")));
    final File[] libraryFiles = new File(dir, "libraries").listFiles();
    if (libraryFiles != null) {
      for (File libraryFile : libraryFiles) {
        if (isXmlFile(libraryFile)) {
          loadProjectLibraries(loadRootElement(libraryFile));
        }
      }
    }
  }

  private void loadFromIpr(File iprFile) {
    final Element root = loadRootElement(iprFile);
    loadProjectRoot(root);
    loadModules(root);
    loadProjectLibraries(findComponent(root, "libraryTable"));
  }

  private void loadProjectRoot(Element root) {
    Element rootManagerElement = findComponent(root, "ProjectRootManager");
    if (rootManagerElement != null) {
      String sdkName = rootManagerElement.getAttributeValue("project-jdk-name");
      String sdkTypeId = rootManagerElement.getAttributeValue("project-jdk-type");
      if (sdkName != null && sdkTypeId != null) {
        JpsSdkType<?> sdkType = JpsModuleLoader.getSdkType(sdkTypeId);
        myProject.getSdkReferencesTable().setSdkReference(sdkType, JpsElementFactory.getInstance().createSdkReference(sdkName, sdkType));
      }
    }
  }

  private void loadProjectLibraries(Element libraryTableElement) {
    JpsLibraryTableLoader.loadLibraries(libraryTableElement, myProject.getLibraryCollection());
  }

  private void loadModules(Element root) {
    Element componentRoot = findComponent(root, "ProjectModuleManager");
    if (componentRoot == null) return;
    final Element modules = componentRoot.getChild("modules");
    List<Future<JpsModule>> futures = new ArrayList<Future<JpsModule>>();
    for (Element moduleElement : JDOMUtil.getChildren(modules, "module")) {
      final String path = moduleElement.getAttributeValue("filepath");
      futures.add(ourThreadPool.submit(new Callable<JpsModule>() {
        @Override
        public JpsModule call() throws Exception {
          return loadModule(path);
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

  private JpsModule loadModule(String path) {
    final File file = new File(path);
    String name = FileUtil.getNameWithoutExtension(file);
    final JpsMacroExpander expander = new JpsMacroExpander(myPathVariables);
    expander.addFileHierarchyReplacements("MODULE_DIR", file.getParentFile());
    final Element moduleRoot = loadRootElement(file, expander);
    final String typeId = moduleRoot.getAttributeValue("type");
    final JpsModulePropertiesLoader<?> loader = getModulePropertiesLoader(typeId);
    final JpsModule module = createModule(name, moduleRoot, loader);
    JpsModuleLoader.loadRootModel(module, findComponent(moduleRoot, "NewModuleRootManager"));
    JpsFacetLoader.loadFacets(module, findComponent(moduleRoot, "FacetManager"));
    return module;
  }

  private static <P extends JpsElementProperties> JpsModule createModule(String name, Element moduleRoot, JpsModulePropertiesLoader<P> loader) {
    return JpsElementFactory.getInstance().createModule(name, loader.getType(), loader.loadProperties(moduleRoot));
  }

  private static JpsModulePropertiesLoader<?> getModulePropertiesLoader(@NotNull String typeId) {
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      for (JpsModulePropertiesLoader<?> loader : extension.getModulePropertiesLoaders()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return new JpsModulePropertiesLoader<DummyJpsElementProperties>(JpsJavaModuleType.INSTANCE, "JAVA_MODULE") {
      @Override
      public DummyJpsElementProperties loadProperties(@Nullable Element moduleRootElement) {
        return DummyJpsElementProperties.INSTANCE;
      }
    };
  }
}
