package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
import org.jetbrains.jps.Library

/**
 * @author max
 */
public class IdeaProjectLoader {
  private int libraryCount = 0;

  def loadFromPath(Project project, String path) {
    def fileAtPath = new File(path)

    if (fileAtPath.isFile() && path.endsWith(".ipr")) {
      loadFromIpr(project, path)
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
    project.error("Loading from .ipr files is not yet supported. Please switch to directory based format")
  }

  def loadFromDirectoryBased(Project project, File dir) {
    def modulesXml = new File(dir, "modules.xml")
    if (!modulesXml.exists()) project.error("Cannot find modules.xml in $dir")

    Node modulesXmlRoot = new XmlParser(false, false).parse(modulesXml)

    def projectBasePath = dir.getParentFile().getAbsolutePath()
    loadModules(project, modulesXmlRoot, projectBasePath)

    def librariesFolder = new File(dir, "libraries")
    if (librariesFolder.isDirectory()) {
      librariesFolder.eachFile {File file ->
        NodeList libs = new XmlParser(false, false).parse(file).library
        libs.each {Node libTag ->
          project.createLibrary(attr(libTag, "name"), libraryInitializer(libTag, projectBasePath, null))
        }
      }
    }
  }

  private def loadModules(Project project, Node modulesXmlRoot, projectBasePath) {
    modulesXmlRoot.component.modules.module.each {Node moduleTag ->
      loadModule(project, projectBasePath, expandMacro(attr(moduleTag, "filepath"), projectBasePath, null))
    }
  }

  private String expandMacro(String path, String projectDir, String moduleDir) {
    String answer = path.replace("\$PROJECT_DIR\$", projectDir)

    if (moduleDir != null) {
      answer = path.replace("\$MODULE_DIR\$", moduleDir)
    }

    return answer
  }

  private Library loadNamedLibrary(Project project, Node libraryTag, String projectBasePath, String moduleBasePath) {
    loadLibrary(project, attr(libraryTag, "name"), libraryTag, projectBasePath, moduleBasePath)
  }

  private Library loadLibrary(Project project, String name, Node libraryTag, String projectBasePath, String moduleBasePath) {
    return new Library(project, name, libraryInitializer(libraryTag, projectBasePath, moduleBasePath))
  }

  private Closure libraryInitializer(Node libraryTag, String projectBasePath, String moduleBasePath) {
    return {
      libraryTag.CLASSES.root.each {Node rootTag ->
        classpath expandMacro(
                pathFromUrl(attr(rootTag, "url")),
                projectBasePath,
                moduleBasePath)
      }

      libraryTag.SOURCES.root.each {Node rootTag ->
        src expandMacro(attr(rootTag, "url"), projectBasePath, moduleBasePath)
      }
    }
  }

  Object loadModule(Project project, String projectBasePath, String imlPath) {
    def moduleBasePath = new File(imlPath).getParentFile().getAbsolutePath()
    def currentModuleName = moduleName(imlPath)
    project.createModule(currentModuleName) {
      def root = new XmlParser(false, false).parse(new File(imlPath))
      root.component.each {Node componentTag ->
        if ("NewModuleRootManager" == componentTag.attribute("name")) {
          componentTag.orderEntry.each {Node entryTag ->
            String type = attr(entryTag, "type")
            switch (type) {
              case "module":
                def moduleName = entryTag.attribute("module-name")
                def module = project.modules[moduleName]
                if (module == null) project.error("Cannot resolve module $moduleName")
                classpath module
                break

              case "module-library":
                def moduleLibrary = loadLibrary(project, "moduleLibrary#${libraryCount++}", entryTag.library.first(), projectBasePath, moduleBasePath)
                classpath moduleLibrary
                break;

              case "library":
                def name = entryTag.attribute("name")
                switch (entryTag.attribute("level")) {
                  case "project":
                    def library = project.libraries[name]
                    if (library == null) project.error("Cannot resolve library $name")
                    classpath library
                    break

                  case "application":
                    project.warning("Application level libraries are not supported: ${name}")
                    break
                }
            }
          }

          componentTag.content.sourceFolder.each {Node folderTag ->
            String path = expandMacro(pathFromUrl(attr(folderTag, "url")), projectBasePath, moduleBasePath)

            if (folderTag.attribute("isTestSource") == "true") {
              testSrc path
            }
            else {
              src path
            }
          }

          componentTag.content.excludeFolder.each {Node exTag ->
            String path = expandMacro(pathFromUrl(attr(exTag, "url")), projectBasePath, moduleBasePath)
            exclude path
          }

          def languageLevel = attr(componentTag, "LANGUAGE_LEVEL")
          if (languageLevel != null) {
            def ll = convertLanguageLevel(languageLevel)
            project.modules[currentModuleName]["sourceLevel"] = ll
            project.modules[currentModuleName]["targetLevel"] = ll
          }
        }
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

  private String pathFromUrl(String url) {
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

  private String attr(Node tag, String name) {
    return tag.attribute(name)
  }
}
