/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.compiler.impl.*;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.util.InspectionValidator;
import com.intellij.openapi.compiler.util.InspectionValidatorWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.javac.DiagnosticOutputConsumer;
import org.jetbrains.jps.javac.ExternalJavacManager;
import org.jetbrains.jps.javac.OutputFileConsumer;
import org.jetbrains.jps.javac.OutputFileObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CompilerManagerImpl extends CompilerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerManagerImpl");

  private final Project myProject;

  private final List<Compiler> myCompilers = new ArrayList<>();

  private final List<CompileTask> myBeforeTasks = new ArrayList<>();
  private final List<CompileTask> myAfterTasks = new ArrayList<>();
  private final Set<FileType> myCompilableTypes = new HashSet<>();
  private final CompilationStatusListener myEventPublisher;
  private final Semaphore myCompilationSemaphore = new Semaphore(1, true);
  private final Set<ModuleType> myValidationDisabledModuleTypes = new HashSet<>();
  private final Set<LocalFileSystem.WatchRequest> myWatchRoots;
  private volatile ExternalJavacManager myExternalJavacManager;

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

    for (InspectionValidator validator : Extensions.getExtensions(InspectionValidator.EP_NAME, myProject)) {
      addCompiler(new InspectionValidatorWrapper(this, InspectionManager.getInstance(project), InspectionProjectProfileManager.getInstance(project), PsiDocumentManager.getInstance(project), PsiManager.getInstance(project), validator));
    }
    addCompilableFileType(StdFileTypes.JAVA);
    
    final File projectGeneratedSrcRoot = CompilerPaths.getGeneratedDataDirectory(project);
    projectGeneratedSrcRoot.mkdirs();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    myWatchRoots = lfs.addRootsToWatch(Collections.singletonList(FileUtil.toCanonicalPath(projectGeneratedSrcRoot.getPath())), true);
    Disposer.register(project, () -> {
      final ExternalJavacManager manager = myExternalJavacManager;
      myExternalJavacManager = null;
      if (manager != null) {
        manager.stop();
      }
      lfs.removeWatchedRoots(myWatchRoots);
      if (ApplicationManager.getApplication().isUnitTestMode()) {    // force cleanup for created compiler system directory with generated sources
        FileUtil.delete(CompilerPaths.getCompilerSystemDirectory(project));
      }
    });
  }

  // returns true if all javacs terminated
  public boolean waitForExternalJavacToTerminate(long time, @NotNull TimeUnit unit) {
    ExternalJavacManager externalJavacManager = myExternalJavacManager;
    if (externalJavacManager != null) {
      if (!externalJavacManager.waitForAllProcessHandlers(time, unit)) return false;
    }
    return true;
  }

  public Semaphore getCompilationSemaphore() {
    return myCompilationSemaphore;
  }

  @Override
  public boolean isCompilationActive() {
    return myCompilationSemaphore.availablePermits() == 0;
  }

  @Override
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

  @Override
  @Deprecated
  public void addTranslatingCompiler(@NotNull TranslatingCompiler compiler, Set<FileType> inputTypes, Set<FileType> outputTypes) {
    // empty
  }

  @Override
  public final void removeCompiler(@NotNull Compiler compiler) {
    for (List<CompileTask> tasks : Arrays.asList(myBeforeTasks, myAfterTasks)) {
      tasks.removeIf(
        task -> task instanceof FileProcessingCompilerAdapterTask && ((FileProcessingCompilerAdapterTask)task).getCompiler() == compiler);
    }
  }

  @Override
  @NotNull
  public <T  extends Compiler> T[] getCompilers(@NotNull Class<T> compilerClass) {
    return getCompilers(compilerClass, CompilerFilter.ALL);
  }

  @Override
  @NotNull
  public <T extends Compiler> T[] getCompilers(@NotNull Class<T> compilerClass, CompilerFilter filter) {
    final List<T> compilers = new ArrayList<>(myCompilers.size());
    for (final Compiler item : myCompilers) {
      if (compilerClass.isAssignableFrom(item.getClass()) && filter.acceptCompiler(item)) {
        compilers.add((T)item);
      }
    }
    final T[] array = (T[])Array.newInstance(compilerClass, compilers.size());
    return compilers.toArray(array);
  }

  @Override
  public void addCompilableFileType(@NotNull FileType type) {
    myCompilableTypes.add(type);
  }

  @Override
  public void removeCompilableFileType(@NotNull FileType type) {
    myCompilableTypes.remove(type);
  }

  @Override
  public boolean isCompilableFileType(@NotNull FileType type) {
    return myCompilableTypes.contains(type);
  }

  @Override
  public final void addBeforeTask(@NotNull CompileTask task) {
    myBeforeTasks.add(task);
  }

  @Override
  public final void addAfterTask(@NotNull CompileTask task) {
    myAfterTasks.add(task);
  }

  @Override
  @NotNull
  public CompileTask[] getBeforeTasks() {
    return getCompileTasks(myBeforeTasks, CompileTaskBean.CompileTaskExecutionPhase.BEFORE);
  }

  private CompileTask[] getCompileTasks(List<CompileTask> taskList, CompileTaskBean.CompileTaskExecutionPhase phase) {
    List<CompileTask> beforeTasks = new ArrayList<>(taskList);
    for (CompileTaskBean extension : CompileTaskBean.EP_NAME.getExtensions(myProject)) {
      if (extension.myExecutionPhase == phase) {
        beforeTasks.add(extension.getTaskInstance());
      }
    }
    return beforeTasks.toArray(new CompileTask[beforeTasks.size()]);
  }

  @Override
  @NotNull
  public CompileTask[] getAfterTasks() {
    return getCompileTasks(myAfterTasks, CompileTaskBean.CompileTaskExecutionPhase.AFTER);
  }

  @Override
  public void compile(@NotNull VirtualFile[] files, CompileStatusNotification callback) {
    compile(createFilesCompileScope(files), callback);
  }

  @Override
  public void compile(@NotNull Module module, CompileStatusNotification callback) {
    new CompileDriver(myProject).compile(createModuleCompileScope(module, false), new ListenerNotificator(callback));
  }

  @Override
  public void compile(@NotNull CompileScope scope, CompileStatusNotification callback) {
    new CompileDriver(myProject).compile(scope, new ListenerNotificator(callback));
  }

  @Override
  public void make(CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createProjectCompileScope(myProject), new ListenerNotificator(callback));
  }

  @Override
  public void make(@NotNull Module module, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createModuleCompileScope(module, true), new ListenerNotificator(callback));
  }

  @Override
  public void make(@NotNull Project project, @NotNull Module[] modules, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(createModuleGroupCompileScope(project, modules, true), new ListenerNotificator(callback));
  }

  @Override
  public void make(@NotNull CompileScope scope, CompileStatusNotification callback) {
    new CompileDriver(myProject).make(scope, new ListenerNotificator(callback));
  }

  @Override
  public void make(@NotNull CompileScope scope, CompilerFilter filter, @Nullable CompileStatusNotification callback) {
    final CompileDriver compileDriver = new CompileDriver(myProject);
    compileDriver.setCompilerFilter(filter);
    compileDriver.make(scope, new ListenerNotificator(callback));
  }

  @Override
  public boolean isUpToDate(@NotNull final CompileScope scope) {
    return new CompileDriver(myProject).isUpToDate(scope);
  }

  @Override
  public void rebuild(CompileStatusNotification callback) {
    new CompileDriver(myProject).rebuild(new ListenerNotificator(callback));
  }

  @Override
  public void executeTask(@NotNull CompileTask task, @NotNull CompileScope scope, String contentName, Runnable onTaskFinished) {
    final CompileDriver compileDriver = new CompileDriver(myProject);
    compileDriver.executeCompileTask(task, scope, contentName, onTaskFinished);
  }

  private final Map<CompilationStatusListener, MessageBusConnection> myListenerAdapters = new HashMap<>();

  @Override
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

  @Override
  public void removeCompilationStatusListener(@NotNull final CompilationStatusListener listener) {
    final MessageBusConnection connection = myListenerAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  @Override
  public boolean isExcludedFromCompilation(@NotNull VirtualFile file) {
    return CompilerConfiguration.getInstance(myProject).isExcludedFromCompilation(file);
  }

  @Override
  @NotNull
  public CompileScope createFilesCompileScope(@NotNull final VirtualFile[] files) {
    CompileScope[] scopes = new CompileScope[files.length];
    for(int i = 0; i < files.length; i++){
      scopes[i] = new OneProjectItemCompileScope(myProject, files[i]);
    }
    return new CompositeScope(scopes);
  }

  @Override
  @NotNull
  public CompileScope createModuleCompileScope(@NotNull final Module module, final boolean includeDependentModules) {
    return createModulesCompileScope(new Module[] {module}, includeDependentModules);
  }

  @Override
  @NotNull
  public CompileScope createModulesCompileScope(@NotNull final Module[] modules, final boolean includeDependentModules) {
    return createModulesCompileScope(modules, includeDependentModules, false);
  }

  @Override
  @NotNull
  public CompileScope createModulesCompileScope(@NotNull Module[] modules, boolean includeDependentModules, boolean includeRuntimeDependencies) {
    return new ModuleCompileScope(myProject, modules, includeDependentModules, includeRuntimeDependencies);
  }

  @Override
  @NotNull
  public CompileScope createModuleGroupCompileScope(@NotNull final Project project, @NotNull final Module[] modules, final boolean includeDependentModules) {
    return new ModuleCompileScope(project, modules, includeDependentModules);
  }

  @Override
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

  @Override
  public Collection<ClassObject> compileJavaCode(List<String> options,
                                                 Collection<File> platformCp,
                                                 Collection<File> classpath,
                                                 Collection<File> modulePath,
                                                 Collection<File> sourcePath,
                                                 Collection<File> files,
                                                 File outputDir) throws IOException, CompilationException {

    final Pair<Sdk, JavaSdkVersion> runtime = BuildManager.getJavacRuntimeSdk(myProject);

    final Sdk sdk = runtime.getFirst();
    final SdkTypeId type = sdk.getSdkType();
    String javaHome = null;
    if (type instanceof JavaSdkType) {
      javaHome = sdk.getHomePath();
      if (!isJdkOrJre(javaHome)) {
        // this can be a java-dependent SDK, implementing JavaSdkType
        // hack, because there is no direct way to obtain the java sdk, this sdk depends on
        final String binPath = ((JavaSdkType)type).getBinPath(sdk);
        javaHome = binPath != null? new File(binPath).getParent() : null;
        if (!isJdkOrJre(javaHome)) {
          javaHome = null;
        }
      }
    }
    if (javaHome == null) {
      throw new IOException("Was not able to determine JDK for project " + myProject.getName());
    }

    final OutputCollector outputCollector = new OutputCollector();
    DiagnosticCollector diagnostic = new DiagnosticCollector();

    final Set<File> sourceRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
    if (!sourcePath.isEmpty()) {
      sourceRoots.addAll(sourcePath);
    }
    else {
      for (File file : files) {
        final File parentFile = file.getParentFile();
        if (parentFile != null) {
          sourceRoots.add(parentFile);
        }
      }
    }
    final Map<File, Set<File>> outs = Collections.singletonMap(outputDir, sourceRoots);

    final ExternalJavacManager javacManager = getJavacManager();
    boolean compiledOk = javacManager != null && javacManager.forkJavac(
      javaHome, -1, Collections.emptyList(), options, platformCp, classpath, modulePath, sourcePath, files, outs, diagnostic, outputCollector,
      new JavacCompilerTool(), CanceledStatus.NULL
    );

    if (!compiledOk) {
      final List<CompilationException.Message> messages = new SmartList<>();
      for (Diagnostic<? extends JavaFileObject> d : diagnostic.getDiagnostics()) {
        final JavaFileObject source = d.getSource();
        final URI uri = source != null ? source.toUri() : null;
        messages.add(new CompilationException.Message(
          kindToCategory(d.getKind()), d.getMessage(Locale.US), uri != null? uri.toURL().toString() : null, (int)d.getLineNumber(), (int)d.getColumnNumber()
        ));
      }
      throw new CompilationException("Compilation failed", messages);
    }

    final List<ClassObject> result = new ArrayList<>();
    for (OutputFileObject fileObject : outputCollector.getCompiledClasses()) {
      final BinaryContent content = fileObject.getContent();
      result.add(new CompiledClass(fileObject.getName(), fileObject.getClassName(), content != null ? content.toByteArray() : null));
    }
    return result;
  }

  private static boolean isJdkOrJre(@Nullable String path) {
    return path != null && (JdkUtil.checkForJre(path) || JdkUtil.checkForJdk(path));
  }

  private static CompilerMessageCategory kindToCategory(Diagnostic.Kind kind) {
    switch (kind) {
      case ERROR: return CompilerMessageCategory.ERROR;
      case MANDATORY_WARNING: return CompilerMessageCategory.WARNING;
      case WARNING: return CompilerMessageCategory.WARNING;
      case NOTE: return CompilerMessageCategory.INFORMATION;
      default:
        return CompilerMessageCategory.INFORMATION;
    }
  }


  @Nullable
  private ExternalJavacManager getJavacManager() throws IOException {
    ExternalJavacManager manager = myExternalJavacManager;
    if (manager == null) {
      synchronized (this) {
        manager = myExternalJavacManager;
        if (manager == null) {
          final File compilerWorkingDir = getJavacCompilerWorkingDir();
          if (compilerWorkingDir == null) {
            return null; // should not happen for real projects
          }
          final int listenPort = NetUtils.findAvailableSocketPort();
          manager = new ExternalJavacManager(compilerWorkingDir);
          manager.start(listenPort);
          myExternalJavacManager = manager;
        }
      }
    }
    return manager;
  }

  @Override
  @Nullable
  public File getJavacCompilerWorkingDir() {
    final File projectBuildDir = BuildManager.getInstance().getProjectSystemDirectory(myProject);
    if (projectBuildDir == null) {
      return null;
    }
    projectBuildDir.mkdirs();
    return projectBuildDir;
  }

  private static class CompiledClass implements ClassObject {
    private final String myPath;
    private final String myClassName;
    private final byte[] myBytes;

    CompiledClass(String path, String className, byte[] bytes) {
      myPath = path;
      myClassName = className;
      myBytes = bytes;
    }

    @Override
    public String getPath() {
      return myPath;
    }

    @Override
    public String getClassName() {
      return myClassName;
    }

    @Nullable
    @Override
    public byte[] getContent() {
      return myBytes;
    }

    @Override
    public String toString() {
      return getClassName();
    }
  }

  private class ListenerNotificator implements CompileStatusNotification {
    @Nullable private final CompileStatusNotification myDelegate;

    private ListenerNotificator(@Nullable CompileStatusNotification delegate) {
      myDelegate = delegate;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      if (!myProject.isDisposed()) {
        myEventPublisher.compilationFinished(aborted, errors, warnings, compileContext);
      }
      if (myDelegate != null) {
        myDelegate.finished(aborted, errors, warnings, compileContext);
      }
    }
  }

  private static class DiagnosticCollector implements DiagnosticOutputConsumer {
    private final List<Diagnostic<? extends JavaFileObject>> myDiagnostics = new ArrayList<>();
    @Override
    public void outputLineAvailable(String line) {
      // for debugging purposes uncomment this line
      //System.out.println(line);
      if (line != null && line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX)) {
        LOG.info(line.trim());
      }
    }

    @Override
    public void registerImports(String className, Collection<String> imports, Collection<String> staticImports) {
      // ignore
    }

    @Override
    public void javaFileLoaded(File file) {
      // ignore
    }

    @Override
    public void customOutputData(String pluginId, String dataName, byte[] data) {
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      myDiagnostics.add(diagnostic);
    }

    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
      return myDiagnostics;
    }
  }


  private static class OutputCollector implements OutputFileConsumer {
    private final List<OutputFileObject> myClasses = new ArrayList<>();

    @Override
    public void save(@NotNull OutputFileObject fileObject) {
      myClasses.add(fileObject);
    }

    List<OutputFileObject> getCompiledClasses() {
      return myClasses;
    }
  }

}
