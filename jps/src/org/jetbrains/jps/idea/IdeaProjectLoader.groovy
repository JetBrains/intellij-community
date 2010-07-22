package org.jetbrains.jps.idea

import org.jetbrains.jps.Library
import org.jetbrains.jps.MacroExpansion
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.Artifact
import org.jetbrains.jps.Module

/**
 * @author max
 */
public class IdeaProjectLoader implements MacroExpansion {
  private int libraryCount = 0
  Project project
  private String projectBasePath
  private String projectOutputPath
  private Map<String, String> pathVariables

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

  public static MacroExpansion loadFromPath(Project project, String path, Map<String, String> pathVariables) {
    def loader = new IdeaProjectLoader(project, pathVariables)
    loader.doLoadFromPath(path);
    return loader;
  }

  public static MacroExpansion loadFromPath(Project project, String path) {
    return loadFromPath(project, path, [:])
  }

  private def IdeaProjectLoader(Project project, Map<String, String> pathVariables) {
    this.project = project;
    this.pathVariables = pathVariables
  }

  private def doLoadFromPath(String path) {
    def fileAtPath = new File(path)

    if (fileAtPath.isFile() && path.endsWith(".ipr")) {
      loadFromIpr(path)
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
        loadFromDirectoryBased(directoryBased.getCanonicalFile())
        return
      }
    }

