package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author nik
 */
public class JpsProjectLoader {
  private final JpsGlobal myGlobal;
  private final JpsProject myProject;
  private ExpandMacroToPathMap myMacroToPathMap;

  public JpsProjectLoader(JpsGlobal global, JpsProject project) {
    myGlobal = global;
    myProject = project;
  }

  public static void loadProject(JpsGlobal global, final JpsProject project, String projectPath) throws IOException {
    new JpsProjectLoader(global, project).loadFromPath(projectPath);
  }

  public void loadFromPath(String path) throws IOException {
    File file = new File(path).getCanonicalFile();
    if (file.isFile() && path.endsWith(".ipr")) {
      loadFromIpr(file);
    }
    else if (file.getName().equals(".idea")) {
      loadFromDirectory(file);
    }
    else {
      File ideaDirectory = new File(file, ".idea");
      if (ideaDirectory.exists()) {
        loadFromDirectory(ideaDirectory);
      }
      else {
        throw new IOException("Cannot find IntelliJ IDEA project files at " + path);
      }
    }
  }

  private void loadFromDirectory(File dir) {
    initMacroMap(dir.getParentFile());
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
    initMacroMap(iprFile.getParentFile());
    final Element root = loadRootElement(iprFile);
    loadModules(root);
    loadProjectLibraries(findComponent(root, "libraryTable"));
  }

  private void initMacroMap(File projectBaseDir) {
    myMacroToPathMap = new ExpandMacroToPathMap();
    myMacroToPathMap.addMacroExpand("PROJECT_DIR", FileUtil.toSystemIndependentName(projectBaseDir.getAbsolutePath()));
  }

  private Element loadRootElement(final File file) {
    try {
      final Element element = JDOMUtil.loadDocument(file).getRootElement();
      myMacroToPathMap.substitute(element, SystemInfo.isFileSystemCaseSensitive);
      return element;
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isXmlFile(File file) {
    return file.isFile() && FileUtil.getNameWithoutExtension(file).equalsIgnoreCase("xml");
  }

  private void loadProjectLibraries(Element libraryTableElement) {
    final ArrayList<JpsLibrary> libraries = new ArrayList<JpsLibrary>();
    JpsLibraryTableLoader.loadLibraries(libraryTableElement, libraries);
    for (JpsLibrary library : libraries) {
      myProject.addLibrary(library);
    }
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
    final JpsModule module = JpsElementFactory.getInstance().createModule(name, getModuleType(typeId));
    JpsModuleLoader.loadRootModel(module, findComponent(moduleRoot, "NewModuleRootManager"));
    return module;
  }

  @Nullable
  private static Element findComponent(Element root, String componentName) {
    for (Element element : JDOMUtil.getChildren(root, "component")) {
      if (componentName.equals(element.getAttributeValue("name"))) {
        return element;
      }
    }
    return null;
  }

  private static JpsModuleType<?> getModuleType(@NotNull String typeId) {
    return JpsJavaModuleType.INSTANCE;
  }
}
