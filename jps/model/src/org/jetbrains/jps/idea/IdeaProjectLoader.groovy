package org.jetbrains.jps.idea

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jps.*
import org.jetbrains.jps.artifacts.Artifact

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
/**
 * @author max
 */
public class IdeaProjectLoader {
  private int libraryCount = 0
  Project project
  private String projectOutputPath
  private String projectLanguageLevel
  private Map<String, String> pathVariables
  private ProjectMacroExpander projectMacroExpander
  private ProjectLoadingErrorReporter errorReporter
  private static final List<AdditionalRootsProviderService> rootsProviderLoader = OwnServiceLoader.load(AdditionalRootsProviderService.class).collect()
  private final XmlParser xmlParser = new XmlParser(false, false)
  private static final ExecutorService ourThreadPool = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors())

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

  public static ProjectMacroExpander loadFromPath(Project project, String path, String script) {
    return loadFromPath(project, path, [:], script);
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
    loadProjectJdkAndOutput(root)
    loadCompilerConfiguration(root)
    loadProjectFileEncodings(root)
    loadWorkspaceConfiguration(new File(iprFile.parentFile, iprFile.name[0..-4]+"iws"))
    loadProjectLibraries(getComponent(root, "libraryTable"))
    loadModules(getComponent(root, "ProjectModuleManager"))
    loadUiDesignerConfiguration(root)
    loadArtifacts(getComponent(root, "ArtifactManager"))
    loadRunConfigurations(getComponent(root, "ProjectRunConfigurationManager"))
  }

  def loadFromDirectoryBased(File dir) {
    project.projectName = getDirectoryBaseProjectName(dir)
    project.locationHash = dir.absolutePath.hashCode()
    projectMacroExpander = new ProjectMacroExpander(pathVariables, dir.parentFile.absolutePath)
    def miscXml = new File(dir, "misc.xml")
    if (miscXml.exists()) {
      loadProjectJdkAndOutput(xmlParser.parse(miscXml))
    }
    else {
      errorReporter.error("Cannot find misc.xml in $dir")
    }

    def encodingsXml = new File(dir, "encodings.xml")
    if (encodingsXml.exists()) {
      loadProjectFileEncodings(xmlParser.parse(encodingsXml))
    }

    def compilerXml = new File(dir, "compiler.xml")
    if (compilerXml.exists()) {
      loadCompilerConfiguration(xmlParser.parse(compilerXml))
    }
    loadWorkspaceConfiguration(new File(dir, "workspace.xml"))

    def librariesFolder = new File(dir, "libraries")
    if (librariesFolder.isDirectory()) {
      librariesFolder.eachFile {File file ->
        if (isXmlFile(file)) {
          Node librariesComponent = xmlParser.parse(file)
          loadProjectLibraries(librariesComponent)
        }
      }
    }

    def modulesXml = new File(dir, "modules.xml")
    if (modulesXml.exists()) {
      Node modulesXmlRoot = xmlParser.parse(modulesXml)
      loadModules(modulesXmlRoot.component[0])
    }
    else {
      errorReporter.error("Cannot find modules.xml in $dir")
    }

    def uiDesignerXml = new File(dir, "uiDesigner.xml")
    if (uiDesignerXml.exists()) {
      loadUiDesignerConfiguration(xmlParser.parse(uiDesignerXml))
    }

    def artifactsFolder = new File(dir, "artifacts")
    if (artifactsFolder.isDirectory()) {
      artifactsFolder.eachFile {File file ->
        if (isXmlFile(file)) {
          def artifactsComponent = xmlParser.parse(file)
          loadArtifacts(artifactsComponent)
        }
      }
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
    if (!targetLevels.isEmpty()) {
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
    return new File(FileUtil.toCanonicalPath(projectMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(url))))
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

  private def loadProjectJdkAndOutput(Node root) {
    def componentTag = getComponent(root, "ProjectRootManager")
    def sdkName = componentTag?."@project-jdk-name"
    def sdk = project.sdks[sdkName]
    if (sdk == null) {
      errorReporter.info("Project SDK '$sdkName' is not defined. Embedded javac will be used")
    }
    def outputTag = componentTag?.output?.getAt(0)
    String outputPath = outputTag != null ? IdeaProjectLoadingUtil.pathFromUrl(outputTag.'@url') : null
    projectOutputPath = outputPath != null && outputPath.length() > 0 ? FileUtil.toCanonicalPath(projectMacroExpander.expandMacros(outputPath)) : null
    project.projectSdk = sdk
    projectLanguageLevel = componentTag?."@languageLevel"
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
          def path = projectMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(url));
          project.filePathToCharset[FileUtil.toCanonicalPath(path)] = charset;
        }
      }
    }
  }

  private NodeList loadProjectLibraries(Node librariesComponent) {
    return (NodeList)librariesComponent?.library?.each {Node libTag ->
      initLibrary(libTag, projectMacroExpander, project.createLibrary(libTag."@name", Closure.IDENTITY))
    }
  }

  def loadArtifacts(Node artifactsComponent) {
    if (artifactsComponent == null) return;
    ArtifactLoader artifactLoader = new ArtifactLoader(project, projectMacroExpander, errorReporter)
    artifactsComponent.artifact.each {Node artifactTag ->
      def artifactName = artifactTag."@name"
      def outputPath = projectMacroExpander.expandMacros(artifactTag."output-path"[0]?.text())
      def root = artifactLoader.loadLayoutElement(artifactTag.root[0], artifactName)
      def options = artifactLoader.loadOptions(artifactTag, artifactName)
      def artifact = new Artifact(name: artifactName, rootElement: root, outputPath: outputPath, properties: options);
      project.artifacts[artifact.name] = artifact;
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

  private def loadModules(Node modulesComponent) {
    List<String> imlPaths = []
    modulesComponent?.modules?.module?.each {Node moduleTag ->
      def imlPath = projectMacroExpander.expandMacros(moduleTag.@filepath)
      project.createModule(moduleName(imlPath), {})
      imlPaths << imlPath
    }

    List<Future> futures = []
    imlPaths.each { String path ->
      futures << (ourThreadPool.submit { loadModule(path) })
    }
    futures.each { it.run(); it.get() }

    Set<File> allContentRoots = project.modules.values().collect { it.contentRoots }.flatten().collect { new File(it) } as Set

    futures.clear()
    project.modules.values().each { module ->
      futures << (ourThreadPool.submit {
        Set<File> myRoots = module.contentRoots.collect { new File(it) } as Set
        for (root in allContentRoots) {
          if (!(root in myRoots) && PathUtil.isUnder(myRoots, root)) {
            module.excludes << FileUtil.toCanonicalPath(root.path)
          }
        }
      })
    }
    futures.each { it.run(); it.get() }
  }

  private Library loadLibrary(Project project, String name, Node libraryTag, MacroExpander macroExpander) {
    return initLibrary(libraryTag, macroExpander, new Library(project, name))
  }

  private Library initLibrary(Node libraryTag, MacroExpander macroExpander, Library library) {
      Map<String, Boolean> jarDirs = [:]
      libraryTag.jarDirectory.each {Node dirNode ->
        jarDirs[dirNode.@url] = Boolean.parseBoolean(dirNode.@recursive)
      }

      libraryTag.CLASSES.root.each {Node rootTag ->
        def url = rootTag.@url
        def path = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(url))
        if (url in jarDirs.keySet()) {
          def paths = []
          collectChildJars(new File(path), jarDirs[url], paths)
          paths.each {
            library.addClasspath it.toString()
          }
        }
        else {
          library.addClasspath path
        }
      }

      libraryTag.SOURCES.root.each {Node rootTag ->
        library.src macroExpander.expandMacros(rootTag.@url)
      }
    return library
  }

  private def collectChildJars(File dir, boolean recursively, List<String> paths) {
    dir.listFiles()?.each {File child ->
      if (child.isDirectory()) {
        if (recursively) {
          collectChildJars(child, recursively, paths)
        }
      }
      else if (child.name.endsWith(".jar") || child.name.endsWith(".zip")) {
        paths << child.absolutePath
      }
    }
  }

  private void loadModule(String imlPath) {
    def moduleFile = new File(imlPath)
    if (!moduleFile.exists()) {
      errorReporter.error("Module file $imlPath not found")
      return
    }
    def moduleBasePath = FileUtil.toSystemIndependentName(moduleFile.getParentFile().getAbsolutePath())
    MacroExpander moduleMacroExpander = new ModuleMacroExpander(projectMacroExpander, moduleBasePath)
    def currentModuleName = moduleName(imlPath)
      Module currentModule = project.modules[currentModuleName]
      currentModule.basePath = moduleBasePath
      def root
      synchronized (xmlParser) {
        root = xmlParser.parse(moduleFile);
      }
      def componentTag = getComponent(root, "NewModuleRootManager")
      if (componentTag != null) {
        loadModuleOrderEntries(componentTag, currentModule, moduleMacroExpander, currentModuleName)

        def srcFolderExists = componentTag.content.sourceFolder[0] != null;

        loadSrcFolders(componentTag, moduleMacroExpander, currentModule)

        componentTag.content.excludeFolder.each {Node exTag ->
          String path = moduleMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(exTag.@url))
          currentModule.exclude path
        }

        def languageLevel = componentTag."@LANGUAGE_LEVEL"
        if (languageLevel == null) {
          languageLevel = projectLanguageLevel
        }
        if (languageLevel != null) {
          currentModule.languageLevel = convertLanguageLevel(languageLevel)
        }

        rootsProviderLoader.each {AdditionalRootsProviderService service ->
          def sourceRoots = service.getAdditionalSourceRoots(currentModule)
          currentModule.sourceRoots.addAll(sourceRoots)
          currentModule.generatedSourceRoots.addAll(sourceRoots)
          if (!sourceRoots.isEmpty()) {
            srcFolderExists = true
          }
        }
        if (srcFolderExists) {
          loadOutputPaths(componentTag, currentModuleName, currentModule, moduleMacroExpander)
        }
      }

      def facetManagerTag = getComponent(root, "FacetManager")
      if (facetManagerTag != null) {
        def facetLoader = new FacetLoader(currentModule, moduleMacroExpander)
        facetLoader.loadFacets(facetManagerTag)
      }
  }

  public void loadOutputPaths(Node componentTag, String currentModuleName, Module currentModule, ModuleMacroExpander moduleMacroExpander) {
    if (componentTag."@inherit-compiler-output" == "true") {
      if (projectOutputPath == null) {
        errorReporter.error("Module '$currentModuleName' uses output path inherited from project but project output path is not specified")
      }
      else {
        currentModule.outputPath = FileUtil.toSystemIndependentName(new File(new File(projectOutputPath, "production"), currentModuleName).absolutePath)
        currentModule.testOutputPath = FileUtil.toSystemIndependentName(new File(new File(projectOutputPath, "test"), currentModuleName).absolutePath)
      }
    }
    else {
      currentModule.outputPath = FileUtil.toCanonicalPath(moduleMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(componentTag.output[0]?.@url)))
      currentModule.testOutputPath = FileUtil.toCanonicalPath(moduleMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(componentTag."output-test"[0]?.'@url')))
      if (currentModule.testOutputPath == null) {
        currentModule.testOutputPath = currentModule.outputPath
      }
    }
  }

  public void loadSrcFolders(Node componentTag, moduleMacroExpander, currentModule) {
    componentTag.content.each {Node contentTag ->
      currentModule.content moduleMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(contentTag.@url))
    }

    componentTag.content.sourceFolder.each {Node folderTag ->
      String path = moduleMacroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(folderTag.@url))
      String prefix = folderTag.@packagePrefix

      if (folderTag.attribute("isTestSource") == "true") {
        currentModule.testSrc path
      }
      else {
        currentModule.src path
      }

      if (prefix != null && prefix != "") {
        currentModule.sourceRootPrefixes[path] = (prefix.replace('.', '/'))
      }
    }
  }

  public void loadModuleOrderEntries(Node componentTag, currentModule, MacroExpander moduleMacroExpander, currentModuleName) {
    componentTag.orderEntry.each {Node entryTag ->
      String type = entryTag.@type
      DependencyScope scope = getScopeById(entryTag.@scope)
      boolean exported = entryTag.@exported != null
      switch (type) {
        case "module":
          def moduleName = entryTag.attribute("module-name")
          def module = project.modules[moduleName]
          if (module == null) {
            errorReporter.warning("Cannot resolve module $moduleName in $currentModuleName")
          }
          else {
            currentModule.dependency(module, scope, exported)
          }
          break

        case "sourceFolder":
          currentModule.moduleSource()
          break

        case "module-library":
          def libraryTag = entryTag.library[0]
          def libraryName = libraryTag."@name"
          def moduleLibrary = loadLibrary(project, libraryName != null ? libraryName : "moduleLibrary#${libraryCount++}",
                                          libraryTag, moduleMacroExpander)
          currentModule.dependency(moduleLibrary, scope, exported)

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
              errorReporter.warning("Cannot resolve project library '$name' in module '$currentModuleName'")
            }
          }
          else {
            library = project.globalLibraries[name]
            if (library == null) {
              errorReporter.warning("Cannot resolve global library '$name' in module '$currentModuleName'")
            }
          }

          if (library != null) {
            currentModule.dependency(library, scope, exported)
          }
          break

        case "jdk":
          def name = entryTag.@jdkName
          def sdk = project.sdks[name]
          if (sdk == null) {
            errorReporter.warning("Cannot resolve SDK '$name' in module '$currentModuleName'. Embedded javac will be used")
          }
          else {
            currentModule.sdk = sdk
            currentModule.dependency(sdk, PredefinedDependencyScopes.COMPILE, false)
          }
          break

        case "inheritedJdk":
          def sdk = project.projectSdk
          if (sdk != null) {
            currentModule.sdk = sdk
            currentModule.dependency(sdk, PredefinedDependencyScopes.COMPILE, false)
          }
          break
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
      case "JDK_1_8": return "8"
    }

    final String prefix = "JDK_";
    if (imlPropertyText != null && imlPropertyText.startsWith(prefix)) {
      return imlPropertyText.substring(prefix.length()).replace('_', '.');
    }

    return "1.6"
  }

  private DependencyScope getScopeById(String id) {
    switch (id) {
      case "COMPILE": return PredefinedDependencyScopes.COMPILE
      case "RUNTIME": return PredefinedDependencyScopes.RUNTIME
      case "TEST": return PredefinedDependencyScopes.TEST
      case "PROVIDED": return PredefinedDependencyScopes.PROVIDED
      default: return PredefinedDependencyScopes.COMPILE
    }
  }

  private String moduleName(String imlPath) {
    def fileName = new File(imlPath).getName()
    return fileName.substring(0, fileName.length() - ".iml".length())
  }

  Node getComponent(Node root, String name) {
    return root.component.find {it."@name" == name}
  }
}