    project.error("Cannot find IntelliJ IDEA project files at $path")
  }

  def loadFromIpr(String path) {
    def iprFile = new File(path).getAbsoluteFile()
    projectBasePath = iprFile.getParentFile().getAbsolutePath()

    def root = new XmlParser(false, false).parse(iprFile)
    loadProjectJdkAndOutput(root)
    loadCompilerConfiguration(root)
    loadModules(getComponent(root, "ProjectModuleManager"))
    loadProjectLibraries(getComponent(root, "libraryTable"))
    loadArtifacts(getComponent(root, "ArtifactManager"))
  }

  def loadFromDirectoryBased(File dir) {
    projectBasePath = dir.parentFile.absolutePath
    def modulesXml = new File(dir, "modules.xml")
    if (!modulesXml.exists()) project.error("Cannot find modules.xml in $dir")

    def miscXml = new File(dir, "misc.xml")
    if (!miscXml.exists()) project.error("Cannot find misc.xml in $dir")
    loadProjectJdkAndOutput(new XmlParser(false, false).parse(miscXml))

    def compilerXml = new File(dir, "compiler.xml")
    if (compilerXml.exists()) {
      loadCompilerConfiguration(new XmlParser(false, false).parse(compilerXml))
    }

    Node modulesXmlRoot = new XmlParser(false, false).parse(modulesXml)
    loadModules(modulesXmlRoot.component.first())

    def librariesFolder = new File(dir, "libraries")
    if (librariesFolder.isDirectory()) {
      librariesFolder.eachFile {File file ->
        Node librariesComponent = new XmlParser(false, false).parse(file)
        loadProjectLibraries(librariesComponent)
      }
    }

    def artifactsFolder = new File(dir, "artifacts")
    if (artifactsFolder.isDirectory()) {
      artifactsFolder.eachFile {File file ->
        def artifactsComponent = new XmlParser(false, false).parse(file)
        loadArtifacts(artifactsComponent)
      }
    }
  }

  private def loadCompilerConfiguration(Node root) {
    def includePatterns = []
    def excludePatterns = []
    def componentTag = getComponent(root, "CompilerConfiguration")
    componentTag?.wildcardResourcePatterns?.first()?.entry?.each {Node entryTag ->
      String pattern = entryTag."@name"
      if (pattern.startsWith("!")) {
        excludePatterns << convertPattern(pattern.substring(1))
      }
      else {
        includePatterns << convertPattern(pattern)
      }
    }
    if (!includePatterns.isEmpty() || !excludePatterns.isEmpty()) {
      project.binding.ant.patternset(id: "compiler.resources") {
        includePatterns.each { include(name: it)}
        excludePatterns.each { exclude(name: it)}
      }
      project.props["compiler.resources.id"] = "compiler.resources"
    }
  }

  private String convertPattern(String pattern) {
    if (pattern.indexOf('/') == -1) {
      return "**/" + pattern
    }
    return pattern
  }

  private def loadProjectJdkAndOutput(Node root) {
    def componentTag = getComponent(root, "ProjectRootManager")
    def sdkName = componentTag."@project-jdk-name"
    def sdk = project.sdks[sdkName]
    if (sdk == null) {
      project.info("Project SDK '$sdkName' is not defined. Embedded javac will be used")
    }
    def outputTag = componentTag.output[0];
    def outputUrl = outputTag != null ? outputTag.'@url' : "file://\$PROJECT_DIR\$/out";
    projectOutputPath = expandProjectMacro(pathFromUrl(outputUrl));
    project.projectSdk = sdk
  }

  private NodeList loadProjectLibraries(Node librariesComponent) {
    return librariesComponent?.library?.each {Node libTag ->
      project.createLibrary(libTag."@name", libraryInitializer(libTag, projectBasePath, null))
    }
  }

  def loadArtifacts(Node artifactsComponent) {
    if (artifactsComponent == null) return;
    ArtifactLoader artifactLoader = new ArtifactLoader(this)
    artifactsComponent.artifact.each {Node artifactTag ->
      def artifactName = artifactTag."@name"
      def outputPath = expandProjectMacro(artifactTag."output-path"?.first()?.text())
      def root = artifactLoader.loadLayoutElement(artifactTag.root.first(), artifactName)
      def artifact = new Artifact(name: artifactName, rootElement: root, outputPath: outputPath)
      project.artifacts[artifact.name] = artifact;
    }
  }

  private def loadModules(Node modulesComponent) {
    modulesComponent?.modules?.module?.each {Node moduleTag ->
      loadModule(projectBasePath, expandMacro(moduleTag.@filepath, null))
    }
  }

  public String expandMacro(String path, String moduleDir) {
    String answer = expandProjectMacro(path)

    if (moduleDir != null) {
      answer = answer.replace("\$MODULE_DIR\$", moduleDir)
    }

    return answer
  }

  public String expandProjectMacro(String path) {
    if (path == null) return path
    path = path.replace("\$PROJECT_DIR\$", projectBasePath)
    pathVariables.each { name, value ->
      path = path.replace("\$${name}\$", value)
    }
    return path
  }

  private Library loadNamedLibrary(Project project, Node libraryTag, String projectBasePath, String moduleBasePath) {
    loadLibrary(project, libraryTag."@name", libraryTag, projectBasePath, moduleBasePath)
  }

  private Library loadLibrary(Project project, String name, Node libraryTag, String projectBasePath, String moduleBasePath) {
    return new Library(project, name, libraryInitializer(libraryTag, projectBasePath, moduleBasePath))
  }

  private Closure libraryInitializer(Node libraryTag, String projectBasePath, String moduleBasePath) {
    return {
      Map<String, Boolean> jarDirs = [:]
      libraryTag.jarDirectory.each {Node dirNode ->
        jarDirs[dirNode.@url] = Boolean.parseBoolean(dirNode.@recursive)
      }

      libraryTag.CLASSES.root.each {Node rootTag ->
        def url = rootTag.@url
        def path = expandMacro(pathFromUrl(url), moduleBasePath)
        if (url in jarDirs.keySet()) {
          def paths = []
          collectChildJars(path, jarDirs[url], paths)
          paths.each {
            classpath it
          }
        }
        else {
          classpath path
        }
      }

      libraryTag.SOURCES.root.each {Node rootTag ->
        src expandMacro(rootTag.@url, moduleBasePath)
      }
    }
  }

  private def collectChildJars(String path, boolean recursively, List<String> paths) {
    def dir = new File(path)
    dir.listFiles()?.each {File child ->
      if (child.isDirectory()) {
        if (recursively) {
          collectChildJars(path, recursively, paths)
        }
      }
      else if (child.name.endsWith(".jar")) {
        paths << child.absolutePath
      }
    }
  }

  Object loadModule(String projectBasePath, String imlPath) {
    def moduleFile = new File(imlPath)
    if (!moduleFile.exists()) {
      project.error("Module file $imlPath not found")
      return
    }

    def moduleBasePath = moduleFile.getParentFile().getAbsolutePath()
    def currentModuleName = moduleName(imlPath)
    project.createModule(currentModuleName) {
      Module currentModule = project.modules[currentModuleName]
      def root = new XmlParser(false, false).parse(moduleFile)
      def componentTag = getComponent(root, "NewModuleRootManager")
      if (componentTag != null) {
        componentTag.orderEntry.each {Node entryTag ->
          String type = entryTag.@type
          String scope = entryTag.@scope
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
              def libraryTag = entryTag.library.first()
              def libraryName = libraryTag."@name"
              def moduleLibrary = loadLibrary(project, libraryName != null ? libraryName : "moduleLibrary#${libraryCount++}",
                                              libraryTag, projectBasePath, moduleBasePath)
              if (scope == "PROVIDED") {
                providedClasspath moduleLibrary
              }
              else if (scope == "TEST") {
                testclasspath moduleLibrary
              }
              else {
                classpath moduleLibrary
              }

              if (libraryName != null) {
                currentModule.libraries[libraryName] = moduleLibrary
              }
              break;

            case "library":
              def name = entryTag.attribute("name")
              def library = null
              if (entryTag.@level == "project") {
                library = project.libraries[name]
                if (library == null) {
                  project.warning("Cannot resolve project library '$name' in module '$currentModuleName'")
                }
              } else {
                library = project.globalLibraries[name]
                if (library == null) {
                  project.warning("Cannot resolve global library '$name' in module '$currentModuleName'")
                }
              }

              if (library != null) {
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

            case "jdk":
              def name = entryTag.@jdkName
              def sdk = project.sdks[name]
              if (sdk == null) {
                project.warning("Cannot resolve SDK '$name' in module '$currentModuleName'. Embedded javac will be used")
              }
              else {
                currentModule.sdk = sdk
                providedClasspath sdk
              }
              break

            case "inheritedJdk":
              def sdk = project.projectSdk
              if (sdk != null) {
                currentModule.sdk = sdk
                providedClasspath sdk
              }
              break
          }
        }
        componentTag.content.sourceFolder.each {Node folderTag ->
          String path = expandMacro(pathFromUrl(folderTag.@url), moduleBasePath)
          String prefix = folderTag.@packagePrefix

          if (folderTag.attribute("isTestSource") == "true") {
            testSrc path
          }
          else {
            src path
          }

          if (prefix != null && prefix != "") {
            currentModule.sourceRootPrefixes[path] = (prefix.replace('.', '/'))
          }
        }

        componentTag.content.excludeFolder.each {Node exTag ->
          String path = expandMacro(pathFromUrl(exTag.@url), moduleBasePath)
          exclude path
        }

        def languageLevel = componentTag."@LANGUAGE_LEVEL"
        if (languageLevel != null) {
          def ll = convertLanguageLevel(languageLevel)
          currentModule["sourceLevel"] = ll
          currentModule["targetLevel"] = ll
        }

        if (componentTag."@inherit-compiler-output" == "true") {
          File compileOutput = new File(projectOutputPath, "classes")
          currentModule.outputPath = new File(new File(compileOutput, "production"), currentModuleName).absolutePath
          currentModule.testOutputPath = new File(new File(compileOutput, "test"), currentModuleName).absolutePath
        }
        else {
          currentModule.outputPath = expandMacro(pathFromUrl(componentTag.output?.first()?.@url), moduleBasePath)
          currentModule.testOutputPath = expandMacro(pathFromUrl(componentTag."output-test"?.first()?.@url), moduleBasePath)
        }
      }

      def facetManagerTag = getComponent(root, "FacetManager")
      if (facetManagerTag != null) {
        def facetLoader = new FacetLoader(this, currentModule, moduleBasePath)
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
    if (url == null) return null
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
