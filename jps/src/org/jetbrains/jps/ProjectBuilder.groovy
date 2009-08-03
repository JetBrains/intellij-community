package org.jetbrains.jps

import org.codehaus.gant.GantBinding

/**
 * @author max
 */
class ProjectBuilder {
  final Map<Module, ModuleChunk> mapping = [:]
  final Map<ModuleChunk, String> outputs = [:]
  final Map<ModuleChunk, String> testOutputs = [:]
  final Map<ModuleChunk, List<String>> cp = [:]
  final Map<ModuleChunk, List<String>> testCp = [:]
  
  final Project project;
  final GantBinding binding;

  List<ModuleChunk> chunks = null

  def ProjectBuilder(GantBinding binding, Project project) {
    this.project = project
    this.binding = binding
  }

  private def buildChunks() {
    if (chunks == null) {
      chunks = new ChunkDAG().build(project, project.modules.values())
      chunks.each { ModuleChunk chunk ->
        chunk.modules.each {
          mapping[it] = chunk
        }
      }
      project.info("Total ${chunks.size()} chunks detected")
    }
  }

  public def buildAll() {
    buildChunks()
    chunks.each {
      makeChunk(it)
      makeChunkTests(it)
    }
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

    project.info("Making module ${chunk.name}")
    def dst = folderForChunkOutput(chunk, classesDir(binding.project))
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

    project.info("Making tests for ${chunk.name}")
    def dst = folderForChunkOutput(chunk, testClassesDir(binding.project))
    testOutputs[chunk] = dst
    compile(chunk, dst, true)

    return dst
  }

  private String classesDir(Project project) {
    return new File(project.targetFolder, "classes").absolutePath
  }

  private String testClassesDir(Project project) {
    return new File(project.targetFolder, "testClasses").absolutePath
  }

  private String folderForChunkOutput(ModuleChunk chunk, String basePath) {
    def customOut = chunk.customOutput
    if (customOut != null) return customOut
    return new File(basePath, chunk.name).absolutePath
  }

  def compile(ModuleChunk chunk, String dst, boolean tests) {
    def ant = binding.ant
    ant.mkdir dir: dst

    List sources = validatePaths(tests ? chunk.testRoots : chunk.sourceRoots)

    def state = new ModuleBuildState
    (
            sourceRoots: sources,
            excludes: chunk.excludes,
            classpath: moduleClasspath(chunk, tests),
            targetFolder: dst,
            tempRootsToDelete: []
    )

    state.print()

    project.builders().each {
      it.processModule(chunk, state)
    }

    state.tempRootsToDelete.each {
      binding.ant.delete(dir: it)
    }
    
    binding.ant.project.setProperty("module.${chunk.name}.output.${tests ? "test" : "main"}", dst)
  }

  List<String> moduleClasspath(ModuleChunk chunk, boolean test) {
    Map<ModuleChunk, List<String>> map = test ? testCp : cp

    if (map[chunk] != null) return map[chunk]

    Set<String> set = new LinkedHashSet()
    Set<Object> processed = new HashSet()

    transitiveClasspath(chunk, test, set, processed)

    if (test) {
      set.add(chunkOutput(chunk))
    }

    map[chunk] = set.asList()
  }

  private def transitiveClasspath(Object chunkOrModule, boolean test, Set<String> set, Set<Object> processed) {
    if (processed.contains(chunkOrModule)) return
    processed << chunkOrModule
    
    chunkOrModule.classpath.each {
      if (it instanceof Module) {
        transitiveClasspath(it, test, set, processed)
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
    if (outputs[chunk] == null) binding.project.error("Module ${chunk.name} haven't yet been built");
    return outputs[chunk]
  }

  private String chunkTestOutput(ModuleChunk chunk) {
    if (testOutputs[chunk] == null) binding.project.error("Tests for module ${chunk.name} haven't yet been built");
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
