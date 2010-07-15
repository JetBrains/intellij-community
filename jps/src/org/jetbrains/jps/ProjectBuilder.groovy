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
  final Map<ModuleChunk, List<String>> cp = [:]
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
        module.classpath.each {entry ->
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
    cp.clear()
    testCp.clear()
  }

  public def buildAll() {
    listeners*.onBuildStarted(project)
    buildChunks()
    chunks.each {
      makeChunk(it)
      makeChunkTests(it)
    }
    listeners*.onBuildFinished(project)
  }

  public def buildProduction() {
    listeners*.onBuildStarted(project)
    buildChunks()
    chunks.each {
      makeChunk(it)
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
    return makeChunk(chunkForModule(module));
  }

  private def makeChunk(ModuleChunk chunk) {
    String currentOutput = outputs[chunk]
    if (currentOutput != null) return currentOutput

    project.stage("Making module ${chunk.name}")
    def dst = folderForChunkOutput(chunk, false)
    outputs[chunk] = dst
    compile(chunk, dst, false)

    return dst
  }

  def makeModuleTests(Module module) {
    return makeChunkTests(chunkForModule(module));
  }

  private def makeChunkTests(ModuleChunk chunk) {
    String currentOutput = testOutputs[chunk]
    if (currentOutput != null) return currentOutput

    project.stage("Making tests for ${chunk.name}")
    def dst = folderForChunkOutput(chunk, true)
    testOutputs[chunk] = dst
    compile(chunk, dst, true)

    return dst
  }

  private String classesDir(Project project) {
    return new File(project.targetFolder, "production").absolutePath
  }

  private String testClassesDir(Project project) {
    return new File(project.targetFolder, "test").absolutePath
  }

  String getModuleOutputFolder(Module module, boolean tests) {
    return folderForChunkOutput(chunkForModule(module), tests)
  }

  private String folderForChunkOutput(ModuleChunk chunk, boolean tests) {
    if (tests) {
      def customOut = chunk.customOutput
      if (customOut != null) return customOut
    }
    def basePath = tests ? testClassesDir(project) : classesDir(project)
    return new File(basePath, chunk.name).absolutePath
  }

  def compile(ModuleChunk chunk, String dst, boolean tests) {
    List sources = validatePaths(tests ? chunk.testRoots : chunk.sourceRoots)

    if (sources.isEmpty()) return

    def ant = binding.ant
    ant.mkdir dir: dst

    def state = new ModuleBuildState
    (
            sourceRoots: sources,
            excludes: chunk.excludes,
            classpath: moduleCompileClasspath(chunk, tests, true),
            targetFolder: dst,
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

  List<String> moduleCompileClasspath(ModuleChunk chunk, boolean test, boolean provided) {
    Map<ModuleChunk, List<String>> map = test ? testCp : cp

    if (map[chunk] != null) return map[chunk]

    Set<String> set = new LinkedHashSet()
    Set<Object> processed = new HashSet()

    transitiveClasspath(chunk, test, provided, set, processed)

    if (test) {
      set.add(chunkOutput(chunk))
    }

    map[chunk] = set.asList()
  }

  List<String> moduleRuntimeClasspath(Module module, boolean test) {
    return chunkRuntimeClasspath(chunkForModule(module), test)
  }

  List<String> chunkRuntimeClasspath(ModuleChunk chunk, boolean test) {
    Set<String> set = new LinkedHashSet()
    set.addAll(moduleCompileClasspath(chunk, test, false))
    set.add(chunkOutput(chunk))

    if (test) {
      set.add(chunkTestOutput(chunk))
    }

    return set.asList()
  }

  private def transitiveClasspath(Object chunkOrModule, boolean test, boolean provided, Set<String> set, Set<Object> processed) {
    if (processed.contains(chunkOrModule)) return
    processed << chunkOrModule
    
    chunkOrModule.getClasspath(test, provided).each {
      if (it instanceof Module) {
        transitiveClasspath(it, test, provided, set, processed)
      }
      set.addAll(it.getClasspathRoots(test))
    }
  }

  String moduleOutput(Module module) {
    return chunkOutput(chunkForModule(module))
  }

  String moduleTestsOutput(Module module) {
    chunkTestOutput(chunkForModule(module))
  }

  private def chunkOutput(ModuleChunk chunk) {
    if (outputs[chunk] == null) {
      project.info("Dependency module ${chunk.name} haven't yet been built, now building it");
      makeChunk(chunk)
    }
    return outputs[chunk]
  }

  private String chunkTestOutput(ModuleChunk chunk) {
    if (testOutputs[chunk] == null) {
      binding.project.warning("Dependency module ${chunk.name} tests haven't yet been built, now building it");
      makeChunkTests(chunk)
    }

    testOutputs[chunk]
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
