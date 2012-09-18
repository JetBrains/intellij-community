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
package com.intellij.openapi.project.impl.convertors;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author max, dsl
 */
public class Convertor34 {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.impl.convertors.Convertor34");

  @NonNls public static final String PROJECT_ROOT_MANAGER = "ProjectRootManager";
  @NonNls public static final String PROJECT_ROOT_MANAGER_CLASS = "com.intellij.openapi.projectRoots.ProjectRootManager";

  private static final String SOURCE_ROOTS_NOT_UNDER_PROJECT_ROOTS = ProjectBundle.message("project.convert.source.roots.not.under.project.roots.error");
  private static final String JAVA_DOC_ROOTS_CANNOT_BE_CONVERTED = ProjectBundle.message("project.convert.javadoc.paths.error");
  private static final String MULTIPLE_OUTPUT_PATHS = ProjectBundle.message("project.convert.multiple.output.paths.error");

  public static void execute(Element root, String filePath, @Nullable ArrayList<String> conversionProblems) {
    if (filePath == null) return;

    if (conversionProblems == null) {
      conversionProblems = new ArrayList<String>();
    }
    convertProjectFile(root, filePath, conversionProblems);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String convertLibraryTable34(Element root, String filePath) {
    if (filePath == null) return null;
    final Element libraryTable = findNamedChild(root, "component", "ProjectLibraryTable");
    if (libraryTable == null) return null;

    final Element applicationLibraryTable = new Element("component");
    applicationLibraryTable.setAttribute("name", "libraryTable");

    final List oldLibraries = libraryTable.getChildren("library");
    for (int i = 0; i < oldLibraries.size(); i++) {
      Element oldLibrary = (Element)oldLibraries.get(i);
      Element newLibrary = convertLibrary(oldLibrary);
      applicationLibraryTable.addContent(newLibrary);
    }

    final String ioFilePath = filePath.replace('/', File.separatorChar);
    String parentPath = new File(ioFilePath).getParent();
    if (parentPath == null) parentPath = ".";
    parentPath += "/applicationLibraries.xml";
    final Element newRoot = new Element("application");
    newRoot.addContent(applicationLibraryTable);
    final Document libraryTableDocument = new Document(newRoot);
    try {
      JDOMUtil.writeDocument(libraryTableDocument, parentPath, "\n");
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
    return parentPath;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element convertLibrary(Element oldLibrary) {
    final Element library = new Element("library");
    final Element nameChild = oldLibrary.getChild("name");
    LOG.assertTrue(nameChild != null);
    library.setAttribute("name", nameChild.getAttributeValue("value"));

    processLibraryRootContainer(oldLibrary, library, "CLASSES", "classPath");
    processLibraryRootContainer(oldLibrary, library, "JAVADOC", "javadocPath");
    processLibraryRootContainer(oldLibrary, library, "SOURCES", "sourcePath");
    return library;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void processLibraryRootContainer(Element oldLibrary,
                                                  final Element library,
                                                  String newElementType,
                                                  String oldElementType) {
    final Element elementCLASSES = new Element(newElementType);
    final Element rootsElement = oldLibrary.getChild("roots");
    final Element classPath = rootsElement.getChild(oldElementType);
    if (classPath != null) {
      processRootTypeElement(classPath, new SimpleRootProcessor(elementCLASSES));
    }
    library.addContent(elementCLASSES);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void convertProjectFile(Element root, String filePath, ArrayList<String> conversionProblems) {
    Element rootComponent = null;
    List components = root.getChildren("component");
    for (final Object component1 : components) {
      Element component = (Element)component1;
      if (isProjectRootManager(component)) rootComponent = component;
    }

    if (rootComponent == null) return;

    Element module = createModule(root);

    final String moduleFilePath = filePath.substring(0, filePath.lastIndexOf('.')) + ModuleFileType.DOT_DEFAULT_EXTENSION;

    Element moduleRootComponent = convertProjectRootManager(rootComponent, conversionProblems);
    module.addContent(moduleRootComponent);

    Document moduleDoc = new Document(module);

    try {
      JDOMUtil.writeDocument(moduleDoc, moduleFilePath, "\n");
    }
    catch (IOException e) {
      LOG.error(e);
    }

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(moduleFilePath));

    rootComponent.setAttribute("name", "ProjectRootManager");
    rootComponent.setAttribute("version", "4");

    Element moduleManager = new Element("component");
    moduleManager.setAttribute("name", "ProjectModuleManager");
    addModule(moduleFilePath, moduleManager);

    String moduleFile = new File(moduleFilePath).getName();
    convertWebApps(moduleManager, root, rootComponent, moduleFile.substring(0, moduleFile.lastIndexOf('.')));
    root.addContent(moduleManager);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element createModule(Element root) {
    Element module = new Element("module");
    module.setAttribute("version", "4");
    String relativePaths = root.getAttributeValue("relativePaths");
    if (relativePaths != null) {
      module.setAttribute("relativePaths", relativePaths);
    }
    return module;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void convertWebApps(Element moduleManager, Element projectElement, Element projectRootManager, String mainModule) {
    Element webRootContainer = findNamedChild(projectElement, "component", "WebRootContainer");
    if(webRootContainer == null) return;
    List roots = webRootContainer.getChildren("root");

    for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
      Element root = (Element)iterator.next();
      String name = root.getAttributeValue("name");
      String url = root.getAttributeValue("url");

      if(name == null || url == null) continue;

      String filepath = VirtualFileManager.extractPath(url);

      VirtualFile moduleDirectory = LocalFileSystem.getInstance().findFileByPath(filepath);
      if(moduleDirectory != null) {
        Element module = createModule(projectElement);
        module.setAttribute("type", "J2EE_WEB_MODULE");

        Element rootManager = createWebModuleRootManager(module, moduleDirectory, projectRootManager, mainModule);
        module.addContent(rootManager);

        Element buildComponent = createWebModuleBuildComponent();
        module.addContent(buildComponent);

        Element moduleProperties = createWebModuleProperties(moduleDirectory);
        module.addContent(moduleProperties);

        Document moduleDocument = new Document(module);
        String moduleName = (!"".equals(name) ? name : moduleDirectory.getName());
        if(moduleName.equals(mainModule)) moduleName = "web" + moduleName;
        try {
          final String modulePath = moduleDirectory.getPath() + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
          JDOMUtil.writeDocument(moduleDocument, modulePath, "\n");
          addModule(modulePath, moduleManager);

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(modulePath));
            }
          });

        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void addSetting(Element parent, String name, String value){
    Element option = new Element("setting");
    option.setAttribute("name", name);
    option.setAttribute("value", value);
    parent.addContent(option);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element createWebModuleProperties(VirtualFile moduleDirectory) {
    Element component = new Element("component");
    component.setAttribute("name", "WebModuleProperties");

    try {
      BufferedWriter writer = null;
      try {
        writer = new BufferedWriter(new FileWriter(new File(moduleDirectory.getPath(), "WEB-INF/web.xml")));
        writer.write(FileTemplateManager.getInstance().getJ2eeTemplate("web.2_3.xml").getText());
      }
      finally {
        if (writer != null) {
          writer.close();
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    Element webRoots = new Element("webroots");
    component.addContent(webRoots);
    Element root  = new Element("root");
    webRoots.addContent(root);
    root.setAttribute("url", moduleDirectory.getUrl());
    root.setAttribute("relative", "/");
    return component;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element createWebModuleBuildComponent() {
    Element component = new Element("component");
    component.setAttribute("name", "WebModuleBuildComponent");

    addSetting(component, "EXPLODED_URL", "file://$MODULE_DIR$");
    addSetting(component, "EXPLODED_ENABLED", "true");

    return component;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element createWebModuleRootManager(Element module, VirtualFile moduleDirectory, Element projectRootManager, String mainModule) {
    Element newModuleRootManager = new Element("component");
    newModuleRootManager.setAttribute("name", "NewModuleRootManager");

    Element jdk = projectRootManager.getChild("jdk");
    if(jdk != null) {
      String jdkName = jdk.getAttributeValue("name");
      if (jdkName != null) {
        Element orderEntry = new Element("orderEntry");
        orderEntry.setAttribute("type", "jdk");
        orderEntry.setAttribute("jdkName", jdkName);
        newModuleRootManager.addContent(orderEntry);
      }
    }

    Element orderEntry = new Element("orderEntry");
    orderEntry.setAttribute("type", "module");
    orderEntry.setAttribute("module-name", mainModule);
    newModuleRootManager.addContent(orderEntry);

    Element output = new Element("output");
    output.setAttribute("url", "file://" + getModulePath("classes", moduleDirectory.getPath()));
    newModuleRootManager.addContent(output);

    Element content = new Element("content");
    content.setAttribute("url", "file://" + getModulePath("", moduleDirectory.getPath()));
    newModuleRootManager.addContent(content);

    VirtualFile classesDir = moduleDirectory.findFileByRelativePath("WEB-INF/classes");
    if(classesDir != null) {
      Element classes = createLibraryEntry(classesDir, module, moduleDirectory);
      newModuleRootManager.addContent(classes);
    }

    VirtualFile lib = moduleDirectory.findFileByRelativePath("WEB-INF/lib");
    if(lib != null) {
      for (VirtualFile virtualFile : lib.getChildren()) {
        Element libEntry = createLibraryEntry(virtualFile, module, moduleDirectory);
        newModuleRootManager.addContent(libEntry);
      }
    }

    return newModuleRootManager;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element createLibraryEntry(VirtualFile file, Element module, VirtualFile moduleDirectory) {
    String path = file.getPath().substring(moduleDirectory.getPath().length() + 1);
    if(file.getFileSystem() instanceof JarFileSystem) {
      path = path + "!/";
    }

    Element orderEntry = new Element("orderEntry");
    orderEntry.setAttribute("type", "module-library");
    Element library = new Element("library");
    orderEntry.addContent(library);
    Element classes = new Element("CLASSES");
    library.addContent(classes);
    Element root = new Element("root");
    root.setAttribute("url", "file://" + getModulePath(path, moduleDirectory.getPath()));
    classes.addContent(root);
    return orderEntry;
  }

  private static String getModulePath(String path, String moduleDirectory) {
    return "".equals(path) ? moduleDirectory  : moduleDirectory + "/" + path;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void addModule(final String moduleFilePath, Element moduleManager) {
    Element moduleEntry = new Element("module");
    final String moduleVfsPath = moduleFilePath.replace(File.separatorChar, '/');
    moduleEntry.setAttribute("filepath", moduleVfsPath);
    moduleEntry.setAttribute("fileurl", "file://" +moduleVfsPath);
    Element modulesEntry = moduleManager.getChild("modules");
    if(modulesEntry == null) {
      modulesEntry = new Element("modules");
      moduleManager.addContent(modulesEntry);
    }
    modulesEntry.addContent(moduleEntry);
  }

  private static Element convertProjectRootManager(Element projectRootManager, ArrayList<String> conversionProblems) {
    return new ProjectToModuleConverter(projectRootManager, conversionProblems).getModuleRootManager();
  }

  private static interface RootElementProcessor {
    void processSimpleRoot(Element root);

    void processJdkRoot(Element root);

    void processOutputRoot(Element root);

    void processExcludedOutputRoot(Element root);

    void processLibraryRoot(Element root);

    void processEjbRoot(Element root);
  }

  private static abstract class EmptyRootProcessor implements RootElementProcessor {
    @Override
    public void processSimpleRoot(Element root) {
      cannotProcess(root);
    }

    @Override
    public void processJdkRoot(Element root) {
      cannotProcess(root);
    }

    @Override
    public void processOutputRoot(Element root) {
      cannotProcess(root);
    }

    @Override
    public void processExcludedOutputRoot(Element root) {
      cannotProcess(root);
    }

    @Override
    public void processLibraryRoot(Element root) {
      cannotProcess(root);
    }

    @Override
    public void processEjbRoot(Element root) {
      cannotProcess(root);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    protected void cannotProcess(Element root) {
      LOG.error("Cannot process roots of type " + root.getAttributeValue("type") + " in " + classId());
    }

    abstract protected String classId();

  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void processRoot(Element root, RootElementProcessor processor) {
    LOG.assertTrue("root".equals(root.getName()));
    final String type = root.getAttributeValue("type");
    LOG.assertTrue(type != null);
    if (ProjectRootUtil.SIMPLE_ROOT.equals(type)) {
      processor.processSimpleRoot(root);
    }
    else if (ProjectRootUtil.OUTPUT_ROOT.equals(type)) {
      processor.processOutputRoot(root);
    }
    else if (ProjectRootUtil.JDK_ROOT.equals(type)) {
      processor.processJdkRoot(root);
    }
    else if (ProjectRootUtil.EXCLUDED_OUTPUT.equals(type)) {
      processor.processExcludedOutputRoot(root);
    }
    else if (ProjectRootUtil.LIBRARY_ROOT.equals(type)) {
      processor.processLibraryRoot(root);
    }
    else if (ProjectRootUtil.EJB_ROOT.equals(type)) {
      processor.processEjbRoot(root);
    }
    else if (ProjectRootUtil.COMPOSITE_ROOT.equals(type)) {
      final List children = root.getChildren("root");
      for (int i = 0; i < children.size(); i++) {
        Element element = (Element)children.get(i);
        processRoot(element, processor);
      }
    }
    else {
      LOG.error("Unknown root type: " + type);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void processRootTypeElement(Element rootTypeElement, RootElementProcessor rootProcessor) {
    if (rootTypeElement == null) return;
    final List children = rootTypeElement.getChildren("root");
    for (int i = 0; i < children.size(); i++) {
      Element element = (Element)children.get(i);
      processRoot(element, rootProcessor);
    }
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  private static class ProjectToModuleConverter {
    private final Element myProjectRootManager;
    private final Element myModuleRootManager;
    private final ArrayList<String> myProjectRoots;
    private final List<String> mySourceFolders;
    private final ArrayList<String> myExcludeFolders;
    private final ArrayList<String> myDetectedProblems;

    private ProjectToModuleConverter(Element projectRootManager, ArrayList<String> problems) {
      myProjectRootManager = projectRootManager;
      myModuleRootManager = new Element("component");
      myModuleRootManager.setAttribute("name", "NewModuleRootManager");
      myProjectRoots = new ArrayList<String>();
      myDetectedProblems = problems;

      final Element projectPath = projectRootManager.getChild("projectPath");
      if(projectPath != null) {
        processRootTypeElement(projectPath, new ProjectRootProcessor());
      }
      Collections.sort(myProjectRoots);
      for (int i = 0; i < myProjectRoots.size(); i++) {
        String path = myProjectRoots.get(i);
        final int next = i + 1;
        while (next < myProjectRoots.size() && myProjectRoots.get(next).startsWith(path)) {
          myProjectRoots.remove(next);
        }
      }

      final Element sourcePath = projectRootManager.getChild("sourcePath");
      mySourceFolders = new ArrayList<String>();
      processRootTypeElement(sourcePath, new SourceRootProcessor());

      final Element excludePath = projectRootManager.getChild("excludePath");
      myExcludeFolders = new ArrayList<String>();
      processRootTypeElement(excludePath, new ExcludeRootsProcessor());

      Element javadocPath = projectRootManager.getChild("javadocPath");
      processRootTypeElement(javadocPath, new JavaDocRootProcessor());

      final Element patternExcludeFolder = new Element("excludeFolder");
      final Element patternSourceFolder = new Element("sourceFolder");
      patternSourceFolder.setAttribute("isTestSource", "false");
      Map<String, List<String>> contentToSource = dispatchFolders(myProjectRoots, mySourceFolders);
      final Map<String, List<String>> contentToExclude = dispatchFolders(myProjectRoots, myExcludeFolders);

      for (String root : myProjectRoots) {
        final Element contentElement = new Element("content");
        contentElement.setAttribute("url", root);
        createFolders(contentElement, patternSourceFolder, contentToSource.get(root));
        createFolders(contentElement, patternExcludeFolder, contentToExclude.get(root));
        myModuleRootManager.addContent(contentElement);
      }

      final Element classPath = projectRootManager.getChild("classPath");
      processRootTypeElement(classPath, new ClassPathRootProcessor());

      final Element projectElement = (Element)myProjectRootManager.getParent();
      final Element compilerConfigurationElement = findNamedChild(projectElement, "component", "CompilerConfiguration");
      if (compilerConfigurationElement != null) {
        final Element option = findNamedChild(compilerConfigurationElement, "option", "DEFAULT_OUTPUT_PATH");
        final String path = option == null ? null : option.getAttributeValue("value");

        if (path != null) {
          final String url = "file://" + path;
          final Element outputElement = new Element("output");
          outputElement.setAttribute("url", url);
          myModuleRootManager.addContent(outputElement);
          final Element outputTestElement = new Element("output-test");
          outputTestElement.setAttribute("url", url);
          myModuleRootManager.addContent(outputTestElement);
        }
        // check for multiple outputs
        {
          final Element outputMode = findNamedChild(compilerConfigurationElement, "option", "OUTPUT_MODE");
          final String attributeValue = outputMode != null ? outputMode.getAttributeValue("value") : "";
          if ("multiple".equals(attributeValue)) {
            addProblem(MULTIPLE_OUTPUT_PATHS);
          }
        }
      }

      final Element excludeOutput = myProjectRootManager.getChild("exclude_output");
      if (excludeOutput != null) {
        final String enabled = excludeOutput.getAttributeValue("enabled");
        if ("yes".equals(enabled) || "true".equals(enabled)) {
          myModuleRootManager.addContent(new Element("exclude-output"));
        }
      }
    }

    private static void createFolders(final Element contentElement,
                               final Element patternFolderElement,
                               final List<String> folders) {
      for (String folder : folders) {
        Element folderElement = (Element)patternFolderElement.clone();
        folderElement.setAttribute("url", folder);
        contentElement.addContent(folderElement);
      }
    }

    private static Map<String, List<String>> dispatchFolders(ArrayList<String> projectRoots, List<String> folders) {
      final Map<String, List<String>> result = new HashMap<String, List<String>>();
      for (String root : projectRoots) {
        final ArrayList<String> foldersForRoot = new ArrayList<String>();
        result.put(root, foldersForRoot);
        for (Iterator<String> iterator1 = folders.iterator(); iterator1.hasNext();) {
          String folder = iterator1.next();
          if (folder.startsWith(root)) {
            foldersForRoot.add(folder);
          }
        }
        Collections.sort(foldersForRoot);
      }
      return result;
    }

    public Element getModuleRootManager() { return myModuleRootManager; }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private class JavaDocRootProcessor extends EmptyRootProcessor {

      @Override
      protected void cannotProcess(Element root) {
        addProblem(JAVA_DOC_ROOTS_CANNOT_BE_CONVERTED);
      }

      @Override
      protected String classId() {
        return "JavaDocRootProcessor";
      }
    }

    private class ProjectRootProcessor extends EmptyRootProcessor {
      @Override
      public void processSimpleRoot(Element root) {
        final String value = root.getAttributeValue("url");
        myProjectRoots.add(value);
      }

      @Override
      public void processEjbRoot(Element root) {
        // todo[cdr,dsl] implement conversion of EJB roots
      }

      @Override
      protected String classId() {
        return "ProjectRootProcessor";
      }
    }

    private void addProblem(final String description) {
      if (!myDetectedProblems.contains(description)) {
        myDetectedProblems.add(description);
      }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private class SourceRootProcessor extends EmptyRootProcessor {

      @Override
      public void processSimpleRoot(Element root) {
        final String url = root.getAttributeValue("url");
        boolean found = false;
        for (int i = 0; i < myProjectRoots.size(); i++) {
          String projectPath = myProjectRoots.get(i);
          if (url.startsWith(projectPath)) {
            mySourceFolders.add(url);
            found = true;
            break;
          }
          else if (projectPath.startsWith(url)) {
            myProjectRoots.remove(i);
            myProjectRoots.add(i, url);
            mySourceFolders.add(url);
            found = true;
            break;
          }
        }
        if (!found) {
          addProblem(SOURCE_ROOTS_NOT_UNDER_PROJECT_ROOTS);
        }
      }

      @Override
      public void processJdkRoot(Element root) {
      }

      @Override
      public void processLibraryRoot(Element root) {
      }

      @Override
      public void processEjbRoot(Element root) {
        // todo[cdr,dsl] implement conversion of EJB roots
      }

      @Override
      protected String classId() {
        return "SourceRootProcessor";
      }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private class ExcludeRootsProcessor extends EmptyRootProcessor {
      @Override
      public void processSimpleRoot(Element root) {
        final String url = root.getAttributeValue("url");
        for (int i = 0; i < myProjectRoots.size(); i++) {
          String projectRoot = myProjectRoots.get(i);
          if (url.startsWith(projectRoot)) {
            myExcludeFolders.add(url);
          }
        }
      }

      @Override
      public void processEjbRoot(Element root) {
        // todo[cdr,dsl] implement conversion of EJB roots
      }

      @Override
      protected String classId() {
        return "ExcludeRootsProcessor";
      }

      @Override
      public void processJdkRoot(Element root) {
        // [dsl]: fix for SCR24517
        // [dsl]: I have no idea how such project can be configured in Ariadna,
        // [dsl]: and what does it mean, but such projects do exist....
      }

      @Override
      public void processExcludedOutputRoot(Element root) {
      }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private class ClassPathRootProcessor extends EmptyRootProcessor {
      @Override
      public void processSimpleRoot(Element root) {
        final Element orderEntry = new Element("orderEntry");
        orderEntry.setAttribute("type", "module-library");
        final Element libraryElement = new Element("library");
        final Element classesElement = new Element("CLASSES");
        final Element rootElement = new Element("root");
        rootElement.setAttribute((Attribute)root.getAttribute("url").clone());
        classesElement.addContent(rootElement);
        libraryElement.addContent(classesElement);
        orderEntry.addContent(libraryElement);
        myModuleRootManager.addContent(orderEntry);
      }

      @Override
      public void processJdkRoot(Element root) {
        final Element orderEntry = new Element("orderEntry");
        orderEntry.setAttribute("type", "jdk");
        orderEntry.setAttribute("jdkName", root.getAttributeValue("name"));
        myModuleRootManager.addContent(orderEntry);
      }

      @Override
      public void processLibraryRoot(Element root) {
        final String libraryName = root.getAttributeValue("name");
        final Element orderEntry = new Element("orderEntry");
        orderEntry.setAttribute("type", "library");
        orderEntry.setAttribute("name", libraryName);
        orderEntry.setAttribute("level", LibraryTablesRegistrar.APPLICATION_LEVEL);
        myModuleRootManager.addContent(orderEntry);
      }

      @Override
      public void processEjbRoot(Element root) {
        // todo[cdr,dsl] implement conversion of EJB roots
      }

      @Override
      protected String classId() {
        return "ClassPathProcessor";
      }

      @Override
      public void processOutputRoot(Element root) {
        final Element orderEntry = new Element("orderEntry");
        orderEntry.setAttribute("type", "sourceFolder");
        myModuleRootManager.addContent(orderEntry);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static boolean isProjectRootManager(Element component) {
    String compName = component.getAttributeValue("name");
    String compClass = component.getAttributeValue("class");
    return compName != null && compName.equals(PROJECT_ROOT_MANAGER) ||
           compClass != null && compClass.equals(PROJECT_ROOT_MANAGER_CLASS);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element findNamedChild(Element root, String name, String nameAttributeValue) {
    final List children = root.getChildren(name);
    for (int i = 0; i < children.size(); i++) {
      Element e = (Element)children.get(i);
      if (nameAttributeValue.equals(e.getAttributeValue("name"))) {
        return e;
      }
    }
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static class SimpleRootProcessor extends EmptyRootProcessor {
    private final Element myTargetElement;

    public SimpleRootProcessor(Element targetElement) {
      myTargetElement = targetElement;
    }

    @Override
    public void processSimpleRoot(Element root) {
      final String url = root.getAttributeValue("url");
      final Element newRoot = new Element("root");
      newRoot.setAttribute("url", url);
      myTargetElement.addContent(newRoot);
    }

    @Override
    public void processEjbRoot(Element root) {
      // todo[cdr,dsl] implement conversion of EJB roots
    }

    @Override
    protected String classId() {
      return "SimpleRootProcessor";
    }

  }
}
