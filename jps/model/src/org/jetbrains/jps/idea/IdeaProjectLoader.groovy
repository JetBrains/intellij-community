package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
/**
 * @author max
 */
public class IdeaProjectLoader {
  Project project
  private Map<String, String> pathVariables
  private ProjectMacroExpander projectMacroExpander
  private ProjectLoadingErrorReporter errorReporter
  private final XmlParser xmlParser = new XmlParser(false, false)

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

  public static ProjectMacroExpander loadFromPath(Project project, String path, Map<String, String> pathVariables) {
    return loadFromPath(project, path, pathVariables, "")
  }

  public static ProjectMacroExpander loadFromPath(Project project, String path, Map<String, String> pathVariables, String script) {
    return loadFromPath(project, path, pathVariables, script, new SystemOutErrorReporter(true))
  }

  public static ProjectMacroExpander loadFromPath(Project project, String path, Map<String, String> pathVariables, String script, ProjectLoadingErrorReporter errorReporter) {
    def loader = new IdeaProjectLoader(project, pathVariables, errorReporter)

    loader.doLoadFromPath(path);

    if (script != null) {
      if (script.startsWith ("@")) {
        new GroovyShell(new Binding(project:project)).evaluate(new File (script.substring(1)))
      };
      else {
        new GroovyShell(new Binding(project:project)).evaluate(script)
      }
    }

    return loader.projectMacroExpander;
  }


  private def IdeaProjectLoader(Project project, Map<String, String> pathVariables, ProjectLoadingErrorReporter errorReporter) {
    this.project = project;
    this.pathVariables = pathVariables
    this.errorReporter = errorReporter
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

    errorReporter.error("Cannot find IntelliJ IDEA project files at $path")
  }

  def loadFromIpr(String path) {
    def iprFile = new File(path).getCanonicalFile()
    projectMacroExpander = new ProjectMacroExpander(pathVariables, iprFile.parentFile.absolutePath)
  }

  def loadFromDirectoryBased(File dir) {
    projectMacroExpander = new ProjectMacroExpander(pathVariables, dir.parentFile.absolutePath)
  }



  Node getComponent(Node root, String name) {
    return root.component.find {it."@name" == name}
  }
}
