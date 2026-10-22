// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.BasicJavacTask;

import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;

/**
 * A javac plugin that applies the bytecode instrumentation of the jvm-inc-builder worker to the classes of the running
 * compile. The result equals the one of {@code NotNullInstrumenter} and of {@code ThreadingModelInstrumenter} for the
 * Java output. As in the worker, the threading model instrumentation runs only when the system property
 * {@code tmh.instrument.annotations} is true.
 * <p>
 * javac finds the plugin with the service loader on the annotation processor path. It starts the plugin without
 * {@code -Xplugin}, because {@link #autoStart()} returns true. The plugin reads and writes the class files through the
 * file manager of the compile session, which must be a {@link StandardJavaFileManager}. With another file manager the
 * plugin reports an error. {@link InstrumentationTaskListener} instruments the classes in one pass when the compile
 * ends.
 */
public final class InstrumentationJavacPlugin implements Plugin {
  public static final String NAME = "BytecodeInstrumentation";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean autoStart() {
    return true;
  }

  @Override
  public void init(JavacTask task, String... args) {
    // No public API gives a plugin the file manager of the compile session, so this is the one internal call.
    JavaFileManager fileManager = ((BasicJavacTask)task).getContext().get(JavaFileManager.class);
    if (fileManager instanceof StandardJavaFileManager) {
      task.addTaskListener(new InstrumentationTaskListener(task, fileManager));
    }
    else {
      task.addTaskListener(new UnsupportedFileManagerReporter(task, fileManager));
    }
  }

  /**
   * Reports one error at the first event that has a compilation unit, because {@link Trees#printMessage} needs one.
   */
  private static final class UnsupportedFileManagerReporter implements TaskListener {
    private final JavacTask myTask;
    private final JavaFileManager myFileManager;
    private boolean myReported;

    UnsupportedFileManagerReporter(JavacTask task, JavaFileManager fileManager) {
      myTask = task;
      myFileManager = fileManager;
    }

    @Override
    public void finished(TaskEvent e) {
      if (!myReported && e.getCompilationUnit() != null) {
        myReported = true;
        Trees.instance(myTask).printMessage(
          Diagnostic.Kind.ERROR,
          "Bytecode instrumentation is not possible: the file manager of the compile session is not a StandardJavaFileManager (" +
          myFileManager.getClass().getName() + ")",
          e.getCompilationUnit(),
          e.getCompilationUnit()
        );
      }
    }
  }
}
