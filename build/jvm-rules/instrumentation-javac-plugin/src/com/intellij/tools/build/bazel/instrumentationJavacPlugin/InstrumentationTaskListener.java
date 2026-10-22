// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the output request of each class at GENERATE started, and instruments the recorded classes at COMPILATION
 * finished.
 */
final class InstrumentationTaskListener implements TaskListener {
  private final JavacTask myTask;
  private final JavaFileManager myFileManager;
  private final Elements myElements;
  private final Map<String, OutputRequest> myRequests = new LinkedHashMap<>();
  private Boolean myMultiModule;
  private boolean myIsNamedModule;

  InstrumentationTaskListener(JavacTask task, JavaFileManager fileManager) {
    myTask = task;
    myFileManager = fileManager;
    myElements = task.getElements();
  }

  @Override
  public void started(TaskEvent e) {
    if (e.getKind() == TaskEvent.Kind.GENERATE) {
      record(e);
    }
  }

  @Override
  public void finished(TaskEvent e) {
    if (e.getKind() == TaskEvent.Kind.COMPILATION) {
      if (!myRequests.isEmpty()) {
        try {
          instrumentRecordedClasses(myRequests.values());
        }
        finally {
          myRequests.clear();
        }
      }
    }
  }

  /**
   * Records the request of ClassWriter.writeClass. A module-info class has the name {@code module-info}, and in the
   * multi-module mode the location is the location of the module in CLASS_OUTPUT.
   */
  private void record(TaskEvent e) {
    TypeElement type = e.getTypeElement();
    Element enclosing = type.getEnclosingElement();
    boolean moduleInfo = type.getSimpleName().contentEquals("module-info") || (enclosing != null && enclosing.getKind() == ElementKind.MODULE);
    ModuleElement module = moduleInfo && enclosing instanceof ModuleElement moduleElement ? moduleElement : myElements.getModuleOf(type);
    if (module != null && !module.isUnnamed()) {
      myIsNamedModule = true;
    }
    String className = moduleInfo ? "module-info" : myElements.getBinaryName(type).toString();
    Location location = StandardLocation.CLASS_OUTPUT;
    if (module != null && isMultiModule()) {
      try {
        location = myFileManager.getLocationForModule(StandardLocation.CLASS_OUTPUT, module.getQualifiedName().toString());
      }
      catch (IOException ex) {
        report(Diagnostic.Kind.ERROR, e.getCompilationUnit(), "Bytecode instrumentation cannot find the output location of " + className + ": " + ex);
        return;
      }
    }
    myRequests.putIfAbsent(location.getName() + "|" + className, new OutputRequest(location, className, e.getSourceFile(), e.getCompilationUnit()));
  }

  private boolean isMultiModule() {
    Boolean multiModule = myMultiModule;
    if (multiModule == null) {
      multiModule = myFileManager.hasLocation(StandardLocation.MODULE_SOURCE_PATH);
      myMultiModule = multiModule;
    }
    return multiModule;
  }

  private void instrumentRecordedClasses(Iterable<OutputRequest> compiledClasses) {
    FileManagerClassFinder finder = new FileManagerClassFinder(
      myFileManager, compiledClasses, !myElements.getAllModuleElements().isEmpty(), myIsNamedModule, isMultiModule()
    );
    try {
      // The instrumenters of BytecodeInstrumentationRunner for the Java output, in its order.
      ClassInstrumenter instrumenter = new CompositeInstrumenter(List.of(new TMHInstrumenter(), new NotNullInstrumenter()));
      for (OutputRequest compiled : compiledClasses) {
        byte[] content;
        try {
          content = compiled.read(myFileManager);
        }
        catch (IOException ex) {
          report(
            Diagnostic.Kind.MANDATORY_WARNING, compiled.unit(), "Bytecode instrumentation of " + compiled.className() + " was not possible: its class file cannot be read (" + ex + ")"
          );
          continue;
        }
        try {
          byte[] instrumented = instrumenter.instrument(content, finder);
          if (instrumented != null) {
            compiled.write(myFileManager, instrumented);
            finder.cleanCachedData(compiled.internalName());
          }
        }
        catch (Throwable failure) {
          String message = failure.getMessage();
          report(Diagnostic.Kind.ERROR, compiled.unit(), "Bytecode instrumentation of " + compiled.className() + " failed: " + (message != null ? message : failure.toString()));
          return;
        }
      }
    }
    finally {
      finder.releaseResources();
    }
  }

  /**
   * An exception from printMessage propagates. In the fail-fast mode JavaBuilder's diagnostic listener throws for an error.
   */
  private void report(Diagnostic.Kind kind, CompilationUnitTree unit, String message) {
    Trees.instance(myTask).printMessage(kind, message, unit, unit);
  }
}
