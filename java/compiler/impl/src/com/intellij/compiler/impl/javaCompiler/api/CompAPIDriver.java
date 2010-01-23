/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.OutputParser;
import com.sun.source.util.*;
import com.sun.tools.javac.api.JavacTool;
import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
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

    @Override
    public String toString() {
      return "FINISH";
    }
  };
  private String myOutputDir;

  private volatile boolean compiling;
  private static final PrintWriter COMPILER_ERRORS = new PrintWriter(System.err);

  public CompAPIDriver() {
  }

  public void compile(List<String> commandLine, List<File> paths, final String outputDir) {
    myOutputDir = outputDir;
    compiling = true;

    assert myCompilationResults.isEmpty();
    JavaCompiler compiler = JavacTool.create(); //use current classloader
    MyFileManager manager = new MyFileManager(this, outputDir);

    Iterable<? extends JavaFileObject> input = manager.getJavaFileObjectsFromFiles(paths);

    DiagnosticListener<JavaFileObject> listener = new DiagnosticListener<JavaFileObject>() {
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        CompilationEvent event = CompilationEvent.diagnostic(diagnostic);
        myCompilationResults.offer(event);
      }
    };
    try {
      JavaCompiler.CompilationTask task = compiler.getTask(COMPILER_ERRORS, manager, listener, commandLine, null, input);
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
      compiling = false;
      myCompilationResults.offer(GUARD);
    }
  }

  private volatile boolean processing;
  public void processAll(@NotNull OutputParser.Callback callback) {
    try {
      processing =  true;
      while (true) {
        CompilationEvent event = myCompilationResults.take();
        if (event == GUARD) break;
        event.process(callback);
      }
    }
    catch (InterruptedException ignored) {
    }
    finally {
      processing = false;
    }
  }

  public void finish() {
    assert !compiling : "still compiling to "+myOutputDir;
    assert !processing;
    //assert myCompilationResults.isEmpty() : myCompilationResults;
    myCompilationResults.clear();
  }

  public void offerClassFile(URI uri, byte[] bytes) {
    CompilationEvent event = CompilationEvent.generateClass(uri, bytes);
    myCompilationResults.offer(event);
  }

}


