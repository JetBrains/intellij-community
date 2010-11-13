package org.jetbrains.jps.idea

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.*

 /**
 * @author nik
 */
class ArtifactLoader {
  private final Project project
  private final MacroExpander macroExpander
  private static OwnServiceLoader<LayoutElementTypeService> elementTypeLoader = OwnServiceLoader.load(LayoutElementTypeService.class)
  private static Map<String, LayoutElementTypeService> elementTypes = null

  def ArtifactLoader(Project project, MacroExpander macroExpander) {
    this.macroExpander = macroExpander
    this.project = project
  }

  LayoutElement loadLayoutElement(Node tag, String artifactName) {
    String id = tag."@id";
    switch (id) {
      case "root":
        return new RootElement(loadChildren(tag, artifactName));
      case "directory":
        return new DirectoryElement(tag."@name", loadChildren(tag, artifactName));
      case "archive":
        return new ArchiveElement(tag."@name", loadChildren(tag, artifactName));
      case "artifact":
        return new ArtifactLayoutElement(artifactName: tag."@artifact-name")
      case "file-copy":
        def path = macroExpander.expandMacros(tag."@path")
        if (!new File(path).exists()) {
           project.warning("Error in '$artifactName' artifact: file '$path' doesn't exist")
        }
        return new FileCopyElement(filePath: path,
                                   outputFileName: tag."@output-file-name");
      case "dir-copy":
        def path = macroExpander.expandMacros(tag."@path")
        if (!new File(path).exists()) {
          project.warning("Error in '$artifactName' artifact: directory '$path' doesn't exist")
        }
        return new DirectoryCopyElement(dirPath: path);
      case "extracted-dir":
        def jarPath = macroExpander.expandMacros(tag."@path")
        String pathInJar = tag."@path-in-jar"
        if (pathInJar == null) pathInJar = "/"
        if (!new File(pathInJar).exists()) {
          project.warning("Error in '$artifactName' artifact: file '$jarPath' doesn't exist")
        }
        return new ExtractedDirectoryElement(jarPath: jarPath, pathInJar: pathInJar)
      case "module-output":
        def name = tag."@name"
        if (project.modules[name] == null) {
          project.error("Unknown module '$name' in '$artifactName' artifact")
        }
        return new ModuleOutputElement(moduleName: name);
      case "library":
        return new LibraryFilesElement(libraryLevel: tag."@level", libraryName: tag."@name", moduleName: tag."@module-name");
    }

    LayoutElementTypeService type = findType(id)
    if (type != null) {
      return type.createElement(project, tag, macroExpander)
    }

    project.error("unknown element in '$artifactName' artifact: $id");
  }

  private LayoutElementTypeService findType(String typeId) {
    if (elementTypes == null) {
      elementTypes = [:]
      elementTypeLoader.each {LayoutElementTypeService type ->
        elementTypes[type.typeId] = type
      }
    }
    return elementTypes[typeId]
  }

  Map<String, Options> loadOptions(Node artifactTag) {
    def Map<String, Options> res = [:];
    artifactTag.properties.each{Node el ->
      def String id = el."@id";
      el.options.each{Node optionsEl ->
        def boolean enabled = optionsEl."@enabled";
        if (enabled) {
          def Map<String, String> options = [:];
          res.put(id, new Options() {
            Map<String, String> getAll() {
              return options;
            }
          })
          optionsEl.children().each{Node opt ->
            def String key = opt.name();
            def String value = macroExpander.expandMacros(opt.text());
            options.put(key, value);
          }
        }
      }
    }
    return res;
  }

  List<LayoutElement> loadChildren(Node node, String artifactName) {
    node.element.collect { loadLayoutElement(it, artifactName) }
  }
}
