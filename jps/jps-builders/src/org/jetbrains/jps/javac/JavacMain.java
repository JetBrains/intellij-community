package org.jetbrains.jps.javac;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/21/12
 */
public class JavacMain {
  private static final boolean IS_VM_6_VERSION = System.getProperty("java.version", "1.6").contains("1.6");
  //private static final boolean ECLIPSE_COMPILER_SINGLE_THREADED_MODE = Boolean.parseBoolean(System.getProperty("jdt.compiler.useSingleThread", "false"));
  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-d", "-classpath", "-cp", "-bootclasspath"
  ));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    /*javac options*/  "-verbose", "-proc:only", "-implicit:class", "-implicit:none",
    /*eclipse options*/"-noExit"
  ));

  public static boolean compile(Collection<String> options,
                                final Collection<File> sources,
                                Collection<File> classpath,
                                Collection<File> platformClasspath,
                                Collection<File> sourcePath,
                                Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer outConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus, boolean useEclipseCompiler) {
    JavaCompiler compiler = null;
    if (useEclipseCompiler) {
      for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) {
        compiler = javaCompiler;
        break;
      }
      if (compiler == null) {
        final String message = "Eclipse Batch Compiler was not found in classpath";
        outConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
        return false;
      }
    }

    final boolean nowUsingJavac;
    if (compiler == null) {
      compiler = ToolProvider.getSystemJavaCompiler();
      nowUsingJavac = true;
    }
    else {
      nowUsingJavac = false;
    }

    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }
    final JavacFileManager fileManager = new JavacFileManager(new ContextImpl(compiler, outConsumer, outputSink, canceledStatus, nowUsingJavac));

    fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator()); // this will clear cached stuff
    fileManager.handleOption("-extdirs", Collections.singleton("").iterator()); // this will clear cached stuff

    try {
      fileManager.setOutputDirectories(outputDirToRoots);
    }
    catch (IOException e) {
      fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
      return false;
    }

    if (!classpath.isEmpty()) {
      try {
        fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
        if (!nowUsingJavac && !isOptionSet(options, "-processorpath")) {
          // for non-javac file manager ensure annotation processor path defaults to classpath
          fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, classpath);
        }
      }
      catch (IOException e) {
        fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
        return false;
      }
    }
    if (!platformClasspath.isEmpty()) {
      try {
        fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, platformClasspath);
      }
      catch (IOException e) {
        fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
        return false;
      }
    }
    try {
    // ensure the source path is set;
    // otherwise, if not set, javac attempts to search both classes and sources in classpath;
    // so if some classpath jars contain sources, it will attempt to compile them
      fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePath);
    }
    catch (IOException e) {
      fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
      return false;
    }

    //noinspection IOResourceOpenedButNotSafelyClosed
    final LineOutputWriter out = new LineOutputWriter() {
      protected void lineAvailable(String line) {
        if (nowUsingJavac) {
          outConsumer.outputLineAvailable(line);
        }
        else {
          // todo: filter too verbose eclipse output?
        }
      }
    };

    try {
      final Collection<String> _options = prepareOptions(options, nowUsingJavac);
      final JavaCompiler.CompilationTask task = compiler.getTask(
        out, fileManager, outConsumer, _options, null, fileManager.toJavaFileObjects(sources)
      );

      //if (!IS_VM_6_VERSION) { //todo!
      //  // Do not add the processor for JDK 1.6 because of the bugs in javac
      //  // The processor's presence may lead to NPE and resolve bugs in compiler
      //  final JavacASTAnalyser analyzer = new JavacASTAnalyser(outConsumer, !annotationProcessingEnabled);
      //  task.setProcessors(Collections.singleton(analyzer));
      //}
      return task.call();
    }
    catch(IllegalArgumentException e) {
      outConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    catch (CompilationCanceledException ignored) {
      outConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.OTHER, "Compilation was canceled"));
    }
    finally {
      fileManager.close();
    }
    return false;
  }

  private static boolean isAnnotationProcessingEnabled(final Collection<String> options) {
    for (String option : options) {
      if ("-proc:none".equals(option)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isOptionSet(final Collection<String> options, String option) {
    for (String opt : options) {
      if (option.equals(opt)) {
        return true;
      }
    }
    return false;
  }

  private static Collection<String> prepareOptions(final Collection<String> options, boolean usingJavac) {
    final List<String> result = new ArrayList<String>();
    if (usingJavac) {
      result.add("-implicit:class"); // the option supported by javac only
    }
    else { // is Eclipse
      result.add("-noExit");
    }
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
                       CanceledStatus canceledStatus, boolean canUseOptimizedmanager) {
      myOutConsumer = outConsumer;
      myOutputFileSink = sink;
      myCanceledStatus = canceledStatus;
      StandardJavaFileManager stdManager = null;
      if (canUseOptimizedmanager) {
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
