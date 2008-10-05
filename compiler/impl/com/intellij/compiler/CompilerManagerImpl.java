package com.intellij.compiler;

import com.intellij.compiler.impl.*;
import com.intellij.compiler.impl.javaCompiler.JavaCompiler;
import com.intellij.compiler.impl.packagingCompiler.IncrementalPackagingCompiler;
import com.intellij.compiler.impl.resourceCompiler.ResourceCompiler;
import com.intellij.compiler.impl.rmiCompiler.RmicCompiler;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.Semaphore;

public class CompilerManagerImpl extends CompilerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerManagerImpl");
  private final Project myProject;

  private List<Compiler> myCompilers = new ArrayList<Compiler>();
  private List<TranslatingCompiler> myTranslators = new ArrayList<TranslatingCompiler>();
  
  private List<CompileTask> myBeforeTasks = new ArrayList<CompileTask>();
  private List<CompileTask> myAfterTasks = new ArrayList<CompileTask>();
  private Set<FileType> myCompilableTypes = new HashSet<FileType>();
  private CompilationStatusListener myEventPublisher;
  private final Semaphore myCompilationSemaphore = new Semaphore(1, true);
  private final Map<Compiler, Set<FileType>> myCompilerToInputTypes = new HashMap<Compiler, Set<FileType>>();
  private final Map<Compiler, Set<FileType>> myCompilerToOutputTypes = new HashMap<Compiler, Set<FileType>>();

  public CompilerManagerImpl(final Project project, CompilerConfigurationImpl compilerConfiguration, MessageBus messageBus) {
    myProject = project;
    myEventPublisher = messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS);

    // predefined compilers
    addTranslatingCompiler(new JavaCompiler(myProject), new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA)), new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)));
    addCompiler(new ResourceCompiler(myProject, compilerConfiguration));
    addCompiler(new RmicCompiler(myProject));
    addCompiler(new IncrementalPackagingCompiler(myProject));

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

  public void addTranslatingCompiler(final TranslatingCompiler compiler, final Set<FileType> inputTypes, final Set<FileType> outputTypes) {
    myTranslators.add(compiler);
    myCompilerToInputTypes.put(compiler, inputTypes);
    myCompilerToOutputTypes.put(compiler, outputTypes);
    
    final List<Chunk<Compiler>> chunks = ModuleCompilerUtil.getSortedChunks(createCompilerGraph(myTranslators.toArray(new Compiler[myTranslators.size()])));
    
    myTranslators.clear();
    for (Chunk<Compiler> chunk : chunks) {
      for (Compiler chunkCompiler : chunk.getNodes()) {
        myTranslators.add((TranslatingCompiler)chunkCompiler);
      }
    }
  }

  @NotNull
  public Set<FileType> getRegisteredInputTypes(final TranslatingCompiler compiler) {
    final Set<FileType> inputs = myCompilerToInputTypes.get(compiler);
    return inputs != null? Collections.unmodifiableSet(inputs) : Collections.<FileType>emptySet();
  }

  @NotNull
  public Set<FileType> getRegisteredOutputTypes(final TranslatingCompiler compiler) {
    final Set<FileType> outs = myCompilerToOutputTypes.get(compiler);
    return outs != null? Collections.unmodifiableSet(outs) : Collections.<FileType>emptySet();
  }

  public final void addCompiler(Compiler compiler) {
    if (compiler instanceof TranslatingCompiler) {
      myTranslators.add((TranslatingCompiler)compiler);
      
    }
    else {
      myCompilers.add(compiler);
    }
  }

  public final void removeCompiler(Compiler compiler) {
    if (compiler instanceof TranslatingCompiler) {
      myTranslators.remove((TranslatingCompiler)compiler);
    }
    else {
      myCompilers.remove(compiler);
    }
    myCompilerToInputTypes.remove(compiler);
    myCompilerToOutputTypes.remove(compiler);
  }

  public <T  extends Compiler> T[] getCompilers(Class<T> compilerClass) {
    final List<T> compilers = new ArrayList<T>(myCompilers.size());
    for (final Compiler item : myCompilers) {
      if (compilerClass.isAssignableFrom(item.getClass())) {
        compilers.add((T)item);
      }
    }
    for (final Compiler item : myTranslators) {
      if (compilerClass.isAssignableFrom(item.getClass())) {
        compilers.add((T)item);
      }
    }
    final T[] array = (T[])Array.newInstance(compilerClass, compilers.size());
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

  private final Map<CompilationStatusListener, MessageBusConnection> myListenerAdapters = new HashMap<CompilationStatusListener, MessageBusConnection>();

  public void addCompilationStatusListener(final CompilationStatusListener listener) {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    myListenerAdapters.put(listener, connection);
    connection.subscribe(CompilerTopics.COMPILATION_STATUS, listener);
  }

  public void removeCompilationStatusListener(final CompilationStatusListener listener) {
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

  public boolean isExcludedFromCompilation(VirtualFile file) {
    return CompilerConfigurationImpl.getInstance(myProject).isExcludedFromCompilation(file);
  }

  public OutputToSourceMapping getJavaCompilerOutputMapping() {
    return new OutputToSourceMapping() {
      public String getSourcePath(final String outputPath) {
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        final VirtualFile outputFile = lfs.findFileByPath(outputPath);

        final VirtualFile sourceFile = outputFile != null ? TranslatingCompilerFilesMonitor.getSourceFileByOutput(outputFile) : null;
        return sourceFile != null? sourceFile.getPath() : null;
      }
    };
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

  private Graph<Compiler> createCompilerGraph(final Compiler[] compilers) {
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Compiler>() {
      public Collection<Compiler> getNodes() {
        return Arrays.asList(compilers);
      }

      public Iterator<Compiler> getIn(Compiler compiler) {
        final Set<FileType> compilerInput = myCompilerToInputTypes.get(compiler);
        if (compilerInput == null || compilerInput.isEmpty()) {
          return Collections.<Compiler>emptySet().iterator();
        }
        
        final Set<Compiler> inCompilers = new HashSet<Compiler>();
        
        for (Compiler c : myCompilerToOutputTypes.keySet()) {
          final Set<FileType> outputs = myCompilerToOutputTypes.get(c);
          if (outputs != null && ModuleCompilerUtil.intersects(compilerInput, outputs)) {
            inCompilers.add(c);
          }
        }
        return inCompilers.iterator();
      }
    }));
  }
  
  private class ListenerNotificator implements CompileStatusNotification {
    private final CompileStatusNotification myDelegate;

    public ListenerNotificator(CompileStatusNotification delegate) {
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
