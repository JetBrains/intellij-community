package org.jetbrains.jps.javac;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.server.ClasspathBootstrap;

import javax.tools.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/21/12
 */
public class JavacMain {
  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-d", "-classpath", "-cp", "-bootclasspath"
  ));

  public static boolean compile(Collection<String> options,
                                final Collection<File> sources,
                                Collection<File> classpath,
                                Collection<File> platformClasspath,
                                Collection<File> sourcePath,
                                Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer outConsumer,
                                final OutputFileConsumer outputSink) {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }
    final JavacFileManager fileManager = new JavacFileManager(new ContextImpl(compiler, outConsumer, outputSink));

    fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator()); // this will clear cached stuff
    fileManager.handleOption("-extdirs", Collections.singleton("").iterator()); // this will clear cached stuff

    fileManager.setOutputDirectories(outputDirToRoots);
    if (!classpath.isEmpty()) {
      if (!fileManager.setLocation(StandardLocation.CLASS_PATH, classpath)) {
        return false;
      }
    }
    if (!platformClasspath.isEmpty()) {
      if (!fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, platformClasspath)) {
        return false;
      }
    }
    if (!sourcePath.isEmpty()) {
      if (!fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePath)) {
        return false;
      }
    }

    //noinspection IOResourceOpenedButNotSafelyClosed
    final LineOutputWriter out = new LineOutputWriter() {
      protected void lineAvailable(String line) {
        outConsumer.outputLineAvailable(line);
      }
    };

    try {
      final JavaCompiler.CompilationTask task = compiler.getTask(
        out, fileManager, outConsumer, filterOptionList(options), null, fileManager.toJavaFileObjects(sources)
      );
      return task.call();
    }
    finally {
      fileManager.close();
    }
  }

  private static Collection<String> filterOptionList(final Collection<String> options) {
    if (options.isEmpty()) {
      return options;
    }
    final List<String> result = new ArrayList<String>();
    boolean skip = false;
    for (String option : options) {
      if (FILTERED_OPTIONS.contains(option)) {
        skip = true;
        continue;
      }
      if (!skip) {
        result.add(option);
      }
      skip = false;
    }
    return result;
  }

  private static class ContextImpl implements JavacFileManager.Context {
    private final StandardJavaFileManager myStdManager;
    private final DiagnosticOutputConsumer myOutConsumer;
    private final OutputFileConsumer myOutputFileSink;

    public ContextImpl(@NotNull JavaCompiler compiler, @NotNull DiagnosticOutputConsumer outConsumer, @NotNull OutputFileConsumer sink) {
      myOutConsumer = outConsumer;
      myOutputFileSink = sink;
      StandardJavaFileManager stdManager = null;
      final Class<StandardJavaFileManager> optimizedManagerClass = ClasspathBootstrap.getOptimizedFileManagerClass();
      if (optimizedManagerClass != null) {
        try {
          stdManager = optimizedManagerClass.newInstance();
        }
        catch (Throwable e) {
          if (SystemInfo.isWindows) {
            System.err.println("Failed to load JPS optimized file manager for javac: " + e.getMessage());
          }
        }
      }
      if (stdManager != null) {
        myStdManager = stdManager;
      }
      else {
        myStdManager = compiler.getStandardFileManager(outConsumer, Locale.US, null);
      }
    }

    public StandardJavaFileManager getStandardFileManager() {
      return myStdManager;
    }

    public void reportMessage(final Diagnostic.Kind kind, String message) {
      myOutConsumer.report(new PlainMessageDiagnostic(kind, message));
    }

    public void consumeOutputFile(@NotNull final OutputFileObject cls) {
      myOutputFileSink.save(cls);
    }
  }
}
