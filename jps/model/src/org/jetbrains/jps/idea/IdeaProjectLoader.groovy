package org.jetbrains.jps.idea
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jps.*
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
    project.projectName = StringUtil.trimEnd(iprFile.getName(), ".ipr")
    project.locationHash = iprFile.absolutePath.hashCode()
    projectMacroExpander = new ProjectMacroExpander(pathVariables, iprFile.parentFile.absolutePath)

    def root = xmlParser.parse(iprFile)
    loadCompilerConfiguration(root)
    loadProjectFileEncodings(root)
    loadWorkspaceConfiguration(new File(iprFile.parentFile, iprFile.name[0..-4]+"iws"))
    loadUiDesignerConfiguration(root)
    loadRunConfigurations(getComponent(root, "ProjectRunConfigurationManager"))
  }

  def loadFromDirectoryBased(File dir) {
    project.projectName = getDirectoryBaseProjectName(dir)
    project.locationHash = dir.absolutePath.hashCode()
    projectMacroExpander = new ProjectMacroExpander(pathVariables, dir.parentFile.absolutePath)

    def encodingsXml = new File(dir, "encodings.xml")
    if (encodingsXml.exists()) {
      loadProjectFileEncodings(xmlParser.parse(encodingsXml))
    }

    def compilerXml = new File(dir, "compiler.xml")
    if (compilerXml.exists()) {
      loadCompilerConfiguration(xmlParser.parse(compilerXml))
    }
    loadWorkspaceConfiguration(new File(dir, "workspace.xml"))

    def uiDesignerXml = new File(dir, "uiDesigner.xml")
    if (uiDesignerXml.exists()) {
      loadUiDesignerConfiguration(xmlParser.parse(uiDesignerXml))
    }

    def runConfFolder = new File(dir, "runConfigurations")
    if (runConfFolder.isDirectory()) {
      runConfFolder.eachFile {File file ->
        if (isXmlFile(file)) {
          def runConfManager = xmlParser.parse(file);
          loadRunConfigurations(runConfManager);
        }
      }
    }
  }

  def getDirectoryBaseProjectName(File dir) {
    File nameFile = new File(dir, ".name")
    if (nameFile.isFile()) {
      return FileUtil.loadFile(nameFile).trim()
    }
    return StringUtil.replace(dir.parentFile.name, ":", "")
  }

  boolean isXmlFile(File file) {
    return file.isFile() && StringUtil.endsWithIgnoreCase(file.name, ".xml")
  }

  private def loadWorkspaceConfiguration(File workspaceFile) {
    if (!workspaceFile.exists()) return

    def root = xmlParser.parse(workspaceFile)
    def options = loadOptions(getComponent(root, "CompilerWorkspaceConfiguration"))
    // compatibility: in older projects this setting was stored in workspace
    if (project.compilerConfiguration.addNotNullAssertions == true) { // if is the same as default value
      project.compilerConfiguration.addNotNullAssertions = parseBoolean(options["ASSERT_NOT_NULL"], true);
    }
    project.compilerConfiguration.clearOutputDirectoryOnRebuild = parseBoolean(options["CLEAR_OUTPUT_DIRECTORY"], true);
  }

  private def loadCompilerConfiguration(Node root) {
    def rawPatterns = []
    def includePatterns = []
    def excludePatterns = []
    def componentTag = getComponent(root, "CompilerConfiguration")

    componentTag?.wildcardResourcePatterns?.getAt(0)?.entry?.each {Node entryTag ->
      String pattern = entryTag."@name"
      rawPatterns << pattern;
      if (pattern.startsWith("!")) {
        excludePatterns << convertPattern(pattern.substring(1))
      }
      else {
        includePatterns << convertPattern(pattern)
      }
    }
    CompilerConfiguration configuration = project.compilerConfiguration
    if (!includePatterns.isEmpty() || !excludePatterns.isEmpty()) {
      configuration.resourceIncludePatterns = includePatterns
      configuration.resourceExcludePatterns = excludePatterns
    }
    if (!rawPatterns.isEmpty()) {
      configuration.resourcePatterns = rawPatterns;
    }

    def excludesTag = componentTag?.excludeFromCompile?.getAt(0)
    if (excludesTag != null) {
      excludesTag.file?.each {
        configuration.excludes.addExcludedFile(getFileByUrl(it."@url"))
      }
      excludesTag.directory?.each {
        configuration.excludes.addExcludedDirectory(getFileByUrl(it."@url"), Boolean.parseBoolean(it."@includeSubdirectories"))
      }
    }
    configuration.options.putAll(loadOptions(componentTag));
    configuration.javacOptions.putAll(loadOptions(getComponent(root, "JavacSettings")))
    configuration.eclipseOptions.putAll(loadOptions(getComponent(root, "EclipseCompilerSettings")))

    def annotationProcessingTag = componentTag?.annotationProcessing
    if (annotationProcessingTag != null) {
      configuration.moduleAnnotationProcessingProfiles = []
      annotationProcessingTag?.profile?.each {profileTag ->
        AnnotationProcessingProfile profile
        if (parseBoolean(profileTag."@default", false)) {
          profile =  configuration.defaultAnnotationProcessingProfile
        }
        else {
          profile = new AnnotationProcessingProfile()
          configuration.moduleAnnotationProcessingProfiles << profile
        }
        profile.name = profileTag."@name";
        profile.enabled = parseBoolean(profileTag."@enabled", false)
        profile.generatedSourcesDirName = profileTag.sourceOutputDir?.getAt(0)?."@name"
        profileTag.processor?.each {
          profile.processors << it."@name"
        }
        profileTag.option?.each {
          profile.processorsOptions[it."@name"] = it."@value" ?: ""
        }
        def pathTag = profileTag.processorPath?.getAt(0)
        profile.obtainProcessorsFromClasspath = parseBoolean(pathTag?."@useClasspath", true)
        List<String> processorPaths = []
        pathTag?.each {
          processorPaths << projectMacroExpander.expandMacros(it."@name")
        }
        profile.processorsPath = processorPaths.join(File.pathSeparator)

        profileTag.module?.each {
          profile.processModule << it."@name"
        }
      }
    }

    def targetLevels = componentTag?.bytecodeTargetLevel
    if (targetLevels != null && !targetLevels.isEmpty()) {
      def targetLevelTag = targetLevels.first()
      configuration.bytecodeTarget.projectBytecodeTarget = targetLevelTag."@target" ?: null;
      targetLevelTag.module?.each {
        configuration.bytecodeTarget.modulesBytecodeTarget[it."@name"] = it."@target" ?: ""
      }
    }

    def addNotNullTag = componentTag?.addNotNullAssertions
    if (addNotNullTag != null) {
      project.compilerConfiguration.addNotNullAssertions = parseBoolean(addNotNullTag."@enabled", true);
    }
  }

  private def loadUiDesignerConfiguration(Node root) {
    def options = loadOptions(getComponent(root, "uidesigner-configuration"))
    project.uiDesignerConfiguration.copyFormsRuntimeToOutput = parseBoolean(options["COPY_FORMS_RUNTIME_TO_OUTPUT"], true)
  }

  private File getFileByUrl(final String url) {
    return new File(FileUtil.toCanonicalPath(projectMacroExpander.expandMacros(JpsPathUtil.urlToPath(url))))
  }

  private static boolean parseBoolean(Object value, boolean defaultValue) {
    if (value instanceof NodeList) {
      if (value.isEmpty()) return defaultValue
      value = value[0]
    }
    return value != null ? Boolean.parseBoolean((String)value) : defaultValue
  }
  
  private Map<String, String> loadOptions(Node optionsTag) {
    def result = new HashMap<String, String>()
    optionsTag?.option?.each {Node optionTag ->
      result[optionTag."@name"] = optionTag."@value";
    }
    return result
  }

  private String convertPattern(String pattern) {
    if (pattern.indexOf('/') == -1) {
      return "**/" + pattern
    }
    if (pattern.startsWith("/")) {
      return pattern.substring(1)
    }
    return pattern
  }

  private def loadProjectFileEncodings(Node root) {
    def componentTag = getComponent(root, "Encoding");
    if (componentTag == null) return;
    componentTag.file?.each {Node fileNode ->
      String url = fileNode."@url";
      String charset = fileNode."@charset";

      if (!StringUtil.isEmptyOrSpaces(charset)) {
        if ("PROJECT".equals(url)) {
          project.projectCharset = charset;
        }
        else {
          def path = projectMacroExpander.expandMacros(JpsPathUtil.urlToPath(url));
          project.filePathToCharset[FileUtil.toCanonicalPath(path)] = charset;
        }
      }
    }
  }

  def loadRunConfigurations(Node runConfManager) {
    if (runConfManager == null) return;

    runConfManager.configuration.each {Node confTag ->
      def name = confTag.'@name';
      RunConfiguration runConf = new RunConfiguration(project, projectMacroExpander, confTag);
      project.runConfigurations[name] = runConf;
    }
  }

  Node getComponent(Node root, String name) {
    return root.component.find {it."@name" == name}
  }
}
