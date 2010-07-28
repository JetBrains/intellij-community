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
import com.intellij.compiler.impl.javaCompiler.AnnotationProcessingCompiler;
import com.intellij.compiler.impl.javaCompiler.JavaCompiler;
import com.intellij.compiler.impl.resourceCompiler.ResourceCompiler;
import com.intellij.compiler.impl.rmiCompiler.RmicCompiler;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.Semaphore;

public class CompilerManagerImpl extends CompilerManager {
  private final Project myProject;

  private final List<Compiler> myCompilers = new ArrayList<Compiler>();
  private final List<TranslatingCompiler> myTranslators = new ArrayList<TranslatingCompiler>();

  private final List<CompileTask> myBeforeTasks = new ArrayList<CompileTask>();
  private final List<CompileTask> myAfterTasks = new ArrayList<CompileTask>();
  private final Set<FileType> myCompilableTypes = new HashSet<FileType>();
  private final CompilationStatusListener myEventPublisher;
  private final Semaphore myCompilationSemaphore = new Semaphore(1, true);
  private final Map<Compiler, Set<FileType>> myCompilerToInputTypes = new HashMap<Compiler, Set<FileType>>();
  private final Map<Compiler, Set<FileType>> myCompilerToOutputTypes = new HashMap<Compiler, Set<FileType>>();

  public CompilerManagerImpl(final Project project, CompilerConfigurationImpl compilerConfiguration, MessageBus messageBus) {
    myProject = project;
    myEventPublisher = messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS);

