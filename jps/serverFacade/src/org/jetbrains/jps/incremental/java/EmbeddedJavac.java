package org.jetbrains.jps.incremental.java;

import org.jetbrains.jps.incremental.CompileContext;

import javax.tools.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/23/11
 */
public class EmbeddedJavac {
  private final JavaCompiler myCompiler;
  private final ExecutorService myTaskRunner;
  //private final SequentialTaskExecutor mySequentialTaskExecutor;
  private final List<ClassPostProcessor> myClassProcessors = new ArrayList<ClassPostProcessor>();
  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-d", "-classpath", "-cp", "-bootclasspath"
  ));

  public static interface DiagnosticOutputConsumer extends DiagnosticListener<JavaFileObject> {
    void outputLineAvailable(String line);
  }

  public static interface OutputFileConsumer {
    void save(OutputFileObject fileObject);
  }

  public static interface ClassPostProcessor {
    void process(CompileContext context, OutputFileObject out);
  }

  public EmbeddedJavac(ExecutorService taskRunner) {
    myTaskRunner = taskRunner;
    //mySequentialTaskExecutor = new SequentialTaskExecutor(taskRunner);
    myCompiler = ToolProvider.getSystemJavaCompiler();
  }

  public void addClassProcessor(ClassPostProcessor processor) {
    myClassProcessors.add(processor);
  }

  public boolean compile(Collection<String> options, final Collection<File> sources, Collection<File> classpath, Collection<File> platformClasspath, Map<File, Set<File>> outputDirToRoots, CompileContext compileContext, final DiagnosticOutputConsumer outConsumer, final OutputFileConsumer outputSink) {
    final FileManagerContext context = new FileManagerContext(compileContext, outConsumer, outputSink); // todo
    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }
    final JavacFileManager fileManager = new JavacFileManager(context);
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
    //todo setup file manager to support multiple outputs

    final LineOutputWriter out = new LineOutputWriter() {
      protected void lineAvailable(String line) {
        outConsumer.outputLineAvailable(line);
      }
    };

    try {
      final JavaCompiler.CompilationTask task = myCompiler.getTask(
        out, fileManager, outConsumer, filterOptionList(options), null, fileManager.toJavaFileObjects(sources)
      );
      return task.call();
    }
    finally {
      context.ensurePendingTasksCompleted();
      fileManager.cleanupResources();
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

  private class FileManagerContext implements JavacFileManager.Context {

    private final StandardJavaFileManager myStdManager;
    private final CompileContext myCompileContext;
    private final DiagnosticOutputConsumer myOutConsumer;
    private final OutputFileConsumer myOutputFileSink;
    private int myTasksInProgress = 0;
    private final Object myCounterLock = new Object();

    public FileManagerContext(CompileContext compileContext, DiagnosticOutputConsumer outConsumer, OutputFileConsumer sink) {
      myCompileContext = compileContext;
      myOutConsumer = outConsumer;
      myOutputFileSink = sink != null? sink : new OutputFileConsumer() {
        public void save(OutputFileObject fileObject) {
          throw new RuntimeException("Output sink for compiler was not specified");
        }
      };
      myStdManager = myCompiler.getStandardFileManager(outConsumer, Locale.US, null);
    }

    public StandardJavaFileManager getStandardFileManager() {
      return myStdManager;
    }

    public void reportMessage(final Diagnostic.Kind kind, String message) {
      myOutConsumer.report(new PlainMessageDiagnostic(kind, message));
    }

    public void consumeOutputFile(final OutputFileObject cls) {
      incTaskCount();
      myTaskRunner.submit(new Runnable() {
        public void run() {
          try {
            runProcessors(cls);
          }
          finally {
            //mySequentialTaskExecutor.submit(new Runnable() {
            //  public void run() {
            try {
              myOutputFileSink.save(cls);
            }
            finally {
              decTaskCount();
            }
            //}
            //});
          }
        }
      });
    }

    private void decTaskCount() {
      synchronized (myCounterLock) {
        myTasksInProgress = Math.max(0, myTasksInProgress - 1);
        if (myTasksInProgress == 0) {
          myCounterLock.notifyAll();
        }
      }
    }

    private void incTaskCount() {
      synchronized (myCounterLock) {
        myTasksInProgress++;
      }
    }

    public void ensurePendingTasksCompleted() {
      synchronized (myCounterLock) {
        while (myTasksInProgress > 0) {
          try {
            myCounterLock.wait();
          }
          catch (InterruptedException ignored) {
          }
        }
      }
    }

    private void runProcessors(OutputFileObject cls) {
      for (ClassPostProcessor processor : myClassProcessors) {
        processor.process(myCompileContext, cls);
      }
    }
  }
}
