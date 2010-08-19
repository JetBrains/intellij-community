package org.jetbrains.jps

import org.codehaus.gant.GantBinding
import org.jetbrains.jps.dag.DagBuilder
import org.jetbrains.jps.builders.*
import org.jetbrains.jps.listeners.JpsBuildListener
import org.jetbrains.jps.listeners.BuildStatisticsListener
import org.jetbrains.jps.listeners.BuildInfoPrinter
import org.jetbrains.jps.listeners.DefaultBuildInfoPrinter

/**
 * @author max
 */
class ProjectBuilder {
  final Map<Module, ModuleChunk> mapping = [:]
  final Map<ModuleChunk, String> outputs = [:]
  final Map<ModuleChunk, String> testOutputs = [:]
  final Map<ClasspathKind, Map<ModuleChunk, List<String>>> cachedClasspaths = [:]
  final Map<ModuleChunk, List<String>> testCp = [:]
  List<ModuleChunk> chunks = null

  final Project project;
  final GantBinding binding;

  final List<ModuleBuilder> sourceGeneratingBuilders = []
  final List<ModuleBuilder> sourceModifyingBuilders = []
  final List<ModuleBuilder> translatingBuilders = []
  final List<ModuleBuilder> weavingBuilders = []
  final CustomTasksBuilder preTasksBuilder = new CustomTasksBuilder()
  final CustomTasksBuilder postTasksBuilder = new CustomTasksBuilder()

  final List<JpsBuildListener> listeners = [new BuildStatisticsListener()]
  BuildInfoPrinter buildInfoPrinter = new DefaultBuildInfoPrinter()
  boolean useInProcessJavac

  def ProjectBuilder(GantBinding binding, Project project) {
    this.project = project
    this.binding = binding
    sourceGeneratingBuilders << new GroovyStubGenerator(project)
    translatingBuilders << new JavacBuilder()
    translatingBuilders << new GroovycBuilder(project)
    translatingBuilders << new ResourceCopier()
    weavingBuilders << new JetBrainsInstrumentations(project)
  }

  private def List<ModuleBuilder> builders() {
    [preTasksBuilder, sourceGeneratingBuilders, sourceModifyingBuilders, translatingBuilders, weavingBuilders, postTasksBuilder].flatten()
  }

  private def buildChunks() {
    if (chunks == null) {
      def iterator = { Module module, Closure processor ->
        module.getFullClasspath().each {entry ->
          if (entry instanceof Module) {
            processor(entry)
          }
        }
      }
      def dagBuilder = new DagBuilder<Module>({new ModuleChunk()}, iterator)
      chunks = dagBuilder.build(project, project.modules.values())
      chunks.each { ModuleChunk chunk ->
        chunk.modules.each {
          mapping[it] = chunk
        }
      }
      project.info("Total ${chunks.size()} chunks detected")
    }
  }

  public def clean() {
    outputs.clear()
    testOutputs.clear()
    cachedClasspaths.clear();
  }

  public def buildAll() {
    buildAllModules(true)
  }

  public def buildProduction() {
    buildAllModules(false)
  }

  private def buildAllModules(boolean includeTests) {
    listeners*.onBuildStarted(project)
    buildChunks()
    chunks.each {
      makeChunk(it, false)
      if (includeTests) {
        makeChunk(it, true)
      }
    }
    listeners*.onBuildFinished(project)
  }

  def preModuleBuildTask(String moduleName, Closure task) {
    preTasksBuilder.registerTask(moduleName, task)
  }

  def postModuleBuildTask(String moduleName, Closure task) {
    postTasksBuilder.registerTask(moduleName, task)
  }

  private ModuleChunk chunkForModule(Module m) {
    buildChunks();
    mapping[m]
  }

  def makeModule(Module module) {
    return makeChunkWithDependencies(chunkForModule(module), false);
  }

  def makeModuleTests(Module module) {
    return makeChunkWithDependencies(chunkForModule(module), true);
  }

  private def makeChunkWithDependencies(ModuleChunk chunk, boolean includeTests) {
    Set<Module> dependencies = new HashSet<Module>()
    chunk.modules.each {
      collectModulesFromClasspath(it, getCompileClasspathKind(includeTests), dependencies)
    }
    buildChunks()
    chunks.each {
      if (!dependencies.intersect(it.modules).isEmpty()) {
        makeChunk(it, false)
        if (includeTests) {
          makeChunk(it, true)
        }
      }
    }
  }

  private def makeChunk(ModuleChunk chunk, boolean tests) {
    Map outputsMap = tests ? testOutputs : outputs
    String currentOutput = outputsMap[chunk]
    if (currentOutput != null) return currentOutput

    project.stage("Making${tests ? ' tests for' : ''} module ${chunk.name}")
    def dst = folderForChunkOutput(chunk, tests)
    outputsMap[chunk] = dst
    compile(chunk, dst, tests)

    return dst
  }

  String getModuleOutputFolder(Module module, boolean tests) {
    return folderForChunkOutput(chunkForModule(module), tests)
  }

