package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class JpsProjectLoader extends JpsLoaderBase {
  private final JpsProject myProject;

  public JpsProjectLoader(JpsProject project, Map<String, String> pathVariables, File baseDir) {
    super(createMacroExpander(pathVariables, baseDir));
    myProject = project;
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
    for (Element moduleElement : JDOMUtil.getChildren(modules, "module")) {
      final String path = moduleElement.getAttributeValue("filepath");
      JpsModule module = loadModule(path);
      myProject.addModule(module);
    }
  }

  private JpsModule loadModule(String path) {
    final File file = new File(path);
    String name = FileUtil.getNameWithoutExtension(file);
    final Element moduleRoot = loadRootElement(file);
    final String typeId = moduleRoot.getAttributeValue("type");
    final JpsModuleType<?> moduleType = getModuleType(typeId);
    final JpsModule module = createModule(name, moduleRoot, moduleType);
    JpsModuleLoader.loadRootModel(module, findComponent(moduleRoot, "NewModuleRootManager"));
    return module;
  }

  private static <P extends JpsElementProperties> JpsModule createModule(String name, Element moduleRoot, JpsModuleType<P> moduleType) {
    return JpsElementFactory.getInstance().createModule(name, moduleType, loadModuleProperties(moduleType, moduleRoot));
  }

  private static <P extends JpsElementProperties> P loadModuleProperties(JpsModuleType<P> type, Element moduleRoot) {
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      P properties = extension.loadModuleProperties(type, moduleRoot);
      if (properties != null) {
        return properties;
      }
    }
    return (P)DummyJpsElementProperties.INSTANCE;
  }

  private static JpsModuleType<?> getModuleType(@NotNull String typeId) {
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      final JpsModuleType<?> type = extension.getModuleType(typeId);
      if (type != null) {
        return type;
      }
    }
    return JpsJavaModuleType.INSTANCE;
  }
}
