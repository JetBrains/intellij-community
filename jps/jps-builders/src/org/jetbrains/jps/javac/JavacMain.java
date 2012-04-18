package org.jetbrains.jps.javac;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.server.ClasspathBootstrap;

import javax.tools.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/21/12
 */
public class JavacMain {
  private static final boolean IS_VM_6_VERSION = System.getProperty("java.version", "1.6").contains("1.6");
  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-d", "-classpath", "-cp", "-bootclasspath"
  ));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-verbose", "-proc:none", "-implicit:class", "-implicit:none"
  ));

  public static boolean compile(Collection<String> options,
                                final Collection<File> sources,
                                Collection<File> classpath,
                                Collection<File> platformClasspath,
                                Collection<File> sourcePath,
                                Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer outConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus) {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }
    final JavacFileManager fileManager = new JavacFileManager(new ContextImpl(compiler, outConsumer, outputSink, canceledStatus));

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
      final Collection<String> _options = prepareOptions(options);
      final JavaCompiler.CompilationTask task = compiler.getTask(
        out, fileManager, outConsumer, _options, null, fileManager.toJavaFileObjects(sources)
      );

      if (!IS_VM_6_VERSION) {
        // Do not add the processor for JDK 1.6 because of the bugs in javac
        // The processor's presence may lead to NPE and resolve bugs in compiler
        final JavacASTAnalyser analyzer = new JavacASTAnalyser(outConsumer, shouldSuppressAnnotationProcessing(options));
        task.setProcessors(Collections.singleton(analyzer));
      }
      return task.call();
    }
    catch(IllegalArgumentException e) {
      outConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    finally {
      fileManager.close();
    }
    return false;
  }

  private static boolean shouldSuppressAnnotationProcessing(final Collection<String> options) {
    for (String option : options) {
      if ("-proc:none".equals(option)) {
        return true;
      }
    }
    return false;
  }

  private static Collection<String> prepareOptions(final Collection<String> options) {
    final List<String> result = new ArrayList<String>();
    result.add("-implicit:class");
    boolean skip = false;
    for (String option : options) {
      if (FILTERED_OPTIONS.contains(option)) {
        skip = true;
        continue;
      }
      if (!skip) {
        if (!FILTERED_SINGLE_OPTIONS.contains(option)) {
          result.add(option);
        }
      }
      skip = false;
    }
    return result;
  }

  private static class ContextImpl implements JavacFileManager.Context {
    private final StandardJavaFileManager myStdManager;
    private final DiagnosticOutputConsumer myOutConsumer;
    private final OutputFileConsumer myOutputFileSink;
    private final CanceledStatus myCanceledStatus;

    public ContextImpl(@NotNull JavaCompiler compiler,
                       @NotNull DiagnosticOutputConsumer outConsumer,
                       @NotNull OutputFileConsumer sink,
                       CanceledStatus canceledStatus) {
      myOutConsumer = outConsumer;
      myOutputFileSink = sink;
      myCanceledStatus = canceledStatus;
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

    public boolean isCanceled() {
      return myCanceledStatus.isCanceled();
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
