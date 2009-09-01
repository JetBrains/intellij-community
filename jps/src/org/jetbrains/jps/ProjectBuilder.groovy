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

  public def clean() {
    outputs.clear()
    testOutputs.clear()
    cp.clear()
    testCp.clear()
  }

  public def buildAll() {
    buildChunks()
    chunks.each {
      makeChunk(it)
      makeChunkTests(it)
    }
  }

  public def buildProduction() {
    buildChunks()
    chunks.each {
      makeChunk(it)
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

    project.stage("Making module ${chunk.name}")
    def dst = folderForChunkOutput(chunk, classesDir(binding.project), false)
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
    def dst = folderForChunkOutput(chunk, testClassesDir(binding.project), true)
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

  private String folderForChunkOutput(ModuleChunk chunk, String basePath, boolean tests) {
    if (tests) {
      def customOut = chunk.customOutput
      if (customOut != null) return customOut
    }

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
            classpath: moduleCompileClasspath(chunk, tests),
            targetFolder: dst,
            tempRootsToDelete: []
    )

    if (!project.dryRun) {
      project.builders().each {
        it.processModule(chunk, state)
      }
      state.tempRootsToDelete.each {
        binding.ant.delete(dir: it)
      }
    }

    chunk.modules.each {
      project.exportProperty("module.${it.name}.output.${tests ? "test" : "main"}", dst)
    }
  }

  List<String> moduleCompileClasspath(ModuleChunk chunk, boolean test) {
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

  List<String> moduleRuntimeClasspath(Module module, boolean test) {
    return chunkRuntimeClasspath(chunkForModule(module), test)
  }

  List<String> chunkRuntimeClasspath(ModuleChunk chunk, boolean test) {
    Set<String> set = new LinkedHashSet()
    set.addAll(moduleCompileClasspath(chunk, test))
    set.add(chunkOutput(chunk))

    return set.asList()
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
    String currentOut = outputs[chunk]
    if (currentOut == null) binding.project.error("Module ${chunk.name} haven't yet been built");

    outputs[chunk] = zipIfNecessary(currentOut, chunk)

    return outputs[chunk]
  }

  private String zipIfNecessary(String currentOut, ModuleChunk chunk) {
    return currentOut

/*
    def currentOutAsFile = new File(currentOut)

    if (currentOutAsFile.isDirectory() && currentOutAsFile.list().length > 0) {
      def zipFolder = new File(new File(currentOut).getParentFile(), "zips")
      zipFolder.mkdirs();

      File zipFile = new File(zipFolder, "${currentOutAsFile.getName()}.zip")
      binding.ant.zip(destfile: zipFile.getAbsolutePath(), basedir: currentOut, level: "0")
      zipFile.getAbsolutePath()
    }
    else {
      currentOut
    }
*/
  }

  private String chunkTestOutput(ModuleChunk chunk) {
    if (testOutputs[chunk] == null) binding.project.error("Tests for module ${chunk.name} haven't yet been built");
    testOutputs[chunk] = zipIfNecessary(testOutputs[chunk], chunk)
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
