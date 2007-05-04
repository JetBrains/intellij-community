/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.compiler;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * A "root" class in compiler subsystem - allows one to register a custom compiler or a compilation task, register/unregister a compilation listener
 * and invoke various types of compilations (make, compile, rebuild)
 */
public abstract class CompilerManager {
  /**
   * Returns the compiler manager instance for the specified project.
   *
   * @param project the project for which the manager is requested.
   * @return the manager instance.
   */
  public static CompilerManager getInstance(Project project) {
    return ServiceManager.getService(project, CompilerManager.class);
  }
  
  public abstract boolean isCompilationActive();
  
  /**
   * Registers a custom compiler.
   *
   * @param compiler the compiler to register.
   */
  public abstract void addCompiler(Compiler compiler);

  /**
   * Unregisters a custom compiler.
   *
   * @param compiler the compiler to unregister.
   */
  public abstract void removeCompiler(Compiler compiler);

  /**
   * Returns all registered compilers of the specified class.
   *
   * @param compilerClass the class for which the compilers should be returned.
   * @return all registered compilers of the specified class.
   */
   public abstract <T  extends Compiler> T[] getCompilers(Class<T> compilerClass);

  /**
   * Registers the type as a compilable type so that Compile action will be enabled on files of this type.
   *
   * @param type the type for which the Compile action is enabled.
   */
  public abstract void addCompilableFileType(FileType type);

  /**
   * Unregisters the type as a compilable type so that Compile action will be disabled on files of this type.
   *
   * @param type the type for which the Compile action is disabled.
   */
  public abstract void removeCompilableFileType(FileType type);

  /**
   * Checks if files of the specified type can be compiled by one of registered compilers.
   * If the compiler can process files of certain type, it should register this file type within
   * the CompilerManager as a compilable file type.
   *
   * @param type the type to check.
   * @return true if the file type is compilable, false otherwise.
   * @see com.intellij.openapi.compiler.CompilerManager#addCompilableFileType(FileType)
   */
  public abstract boolean isCompilableFileType(FileType type);

  /**
   * Registers a compiler task that will be executed before the compilation.
   *
   * @param task the task to register.
   */
  public abstract void addBeforeTask(CompileTask task);

  /**
   * Registers a compiler task  that will be executed after the compilation.
   *
   * @param task the task to register.
   */
  public abstract void addAfterTask(CompileTask task);

  /**
   * Returns the list of all tasks to be executed before compilation.
   *
   * @return all tasks to be executed before compilation.
   */
  public abstract CompileTask[] getBeforeTasks();

  /**
   * Returns the list of all tasks to be executed after compilation.
   *
   * @return all tasks to be executed after compilation.
   */
  public abstract CompileTask[] getAfterTasks();

  /**
   * Compile a set of files.
   *
   * @param files             a list of files to compile. If a VirtualFile is a directory, all containing files are processed.
   *                          Compiler excludes are not honored.
   * @param callback          a notification callback, or null if no notifications needed.
   * @param trackDependencies if true, all files that the given set depends on, recursively, will be compiled.
   */
  public abstract void compile(VirtualFile[] files, @Nullable CompileStatusNotification callback, boolean trackDependencies);

  /**
   * Compile all sources (including test sources) from the module. Compiler excludes are not honored.
   *
   * @param module            a module which sources are to be compiled
   * @param callback          a notification callback, or null if no notifications needed
   * @param trackDependencies if true, all files that the given set depends on, recursively, will be compiled.
   */
  public abstract void compile(Module module, @Nullable CompileStatusNotification callback, final boolean trackDependencies);

  /**
   * Compile all files from the scope given.  Compiler excludes are not honored.
   *
   * @param scope             a scope to be compiled
   * @param callback          a notification callback, or null if no notifications needed
   * @param trackDependencies if true, all files that the given set depends on, recursively, will be compiled.
   */
  public abstract void compile(CompileScope scope, CompileStatusNotification callback, final boolean trackDependencies);

  /**
   * Compile all modified files and all files that depend on them all over the project.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored.
   *
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(@Nullable CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the given module and all modules this module depends on recursively.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored.
   *
   * @param module   a module which sources are to be compiled.
   * @param callback a notification callback, or null if no notifications needed.
   */
  public abstract void make(Module module, @Nullable CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the modules and all modules these modules depend on recursively.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project.
   *
   * @param project  a project modules belong to
   * @param modules  modules to compile
   * @param callback a notification callback, or null if no notifications needed.
   */
  public abstract void make(Project project, Module[] modules, @Nullable CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the scope given.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project
   *
   * @param scope    a scope to be compiled
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(CompileScope scope, @Nullable CompileStatusNotification callback);

  /**
   * Rebuild the whole project from scratch. Compiler excludes are honored.
   *
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void rebuild(@Nullable CompileStatusNotification callback);

  /**
   * Execute a custom compile task.
   *
   * @param task           the task to execute.
   * @param scope          compile scope for which the task is executed.
   * @param contentName    the name of a tab in message view where the execution results will be displayed.
   * @param onTaskFinished a runnable to be executed when the task finishes, null if nothing should be executed.
   */
  public abstract void executeTask(CompileTask task, CompileScope scope, String contentName,
                                   @Nullable Runnable onTaskFinished);

  /**
   * Register a listener to track compilation events.
   *
   * @param listener the listener to be registered.
   */
  public abstract void addCompilationStatusListener(CompilationStatusListener listener);

  /**
   * Unregister a compilation listener.
   *
   * @param listener the listener to be unregistered.
   */
  public abstract void removeCompilationStatusListener(CompilationStatusListener listener);

  /**
   * Checks if the specified file is excluded from compilation.
   *
   * @param file the file to check.
   * @return true if the file is excluded from compilation, false otherwise
   */
  public abstract boolean isExcludedFromCompilation(VirtualFile file);
}
