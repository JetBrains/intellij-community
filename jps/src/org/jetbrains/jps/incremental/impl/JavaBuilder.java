package org.jetbrains.jps.incremental.impl;

import org.jetbrains.jps.ClasspathKind;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Sdk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends Builder{
  private static final String JAVA_EXTENSION = ".java";

  private static final FileFilter JAVA_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getName().endsWith(JAVA_EXTENSION);
    }
  };
  private static final String JAVAC_COMPILER_NAME = "javac";
  private final EmbeddedJavac myJavacCompiler;

  public JavaBuilder(ExecutorService tasksExecutor) {
    myJavacCompiler = new EmbeddedJavac(tasksExecutor);
  }

  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    final List<File> files = new ArrayList<File>();
    context.processFiles(chunk, new FilesCollector(files, JAVA_SOURCES_FILTER));
    return compile(context, chunk, files);
  }

  private ExitCode compile(final CompileContext context, ModuleChunk chunk, List<File> files) throws ProjectBuildException {
    if (files.isEmpty()) {
      return ExitCode.FINISHED;
    }

    final ProjectPaths paths = new ProjectPaths(context.getScope().getProject());

    final Collection<File> classpath = paths.getCompilationClasspath(chunk, context.isCompilingTests(), true);
    final Collection<File> platformPaths = paths.getBootstrapCompilationClasspath(chunk, context.isCompilingTests(), true);
    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk);

    final int ERROR = 0, WARNING = 1;
    final int[] statistics = new int[] {0, 0};

    final boolean compilationOk = myJavacCompiler.compile(options, files, classpath, platformPaths, outs, new EmbeddedJavac.OutputConsumer() {
      public void outputLineAvailable(String line) {
        context.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, line));
      }

      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        context.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, diagnostic.getKind().name() + "/" + diagnostic.getMessage(Locale.US)));
        switch (diagnostic.getKind()) {
          case ERROR:
            statistics[ERROR]++;
            break;
          case MANDATORY_WARNING:
          case WARNING:
            statistics[WARNING]++;
            break;
        }
      }
    });

    if (!compilationOk || statistics[ERROR] > 0) {
      throw new ProjectBuildException("Compilation failed: errors: " + statistics[ERROR] + "; warnings: " + statistics[WARNING]);
    }

    return ExitCode.FINISHED;
  }

  public String getDescription() {
    return "Java Builder";
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk) {
    return Collections.emptyList();// todo
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new HashMap<File, Set<File>>();
    final boolean compilingTests = context.isCompilingTests();
    for (Module module : chunk.getModules()) {
      final String outputPath;
      final Collection<String> srcPaths;
      if (compilingTests) {
        outputPath = module.getTestOutputPath();
        srcPaths = (Collection<String>)module.getTestRoots();
      }
      else {
        outputPath = module.getOutputPath();
        srcPaths = (Collection<String>)module.getSourceRoots();
      }
      final Set<File> roots = new HashSet<File>();
      for (String path : srcPaths) {
        roots.add(new File(path));
      }
      map.put(new File(outputPath), roots);
    }
    return map;
  }
}
