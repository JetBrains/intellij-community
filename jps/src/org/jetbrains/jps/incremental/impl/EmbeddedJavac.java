package org.jetbrains.jps.incremental.impl;

import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/23/11
 */
public class EmbeddedJavac {
  private final JavaCompiler myCompiler;
  private final ExecutorService myTaskRunner;
  private final SequentialTaskExecutor mySequentialTaskExecutor;
  private final List<ClassPostProcessor> myClassProcessors = new ArrayList<ClassPostProcessor>();

  public static interface OutputConsumer extends DiagnosticListener<JavaFileObject> {
    void outputLineAvailable(String line);
  }

  public static interface ClassPostProcessor {
    void process(OutputFileObject out);
  }

  public EmbeddedJavac(ExecutorService taskRunner) {
    myTaskRunner = taskRunner;
    mySequentialTaskExecutor = new SequentialTaskExecutor(taskRunner);
    myCompiler = ToolProvider.getSystemJavaCompiler();
  }

  public void addClassProcessor(ClassPostProcessor processor) {
    myClassProcessors.add(processor);
  }

  public boolean compile(final List<File> sources, final List<String> options, File outputDir, final OutputConsumer outConsumer) {

    final JavacFileManager fileManager = new JavacFileManager(new FileManagerContext(outConsumer));
    if (!fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDir))) {
      return false;
    }
    //todo setup file manager to support multiple outputs

    final LineOutputWriter out = new LineOutputWriter() {
      protected void lineAvailable(String line) {
        outConsumer.outputLineAvailable(line);
      }
    };
    final List<String> _options = new ArrayList<String>();
    boolean skip = false;
    for (String option : options) {
      if ("-d".equals(option)) {
        skip = true;
        continue;
      }
      if (!skip) {
        _options.add(option);
      }
      skip = false;
    }
    final JavaCompiler.CompilationTask task = myCompiler.getTask(
      out, fileManager, outConsumer, _options, null, fileManager.toJavaFileObjects(sources)
    );
    return task.call();
  }


  private class FileManagerContext implements JavacFileManager.Context {

    private final StandardJavaFileManager myStdManager;
    private final OutputConsumer myOutConsumer;

    public FileManagerContext(OutputConsumer outConsumer) {
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
      myTaskRunner.submit(new Runnable() {
        public void run() {
          try {
            for (ClassPostProcessor processor : myClassProcessors) {
              processor.process(cls);
            }
          }
          finally {
            mySequentialTaskExecutor.submit(new Runnable() {
              public void run() {
                try {
                  final File file = cls.getFile();
                  final OutputFileObject.Content content = cls.getContent();
                  if (content != null) {
                    writeToFile(file, content.getBuffer(), content.getOffset(), content.getLength(), false);
                  }
                  else {
                    myOutConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, "Missing content for file " + file));
                  }
                }
                catch (IOException e) {
                  myOutConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
                }
              }
            });
          }
        }
      });
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

  public static boolean createParentDirs(@NotNull File file) {
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
