package org.jetbrains.jps.builders.javacApi

import javax.tools.JavaCompiler.CompilationTask
import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk
import org.jetbrains.jps.builders.JavaFileCollector
import javax.tools.*

 /**
 * @author nik
 */
class Java16ApiCompiler {
  private static instance
  private StandardJavaFileManager fileManager
  private JavaCompiler compiler

  static Java16ApiCompiler getInstance() {
    if (instance == null) {
      instance = new Java16ApiCompiler()
    }
    return instance
  }

  def Java16ApiCompiler() {
    compiler = ToolProvider.getSystemJavaCompiler()
    fileManager = new OptimizedFileManager();
  }

  def compile(ModuleChunk chunk, ModuleBuildState state, String sourceLevel, String targetLevel, String customArgs) {
    List<String> options = []
    if (sourceLevel != null) {
      options << "-source"
      options << sourceLevel
    }
    if (targetLevel != null) {
      options << "-target"
      options << targetLevel
    }
    options << "-g"
    options << "-nowarn"
//    options << "-verbose"

    List<File> filesToCompile = []
    Set<File> excluded = state.excludes.collect { new File(it.toString()) }
    state.sourceRoots.each {
      JavaFileCollector.collectRecursively(new File(it.toString()), filesToCompile, excluded)
    }

    if (filesToCompile.size() > 0) {
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [new File(state.targetFolder)])
      List<File> classpath = []
      Sdk sdk = chunk.getSdk()
      if (sdk != null) {
        sdk.classpath.each { classpath << new File(String.valueOf(it)) }
      }
      state.classpath.each { classpath << new File(String.valueOf(it)) }
      fileManager.setLocation(StandardLocation.CLASS_PATH, classpath)
      Iterable<? extends JavaFileObject> toCompile = fileManager.getJavaFileObjectsFromFiles(filesToCompile)
      Project project = chunk.project
      StringWriter out = new StringWriter()
      CompilationTask task = compiler.getTask(new PrintWriter(out), fileManager, null, options, null, toCompile)
      if (!task.call()) {
        project.builder.buildInfoPrinter.printCompilationErrors(project, "javac", out.toString())
        project.error("Compilation failed")
      }
      else {
        System.out.println(out.toString());
      }
      project.builder.listeners*.onJavaFilesCompiled(chunk, filesToCompile.size())
      fileManager.flush()
    }
    else {
      chunk.project.info("No java source files found in '${chunk.name}', skipping compilation")
    }
  }

}
