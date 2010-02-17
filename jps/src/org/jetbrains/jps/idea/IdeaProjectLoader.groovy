package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
import org.jetbrains.jps.Library
import org.jetbrains.jps.artifacts.Artifact

/**
 * @author max
 */
public class IdeaProjectLoader {
  private int libraryCount = 0;

  public static String guessHome(Script script) {
    File home = new File(script["gant.file"].substring("file:".length()))

    while (home != null) {
      if (home.isDirectory()) {
        if (new File(home, ".idea").exists()) return home.getCanonicalPath()
      }

      home = home.getParentFile()
    }

    return null
  }

  def loadFromPath(Project project, String path) {
    def fileAtPath = new File(path)

    if (fileAtPath.isFile() && path.endsWith(".ipr")) {
      loadFromIpr(project, path)
      return
    }
    else {
      File directoryBased = null;
      if (".idea".equals(fileAtPath.getName())) {
        directoryBased = fileAtPath;
      }
      else {
        def child = new File(fileAtPath, ".idea")
        if (child.exists()) {
          directoryBased = child
        }
      }

      if (directoryBased != null) {
        loadFromDirectoryBased(project, directoryBased.getCanonicalFile())
        return
      }
    }

    project.error("Cannot file IntelliJ IDEA project files at $path")
  }

  def loadFromIpr(Project project, String path) {
    def iprFile = new File(path)
    def projectBasePath = iprFile.getParentFile().getAbsolutePath()

    def root = new XmlParser(false, false).parse(iprFile)
    loadModules(getComponent(root, "ProjectModuleManager"), project, projectBasePath)
    loadProjectLibraries(getComponent(root, "libraryTable"), project, projectBasePath)
    loadArtifacts(getComponent(root, "ArtifactManager"), project, projectBasePath)
  }

  def loadFromDirectoryBased(Project project, File dir) {
    def modulesXml = new File(dir, "modules.xml")
    if (!modulesXml.exists()) project.error("Cannot find modules.xml in $dir")

    Node modulesXmlRoot = new XmlParser(false, false).parse(modulesXml)

    def projectBasePath = dir.parentFile.absolutePath
    loadModules(modulesXmlRoot.component[0], project, projectBasePath)

    def librariesFolder = new File(dir, "libraries")
    if (librariesFolder.isDirectory()) {
      librariesFolder.eachFile {File file ->
        Node librariesComponent = new XmlParser(false, false).parse(file)
        loadProjectLibraries(librariesComponent, project, projectBasePath)
      }
    }

    def artifactsFolder = new File(dir, "artifacts")
    if (artifactsFolder.isDirectory()) {
      artifactsFolder.eachFile {File file ->
        def artifactsComponent = new XmlParser(false, false).parse(file)
        loadArtifacts(artifactsComponent, project, projectBasePath)
      }
    }
  }

  private NodeList loadProjectLibraries(Node librariesComponent, Project project, String projectBasePath) {
    return librariesComponent?.library.each {Node libTag ->
      project.createLibrary(libTag."@name", libraryInitializer(libTag, projectBasePath, null))
    }
  }

  def loadArtifacts(Node artifactsComponent, Project project, String projectBasePath) {
    ArtifactLoader artifactLoader = new ArtifactLoader(project, projectBasePath)
    artifactsComponent.artifact.each {Node artifactTag ->
      def artifactName = artifactTag."@name"
      def root = artifactLoader.loadLayoutElement(artifactTag.root[0], artifactName)
      def artifact = new Artifact(name: artifactName, rootElement: root)
      project.artifacts[artifact.name] = artifact;
    }
  }

  private def loadModules(Node modulesComponent, Project project, String projectBasePath) {
    modulesComponent?.modules.module.each {Node moduleTag ->
      loadModule(project, projectBasePath, expandMacro(moduleTag."@filepath", projectBasePath, null))
    }
  }

  public static String expandMacro(String path, String projectDir, String moduleDir) {
    String answer = expandProjectMacro(path, projectDir)

    if (moduleDir != null) {
      answer = path.replace("\$MODULE_DIR\$", moduleDir)
    }

    return answer
  }

  public static String expandProjectMacro(String path, String projectDir) {
    return path.replace("\$PROJECT_DIR\$", projectDir)
  }

  private Library loadNamedLibrary(Project project, Node libraryTag, String projectBasePath, String moduleBasePath) {
    loadLibrary(project, libraryTag."@name", libraryTag, projectBasePath, moduleBasePath)
  }

  private Library loadLibrary(Project project, String name, Node libraryTag, String projectBasePath, String moduleBasePath) {
    return new Library(project, name, libraryInitializer(libraryTag, projectBasePath, moduleBasePath))
  }

