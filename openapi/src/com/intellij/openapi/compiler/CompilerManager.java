/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * A "root" class in compiler subsystem - allows one to register a custom compiler or a compilation task, register/unregister a compilation listener
 * and invoke various types of compilations (make, compile, rebuild)
 */
public abstract class CompilerManager {
  /**
   * Allows to register a custom compiler
   * @param compiler
   */
  public abstract void addCompiler(com.intellij.openapi.compiler.Compiler compiler);

  /**
   * Allows to unregister a custom compiler
   * @param compiler
   */
  public abstract void removeCompiler(Compiler compiler);

  /**
   * @param compilerClass
   * @return all registered compilers of the specified class
   */
  public abstract Compiler[] getCompilers(Class compilerClass);

  /**
   * Register compiler tast that will be executed before the compilation
   * @param task
   */
  public abstract void addBeforeTask(CompileTask task);

  /**
   * Register compiler tast that will be executed after the compilation
   * @param task
   */
  public abstract void addAfterTask(CompileTask task);

  /**
   * @return all tasks to be executed before compilation
   */
  public abstract CompileTask[] getBeforeTasks();

  /**
   * @return all tasks to be executed after compilation
   */
  public abstract CompileTask[] getAfterTasks();

  /**
   * Compile a set of files
   * @param files a list of files to compile. If a VirtualFile is a directory, all containing files are processed.
   * Compiler excludes are not honored.
   * @param callback a notification callback, or null if no notifications needed
   * @param trackDependencies if true, all files that the given set depends on, recursively, will be compiled
   */
  public abstract void compile(VirtualFile[] files, CompileStatusNotification callback, boolean trackDependencies);

  /**
   * Compile all sources (including test sources) from the module. No files from dependent modules are compiled.
   * Compiler excludes are not honored
   * @param module a module which sources are to be compiled
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void compile(Module module, CompileStatusNotification callback);

  /**
   * Compile all files from the scope given. No files from dependent modules are compiled.
   * Compiler excludes are not honored
   * @param scope a scope to be compiled
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void compile(CompileScope scope, CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them all over the project.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored.
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the given module and all modules this module depends on recursively.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored.
   * @param module a module which sources are to be compiled
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(Module module, CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the modules and all modules these modules depend on recursively.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project
   * @param project a project modules belong to
   * @param modules modules to compile
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(Project project, Module[] modules, CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the scope given.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project
   * @param scope a scope to be compiled
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(CompileScope scope, CompileStatusNotification callback);

  /**
   * Rebuild the whole project from scratch. Compiler excludes are honored.
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void rebuild(CompileStatusNotification callback);

  /**
   * Execute custom compile task
   * @param task the tast to execute
   * @param scope compile scope for which the tast is executed
   * @param contentName the name of a tab in message view where the execution results will be displayed
   * @param onTaskFinished a runnable to be executed when the task finishes, null if nothing should be executed
   */
  public abstract void executeTask(CompileTask task, CompileScope scope, String contentName, Runnable onTaskFinished);

  public static CompilerManager getInstance(Project project) {
    return project.getComponent(CompilerManager.class);
  }

  /**
   * Register a listener to track compilation events
   * @param listener the listener to be registered
   */
  public abstract void addCompilationStatusListener(CompilationStatusListener listener);

  /**
   * Unregister compilation listener
   * @param listener the listener to be unregistered
   */
  public abstract void removeCompilationStatusListener(CompilationStatusListener listener);

  public abstract boolean isExcludedFromCompilation(VirtualFile file);
}
