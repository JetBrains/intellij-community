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
package com.intellij.compiler;

import com.intellij.compiler.impl.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.Semaphore;

public class CompilerManagerImpl extends CompilerManager {
  private final Project myProject;

  private final List<Compiler> myCompilers = new ArrayList<Compiler>();

  private final List<CompileTask> myBeforeTasks = new ArrayList<CompileTask>();
  private final List<CompileTask> myAfterTasks = new ArrayList<CompileTask>();
  private final Set<FileType> myCompilableTypes = new HashSet<FileType>();
  private final CompilationStatusListener myEventPublisher;
  private final Semaphore myCompilationSemaphore = new Semaphore(1, true);
  private final Set<ModuleType> myValidationDisabledModuleTypes = new HashSet<ModuleType>();
  private final Set<LocalFileSystem.WatchRequest> myWatchRoots;

  public CompilerManagerImpl(final Project project, MessageBus messageBus) {
    myProject = project;
    myEventPublisher = messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS);

    // predefined compilers
    for(Compiler compiler: Extensions.getExtensions(Compiler.EP_NAME, myProject)) {
      addCompiler(compiler);
    }
    for(CompilerFactory factory: Extensions.getExtensions(CompilerFactory.EP_NAME, myProject)) {
      Compiler[] compilers = factory.createCompilers(this);
      for (Compiler compiler : compilers) {
        addCompiler(compiler);
      }
    }

    addCompilableFileType(StdFileTypes.JAVA);
    
