package org.jetbrains.jps.builders.javacApi

import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectBuilder
import org.jetbrains.jps.Sdk
import org.jetbrains.jps.builders.JavaFileCollector

import javax.tools.JavaCompiler
import javax.tools.JavaCompiler.CompilationTask
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

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

    def compile(ModuleChunk chunk, ProjectBuilder projectBuilder, ModuleBuildState state, String sourceLevel, String targetLevel, String customArgs) {
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

        if (state.incremental) {
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
            StringBuilder cp = new StringBuilder()

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
            fileManager.setProperties(state.callback, toURLs(cp.toString()))

            Iterable<? extends JavaFileObject> toCompile = fileManager.getJavaFileObjectsFromFiles(filesToCompile)
            StringWriter out = new StringWriter()
            CompilationTask task = compiler.getTask(new PrintWriter(out), fileManager, null, options, null, toCompile)

            if (!task.call()) {
                projectBuilder.buildInfoPrinter.printCompilationErrors(projectBuilder, "javac", out.toString())
                projectBuilder.error("Compilation failed")
            }
            else {
                System.out.println(out.toString());
            }
            projectBuilder.listeners*.onJavaFilesCompiled(chunk, filesToCompile.size())
        }
        else {
            projectBuilder.info("No java source files found in '${chunk.name}', skipping compilation")
        }
    }

  private URL[] toURLs(final String classPath) {
    final List urls = new ArrayList();
    for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens();) {
      final String s = tokenizer.nextToken();
      urls.add(new File(s).toURL());
    }
    return (URL[])urls.toArray(new URL[urls.size()]);
  }

}