  private Closure libraryInitializer(Node libraryTag, String projectBasePath, String moduleBasePath) {
    return {
      libraryTag.CLASSES.root.each {Node rootTag ->
        classpath expandMacro(
                pathFromUrl(rootTag."@url"),
                projectBasePath,
                moduleBasePath)
      }

      libraryTag.SOURCES.root.each {Node rootTag ->
        src expandMacro(rootTag."@url", projectBasePath, moduleBasePath)
      }
    }
  }

  Object loadModule(Project project, String projectBasePath, String imlPath) {
    def moduleFile = new File(imlPath)
    if (!moduleFile.exists()) {
      project.error("Module file $imlPath not found")
      return 
    }

    def moduleBasePath = moduleFile.getParentFile().getAbsolutePath()
    def currentModuleName = moduleName(imlPath)
    project.createModule(currentModuleName) {
      def root = new XmlParser(false, false).parse(moduleFile)
      def componentTag = getComponent(root, "NewModuleRootManager")
      if (componentTag != null) {
        componentTag.orderEntry.each {Node entryTag ->
          String type = entryTag."@type"
          String scope = entryTag."@scope"
          switch (type) {
            case "module":
              def moduleName = entryTag.attribute("module-name")
              def module = project.modules[moduleName]
              if (module == null) {
                project.warning("Cannot resolve module $moduleName in $currentModuleName")
              }
              else {
                if (scope == "TEST") {
                  testclasspath module
                }
                else {
                  classpath module
                }
              }
              break

            case "module-library":
              def moduleLibrary = loadLibrary(project, "moduleLibrary#${libraryCount++}", entryTag.library.first(), projectBasePath, moduleBasePath)
              if (scope == "PROVIDED") {
                providedClasspath moduleLibrary
              }
              else if (scope == "TEST") {
                testclasspath moduleLibrary
              }
              else {
                classpath moduleLibrary
              }
              break;

            case "library":
              def name = entryTag.attribute("name")
              switch (entryTag.attribute("level")) {
                case "project":
                  def library = project.libraries[name]
                  if (library == null) {
                    project.warning("Cannot resolve library $name in $currentModuleName")
                  }
                  else {
                    if (scope == "PROVIDED") {
                      providedClasspath library
                    }
                    else if (scope == "TEST") {
                      testclasspath library
                    }
                    else {
                      classpath library
                    }
                  }
                  break

                case "application":
                  project.warning("Application level libraries are not supported: ${name}")
                  break
              }
          }
        }
        componentTag.content.sourceFolder.each {Node folderTag ->
          String path = expandMacro(pathFromUrl(folderTag."@url"), projectBasePath, moduleBasePath)
          String prefix = folderTag."@packagePrefix"

          if (folderTag.attribute("isTestSource") == "true") {
            testSrc path
          }
          else {
            src path
          }

          if (prefix != null && prefix != "") {
            project.modules[currentModuleName].sourceRootPrefixes[path] = (prefix.replace('.', '/'))
          }
        }
        componentTag.content.excludeFolder.each {Node exTag ->
          String path = expandMacro(pathFromUrl(exTag."@url"), projectBasePath, moduleBasePath)
          exclude path
        }
        def languageLevel = componentTag."@LANGUAGE_LEVEL"
        if (languageLevel != null) {
          def ll = convertLanguageLevel(languageLevel)
          project.modules[currentModuleName]["sourceLevel"] = ll
          project.modules[currentModuleName]["targetLevel"] = ll
        }
      }

      def facetManagerTag = getComponent(root, "FacetManager")
      if (facetManagerTag != null) {
        def facetLoader = new FacetLoader(project.modules[currentModuleName], projectBasePath, moduleBasePath)
        facetLoader.loadFacets(facetManagerTag)
      }
    }
  }

  private String convertLanguageLevel(String imlPropertyText) {
    switch (imlPropertyText) {
      case "JDK_1_3": return "1.3"
      case "JDK_1_4": return "1.4"
      case "JDK_1_5": return "1.5"
      case "JDK_1_6": return "1.6"
      case "JDK_1_7": return "1.7"
    }

    return "1.6"
  }

  static String pathFromUrl(String url) {
    if (url.startsWith("file://")) {
      return url.substring("file://".length())
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length())
      if (url.endsWith("!/"))
        url = url.substring(0, url.length() - "!/".length())
    }
    url
  }

  private String moduleName(String imlPath) {
    def fileName = new File(imlPath).getName()
    return fileName.substring(0, fileName.length() - ".iml".length())
  }

  Node getComponent(Node root, String name) {
    return root.component.find {it."@name" == name}
  }
}
