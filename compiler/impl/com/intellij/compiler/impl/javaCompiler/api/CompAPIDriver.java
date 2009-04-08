package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.OutputParser;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.File;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author cdr
 */
@SuppressWarnings({"Since15"})
public class CompAPIDriver {
  private final BlockingQueue<CompilationEvent> myCompilationResults = new LinkedBlockingQueue<CompilationEvent>();
  private static final CompilationEvent GUARD = new CompilationEvent() {
    @Override
    protected void process(OutputParser.Callback callback) {
    }
  };

  public void compile(List<String> commandLine, List<File> paths, final String outputDir) {
    JavaCompiler compiler = new com.sun.tools.javac.api.JavacTool(); //use current classloader
    StandardJavaFileManager manager = new MyFileManager(this, outputDir);

    Iterable<? extends JavaFileObject> input = manager.getJavaFileObjectsFromFiles(paths);

    DiagnosticListener<JavaFileObject> listener = new DiagnosticListener<JavaFileObject>() {
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
          CompilationEvent event = CompilationEvent.diagnostic(diagnostic);
          myCompilationResults.offer(event);
        }
    };
    try {
      PrintWriter silent = new PrintWriter(new OutputStream(){
          public void write(int b) {}
      });
      
      JavaCompiler.CompilationTask task = compiler.getTask(silent, manager, listener, commandLine, null, input);
      ((JavacTask)task).setTaskListener(new TaskListener() {
        public void started(TaskEvent taskEvent) {
          JavaFileObject sourceFile = taskEvent.getSourceFile();
          CompilationEvent event;
          switch (taskEvent.getKind()) {
            case ANALYZE:
              event = CompilationEvent.progress("Analyzing ",sourceFile);
              break;
            case PARSE:
              event = CompilationEvent.progress("Parsing ", sourceFile);
              break;
            default:
              event = null;
          }
          if (event != null) {
            myCompilationResults.offer(event);
          }
        }
        public void finished(TaskEvent taskEvent) {
          CompilationEvent event;
          switch (taskEvent.getKind()) {
            case ENTER:
              event = CompilationEvent.fileProcessed();
              break;
            default:
              event = null;
          }
          if (event != null) {
            myCompilationResults.offer(event);
          }
        }
      });
      task.call();
    }
    finally {
      myCompilationResults.offer(GUARD);
    }
  }

  public boolean processAll(@NotNull OutputParser.Callback callback) {
    try {
      while (true) {
        CompilationEvent event = myCompilationResults.take();
        if (event == GUARD) break;
        event.process(callback);
      }
    }
    catch (InterruptedException ignored) {
      System.out.println("ignored " + ignored);
    }
    return false;
  }

  public void finish() {
    myCompilationResults.clear();
  }


  public void offer(CompilationEvent compilationEvent) {
    myCompilationResults.offer(compilationEvent);
  }
}