    // predefined compilers
    addTranslatingCompiler(new AnnotationProcessingCompiler(project), new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA)), new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA, StdFileTypes.CLASS)));
    addTranslatingCompiler(new JavaCompiler(project), new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA)), new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)));
    addCompiler(new ResourceCompiler(project, compilerConfiguration));
    addCompiler(new RmicCompiler());

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
    //
    //addCompiler(new DummyTransformingCompiler()); // this one is for testing purposes only
    //addCompiler(new DummySourceGeneratingCompiler(myProject)); // this one is for testing purposes only
    /*
    // for testing only
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            FileTypeManager.getInstance().registerFileType(DummyTranslatingCompiler.INPUT_FILE_TYPE, DummyTranslatingCompiler.FILETYPE_EXTENSION);
            addTranslatingCompiler(
              new DummyTranslatingCompiler(),
              new HashSet<FileType>(Arrays.asList(DummyTranslatingCompiler.INPUT_FILE_TYPE)),
              new HashSet<FileType>(Arrays.asList( StdFileTypes.JAVA))
            );
          }
        });

      }
    });
    */
  }

  public Semaphore getCompilationSemaphore() {
    return myCompilationSemaphore;
  }

  public boolean isCompilationActive() {
    return myCompilationSemaphore.availablePermits() == 0;
  }

  public void addTranslatingCompiler(@NotNull final TranslatingCompiler compiler, final Set<FileType> inputTypes, final Set<FileType> outputTypes) {
    myTranslators.add(compiler);
    myCompilerToInputTypes.put(compiler, inputTypes);
    myCompilerToOutputTypes.put(compiler, outputTypes);

    final List<Chunk<Compiler>> chunks = ModuleCompilerUtil.getSortedChunks(createCompilerGraph((List<Compiler>)(List)myTranslators));

    myTranslators.clear();
    for (Chunk<Compiler> chunk : chunks) {
      for (Compiler chunkCompiler : chunk.getNodes()) {
        myTranslators.add((TranslatingCompiler)chunkCompiler);
      }
    }
  }

  @NotNull
  public Set<FileType> getRegisteredInputTypes(@NotNull final TranslatingCompiler compiler) {
    final Set<FileType> inputs = myCompilerToInputTypes.get(compiler);
    return inputs != null? Collections.unmodifiableSet(inputs) : Collections.<FileType>emptySet();
  }

  @NotNull
  public Set<FileType> getRegisteredOutputTypes(@NotNull final TranslatingCompiler compiler) {
    final Set<FileType> outs = myCompilerToOutputTypes.get(compiler);
    return outs != null? Collections.unmodifiableSet(outs) : Collections.<FileType>emptySet();
  }

  public final void addCompiler(@NotNull Compiler compiler) {
    if (compiler instanceof TranslatingCompiler) {
      myTranslators.add((TranslatingCompiler)compiler);

    }
    else {
      myCompilers.add(compiler);
    }
  }

  public final void removeCompiler(@NotNull Compiler compiler) {
    if (compiler instanceof TranslatingCompiler) {
      myTranslators.remove(compiler);
    }
    else {
      myCompilers.remove(compiler);
    }
    myCompilerToInputTypes.remove(compiler);
    myCompilerToOutputTypes.remove(compiler);
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
    for (final Compiler item : myTranslators) {
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
    return myBeforeTasks.toArray(new CompileTask[myBeforeTasks.size()]);
  }

  @NotNull
  public CompileTask[] getAfterTasks() {
    return myAfterTasks.toArray(new CompileTask[myAfterTasks.size()]);
  }

  public void compile(@NotNull VirtualFile[] files, CompileStatusNotification callback, final boolean trackDependencies) {
    compile(createFilesCompileScope(files), callback, trackDependencies);
  }

  public void compile(@NotNull Module module, CompileStatusNotification callback, final boolean trackDependencies) {
    new CompileDriver(myProject).compile(createModuleCompileScope(module, false), new ListenerNotificator(callback), trackDependencies, true);
  }

  public void compile(@NotNull CompileScope scope, CompileStatusNotification callback, final boolean trackDependencies) {
    new CompileDriver(myProject).compile(scope, new ListenerNotificator(callback), trackDependencies, false);
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

  public void removeCompilationStatusListener(@NotNull final CompilationStatusListener listener) {
    final MessageBusConnection connection = myListenerAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  // Compiler tests support

  private static List<String> ourDeletedPaths;
  private static List<String> ourRecompiledPaths;
  private static List<String> ourCompiledPaths;

  public static void testSetup() {
    ourDeletedPaths = new ArrayList<String>();
    ourRecompiledPaths = new ArrayList<String>();
    ourCompiledPaths = new ArrayList<String>();
  }

  public static void addDeletedPath(String path) {
    ourDeletedPaths.add(path);
  }

  public static void addRecompiledPath(String path) {
    ourRecompiledPaths.add(path);
  }

  public static void addCompiledPath(String path) {
    ourCompiledPaths.add(path);
  }

  public static String[] getPathsToDelete() {
    return ArrayUtil.toStringArray(ourDeletedPaths);
  }

  public static String[] getPathsToRecompile() {
    return ArrayUtil.toStringArray(ourRecompiledPaths);
  }

  public static String[] getPathsToCompile() {
    return ArrayUtil.toStringArray(ourCompiledPaths);
  }

  public static void clearPathsToCompile() {
    if (ourCompiledPaths != null) {
      ourCompiledPaths.clear();
    }
  }

  public boolean isExcludedFromCompilation(@NotNull VirtualFile file) {
    return CompilerConfiguration.getInstance(myProject).isExcludedFromCompilation(file);
  }

  private static final OutputToSourceMapping OUTPUT_TO_SOURCE_MAPPING = new OutputToSourceMapping() {
    public String getSourcePath(final String outputPath) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      final VirtualFile outputFile = lfs.findFileByPath(outputPath);

      final VirtualFile sourceFile = outputFile != null ? TranslatingCompilerFilesMonitor.getSourceFileByOutput(outputFile) : null;
      return sourceFile != null? sourceFile.getPath() : null;
    }
  };
  @NotNull
  public OutputToSourceMapping getJavaCompilerOutputMapping() {
    return OUTPUT_TO_SOURCE_MAPPING;
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
    return new ModuleCompileScope(module, includeDependentModules);
  }

  @NotNull
  public CompileScope createModulesCompileScope(@NotNull final Module[] modules, final boolean includeDependentModules) {
    return new ModuleCompileScope(myProject, modules, includeDependentModules);
  }

  @NotNull
  public CompileScope createModuleGroupCompileScope(@NotNull final Project project, @NotNull final Module[] modules, final boolean includeDependentModules) {
    return new ModuleCompileScope(project, modules, includeDependentModules);
  }

  @NotNull
  public CompileScope createProjectCompileScope(@NotNull final Project project) {
    return new ProjectCompileScope(project);
  }

  private Graph<Compiler> createCompilerGraph(final List<Compiler> compilers) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Compiler>() {
      public Collection<Compiler> getNodes() {
        return compilers;
      }

      public Iterator<Compiler> getIn(Compiler compiler) {
        final Set<FileType> compilerInput = myCompilerToInputTypes.get(compiler);
        if (compilerInput == null || compilerInput.isEmpty()) {
          return Collections.<Compiler>emptySet().iterator();
        }

        final Set<Compiler> inCompilers = new HashSet<Compiler>();

        for (Map.Entry<Compiler, Set<FileType>> entry : myCompilerToOutputTypes.entrySet()) {
          final Set<FileType> outputs = entry.getValue();
          Compiler comp = entry.getKey();
          if (outputs != null && ContainerUtil.intersects(compilerInput, outputs)) {
            inCompilers.add(comp);
          }
        }
        return inCompilers.iterator();
      }
    }));
  }
  
  private class ListenerNotificator implements CompileStatusNotification {
    private final CompileStatusNotification myDelegate;

    private ListenerNotificator(CompileStatusNotification delegate) {
      myDelegate = delegate;
    }

    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      myEventPublisher.compilationFinished(aborted, errors, warnings, compileContext);
      if (myDelegate != null) {
        myDelegate.finished(aborted, errors, warnings, compileContext);
      }
    }
  }
}
