package org.jetbrains.jps.incremental.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;

import javax.tools.*;
import java.io.*;
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

  public static interface OutputConsumer extends DiagnosticListener<JavaFileObject> {
    void outputLineAvailable(String line);
    void classFileWritten(OutputFileObject output);
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

  public boolean compile(final Collection<String> options, final Collection<File> sources, Collection<File> classpath, Collection<File> bootclasspath, File outputDir, final CompileContext compileContext, final OutputConsumer outConsumer) {
    return compile(options, sources, classpath, bootclasspath, Collections.singletonMap(outputDir, Collections.<File>emptySet()), compileContext, outConsumer);
  }

  public boolean compile(Collection<String> options, final Collection<File> sources, Collection<File> classpath, Collection<File> platformClasspath, Map<File, Set<File>> outputDirToRoots, CompileContext compileContext, final OutputConsumer outConsumer) {
    final FileManagerContext context = new FileManagerContext(compileContext, outConsumer);
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
    private final OutputConsumer myOutConsumer;
    private int myTasksInProgress = 0;
    private final Object myCounterLock = new Object();

    public FileManagerContext(CompileContext compileContext, OutputConsumer outConsumer) {
      myCompileContext = compileContext;
      myOutConsumer = outConsumer;
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
              save(cls);
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

    private void save(OutputFileObject cls) {
      try {
        final File file = cls.getFile();
        final OutputFileObject.Content content = cls.getContent();
        if (content != null) {
          writeToFile(file, content.getBuffer(), content.getOffset(), content.getLength(), false);
          myOutConsumer.classFileWritten(cls);
        }
        else {
          myOutConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, "Missing content for file " + file));
        }
      }
      catch (IOException e) {
        myOutConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
      }
    }
  }

  private static void writeToFile(@NotNull File file, @NotNull byte[] text, final int off, final int len, boolean append) throws IOException {
    createParentDirs(file);
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(file, append));
    try {
      stream.write(text, off, len);
    }
    finally {
      stream.close();
    }
  }

  private static boolean createParentDirs(@NotNull File file) {
    if (!file.exists()) {
      String parentDirPath = file.getParent();
      if (parentDirPath != null) {
        final File parentFile = new File(parentDirPath);
        return parentFile.exists() && parentFile.isDirectory() || parentFile.mkdirs();
      }
    }
    return true;
  }
}
