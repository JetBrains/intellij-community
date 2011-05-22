package org.jetbrains.jps.builders.javacApi

import com.intellij.ant.InstrumentationUtil
import javax.tools.JavaCompiler
import javax.tools.JavaCompiler.CompilationTask
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk
import org.jetbrains.jps.builders.JavaFileCollector

/**
 * @author nik
 */
class Java16ApiCompiler {
  private static instance
  private OptimizedFileManager fileManager
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

    if (customArgs != null) {
      options << customArgs
    }

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

    List<File> filesToCompile = []

    if (state.sourceFiles.size() > 0) {
      for (String src: state.sourceFiles) {
        if (src.endsWith(".java")) {
          filesToCompile << new File(src)
        }
      }
    }
    else {
      Set<File> excluded = state.excludes.collect { new File(it.toString()) }
      state.sourceRoots.each {
        JavaFileCollector.collectRecursively(new File(it.toString()), filesToCompile, excluded)
      }
    }

    if (filesToCompile.size() > 0) {
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [new File(state.targetFolder)])
      List<File> classpath = []
      List<File> bootclasspath = []
      StringBuffer cp = new StringBuffer()

      Sdk sdk = chunk.getSdk()

      if (sdk != null) {
        sdk.classpath.each { bootclasspath << new File(String.valueOf(it)) }

        fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootclasspath)
      }

      state.classpath.each {
        classpath << new File(String.valueOf(it))
        cp.append(String.valueOf(it))
        cp.append(File.pathSeparator)
      }

      cp.append(state.targetFolder)

      fileManager.setLocation(StandardLocation.CLASS_PATH, classpath)

      System.out.println("Chunk: " + chunk.toString() + " Classpath: " + cp.toString());

      fileManager.setProperties(state.callback, InstrumentationUtil.createClassLoader(cp.toString()))

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
