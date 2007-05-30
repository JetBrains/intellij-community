package com.intellij.compiler;

import com.intellij.compiler.impl.*;
import com.intellij.compiler.impl.javaCompiler.JavaCompiler;
import com.intellij.compiler.impl.make.IncrementalPackagingCompiler;
import com.intellij.compiler.impl.resourceCompiler.ResourceCompiler;
import com.intellij.compiler.impl.rmiCompiler.RmicCompiler;
import com.intellij.compiler.notNullVerification.NotNullVerifyingCompiler;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.StringInterner;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class CompilerManagerImpl extends CompilerManager {
  private final Project myProject;

  private List<Compiler> myCompilers = new ArrayList<Compiler>();
  private List<CompileTask> myBeforeTasks = new ArrayList<CompileTask>();
  private List<CompileTask> myAfterTasks = new ArrayList<CompileTask>();
  private Set<FileType> myCompilableTypes = new HashSet<FileType>();
  private EventDispatcher<CompilationStatusListener> myEventDispatcher = EventDispatcher.create(CompilationStatusListener.class);
  private final Semaphore myCompilationSemaphore = new Semaphore(1, true);

  public CompilerManagerImpl(Project project, CompilerConfigurationImpl compilerConfiguration) {
    myProject = project;

    // predefined compilers
    addCompiler(new JavaCompiler(myProject));
    addCompiler(new NotNullVerifyingCompiler(myProject));
    addCompiler(new ResourceCompiler(myProject, compilerConfiguration));
    addCompiler(new RmicCompiler(myProject));
    addCompiler(new IncrementalPackagingCompiler());

    addCompilableFileType(StdFileTypes.JAVA);
    //
    //addCompiler(new DummyTransformingCompiler()); // this one is for testing purposes only
    //addCompiler(new DummySourceGeneratingCompiler(myProject)); // this one is for testing purposes only
  }

  public Semaphore getCompilationSemaphore() {
    return myCompilationSemaphore;
  }

  public boolean isCompilationActive() {
    return myCompilationSemaphore.availablePermits() == 0;
  }

  public final void addCompiler(Compiler compiler) {
    myCompilers.add(compiler);
  }

  public final void removeCompiler(Compiler compiler) {
    myCompilers.remove(compiler);
  }

  public <T  extends Compiler> T[] getCompilers(Class<T> compilerClass) {
    final List<T> compilers = new ArrayList<T>(myCompilers.size());
    for (final Compiler item : myCompilers) {
      if (compilerClass.isAssignableFrom(item.getClass())) {
        compilers.add((T)item);
      }
    }
    T[] array = (T[])Array.newInstance(compilerClass, compilers.size());
    return compilers.toArray(array);
  }

  public void addCompilableFileType(FileType type) {
    myCompilableTypes.add(type);
  }

  public void removeCompilableFileType(FileType type) {
    myCompilableTypes.remove(type);
  }

  public boolean isCompilableFileType(FileType type) {
    return myCompilableTypes.contains(type);
  }

  public final void addBeforeTask(CompileTask task) {
    myBeforeTasks.add(task);
  }

  public final void addAfterTask(CompileTask task) {
    myAfterTasks.add(task);
  }

  public CompileTask[] getBeforeTasks() {
    return myBeforeTasks.toArray(new CompileTask[myBeforeTasks.size()]);
  }

  public CompileTask[] getAfterTasks() {
    return myAfterTasks.toArray(new CompileTask[myAfterTasks.size()]);
  }

  public void compile(VirtualFile[] files, CompileStatusNotification callback, final boolean trackDependencies) {
    compile(createFilesCompileScope(files), callback, trackDependencies);
  }

  public void compile(Module module, CompileStatusNotification callback, final boolean trackDependencies) {
    compile(createModuleCompileScope(module, false), callback, trackDependencies);
  }

  public void compile(CompileScope scope, CompileStatusNotification callback, final boolean trackDependencies) {
    new CompileDriver(myProject).compile(scope, new ListenerNotificator(callback), trackDependencies);
  }

  public void make(CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createProjectCompileScope(myProject), new ListenerNotificator(callback));
  }

  public void make(Module module, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createModuleCompileScope(module, true), new ListenerNotificator(callback));
  }

  public void make(Project project, Module[] modules, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createModuleGroupCompileScope(project, modules, true), new ListenerNotificator(callback));
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(scope, new ListenerNotificator(callback));
  }

  public boolean isUpToDate(final CompileScope scope) {
    return new CompileDriver(myProject).isUpToDate(scope);
  }

  public void rebuild(CompileStatusNotification callback) {
    new CompileDriver(myProject).rebuild(new ListenerNotificator(callback));
  }

  public void executeTask(CompileTask task, CompileScope scope, String contentName, Runnable onTaskFinished) {
    final CompileDriver compileDriver = new CompileDriver(myProject);
    compileDriver.executeCompileTask(task, scope, contentName, onTaskFinished);
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

  /**
   * @param path a relative to output directory path
   */
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
    return ourDeletedPaths.toArray(new String[ourDeletedPaths.size()]);
  }

  public static String[] getPathsToRecompile() {
    return ourRecompiledPaths.toArray(new String[ourRecompiledPaths.size()]);
  }

  public static String[] getPathsToCompile() {
    return ourCompiledPaths.toArray(new String[ourCompiledPaths.size()]);
  }

  public static void clearPathsToCompile() {
    if (ourCompiledPaths != null) {
      ourCompiledPaths.clear();
    }
  }

  public void addCompilationStatusListener(CompilationStatusListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeCompilationStatusListener(CompilationStatusListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public boolean isExcludedFromCompilation(VirtualFile file) {
    return CompilerConfigurationImpl.getInstance(myProject).isExcludedFromCompilation(file);
  }

  public OutputToSourceMapping getJavaCompilerOutputMapping() {
    final String cachesDir = CompilerPaths.getCacheStoreDirectory(myProject).getPath().replace('/', File.separatorChar);
    final StringInterner interner = new StringInterner();
    final JavaCompiler[] javaCompilers = getCompilers(JavaCompiler.class);
    final TranslatingCompilerStateCache[] caches = new TranslatingCompilerStateCache[javaCompilers.length];
    for (int idx = 0; idx < javaCompilers.length; idx++) {
      caches[idx] = new TranslatingCompilerStateCache(cachesDir, CompileDriver.getCompilerIdString(javaCompilers[idx]), interner);
    }
    return new OutputToSourceMappingImpl(caches);
  }

  public CompileScope createFilesCompileScope(final VirtualFile[] files) {
    CompileScope[] scopes = new CompileScope[files.length];
    for(int i = 0; i < files.length; i++){
      scopes[i] = new OneProjectItemCompileScope(myProject, files[i]);
    }
    return new CompositeScope(scopes);
  }

  public CompileScope createModuleCompileScope(final Module module, final boolean includeDependentModules) {
    return new ModuleCompileScope(module, includeDependentModules);
  }

  public CompileScope createModulesCompileScope(final Module[] modules, final boolean includeDependentModules) {
    return new ModuleCompileScope(myProject, modules, includeDependentModules);
  }

  public CompileScope createModuleGroupCompileScope(final Project project, final Module[] modules, final boolean includeDependentModules) {
    return new ModuleCompileScope(project, modules, includeDependentModules);
  }

  public CompileScope createProjectCompileScope(final Project project) {
    return new ProjectCompileScope(project);
  }

  private class ListenerNotificator implements CompileStatusNotification {
    private final CompileStatusNotification myDelegate;

    public ListenerNotificator(CompileStatusNotification delegate) {
      myDelegate = delegate;
    }

    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      myEventDispatcher.getMulticaster().compilationFinished(aborted, errors, warnings, compileContext);
      if (myDelegate != null) {
        myDelegate.finished(aborted, errors, warnings, compileContext);
      }
    }
  }

  private static class OutputToSourceMappingImpl implements OutputToSourceMapping {
    private final TranslatingCompilerStateCache[] myCaches;

    public OutputToSourceMappingImpl(final TranslatingCompilerStateCache[] caches) {
      myCaches = caches;
    }

    public String getSourcePath(final String outputPath) {
      for (TranslatingCompilerStateCache cache : myCaches) {
        final String sourceUrl = cache.getSourceUrl(outputPath);
        if (sourceUrl != null) {
          return VirtualFileManager.extractPath(sourceUrl);
        }
      }
      return null;
    }
  }
}