  private String folderForChunkOutput(ModuleChunk chunk, boolean tests) {
    if (tests) {
      def customOut = chunk.customOutput
      if (customOut != null) return customOut
    }

    String targetFolder = project.targetFolder
    if (targetFolder != null) {
      def basePath = tests ? new File(targetFolder, "test").absolutePath : new File(targetFolder, "production").absolutePath
      return new File(basePath, chunk.name).absolutePath
    }
    else {
      Set<Module> modules = chunk.modules
      def module = modules.toList().first()
      if (modules.size() > 1) {
        project.warning("Modules $modules with cyclic dependencies will be compiled to output of $module")
      }
      return tests ? module.testOutputPath : module.outputPath
    }
  }

  private def compile(ModuleChunk chunk, String dst, boolean tests) {
    List sources = validatePaths(tests ? chunk.testRoots : chunk.sourceRoots)

    if (sources.isEmpty()) return

    if (dst == null) {
      project.error("${tests ? 'Test output' : 'Output'} path for module $chunk is not specified")
    }
    def ant = binding.ant
    ant.mkdir dir: dst

    def state = new ModuleBuildState
    (
            sourceRoots: sources,
            excludes: chunk.excludes,
            classpath: moduleClasspath(chunk, getCompileClasspathKind(tests)),
            targetFolder: dst,
            moduleDependenciesSourceRoots: transitiveModuleDependenciesSourcePaths(chunk, tests),
            tempRootsToDelete: []
    )

    if (!project.dryRun) {
      listeners*.onCompilationStarted(chunk)
      builders().each {
        listeners*.onModuleBuilderStarted(it, chunk)
        it.processModule(chunk, state)
        listeners*.onModuleBuilderFinished(it, chunk)
      }
      state.tempRootsToDelete.each {
        binding.ant.delete(dir: it)
      }
      listeners*.onCompilationFinished(chunk)
    }

    chunk.modules.each {
      project.exportProperty("module.${it.name}.output.${tests ? "test" : "main"}", dst)
    }
  }

  private ClasspathKind getCompileClasspathKind(boolean tests) {
    return tests ? ClasspathKind.TEST_COMPILE : ClasspathKind.PRODUCTION_COMPILE
  }

  List<String> moduleClasspath(ModuleChunk chunk, ClasspathKind kind) {
    Map<ModuleChunk, List<String>> map = cachedClasspaths[kind]
    if (map == null) {
      map = new HashMap()
      cachedClasspaths[kind] = map
    }

    if (map[chunk] != null) return map[chunk]

    Set<String> set = new LinkedHashSet()
    Set<Object> processed = new HashSet()

    collectPathTransitively(chunk, false, kind, set, processed)

    if (kind.isTestsIncluded()) {
      set.add(folderForChunkOutput(chunk, false))
    }

    map[chunk] = set.asList()
  }

  List<String> transitiveModuleDependenciesSourcePaths(ModuleChunk chunk, boolean tests) {
    Set<String> result = new LinkedHashSet<String>()
    collectPathTransitively(chunk, true, getCompileClasspathKind(tests), result, new HashSet<Object>())
    return result.asList()
  }

  List<String> moduleRuntimeClasspath(Module module, boolean test) {
    return chunkRuntimeClasspath(chunkForModule(module), test)
  }

  List<String> chunkRuntimeClasspath(ModuleChunk chunk, boolean test) {
    Set<String> set = new LinkedHashSet()
    set.addAll(moduleClasspath(chunk, test ? ClasspathKind.TEST_RUNTIME : ClasspathKind.PRODUCTION_RUNTIME))
    set.add(folderForChunkOutput(chunk, false))

    if (test) {
      set.add(folderForChunkOutput(chunk, true))
    }

    return set.asList()
  }

  private def collectModulesFromClasspath(Module module, ClasspathKind kind, Set<Module> result) {
    if (result.contains(module)) return
    result << module
    module.getClasspath(kind).each {
      if (it instanceof Module) {
        collectModulesFromClasspath(it, kind, result)
      }
    }
  }

  private def collectPathTransitively(Object chunkOrModule, boolean collectSources, ClasspathKind classpathKind, Set<String> set, Set<Object> processed) {
    if (processed.contains(chunkOrModule)) return
    processed << chunkOrModule
    
    chunkOrModule.getClasspath(classpathKind).each {
      if (it instanceof Module) {
        collectPathTransitively(it, collectSources, classpathKind, set, processed)
        if (collectSources) {
          set.addAll(it.sourceRoots)
          if (classpathKind.isTestsIncluded()) {
            set.addAll(it.testRoots)
          }
        }
      }
      if (!collectSources) {
        set.addAll(it.getClasspathRoots(classpathKind))
      }

    }
  }

  String moduleOutput(Module module) {
    return folderForChunkOutput(chunkForModule(module), false)
  }

  String moduleTestsOutput(Module module) {
    return folderForChunkOutput(chunkForModule(module), true)
  }

  List<String> validatePaths(List<String> list) {
    List<String> answer = new ArrayList<String>()
    for (path in list) {
      if (new File(path).exists()) {
        answer.add(path)
      }
      else {
        project.warning("'$path' does not exist!")
      }
    }

    answer
  }
}
