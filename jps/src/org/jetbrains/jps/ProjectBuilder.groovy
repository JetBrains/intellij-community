package org.jetbrains.jps

import org.codehaus.gant.GantBinding

/**
 * @author max
 */
class ProjectBuilder {
  final Map<Module, String> outputs = [:]
  final Map<Module, String> testOutputs = [:]
  final Map<Module, List<String>> cp = [:]
  final Map<Module, List<String>> testCp = [:]
  final Project project;
  final GantBinding binding;

  def ProjectBuilder(GantBinding binding, Project project) {
    this.project = project
    this.binding = binding
  }

  def makeModule(Module module) {
    String currentOutput = outputs[module]
    if (currentOutput != null) return currentOutput

    def dst = folderForModuleOutput(module, classesDir(binding.project))
    outputs[module] = dst
    compile(module, dst, false)

    return dst;
  }

  def makeModuleTests(Module module) {
    String currentOutput = testOutputs[module]
    if (currentOutput != null) return currentOutput

    def dst = folderForModuleOutput(module, testClassesDir(binding.project))
    testOutputs[module] = dst
    compile(module, dst, true)

    return dst;
  }

  private String classesDir(Project project) {
    return new File(project.targetFolder, "classes").absolutePath
  }

  private String testClassesDir(Project project) {
    return new File(project.targetFolder, "testClasses").absolutePath
  }

  def folderForModuleOutput(Module module, String basePath) {
    def customOut = module.props["destDir"]
    if (customOut != null) return customOut
    return new File(basePath, module.name).absolutePath
  }

  def compile(Module module, String dst, boolean tests) {
    def ant = binding.ant
    ant.mkdir dir: dst

    List sources = tests ? module.testRoots : module.sourceRoots

    def state = new ModuleBuildState
    (
            sourceRoots: sources,
            classpath: moduleClasspath(module, tests),
            targetFolder: dst,
            tempRootsToDelete: []
    )

    project.builders().each {
      it.processModule(module, state)
    }

    state.tempRootsToDelete.each {
      binding.ant.delete(dir: it)
    }
    
    binding.ant.project.setProperty("module.${module.name}.output.${tests ? "test" : "main"}", dst)
  }

  List<String> moduleClasspath(Module module, boolean test) {
    Map<Module, List<String>> map = test ? testCp : cp

    if (map[module] != null) return map[module]

    Set<String> set = new LinkedHashSet()

    module.classpath.flatten().each {
      def resolved = project.resolve(it)
      def roots = resolved.getClasspathRoots(test)
      set.addAll(roots)
    }

    if (test) {
      set.add(moduleOutput(module))
    }

    map[module] = set.asList()
  }

  String moduleOutput(Module module) {
    if (outputs[module] == null) binding.project.error("Module ${module.name} haven't yet been built");
    return outputs[module]
  }

  String moduleTestsOutput(Module module) {
    if (testOutputs[module] == null) binding.project.error("Tests for module ${module.name} haven't yet been built");
    testOutputs[module]
  }
}