    final File projectGeneratedSrcRoot = CompilerPaths.getGeneratedDataDirectory(project);
    projectGeneratedSrcRoot.mkdirs();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    myWatchRoots = lfs.addRootsToWatch(Collections.singletonList(FileUtil.toCanonicalPath(projectGeneratedSrcRoot.getPath())), true);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        lfs.removeWatchedRoots(myWatchRoots);
        if (ApplicationManager.getApplication().isUnitTestMode()) {    // force cleanup for created compiler system directory with generated sources
          FileUtil.delete(CompilerPaths.getCompilerSystemDirectory(project));
        }
      }
    });
  }

  public Semaphore getCompilationSemaphore() {
    return myCompilationSemaphore;
  }

  public boolean isCompilationActive() {
    return myCompilationSemaphore.availablePermits() == 0;
  }

  public final void addCompiler(@NotNull Compiler compiler) {
    myCompilers.add(compiler);
    // supporting file instrumenting compilers and validators for external build
    // Since these compilers are IDE-specific and use PSI, it is ok to run them before and after the build in the IDE
    if (compiler instanceof SourceInstrumentingCompiler) {
      addBeforeTask(new FileProcessingCompilerAdapterTask((FileProcessingCompiler)compiler));
    }
    else if (compiler instanceof Validator) {
      addAfterTask(new FileProcessingCompilerAdapterTask((FileProcessingCompiler)compiler));
    }
  }

  @Deprecated
  public void addTranslatingCompiler(@NotNull TranslatingCompiler compiler, Set<FileType> inputTypes, Set<FileType> outputTypes) {
    // empty
  }

  public final void removeCompiler(@NotNull Compiler compiler) {
    for (List<CompileTask> tasks : Arrays.asList(myBeforeTasks, myAfterTasks)) {
      for (Iterator<CompileTask> iterator = tasks.iterator(); iterator.hasNext(); ) {
        CompileTask task = iterator.next();
        if (task instanceof FileProcessingCompilerAdapterTask && ((FileProcessingCompilerAdapterTask)task).getCompiler() == compiler) {
          iterator.remove();
        }
      }
    }
  }

  @NotNull
  public <T  extends Compiler> T[] getCompilers(@NotNull Class<T> compilerClass) {
    return getCompilers(compilerClass, CompilerFilter.ALL);
  }

  @NotNull
  public <T extends Compiler> T[] getCompilers(@NotNull Class<T> compilerClass, CompilerFilter filter) {
    final List<T> compilers = new ArrayList<T>(myCompilers.size());
    for (final Compiler item : myCompilers) {
      if (compilerClass.isAssignableFrom(item.getClass()) && filter.acceptCompiler(item)) {
        compilers.add((T)item);
      }
    }
    final T[] array = (T[])Array.newInstance(compilerClass, compilers.size());
    return compilers.toArray(array);
  }

  public void addCompilableFileType(@NotNull FileType type) {
    myCompilableTypes.add(type);
  }

  public void removeCompilableFileType(@NotNull FileType type) {
    myCompilableTypes.remove(type);
  }

  public boolean isCompilableFileType(@NotNull FileType type) {
    return myCompilableTypes.contains(type);
  }

  public final void addBeforeTask(@NotNull CompileTask task) {
    myBeforeTasks.add(task);
  }

  public final void addAfterTask(@NotNull CompileTask task) {
    myAfterTasks.add(task);
  }

  @NotNull
  public CompileTask[] getBeforeTasks() {
    return getCompileTasks(myBeforeTasks, CompileTaskBean.CompileTaskExecutionPhase.BEFORE);
  }

  private CompileTask[] getCompileTasks(List<CompileTask> taskList, CompileTaskBean.CompileTaskExecutionPhase phase) {
    List<CompileTask> beforeTasks = new ArrayList<CompileTask>(taskList);
    for (CompileTaskBean extension : CompileTaskBean.EP_NAME.getExtensions(myProject)) {
      if (extension.myExecutionPhase == phase) {
        beforeTasks.add(extension.getTaskInstance());
      }
    }
    return beforeTasks.toArray(new CompileTask[beforeTasks.size()]);
  }

  @NotNull
  public CompileTask[] getAfterTasks() {
    return getCompileTasks(myAfterTasks, CompileTaskBean.CompileTaskExecutionPhase.AFTER);
  }

  public void compile(@NotNull VirtualFile[] files, CompileStatusNotification callback) {
    compile(createFilesCompileScope(files), callback);
  }

  public void compile(@NotNull Module module, CompileStatusNotification callback) {
    new CompileDriver(myProject).compile(createModuleCompileScope(module, false), new ListenerNotificator(callback));
  }

  public void compile(@NotNull CompileScope scope, CompileStatusNotification callback) {
    new CompileDriver(myProject).compile(scope, new ListenerNotificator(callback));
  }

  public void make(CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createProjectCompileScope(myProject), new ListenerNotificator(callback));
  }

  public void make(@NotNull Module module, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createModuleCompileScope(module, true), new ListenerNotificator(callback));
  }

  public void make(@NotNull Project project, @NotNull Module[] modules, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createModuleGroupCompileScope(project, modules, true), new ListenerNotificator(callback));
  }

  public void make(@NotNull CompileScope scope, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(scope, new ListenerNotificator(callback));
  }

  public void make(@NotNull CompileScope scope, CompilerFilter filter, @Nullable CompileStatusNotification callback) {
    final CompileDriver compileDriver = new CompileDriver(myProject);
    compileDriver.setCompilerFilter(filter);
    compileDriver.make(scope, new ListenerNotificator(callback));
  }

  public boolean isUpToDate(@NotNull final CompileScope scope) {
    return new CompileDriver(myProject).isUpToDate(scope);
  }

  public void rebuild(CompileStatusNotification callback) {
    new CompileDriver(myProject).rebuild(new ListenerNotificator(callback));
  }

  public void executeTask(@NotNull CompileTask task, @NotNull CompileScope scope, String contentName, Runnable onTaskFinished) {
    final CompileDriver compileDriver = new CompileDriver(myProject);
    compileDriver.executeCompileTask(task, scope, contentName, onTaskFinished);
  }

  private final Map<CompilationStatusListener, MessageBusConnection> myListenerAdapters = new HashMap<CompilationStatusListener, MessageBusConnection>();

  public void addCompilationStatusListener(@NotNull final CompilationStatusListener listener) {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    myListenerAdapters.put(listener, connection);
    connection.subscribe(CompilerTopics.COMPILATION_STATUS, listener);
  }

  @Override
  public void addCompilationStatusListener(@NotNull CompilationStatusListener listener, @NotNull Disposable parentDisposable) {
    final MessageBusConnection connection = myProject.getMessageBus().connect(parentDisposable);
    connection.subscribe(CompilerTopics.COMPILATION_STATUS, listener);
  }

  public void removeCompilationStatusListener(@NotNull final CompilationStatusListener listener) {
    final MessageBusConnection connection = myListenerAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  public boolean isExcludedFromCompilation(@NotNull VirtualFile file) {
    return CompilerConfiguration.getInstance(myProject).isExcludedFromCompilation(file);
  }

  @NotNull
  public CompileScope createFilesCompileScope(@NotNull final VirtualFile[] files) {
    CompileScope[] scopes = new CompileScope[files.length];
    for(int i = 0; i < files.length; i++){
      scopes[i] = new OneProjectItemCompileScope(myProject, files[i]);
    }
    return new CompositeScope(scopes);
  }

  @NotNull
  public CompileScope createModuleCompileScope(@NotNull final Module module, final boolean includeDependentModules) {
    return createModulesCompileScope(new Module[] {module}, includeDependentModules);
  }

  @NotNull
  public CompileScope createModulesCompileScope(@NotNull final Module[] modules, final boolean includeDependentModules) {
    return createModulesCompileScope(modules, includeDependentModules, false);
  }

  @NotNull 
  public CompileScope createModulesCompileScope(@NotNull Module[] modules, boolean includeDependentModules, boolean includeRuntimeDependencies) {
    return new ModuleCompileScope(myProject, modules, includeDependentModules, includeRuntimeDependencies);
  }

  @NotNull
  public CompileScope createModuleGroupCompileScope(@NotNull final Project project, @NotNull final Module[] modules, final boolean includeDependentModules) {
    return new ModuleCompileScope(project, modules, includeDependentModules);
  }

  @NotNull
  public CompileScope createProjectCompileScope(@NotNull final Project project) {
    return new ProjectCompileScope(project);
  }

  @Override
  public void setValidationEnabled(ModuleType moduleType, boolean enabled) {
    if (enabled) {
      myValidationDisabledModuleTypes.remove(moduleType);
    }
    else {
      myValidationDisabledModuleTypes.add(moduleType);
    }
  }

  @Override
  public boolean isValidationEnabled(Module module) {
    if (myValidationDisabledModuleTypes.isEmpty()) {
      return true; // optimization
    }
    return !myValidationDisabledModuleTypes.contains(ModuleType.get(module));
  }

  private class ListenerNotificator implements CompileStatusNotification {
    private final @Nullable CompileStatusNotification myDelegate;

    private ListenerNotificator(@Nullable CompileStatusNotification delegate) {
      myDelegate = delegate;
    }

    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      if (!myProject.isDisposed()) {
        myEventPublisher.compilationFinished(aborted, errors, warnings, compileContext);
      }
      if (myDelegate != null) {
        myDelegate.finished(aborted, errors, warnings, compileContext);
      }
    }
  }
}
