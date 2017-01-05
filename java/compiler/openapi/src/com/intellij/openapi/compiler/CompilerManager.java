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
package com.intellij.openapi.compiler;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A "root" class in compiler subsystem - allows one to register a custom compiler or a compilation task, register/unregister a compilation listener
 * and invoke various types of compilations (make, compile, rebuild)
 */
public abstract class CompilerManager {
  @Deprecated
  public static final Key<Key> CONTENT_ID_KEY = Key.create("COMPILATION_CONTENT_ID_CUSTOM_KEY");
  public static final Key<RunConfiguration> RUN_CONFIGURATION_KEY = Key.create("RUN_CONFIGURATION");
  public static final Key<String> RUN_CONFIGURATION_TYPE_ID_KEY = Key.create("RUN_CONFIGURATION_TYPE_ID");

  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Compiler");

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
  public abstract void addCompiler(@NotNull Compiler compiler);
  
  /**
   * Registers a custom translating compiler. Input and output filetype sets allow compiler manager
   * to sort translating compilers so that output of one compiler will be used as input for another one
   * 
   * @param compiler compiler implementation 
   * @param inputTypes a set of filetypes that compiler accepts as input
   * @param outputTypes a set of filetypes that compiler can generate
   *
   * @deprecated this method is part of the obsolete build system which runs as part of the IDE process. Since IDEA 12 plugins need to
   * integrate into 'external build system' instead (https://confluence.jetbrains.com/display/IDEADEV/External+Builder+API+and+Plugins).
   * Since IDEA 13 users cannot switch to the old build system via UI and it will be completely removed in IDEA 14.
   */
  public abstract void addTranslatingCompiler(@NotNull TranslatingCompiler compiler, Set<FileType> inputTypes, Set<FileType> outputTypes);

  /**
   * Unregisters a custom compiler.
   *
   * @param compiler the compiler to unregister.
   */
  public abstract void removeCompiler(@NotNull Compiler compiler);

  /**
   * Returns all registered compilers of the specified class.
   *
   * @param compilerClass the class for which the compilers should be returned.
   * @return all registered compilers of the specified class.
   */
  @NotNull
  public abstract <T  extends Compiler> T[] getCompilers(@NotNull Class<T> compilerClass);

  /**
   * Returns all registered compilers of the specified class that the filter accepts
   *
   * @param compilerClass the class for which the compilers should be returned.
   * @param filter additional filter to restrict compiler instances
   * @return all registered compilers of the specified class.
   */
  @NotNull
  public abstract <T  extends Compiler> T[] getCompilers(@NotNull Class<T> compilerClass, CompilerFilter filter);

  /**
   * Registers the type as a compilable type so that Compile action will be enabled on files of this type.
   *
   * @param type the type for which the Compile action is enabled.
   */
  public abstract void addCompilableFileType(@NotNull FileType type);

  /**
   * Unregisters the type as a compilable type so that Compile action will be disabled on files of this type.
   *
   * @param type the type for which the Compile action is disabled.
   */
  public abstract void removeCompilableFileType(@NotNull FileType type);

  /**
   * Checks if files of the specified type can be compiled by one of registered compilers.
   * If the compiler can process files of certain type, it should register this file type within
   * the CompilerManager as a compilable file type.
   *
   * @param type the type to check.
   * @return true if the file type is compilable, false otherwise.
   * @see com.intellij.openapi.compiler.CompilerManager#addCompilableFileType(FileType)
   */
  public abstract boolean isCompilableFileType(@NotNull FileType type);

  /**
   * Registers a compiler task that will be executed before the compilation.
   *
   * @param task the task to register.
   */
  public abstract void addBeforeTask(@NotNull CompileTask task);

  /**
   * Registers a compiler task  that will be executed after the compilation.
   *
   * @param task the task to register.
   */
  public abstract void addAfterTask(@NotNull CompileTask task);

  /**
   * Returns the list of all tasks to be executed before compilation.
   *
   * @return all tasks to be executed before compilation.
   */
  @NotNull
  public abstract CompileTask[] getBeforeTasks();

  /**
   * Returns the list of all tasks to be executed after compilation.
   *
   * @return all tasks to be executed after compilation.
   */
  @NotNull
  public abstract CompileTask[] getAfterTasks();

  /**
   * Compile a set of files.
   *
   * @param files             a list of files to compile. If a VirtualFile is a directory, all containing files are processed.
   *                          Compiler excludes are not honored.
   * @param callback          a notification callback, or null if no notifications needed.
   */
  public abstract void compile(@NotNull VirtualFile[] files, @Nullable CompileStatusNotification callback);

  /**
   * Compile all sources (including test sources) from the module. Compiler excludes are not honored.
   *
   * @param module            a module which sources are to be compiled
   * @param callback          a notification callback, or null if no notifications needed
   */
  public abstract void compile(@NotNull Module module, @Nullable CompileStatusNotification callback);

  /**
   * Compile all files from the scope given.  Compiler excludes are not honored.
   *
   * @param scope             a scope to be compiled
   * @param callback          a notification callback, or null if no notifications needed
   */
  public abstract void compile(@NotNull CompileScope scope, @Nullable CompileStatusNotification callback);

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
  public abstract void make(@NotNull Module module, @Nullable CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the modules and all modules these modules depend on recursively.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project.
   *
   * @param project  a project modules belong to
   * @param modules  modules to compile
   * @param callback a notification callback, or null if no notifications needed.
   */
  public abstract void make(@NotNull Project project, @NotNull Module[] modules, @Nullable CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the scope given.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project
   *
   * @param scope    a scope to be compiled
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(@NotNull CompileScope scope, @Nullable CompileStatusNotification callback);

  /**
   * Compile all modified files and all files that depend on them from the scope given.
   * Files are compiled according to dependencies between the modules they belong to. Compiler excludes are honored. All modules must belong to the same project
   *
   * @param scope    a scope to be compiled
   * @param filter filter allowing choose what compilers should be executed
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void make(@NotNull CompileScope scope, CompilerFilter filter, @Nullable CompileStatusNotification callback);

  /**
   * Checks if compile scope given is up-to-date
   * @param scope
   * @return true if make on the scope specified wouldn't do anything or false if something is to be compiled or deleted 
   */
  public abstract boolean isUpToDate(@NotNull CompileScope scope);
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
  public abstract void executeTask(@NotNull CompileTask task, @NotNull CompileScope scope, String contentName,
                                   @Nullable Runnable onTaskFinished);

  /**
   * Register a listener to track compilation events.
   *
   * @param listener the listener to be registered.
   */
  public abstract void addCompilationStatusListener(@NotNull CompilationStatusListener listener);
  public abstract void addCompilationStatusListener(@NotNull CompilationStatusListener listener, @NotNull Disposable parentDisposable);

  /**
   * Unregister a compilation listener.
   *
   * @param listener the listener to be unregistered.
   */
  public abstract void removeCompilationStatusListener(@NotNull CompilationStatusListener listener);

  /**
   * Checks if the specified file is excluded from compilation.
   *
   * @param file the file to check.
   * @return true if the file is excluded from compilation, false otherwise
   */
  public abstract boolean isExcludedFromCompilation(@NotNull VirtualFile file);

  /*
   * Convetience methods for creating frequently-used compile scopes
   */
  @NotNull
  public abstract CompileScope createFilesCompileScope(@NotNull VirtualFile[] files);
  @NotNull
  public abstract CompileScope createModuleCompileScope(@NotNull Module module, boolean includeDependentModules);
  @NotNull
  public abstract CompileScope createModulesCompileScope(@NotNull Module[] modules, boolean includeDependentModules);
  @NotNull
  public abstract CompileScope createModulesCompileScope(@NotNull Module[] modules, boolean includeDependentModules, boolean includeRuntimeDependencies);
  @NotNull
  public abstract CompileScope createModuleGroupCompileScope(@NotNull Project project, @NotNull Module[] modules, boolean includeDependentModules);
  @NotNull
  public abstract CompileScope createProjectCompileScope(@NotNull Project project);

  public abstract void setValidationEnabled(ModuleType moduleType, boolean enabled);

  public abstract boolean isValidationEnabled(Module moduleType);

  public abstract Collection<ClassObject> compileJavaCode(List<String> options,
                                                          Collection<File> platformCp,
                                                          Collection<File> classpath,
                                                          Collection<File> modulePath,
                                                          Collection<File> sourcePath,
                                                          Collection<File> files,
                                                          File outputDir) throws IOException, CompilationException;

  @Nullable
  public abstract File getJavacCompilerWorkingDir();
}
