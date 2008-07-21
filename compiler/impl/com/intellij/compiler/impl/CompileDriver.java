/**
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 1:42:26 PM
 */
package com.intellij.compiler.impl;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.compiler.*;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Chunk;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ProfilingUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.*;

public class CompileDriver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileDriver");

  private final Project myProject;
  private Map<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> myGenerationCompilerModuleToOutputDirMap; // [IntermediateOutputCompiler, Module] -> [ProductionSources, TestSources]
  private String myCachesDirectoryPath;
  private boolean myShouldClearOutputDirectory;

  private Map<Module, String> myModuleOutputPaths = new HashMap<Module, String>();
  private Map<Module, String> myModuleTestOutputPaths = new HashMap<Module, String>();

  @NonNls private static final String VERSION_FILE_NAME = "version.dat";
  @NonNls private static final String LOCK_FILE_NAME = "in_progress.dat";

  @NonNls private static final boolean GENERATE_CLASSPATH_INDEX = "true".equals(System.getProperty("generate.classpath.index"));

  private final FileProcessingCompilerAdapterFactory myProcessingCompilerAdapterFactory;
  private final FileProcessingCompilerAdapterFactory myPackagingCompilerAdapterFactory;

  public CompileDriver(Project project) {
    myProject = project;
    myCachesDirectoryPath = CompilerPaths.getCacheStoreDirectory(myProject).getPath().replace('/', File.separatorChar);
    myShouldClearOutputDirectory = CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY;

    myGenerationCompilerModuleToOutputDirMap = new HashMap<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>>();

    final IntermediateOutputCompiler[] generatingCompilers = CompilerManager.getInstance(myProject).getCompilers(IntermediateOutputCompiler.class);
    if (generatingCompilers.length > 0) {
      final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
      for (IntermediateOutputCompiler compiler : generatingCompilers) {
        for (final Module module : allModules) {
          final VirtualFile productionOutput = lookupVFile(compiler, module, false);
          final VirtualFile testOutput = lookupVFile(compiler, module, true);
          final Pair<IntermediateOutputCompiler, Module> pair = new Pair<IntermediateOutputCompiler, Module>(compiler, module);
          final Pair<VirtualFile, VirtualFile> outputs = new Pair<VirtualFile, VirtualFile>(productionOutput, testOutput);
          myGenerationCompilerModuleToOutputDirMap.put(pair, outputs);
        }
      }
    }

    myProcessingCompilerAdapterFactory = new FileProcessingCompilerAdapterFactory() {
      public FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler) {
        return new FileProcessingCompilerAdapter(context, compiler);
      }
    };
    myPackagingCompilerAdapterFactory = new FileProcessingCompilerAdapterFactory() {
      public FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler) {
        return new PackagingCompilerAdapter(context, (PackagingCompiler)compiler);
      }
    };
  }

  public void rebuild(CompileStatusNotification callback) {
    doRebuild(callback, null, true, addAdditionalRoots(new ProjectCompileScope(myProject)));
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    scope = addAdditionalRoots(scope);
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, false, callback, null, true, false);
    }
  }

  public boolean isUpToDate(CompileScope scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation started");
    }
    scope = addAdditionalRoots(scope);

    final CompilerTask task = new CompilerTask(myProject, true, "", true);
    final CompileContextImpl compileContext =
      new CompileContextImpl(myProject, task, scope, createDependencyCache(), this, true);

    checkCachesVersion(compileContext);
    if (compileContext.isRebuildRequested()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Rebuild requested, up-to-date=false");
      }
      return false;
    }

    for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      final Pair<VirtualFile, VirtualFile> outputs = myGenerationCompilerModuleToOutputDirMap.get(pair);
      compileContext.assignModule(outputs.getFirst(), pair.getSecond(), false);
      compileContext.assignModule(outputs.getSecond(), pair.getSecond(), true);
    }

    final Ref<ExitStatus> status = new Ref<ExitStatus>();

    task.start(new Runnable() {
      public void run() {
        status.set(doCompile(compileContext, false, false, false, getAllOutputDirectories(), true));
      }
    });

    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation finished");
    }

    return ExitStatus.UP_TO_DATE.equals(status.get());
  }

  private DependencyCache createDependencyCache() {
    return new DependencyCache(myCachesDirectoryPath + File.separator + ".dependency-info");
  }

  public void compile(CompileScope scope, CompileStatusNotification callback, boolean trackDependencies) {
    if (trackDependencies) {
      scope = new TrackDependenciesScope(scope);
    }
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, true, callback, null, true, trackDependencies);
    }
  }

  private static class CompileStatus {
    final int CACHE_FORMAT_VERSION;
    final boolean COMPILATION_IN_PROGRESS;

    public CompileStatus(int cacheVersion, boolean isCompilationInProgress) {
      CACHE_FORMAT_VERSION = cacheVersion;
      COMPILATION_IN_PROGRESS = isCompilationInProgress;
    }
  }

  private CompileStatus readStatus() {
    final boolean isInProgress = getLockFile().exists();
    int version = -1;
    try {
      final File versionFile = new File(myCachesDirectoryPath, VERSION_FILE_NAME);
      DataInputStream in = new DataInputStream(new FileInputStream(versionFile));
      try {
        version = in.readInt();
      }
      finally {
        in.close();
      }
    }
    catch (FileNotFoundException e) {
      // ignore
    }
    catch (IOException e) {
      LOG.info(e);  // may happen in case of IDEA crashed and the file is not written properly
      return null;
    }
    return new CompileStatus(version, isInProgress);
  }

  private void writeStatus(CompileStatus status, CompileContext context) {
    final File statusFile = new File(myCachesDirectoryPath, VERSION_FILE_NAME);
    final File lockFile = getLockFile();
    try {
      statusFile.createNewFile();
      DataOutputStream out = new DataOutputStream(new FileOutputStream(statusFile));
      try {
        out.writeInt(status.CACHE_FORMAT_VERSION);
      }
      finally {
        out.close();
      }
      if (status.COMPILATION_IN_PROGRESS) {
        lockFile.createNewFile();
      }
      else {
        lockFile.delete();
      }
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.exception", e.getMessage()), null, -1, -1);
    }
  }

  private File getLockFile() {
    return new File(CompilerPaths.getCompilerSystemDirectory(myProject), LOCK_FILE_NAME);
  }

  private void doRebuild(CompileStatusNotification callback,
                         CompilerMessage message,
                         final boolean checkCachesVersion,
                         final CompileScope compileScope) {
    if (validateCompilerConfiguration(compileScope, true)) {
      startup(compileScope, true, false, callback, message, checkCachesVersion, false);
    }
  }

  private CompileScope addAdditionalRoots(CompileScope originalScope) {
    CompileScope scope = originalScope;
    for (final Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      final Pair<VirtualFile, VirtualFile> outputs = myGenerationCompilerModuleToOutputDirMap.get(pair);
      scope = new CompositeScope(scope, new FileSetCompileScope(new VirtualFile[]{outputs.getFirst(), outputs.getSecond()}, new Module[]{pair.getSecond()}));
    }

    final AdditionalCompileScopeProvider[] scopeProviders = Extensions.getExtensions(AdditionalCompileScopeProvider.EXTENSION_POINT_NAME);
    CompileScope baseScope = scope;
    for (AdditionalCompileScopeProvider scopeProvider : scopeProviders) {
      final CompileScope additionalScope = scopeProvider.getAdditionalScope(baseScope);
      if (additionalScope != null) {
        scope = new CompositeScope(scope, additionalScope);
      }
    }
    return scope;
  }

  public static final Key<Long> COMPILATION_START_TIMESTAMP = Key.create("COMPILATION_START_TIMESTAMP");

  private void startup(final CompileScope scope,
                       final boolean isRebuild,
                       final boolean forceCompile,
                       final CompileStatusNotification callback,
                       final CompilerMessage message,
                       final boolean checkCachesVersion,
                       final boolean trackDependencies) {
    final CompilerTask compileTask = new CompilerTask(myProject, CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND,
                                                    forceCompile
                                                    ? CompilerBundle.message("compiler.content.name.compile")
                                                    : CompilerBundle.message("compiler.content.name.make"), false);
    final WindowManager windowManager = WindowManager.getInstance();
    if (windowManager != null) {
      windowManager.getStatusBar(myProject).setInfo("");
    }

    final DependencyCache dependencyCache = createDependencyCache();
    final CompileContextImpl compileContext =
      new CompileContextImpl(myProject, compileTask, scope, dependencyCache, this, !isRebuild && !forceCompile);
    compileContext.putUserData(COMPILATION_START_TIMESTAMP, LocalTimeCounter.currentTime());
    for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      final Pair<VirtualFile, VirtualFile> outputs = myGenerationCompilerModuleToOutputDirMap.get(pair);
      compileContext.assignModule(outputs.getFirst(), pair.getSecond(), false);
      compileContext.assignModule(outputs.getSecond(), pair.getSecond(), true);
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    compileTask.start(new Runnable() {
      public void run() {
        try {
          if (myProject.isDisposed()) {
            return;
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("COMPILATION STARTED");
          }
          if (message != null) {
            compileContext.addMessage(message);
          }
          doCompile(compileContext, isRebuild, forceCompile, callback, checkCachesVersion, trackDependencies);
        }
        finally {
          if (LOG.isDebugEnabled()) {
            LOG.debug("COMPILATION FINISHED");
          }
        }
      }
    });
  }

  private void doCompile(final CompileContextImpl compileContext,
                         final boolean isRebuild,
                         final boolean forceCompile,
                         final CompileStatusNotification callback,
                         final boolean checkCachesVersion,
                         final boolean trackDependencies) {
    ExitStatus status = ExitStatus.ERRORS;
    boolean wereExceptions = false;
    try {
      compileContext.getProgressIndicator().pushState();
      if (checkCachesVersion) {
        checkCachesVersion(compileContext);
        if (compileContext.isRebuildRequested()) {
          return;
        }
      }
      writeStatus(new CompileStatus(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION, true), compileContext);
      if (compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return;
      }

      status = doCompile(compileContext, isRebuild, forceCompile, trackDependencies, getAllOutputDirectories(), false);
    }
    catch (Throwable ex) {
      wereExceptions = true;
      PluginId pluginId = IdeErrorsDialog.findPluginId(ex);
      if (pluginId != null) {
        throw new PluginException(ex, pluginId);
      }
      throw new RuntimeException(ex);
    }
    finally {
      dropDependencyCache(compileContext);
      compileContext.getProgressIndicator().popState();
      final ExitStatus _status = status;
      if (compileContext.isRebuildRequested()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doRebuild(callback, new CompilerMessageImpl(myProject, CompilerMessageCategory.INFORMATION, compileContext.getRebuildReason(),
                                                        null, -1, -1, null), false, compileContext.getCompileScope());
          }
        }, ModalityState.NON_MODAL);
      }
      else {
        writeStatus(new CompileStatus(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION, wereExceptions), compileContext);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final int errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
            final int warningCount = compileContext.getMessageCount(CompilerMessageCategory.WARNING);
            final String statusMessage = createStatusMessage(_status, warningCount, errorCount);
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
            if (statusBar != null) { // because this code is in invoke later, the code may work for already closed project
              // in case another project was opened in the frame while the compiler was working (See SCR# 28591)
              statusBar.setInfo(statusMessage);
            }
            if (_status != ExitStatus.UP_TO_DATE && compileContext.getMessageCount(null) > 0) {
              compileContext.addMessage(CompilerMessageCategory.INFORMATION, statusMessage, null, -1, -1);
            }
            if (callback != null) {
              callback.finished(_status == ExitStatus.CANCELLED, errorCount, warningCount, compileContext);
            }

            ProfilingUtil.operationFinished("make");
          }
        }, ModalityState.NON_MODAL);
      }
    }
  }

  private void checkCachesVersion(final CompileContextImpl compileContext) {
    final CompileStatus compileStatus = readStatus();
    if (compileStatus == null) {
      compileContext.requestRebuildNextTime(CompilerBundle.message("error.compiler.caches.corrupted"));
    }
    else if (compileStatus.CACHE_FORMAT_VERSION != -1 &&
             compileStatus.CACHE_FORMAT_VERSION != CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION) {
      compileContext.requestRebuildNextTime(CompilerBundle.message("error.caches.old.format"));
    }
    else if (compileStatus.COMPILATION_IN_PROGRESS) {
      compileContext.requestRebuildNextTime(CompilerBundle.message("error.previous.compilation.failed"));
    }
  }

  private static String createStatusMessage(final ExitStatus status, final int warningCount, final int errorCount) {
    if (status == ExitStatus.CANCELLED) {
      return CompilerBundle.message("status.compilation.aborted");
    }
    if (status == ExitStatus.UP_TO_DATE) {
      return CompilerBundle.message("status.all.up.to.date");
    }
    if (status == ExitStatus.SUCCESS) {
      return warningCount > 0
             ? CompilerBundle.message("status.compilation.completed.successfully.with.warnings", warningCount)
             : CompilerBundle.message("status.compilation.completed.successfully");
    }
    return CompilerBundle.message("status.compilation.completed.successfully.with.warnings.and.errors", errorCount, warningCount);
  }

  private static class ExitStatus {
    private String myName;

    private ExitStatus(@NonNls String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }

    public static final ExitStatus CANCELLED = new ExitStatus("CANCELLED");
    public static final ExitStatus ERRORS = new ExitStatus("ERRORS");
    public static final ExitStatus SUCCESS = new ExitStatus("SUCCESS");
    public static final ExitStatus UP_TO_DATE = new ExitStatus("UP_TO_DATE");
  }

  private static class ExitException extends Exception {
    private final ExitStatus myStatus;

    public ExitException(ExitStatus status) {
      myStatus = status;
    }

    public ExitStatus getExitStatus() {
      return myStatus;
    }
  }

  private ExitStatus doCompile(CompileContextEx context,
                               boolean isRebuild,
                               final boolean forceCompile,
                               final boolean trackDependencies,
                               final Set<File> outputDirectories,
                               final boolean onlyCheckStatus) {
    try {
      if (isRebuild) {
        deleteAll(context, outputDirectories);
        if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
          if (LOG.isDebugEnabled()) {
            logErrorMessages(context);
          }
          return ExitStatus.ERRORS;
        }
      }

      if (!onlyCheckStatus) {
        try {
          context.getProgressIndicator().pushState();
          if (!executeCompileTasks(context, true)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Compilation cancelled");
            }
            return ExitStatus.CANCELLED;
          }
        }
        finally {
          context.getProgressIndicator().popState();
        }
      }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        if (LOG.isDebugEnabled()) {
          logErrorMessages(context);
        }
        return ExitStatus.ERRORS;
      }
      
      // need this to make sure the VFS is built
      final List<VirtualFile> outputsToRefresh = new ArrayList<VirtualFile>();
      for (VirtualFile output : context.getAllOutputDirectories()) {
        walkChildren(output);
        outputsToRefresh.add(output);
      }
      for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
        final Pair<VirtualFile, VirtualFile> generated = myGenerationCompilerModuleToOutputDirMap.get(pair);
        walkChildren(generated.getFirst());
        outputsToRefresh.add(generated.getFirst());
        walkChildren(generated.getSecond());
        outputsToRefresh.add(generated.getSecond());
      }
      RefreshQueue.getInstance().refresh(false, true, null, outputsToRefresh.toArray(new VirtualFile[outputsToRefresh.size()]));
      
      boolean didSomething = false;

      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);

      try {
        didSomething |= generateSources(compilerManager, context, forceCompile, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, SourceInstrumentingCompiler.class,
                                                      myProcessingCompilerAdapterFactory, forceCompile, true, onlyCheckStatus);

        didSomething |= translate(context, compilerManager, forceCompile, isRebuild, trackDependencies, outputDirectories, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassInstrumentingCompiler.class,
                                                      myProcessingCompilerAdapterFactory, isRebuild, false, onlyCheckStatus);

        // explicitly passing forceCompile = false because in scopes that is narrower than ProjectScope it is impossible
        // to understand whether the class to be processed is in scope or not. Otherwise compiler may process its items even if
        // there were changes in completely independent files.
        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassPostProcessingCompiler.class,
                                                      myProcessingCompilerAdapterFactory, isRebuild, false, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, PackagingCompiler.class, myPackagingCompilerAdapterFactory,
                                                      isRebuild, true, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, Validator.class, myProcessingCompilerAdapterFactory,
                                                      forceCompile, true, onlyCheckStatus);
      }
      catch (ExitException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(e);
          logErrorMessages(context);
        }
        return e.getExitStatus();
      }
      finally {
        // drop in case it has not been dropped yet.
        dropDependencyCache(context);

        final VirtualFile[] allOutputDirs = context.getAllOutputDirectories();
        
        if (didSomething && GENERATE_CLASSPATH_INDEX) {
          context.getProgressIndicator().pushState();
          context.getProgressIndicator().setText("Generating classpath index...");
          int count = 0;
          for (VirtualFile file : allOutputDirs) {
            context.getProgressIndicator().setFraction(((double)++count) / allOutputDirs.length);
            createClasspathIndex(file); 
          }
          context.getProgressIndicator().popState();
        }
        
        if (!context.getProgressIndicator().isCanceled() && context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
          RefreshQueue.getInstance().refresh(true, true, new Runnable() {
            public void run() {
              CompilerDirectoryTimestamp.updateTimestamp(Arrays.asList(allOutputDirs));
            }
          }, allOutputDirs);
        }
      }

      if (!onlyCheckStatus) {
        try {
          context.getProgressIndicator().pushState();
          if (!executeCompileTasks(context, false)) {
            return ExitStatus.CANCELLED;
          }
        }
        finally {
          context.getProgressIndicator().popState();
        }
      }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        if (LOG.isDebugEnabled()) {
          logErrorMessages(context);
        }
        return ExitStatus.ERRORS;
      }
      if (!didSomething) {
        return ExitStatus.UP_TO_DATE;
      }
      return ExitStatus.SUCCESS;
    }
    catch (ProcessCanceledException e) {
      return ExitStatus.CANCELLED;
    }
  }

  private static void logErrorMessages(final CompileContext context) {
    final CompilerMessage[] errors = context.getMessages(CompilerMessageCategory.ERROR);
    if (errors.length > 0) {
      LOG.debug("There were errors while deleting output directories");
      for (CompilerMessage error : errors) {
        LOG.debug("\t" + error.getMessage());
      }
    }
  }

  private static void walkChildren(VirtualFile from) {
    final VirtualFile[] files = from.getChildren();
    if (files != null && files.length > 0) {
      for (VirtualFile file : files) {
        walkChildren(file);
      }
    }
  }

  private static void createClasspathIndex(final VirtualFile file) {
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(new File(VfsUtil.virtualToIoFile(file), "classpath.index")));
      try {
        writeIndex(writer, file, file);
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      // Ignore. Failed to create optional classpath index
    }
  }

  private static void writeIndex(final BufferedWriter writer, final VirtualFile root, final VirtualFile file) throws IOException {
    writer.write(VfsUtil.getRelativePath(file, root, '/'));
    writer.write('\n');

    for (VirtualFile child : file.getChildren()) {
      writeIndex(writer, root, child);
    }
  }

  private static void dropDependencyCache(final CompileContextEx context) {
    context.getProgressIndicator().pushState();
    try {
      context.getProgressIndicator().setText(CompilerBundle.message("progress.saving.caches"));
      context.getDependencyCache().dispose();
    }
    finally {
      context.getProgressIndicator().popState();
    }
  }

  private boolean generateSources(final CompilerManager compilerManager,
                                  CompileContextEx context,
                                  final boolean forceCompile,
                                  final boolean onlyCheckStatus) throws ExitException {
    boolean didSomething = false;

    final SourceGeneratingCompiler[] sourceGenerators = compilerManager.getCompilers(SourceGeneratingCompiler.class);
    for (final SourceGeneratingCompiler sourceGenerator : sourceGenerators) {
      if (context.getProgressIndicator().isCanceled()) {
        throw new ExitException(ExitStatus.CANCELLED);
      }

      final boolean generatedSomething = generateOutput(context, sourceGenerator, forceCompile, onlyCheckStatus);

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        throw new ExitException(ExitStatus.ERRORS);
      }
      didSomething |= generatedSomething;
    }
    return didSomething;
  }

  private boolean translate(final CompileContextEx context,
                            final CompilerManager compilerManager,
                            final boolean forceCompile,
                            boolean isRebuild,
                            final boolean trackDependencies,
                            final Set<File> outputDirectories,
                            final boolean onlyCheckStatus) throws ExitException {

    boolean didSomething = false;

    final TranslatingCompiler[] translators = compilerManager.getCompilers(TranslatingCompiler.class);
    
    final Set<FileType> generatedTypes = new HashSet<FileType>();
    VfsSnapshot snapshot = null;

    for (final TranslatingCompiler translator : translators) {
      if (context.getProgressIndicator().isCanceled()) {
        throw new ExitException(ExitStatus.CANCELLED);
      }

      if (snapshot == null || ModuleCompilerUtil.intersects(generatedTypes, compilerManager.getRegisteredInputTypes(translator))) {
        // rescan snapshot if previously generated files can influence the input of this compiler
        snapshot = ApplicationManager.getApplication().runReadAction(new Computable<VfsSnapshot>() {
          public VfsSnapshot compute() {
            return new VfsSnapshot(context.getCompileScope().getFiles(null, true));
          }
        });
      }

      final CompileContextEx _context;
      if (translator instanceof IntermediateOutputCompiler) {
        // wrap compile context so that output goes into intermediate directories
        final IntermediateOutputCompiler _translator = (IntermediateOutputCompiler)translator;
        _context = new CompileContextExProxy(context) {
          public VirtualFile getModuleOutputDirectory(final Module module) {
            return getGenerationOutputDir(_translator, module, false);
          }
        
          public VirtualFile getModuleOutputDirectoryForTests(final Module module) {
            return getGenerationOutputDir(_translator, module, true);
          }
        };
      }
      else {
        _context = context;
      }
      final boolean compiledSomething =
        compileSources(_context, snapshot, translator, forceCompile, isRebuild, trackDependencies, outputDirectories, onlyCheckStatus);
      
      if (compiledSomething) {
        generatedTypes.addAll(compilerManager.getRegisteredOutputTypes(translator));
      }
      // free memory earlier to leave other compilers more space
      dropDependencyCache(context);

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        throw new ExitException(ExitStatus.ERRORS);
      }

      didSomething |= compiledSomething;
    }
    return didSomething;
  }

  private static interface FileProcessingCompilerAdapterFactory {
    FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler);
  }

  private boolean invokeFileProcessingCompilers(final CompilerManager compilerManager,
                                                CompileContextEx context,
                                                Class<? extends FileProcessingCompiler> fileProcessingCompilerClass,
                                                FileProcessingCompilerAdapterFactory factory,
                                                boolean forceCompile,
                                                final boolean checkScope,
                                                final boolean onlyCheckStatus) throws ExitException {
    LOG.assertTrue(FileProcessingCompiler.class.isAssignableFrom(fileProcessingCompilerClass));
    boolean didSomething = false;
    final FileProcessingCompiler[] compilers = compilerManager.getCompilers(fileProcessingCompilerClass);
    if (compilers.length > 0) {
      try {
        CacheDeferredUpdater cacheUpdater = new CacheDeferredUpdater();
        try {
          for (final FileProcessingCompiler compiler : compilers) {
            if (context.getProgressIndicator().isCanceled()) {
              throw new ExitException(ExitStatus.CANCELLED);
            }
  
            final boolean processedSomething = processFiles(factory.create(context, compiler), forceCompile, checkScope, onlyCheckStatus, cacheUpdater);
  
            if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
              throw new ExitException(ExitStatus.ERRORS);
            }
  
            didSomething |= processedSomething;
          }
        }
        finally {
          cacheUpdater.doUpdate();
        }
      }
      catch (IOException e) {
        LOG.info(e);
        context.requestRebuildNextTime(e.getMessage());
        throw new ExitException(ExitStatus.ERRORS);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (ExitException e) {
        throw e;
      }
      catch (Exception e) {
        context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.exception", e.getMessage()), null, -1, -1);
        LOG.error(e);
      }
    }

    return didSomething;
  }

  private static Map<Module, Set<GeneratingCompiler.GenerationItem>> buildModuleToGenerationItemMap(GeneratingCompiler.GenerationItem[] items) {
    final Map<Module, Set<GeneratingCompiler.GenerationItem>> map = new HashMap<Module, Set<GeneratingCompiler.GenerationItem>>();
    for (GeneratingCompiler.GenerationItem item : items) {
      Module module = item.getModule();
      LOG.assertTrue(module != null);
      Set<GeneratingCompiler.GenerationItem> itemSet = map.get(module);
      if (itemSet == null) {
        itemSet = new HashSet<GeneratingCompiler.GenerationItem>();
        map.put(module, itemSet);
      }
      itemSet.add(item);
    }
    return map;
  }

  private void deleteAll(final CompileContext context, Set<File> outputDirectories) {
    context.getProgressIndicator().pushState();
    try {
      final boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
      final Compiler[] allCompilers = CompilerManager.getInstance(myProject).getCompilers(Compiler.class);
      final VirtualFile[] allSources = context.getProjectCompileScope().getFiles(null, true);
      context.getProgressIndicator().setText(CompilerBundle.message("progress.clearing.output"));
      for (final Compiler compiler : allCompilers) {
        if (compiler instanceof GeneratingCompiler) {
          try {
            if (!myShouldClearOutputDirectory) {
              final StateCache<ValidityState> cache = getGeneratingCompilerCache((GeneratingCompiler)compiler);
              final Iterator<String> urlIterator = cache.getUrlsIterator();
              while (urlIterator.hasNext()) {
                new File(VirtualFileManager.extractPath(urlIterator.next())).delete();
              }
            }
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
        else if (compiler instanceof FileProcessingCompiler) {
        }
        else if (compiler instanceof TranslatingCompiler) {
          if (!myShouldClearOutputDirectory) {
            final ArrayList<Trinity<File, String, Boolean>> toDelete = new ArrayList<Trinity<File, String, Boolean>>();
            TranslatingCompilerFilesMonitor.getInstance().collectFiles(
                context, 
                (TranslatingCompiler)compiler, Arrays.<VirtualFile>asList(allSources).iterator(), 
                true /*pass true to make sure that every source in scope file is processed*/, 
                false /*important! should pass false to enable collection of files to delete*/, 
                new ArrayList<VirtualFile>(), 
                toDelete
            );
            for (Trinity<File, String, Boolean> trinity : toDelete) {
              final File file = trinity.getFirst();
              file.delete();
              if (isTestMode) {
                CompilerManagerImpl.addDeletedPath(FileUtil.toSystemIndependentName(file.getPath()));
              }
            }
          }
        }
      }                          
      if (myShouldClearOutputDirectory) {
        clearOutputDirectories(outputDirectories);
      }
      else { // refresh is still required
        pruneEmptyDirectories(outputDirectories); // to avoid too much files deleted events

        CompilerUtil.refreshIODirectories(outputDirectories);
      }
      dropScopesCaches();

      clearCompilerSystemDirectory(context);
    }
    finally {
      context.getProgressIndicator().popState();
    }
  }

  private void dropScopesCaches() {
    // hack to be sure the classpath will include the output directories
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).clearScopesCachesForModules();
      }
    });
  }

  private static void pruneEmptyDirectories(final Set<File> directories) {
    for (File directory : directories) {
      doPrune(directory, directories);
    }
  }

  private static boolean doPrune(final File directory, final Set<File> outPutDirectories) {
    final File[] files = directory.listFiles();
    boolean isEmpty = true;
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory() && !outPutDirectories.contains(file)) {
          if (doPrune(file, outPutDirectories)) {
            file.delete();
          }
          else {
            isEmpty = false;
          }
        }
        else {
          isEmpty = false;
        }
      }
    }

    return isEmpty;
  }

  private Set<File> getAllOutputDirectories() {
    final Set<File> outputDirs = new THashSet<File>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final VirtualFile[] outputDirectories = CompilerPathsEx.getOutputDirectories(ModuleManager.getInstance(myProject).getModules());
        for (final VirtualFile outputDirectory : outputDirectories) {
          final File directory = VfsUtil.virtualToIoFile(outputDirectory);
          outputDirs.add(directory);
        }
      }
    });

    return outputDirs;
  }

  private void clearOutputDirectories(final Set<File> _outputDirectories) {
    // do not delete directories themselves, or we'll get rootsChanged() otherwise
    final List<File> outputDirectories = new ArrayList<File>(_outputDirectories);
    for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      outputDirectories.add(new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), false)));
      outputDirectories.add(new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), true)));
    }
    Collection<File> filesToDelete = new ArrayList<File>(outputDirectories.size() * 2);
    for (File outputDirectory : outputDirectories) {
      File[] files = outputDirectory.listFiles();
      if (files != null) {
        filesToDelete.addAll(Arrays.asList(files));
      }
    }
    FileUtil.asyncDelete(filesToDelete);

    // ensure output directories exist
    for (final File file : outputDirectories) {
      file.mkdirs();
    }
    final TranslatingCompilerFilesMonitor filesMonitor = TranslatingCompilerFilesMonitor.getInstance();
    try {
      filesMonitor.addIgnoredRoots(outputDirectories);
      CompilerUtil.refreshIODirectories(outputDirectories);
    }
    finally {
      filesMonitor.removeIgnoredRoots(outputDirectories);
    }
  }

  private void clearCompilerSystemDirectory(final CompileContext context) {
    CompilerCacheManager.getInstance(myProject).clearCaches(context);
    
    for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      final File[] outputs = {
        new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), false)), 
        new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), true))
      };
      for (File output : outputs) {
        final File[] files = output.listFiles();
        if (files != null) {
          for (final File file : files) {
            final boolean deleteOk = FileUtil.delete(file);
            if (!deleteOk) {
              context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.failed.to.delete", file.getPath()),
                                 null, -1, -1);
            }
          }
        }
      }
    }
  }

  private VirtualFile getGenerationOutputDir(final IntermediateOutputCompiler compiler, final Module module, final boolean forTestSources) {
    final Pair<VirtualFile, VirtualFile> outputs =
      myGenerationCompilerModuleToOutputDirMap.get(new Pair<IntermediateOutputCompiler, Module>(compiler, module));
    return forTestSources? outputs.getSecond() : outputs.getFirst();
  }

  private boolean generateOutput(final CompileContextEx context,
                                 final GeneratingCompiler compiler,
                                 final boolean forceGenerate,
                                 final boolean onlyCheckStatus) throws ExitException {
    final GeneratingCompiler.GenerationItem[] allItems = compiler.getGenerationItems(context);
    final List<GeneratingCompiler.GenerationItem> toGenerate = new ArrayList<GeneratingCompiler.GenerationItem>();
    final List<File> filesToRefresh = new ArrayList<File>();
    final List<File> generatedFiles = new ArrayList<File>();
    final List<Module> affectedModules = new ArrayList<Module>();
    try {
      final StateCache<ValidityState> cache = getGeneratingCompilerCache(compiler);
      final Set<String> pathsToRemove = new HashSet<String>(cache.getUrls());

      final Map<GeneratingCompiler.GenerationItem, String> itemToOutputPathMap = new HashMap<GeneratingCompiler.GenerationItem, String>();
      final IOException[] ex = new IOException[] {null};
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (final GeneratingCompiler.GenerationItem item : allItems) {
            final Module itemModule = item.getModule();
            final String outputDirPath = CompilerPaths.getGenerationOutputPath(compiler, itemModule, item.isTestSource());
            final String outputPath = outputDirPath + "/" + item.getPath();
            itemToOutputPathMap.put(item, outputPath);

            try {
              final ValidityState savedState = cache.getState(outputPath);

              if (forceGenerate || savedState == null || !savedState.equalsTo(item.getValidityState())) {
                final String outputPathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, outputPath);
                if (context.getCompileScope().belongs(outputPathUrl)) {
                  toGenerate.add(item);
                }
                else {
                  pathsToRemove.remove(outputPath);
                }
              }
              else {
                pathsToRemove.remove(outputPath);
              }
            }
            catch (IOException e) {
              ex[0] = e;
            }
          }
        }
      });
      if (ex[0] != null) {
        throw ex[0];
      }

      if (onlyCheckStatus) {
        if (toGenerate.isEmpty() && pathsToRemove.isEmpty()) {
          return false;
        }
        if (LOG.isDebugEnabled()) {
          if (!toGenerate.isEmpty()) {
            LOG.debug("Found items to generate, compiler " + compiler.getDescription());
          }
          if (!pathsToRemove.isEmpty()) {
            LOG.debug("Found paths to remove, compiler " + compiler.getDescription());
          }
        }
        throw new ExitException(ExitStatus.CANCELLED);
      }

      if (!pathsToRemove.isEmpty()) {
        context.getProgressIndicator().pushState();
        context.getProgressIndicator().setText(CompilerBundle.message("progress.synchronizing.output.directory"));
        for (final String path : pathsToRemove) {
          final File file = new File(path);
          final boolean deleted = file.delete();
          if (deleted) {
            cache.remove(path);
            filesToRefresh.add(file);
          }
        }
        context.getProgressIndicator().popState();
      }

      Map<Module, Set<GeneratingCompiler.GenerationItem>> moduleToItemMap =
          buildModuleToGenerationItemMap(toGenerate.toArray(new GeneratingCompiler.GenerationItem[toGenerate.size()]));
      List<Module> modules = new ArrayList<Module>(moduleToItemMap.size());
      for (final Module module : moduleToItemMap.keySet()) {
        modules.add(module);
      }
      ModuleCompilerUtil.sortModules(myProject, modules);

      for (final Module module : modules) {
        context.getProgressIndicator().pushState();
        try {
          final Set<GeneratingCompiler.GenerationItem> items = moduleToItemMap.get(module);
          if (items != null && !items.isEmpty()) {
            final GeneratingCompiler.GenerationItem[][] productionAndTestItems = splitGenerationItems(items);
            boolean moduleAffected = false;
            for (GeneratingCompiler.GenerationItem[] _items : productionAndTestItems) {
              if (_items.length > 0) {
                final VirtualFile outputDir = getGenerationOutputDir(compiler, module, _items[0].isTestSource());
                final GeneratingCompiler.GenerationItem[] successfullyGenerated = compiler.generate(context, _items, outputDir);
                context.getProgressIndicator().setText(CompilerBundle.message("progress.updating.caches"));
                if (successfullyGenerated.length > 0) {
                  moduleAffected = true;
                }
                for (final GeneratingCompiler.GenerationItem item : successfullyGenerated) {
                  final String fullOutputPath = itemToOutputPathMap.get(item);
                  cache.update(fullOutputPath, item.getValidityState());
                  final File file = new File(fullOutputPath);
                  filesToRefresh.add(file);
                  generatedFiles.add(file);
                }
              }
            }

            if (moduleAffected) {
              affectedModules.add(module);
            }
          }
        }
        finally {
          context.getProgressIndicator().popState();
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
      throw new ExitException(ExitStatus.ERRORS);
    }
    finally {
      context.getProgressIndicator().pushState();
      CompilerUtil.refreshIOFiles(filesToRefresh);
      if (forceGenerate && !generatedFiles.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            List<VirtualFile> vFiles = new ArrayList<VirtualFile>(generatedFiles.size());
            for (File generatedFile : generatedFiles) {
              final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(generatedFile);
              if (vFile != null) {
                vFiles.add(vFile);
              }
            }
            final FileSetCompileScope additionalScope = new FileSetCompileScope(
                vFiles.toArray(new VirtualFile[vFiles.size()]), affectedModules.toArray(new Module[affectedModules.size()])
            );
            context.addScope(additionalScope);
          }
        });
      }
    }
    return !toGenerate.isEmpty() || !filesToRefresh.isEmpty();
  }

  private static GeneratingCompiler.GenerationItem[][] splitGenerationItems(final Set<GeneratingCompiler.GenerationItem> items) {
    final List<GeneratingCompiler.GenerationItem> production = new ArrayList<GeneratingCompiler.GenerationItem>();
    final List<GeneratingCompiler.GenerationItem> tests = new ArrayList<GeneratingCompiler.GenerationItem>();
    for (GeneratingCompiler.GenerationItem item : items) {
      if (item.isTestSource()) {
        tests.add(item);
      }
      else {
        production.add(item);
      }
    }
    return new GeneratingCompiler.GenerationItem[][]{
      production.toArray(new GeneratingCompiler.GenerationItem[production.size()]),
      tests.toArray(new GeneratingCompiler.GenerationItem[tests.size()])
    };
  }

  private boolean compileSources(final CompileContextEx context,
                                 final VfsSnapshot snapshot,
                                 final TranslatingCompiler compiler,
                                 final boolean forceCompile,
                                 final boolean isRebuild,
                                 final boolean trackDependencies,
                                 final Set<File> outputDirectories,
                                 final boolean onlyCheckStatus) throws ExitException {


    final Set<VirtualFile> toCompile = new HashSet<VirtualFile>();
    final List<Trinity<File, String, Boolean>> toDelete = new ArrayList<Trinity<File, String, Boolean>>();
    context.getProgressIndicator().pushState();

    final boolean[] wereFilesDeleted = new boolean[]{false};
    try {
      final TranslatingCompilerFilesMonitor monitor = TranslatingCompilerFilesMonitor.getInstance();
      ApplicationManager.getApplication().runReadAction(new Runnable() { // todo: do we really need readAction here?
        public void run() {

          TranslatingCompilerFilesMonitor.getInstance().collectFiles(
              context, compiler, Arrays.asList(snapshot.getFiles()).iterator(), forceCompile, isRebuild, toCompile, toDelete
          );
          if (trackDependencies && !toCompile.isEmpty()) { // should add dependent files
            final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
            final PsiManager psiManager = PsiManager.getInstance(myProject);
            final VirtualFile[] filesToCompile = toCompile.toArray(new VirtualFile[toCompile.size()]);
            for (final VirtualFile file : filesToCompile) {
              if (fileTypeManager.getFileTypeByFile(file) == StdFileTypes.JAVA) {
                final PsiFile psiFile = psiManager.findFile(file);
                if (psiFile != null) {
                  addDependentFiles(psiFile, toCompile, compiler, context);
                }
              }
            }
          }
        }
      });

      if (onlyCheckStatus) {
        if (toDelete.isEmpty() && toCompile.isEmpty()) {
          return false;
        }
        if (LOG.isDebugEnabled()) {
          if (!toDelete.isEmpty()) {
            LOG.debug("Found items to delete, compiler " + compiler.getDescription());
          }
          if (!toCompile.isEmpty()) {
            LOG.debug("Found items to compile, compiler " + compiler.getDescription());
          }
        }
        throw new ExitException(ExitStatus.CANCELLED);
      }

      if (!toDelete.isEmpty()) {
        try {
          wereFilesDeleted[0] = syncOutputDir(context, toDelete, outputDirectories);
        }
        catch (CacheCorruptedException e) {
          LOG.info(e);
          context.requestRebuildNextTime(e.getMessage());
        }
      }

      if ((wereFilesDeleted[0] || !toCompile.isEmpty()) && context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
        final TranslatingCompiler.ExitStatus exitStatus = compiler.compile(context, toCompile.toArray(new VirtualFile[toCompile.size()]));
        monitor.update(context, exitStatus.getSuccessfullyCompiled(), exitStatus.getFilesToRecompile());
      }
    }
    catch (IOException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
      throw new ExitException(ExitStatus.ERRORS);
    }
    finally {
      context.getProgressIndicator().popState();
    }
    return !toCompile.isEmpty() || wereFilesDeleted[0];
  }

  private static boolean syncOutputDir(final CompileContextEx context, final Collection<Trinity<File, String, Boolean>> toDelete, final Set<File> outputDirectories) throws CacheCorruptedException {
    DeleteHelper deleteHelper = new DeleteHelper(outputDirectories);
    int total = toDelete.size();
    final DependencyCache dependencyCache = context.getDependencyCache();
    final boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();

    context.getProgressIndicator().pushState();
    final List<File> filesToRefresh = new ArrayList<File>();
    try {
      context.getProgressIndicator().setText(CompilerBundle.message("progress.synchronizing.output.directory"));
      int current = 0;
      boolean wereFilesDeleted = false;
      for (final Trinity<File, String, Boolean> trinity : toDelete) {
        context.getProgressIndicator().setFraction(((double)(++current)) / total);
        final File outputPath = trinity.getFirst();
        filesToRefresh.add(outputPath);
        if (deleteHelper.delete(outputPath)) {
          wereFilesDeleted = true;
          final String className = trinity.getSecond();
          if (className != null) {
            final int id = dependencyCache.getSymbolTable().getId(className);
            dependencyCache.addTraverseRoot(id);
            final boolean sourcePresent = trinity.getThird().booleanValue();
            if (!sourcePresent) {
              dependencyCache.markSourceRemoved(id);
            }
          }
          if (isTestMode) {
            CompilerManagerImpl.addDeletedPath(outputPath.getPath());
          }
        }
      }
      return wereFilesDeleted;
    }
    finally {
      deleteHelper.finish();
      CompilerUtil.refreshIOFiles(filesToRefresh);
      context.getProgressIndicator().popState();
    }
  }

  private void addDependentFiles(final PsiFile psiFile, Set<VirtualFile> toCompile, TranslatingCompiler compiler, CompileContext context) {
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(psiFile));
    builder.analyze();
    final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
    final Set<PsiFile> dependentFiles = dependencies.get(psiFile);
    if (dependentFiles != null && !dependentFiles.isEmpty()) {
      final TranslatingCompilerFilesMonitor monitor = TranslatingCompilerFilesMonitor.getInstance();
      for (final PsiFile dependentFile : dependentFiles) {
        if (dependentFile instanceof PsiCompiledElement) {
          continue;
        }
        final VirtualFile vFile = dependentFile.getVirtualFile();
        if (vFile == null || toCompile.contains(vFile)) {
          continue;
        }
        if (!compiler.isCompilableFile(vFile, context)) {
          continue;
        }
        if (!monitor.isMarkedForCompilation(myProject, vFile)) {
          continue; // no need to compile since already compiled
        }
        toCompile.add(vFile);
        addDependentFiles(dependentFile, toCompile, compiler, context);
      }
    }
  }

  // [mike] performance optimization - this method is accessed > 15,000 times in Aurora
  private String getModuleOutputPath(final Module module, boolean inTestSourceContent) {
    final Map<Module, String> map = inTestSourceContent ? myModuleTestOutputPaths : myModuleOutputPaths;
    String path = map.get(module);
    if (path == null) {
      path = CompilerPaths.getModuleOutputPath(module, inTestSourceContent);
      map.put(module, path);
    }

    return path;
  }

  private boolean processFiles(final FileProcessingCompilerAdapter adapter,
                               final boolean forceCompile,
                               final boolean checkScope,
                               final boolean onlyCheckStatus, final CacheDeferredUpdater cacheUpdater) throws ExitException, IOException {
    final CompileContext context = adapter.getCompileContext();
    final FileProcessingCompilerStateCache cache = getFileProcessingCompilerCache(adapter.getCompiler());
    final FileProcessingCompiler.ProcessingItem[] items = adapter.getProcessingItems();
    if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      return false;
    }
    final CompileScope scope = context.getCompileScope();
    final List<FileProcessingCompiler.ProcessingItem> toProcess = new ArrayList<FileProcessingCompiler.ProcessingItem>();
    final Set<String> allUrls = new HashSet<String>();
    final IOException[] ex = new IOException[] {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          for (FileProcessingCompiler.ProcessingItem item : items) {
            final VirtualFile file = item.getFile();
            if (file == null) {
              LOG.assertTrue(false, "FileProcessingCompiler.ProcessingItem.getFile() must not return null: compiler " + adapter.getCompiler().getDescription());
            }
            final String url = file.getUrl();
            allUrls.add(url);
            if (!forceCompile && cache.getTimestamp(url) == file.getTimeStamp()) {
              final ValidityState state = cache.getExtState(url);
              final ValidityState itemState = item.getValidityState();
              if (state != null ? state.equalsTo(itemState) : itemState == null) {
                continue;
              }
            }
            if (LOG.isDebugEnabled()) {
              LOG.debug("Adding item to process: " + url + "; saved ts= " + cache.getTimestamp(url) + "; VFS ts=" + file.getTimeStamp());
            }
            toProcess.add(item);
          }
        }
        catch (IOException e) {
          ex[0] = e;
        }
      }
    });

    if (ex[0] != null) {
      throw ex[0];
    }
    
    final Collection<String> urls = cache.getUrls();
    final List<String> urlsToRemove = new ArrayList<String>();
    if (!urls.isEmpty()) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText(CompilerBundle.message("progress.processing.outdated.files"));
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (final String url : urls) {
            if (!allUrls.contains(url)) {
              if (!checkScope || scope.belongs(url)) {
                urlsToRemove.add(url);
              }
            }
          }
        }
      });
      if (!onlyCheckStatus && !urlsToRemove.isEmpty()) {
        for (final String url : urlsToRemove) {
          adapter.processOutdatedItem(context, url, cache.getExtState(url));
          cache.remove(url);
        }
      }
      context.getProgressIndicator().popState();
    }

    if (onlyCheckStatus) {
      if (urlsToRemove.isEmpty() && toProcess.isEmpty()) {
        return false;
      }
      if (LOG.isDebugEnabled()) {
        if (!urlsToRemove.isEmpty()) {
          LOG.debug("Found urls to remove, compiler " + adapter.getCompiler().getDescription());
          for (String url : urlsToRemove) {
            LOG.debug("\t" + url);
          }
        }
        if (!toProcess.isEmpty()) {
          LOG.debug("Found items to compile, compiler " + adapter.getCompiler().getDescription());
          for (FileProcessingCompiler.ProcessingItem item : toProcess) {
            LOG.debug("\t" + item.getFile().getPresentableUrl());
          }
        }
      }
      throw new ExitException(ExitStatus.CANCELLED);
    }

    if (toProcess.isEmpty()) {
      return false;
    }

    context.getProgressIndicator().pushState();
    final FileProcessingCompiler.ProcessingItem[] processed =
      adapter.process(toProcess.toArray(new FileProcessingCompiler.ProcessingItem[toProcess.size()]));
    context.getProgressIndicator().popState();

    if (processed.length > 0) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText(CompilerBundle.message("progress.updating.caches"));
      List<VirtualFile> vFiles = new ArrayList<VirtualFile>(processed.length);
      for (FileProcessingCompiler.ProcessingItem aProcessed : processed) {
        final VirtualFile file = aProcessed.getFile();
        vFiles.add(file);
        if (LOG.isDebugEnabled()) {
          LOG.debug("File processed by " + adapter.getCompiler().getDescription());
          LOG.debug("\tFile processed " + file.getPresentableUrl() + "; ts=" + file.getTimeStamp());
        }
      }
      LocalFileSystem.getInstance().refreshFiles(vFiles);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Files after VFS refresh:");
        for (VirtualFile file : vFiles) {
          LOG.debug("\t" + file.getPresentableUrl() + "; ts=" + file.getTimeStamp());
        }
      }
      for (FileProcessingCompiler.ProcessingItem item : processed) {
        cacheUpdater.addFileForUpdate(item, cache);
      }
    }
    return true;
  }

  private FileProcessingCompilerStateCache getFileProcessingCompilerCache(FileProcessingCompiler compiler) throws IOException {
    return CompilerCacheManager.getInstance(myProject).getFileProcessingCompilerCache(compiler);
  }

  private StateCache<ValidityState> getGeneratingCompilerCache(final GeneratingCompiler compiler) throws IOException {
    return CompilerCacheManager.getInstance(myProject).getGeneratingCompilerCache(compiler);
  }

  public void executeCompileTask(final CompileTask task, final CompileScope scope, final String contentName, final Runnable onTaskFinished) {
    final CompilerTask progressManagerTask =
      new CompilerTask(myProject, CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND, contentName, false);
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, progressManagerTask, scope, null, this, false);

    FileDocumentManager.getInstance().saveAllDocuments();

    progressManagerTask.start(new Runnable() {
      public void run() {
        try {
          task.execute(compileContext);
        }
        catch (ProcessCanceledException ex) {
          // suppressed
        }
        finally {
          if (onTaskFinished != null) {
            onTaskFinished.run();
          }
        }
      }
    });
  }

  private boolean executeCompileTasks(CompileContext context, boolean beforeTasks) {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    try {
      CompileTask[] tasks = beforeTasks ? manager.getBeforeTasks() : manager.getAfterTasks();
      if (tasks.length > 0) {
        progressIndicator.setText(beforeTasks
                                  ? CompilerBundle.message("progress.executing.precompile.tasks")
                                  : CompilerBundle.message("progress.executing.postcompile.tasks"));
        for (CompileTask task : tasks) {
          if (!task.execute(context)) {
            return false;
          }
        }
      }
    }
    finally {
      WindowManager.getInstance().getStatusBar(myProject).setInfo("");
      if (progressIndicator instanceof CompilerTask) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ((CompilerTask)progressIndicator).showCompilerContent();
          }
        });
      }
    }
    return true;
  }

  // todo: add validation for module chunks: all modules that form a chunk must have the same JDK
  private boolean validateCompilerConfiguration(final CompileScope scope, boolean checkOutputAndSourceIntersection) {
    final Module[] scopeModules = scope.getAffectedModules()/*ModuleManager.getInstance(myProject).getModules()*/;
    final List<String> modulesWithoutOutputPathSpecified = new ArrayList<String>();
    final List<String> modulesWithoutJdkAssigned = new ArrayList<String>();
    final Set<File> nonExistingOutputPaths = new HashSet<File>();

    for (final Module module : scopeModules) {
      final boolean hasSources = hasSources(module, false);
      final boolean hasTestSources = hasSources(module, true);
      if (!hasSources && !hasTestSources) {
        // If module contains no sources, shouldn't have to select JDK or output directory (SCR #19333)
        // todo still there may be problems with this approach if some generated files are attributed by this module
        continue;
      }
      final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
      if (jdk == null) {
        modulesWithoutJdkAssigned.add(module.getName());
      }
      final String outputPath = getModuleOutputPath(module, false);
      final String testsOutputPath = getModuleOutputPath(module, true);
      if (outputPath == null && testsOutputPath == null) {
        modulesWithoutOutputPathSpecified.add(module.getName());
      }
      else {
        if (outputPath != null) {
          final File file = new File(outputPath.replace('/', File.separatorChar));
          if (!file.exists()) {
            nonExistingOutputPaths.add(file);
          }
        }
        else {
          if (hasSources) {
            modulesWithoutOutputPathSpecified.add(module.getName());
          }
        }
        if (testsOutputPath != null) {
          final File f = new File(testsOutputPath.replace('/', File.separatorChar));
          if (!f.exists()) {
            nonExistingOutputPaths.add(f);
          }
        }
        else {
          if (hasTestSources) {
            modulesWithoutOutputPathSpecified.add(module.getName());
          }
        }
      }
    }
    if (!modulesWithoutJdkAssigned.isEmpty()) {
      showNotSpecifiedError("error.jdk.not.specified", modulesWithoutJdkAssigned, ClasspathEditor.NAME);
      return false;
    }

    if (!modulesWithoutOutputPathSpecified.isEmpty()) {
      showNotSpecifiedError("error.output.not.specified", modulesWithoutOutputPathSpecified, CommonContentEntriesEditor.NAME);
      return false;
    }

    if (!nonExistingOutputPaths.isEmpty()) {
      for (File file : nonExistingOutputPaths) {
        final boolean succeeded = file.mkdirs();
        if (!succeeded) {
          Messages.showMessageDialog(myProject, CompilerBundle.message("error.failed.to.create.directory", file.getPath()),
                                     CommonBundle.getErrorTitle(), Messages.getErrorIcon());
          return false;
        }
      }
      final Boolean refreshSuccess = ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
        public Boolean compute() {
          LocalFileSystem.getInstance().refreshIoFiles(nonExistingOutputPaths);
          for (File file : nonExistingOutputPaths) {
            if (LocalFileSystem.getInstance().findFileByIoFile(file) == null) {
              return Boolean.FALSE;
            }
          }
          return Boolean.TRUE;
        }
      });
      if (!refreshSuccess.booleanValue()) {
        return false;
      }
      dropScopesCaches();
    }

    if (checkOutputAndSourceIntersection) {
      if (myShouldClearOutputDirectory) {
        if (!validateOutputAndSourcePathsIntersection()) {
          return false;
        }
      }
    }
    final List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(myProject, scopeModules);
    for (final Chunk<Module> chunk : chunks) {
      final Set<Module> chunkModules = chunk.getNodes();
      if (chunkModules.size() <= 1) {
        continue; // no need to check one-module chunks
      }
      Sdk jdk = null;
      LanguageLevel languageLevel = null;
      for (final Module module : chunkModules) {
        final Sdk moduleJdk = ModuleRootManager.getInstance(module).getSdk();
        if (jdk == null) {
          jdk = moduleJdk;
        }
        else {
          if (!jdk.equals(moduleJdk)) {
            showCyclicModulesHaveDifferentJdksError(chunkModules.toArray(new Module[chunkModules.size()]));
            return false;
          }
        }

        LanguageLevel moduleLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
        if (languageLevel == null) {
          languageLevel = moduleLanguageLevel;
        }
        else {
          if (!languageLevel.equals(moduleLanguageLevel)) {
            showCyclicModulesHaveDifferentLanguageLevel(chunkModules.toArray(new Module[chunkModules.size()]));
            return false;
          }
        }
      }
    }
    final Compiler[] allCompilers = CompilerManager.getInstance(myProject).getCompilers(Compiler.class);
    for (Compiler compiler : allCompilers) {
      if (!compiler.validateConfiguration(scope)) {
        return false;
      }
    }
    return true;
  }

  private void showCyclicModulesHaveDifferentLanguageLevel(Module[] modulesInChunk) {
    LOG.assertTrue(modulesInChunk.length > 0);
    String moduleNameToSelect = modulesInChunk[0].getName();
    final String moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.chunk.modules.must.have.same.language.level", moduleNames),
                               CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private void showCyclicModulesHaveDifferentJdksError(Module[] modulesInChunk) {
    LOG.assertTrue(modulesInChunk.length > 0);
    String moduleNameToSelect = modulesInChunk[0].getName();
    final String moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.chunk.modules.must.have.same.jdk", moduleNames),
                               CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private static String getModulesString(Module[] modulesInChunk) {
    final StringBuilder moduleNames = StringBuilderSpinAllocator.alloc();
    try {
      for (Module module : modulesInChunk) {
        if (moduleNames.length() > 0) {
          moduleNames.append("\n");
        }
        moduleNames.append("\"").append(module.getName()).append("\"");
      }
      return moduleNames.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(moduleNames);
    }
  }

  private static boolean hasSources(Module module, boolean checkTestSources) {
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (final ContentEntry contentEntry : contentEntries) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (final SourceFolder sourceFolder : sourceFolders) {
        if (sourceFolder.getFile() == null) {
          continue; // skip invalid source folders
        }
        if (checkTestSources) {
          if (sourceFolder.isTestSource()) {
            return true;
          }
        }
        else {
          if (!sourceFolder.isTestSource()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void showNotSpecifiedError(@NonNls final String resourceId, List<String> modules, String tabNameToSelect) {
    String nameToSelect = null;
    final StringBuilder names = StringBuilderSpinAllocator.alloc();
    final String message;
    try {
      final int maxModulesToShow = 10;
      for (String name : modules.size() > maxModulesToShow ? modules.subList(0, maxModulesToShow) : modules) {
        if (nameToSelect == null) {
          nameToSelect = name;
        }
        if (names.length() > 0) {
          names.append(",\n");
        }
        names.append("\"");
        names.append(name);
        names.append("\"");
      }
      if (modules.size() > maxModulesToShow) {
        names.append(",\n...");
      }
      message = CompilerBundle.message(resourceId, modules.size(), names.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(names);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(message);
    }

    Messages.showMessageDialog(myProject, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(nameToSelect, tabNameToSelect);
  }

  private boolean validateOutputAndSourcePathsIntersection() {
    final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
    final VirtualFile[] outputPaths = CompilerPathsEx.getOutputDirectories(allModules);
    final Set<VirtualFile> affectedOutputPaths = new HashSet<VirtualFile>();
    for (Module allModule : allModules) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(allModule);
      final VirtualFile[] sourceRoots = rootManager.getSourceRoots();
      for (final VirtualFile outputPath : outputPaths) {
        for (VirtualFile sourceRoot : sourceRoots) {
          if (VfsUtil.isAncestor(outputPath, sourceRoot, true) || VfsUtil.isAncestor(sourceRoot, outputPath, false)) {
            affectedOutputPaths.add(outputPath);
          }
        }
      }
    }
    if (!affectedOutputPaths.isEmpty()) {
      final StringBuilder paths = new StringBuilder();
      for (final VirtualFile affectedOutputPath : affectedOutputPaths) {
        if (paths.length() < 0) {
          paths.append("\n");
        }
        paths.append(affectedOutputPath.getPath().replace('/', File.separatorChar));
      }
      final int answer = Messages.showOkCancelDialog(myProject,
                                                     CompilerBundle.message("warning.sources.under.output.paths", paths.toString()),
                                                     CommonBundle.getErrorTitle(), Messages.getWarningIcon());
      if (answer == 0) { // ok
        myShouldClearOutputDirectory = false;
        return true;
      }
      else {
        return false;
      }
    }
    return true;
  }

  private void showConfigurationDialog(String moduleNameToSelect, String tabNameToSelect) {
    ModulesConfigurator.showDialog(myProject, moduleNameToSelect, tabNameToSelect, false);
  }
  
  private static VirtualFile lookupVFile(final IntermediateOutputCompiler compiler, final Module module, final boolean forTestSources) {
    final File file = new File(CompilerPaths.getGenerationOutputPath(compiler, module, forTestSources));
    final VirtualFile vFile;
    if (file.mkdirs()) {
      vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    }
    else {
      vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    }
    assert vFile != null: "Virtual file not found for " + file.getPath();
    return vFile;
  }

  private static class CacheDeferredUpdater {
    private Map<VirtualFile, List<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>>> myData = new java.util.HashMap<VirtualFile, List<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>>>();
    
    public void addFileForUpdate(final FileProcessingCompiler.ProcessingItem item, FileProcessingCompilerStateCache cache) {
      final VirtualFile file = item.getFile();
      List<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>> list = myData.get(file);
      if (list == null) {
        list = new ArrayList<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>>();
        myData.put(file, list);
      }
      list.add(new Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>(cache, item));
    }
    
    public void doUpdate() throws IOException{
      final IOException[] ex = new IOException[] {null};
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          try {
            for (Map.Entry<VirtualFile, List<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>>> entry : myData.entrySet()) {
              for (Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem> pair : entry.getValue()) {
                final FileProcessingCompiler.ProcessingItem item = pair.getSecond();
                pair.getFirst().update(entry.getKey(), item.getValidityState());
              }
            }
          }
          catch (IOException e) {
            ex[0] = e;
          }
        }
      });
      if (ex[0] != null) {
        throw ex[0];
      }
    }
  }
  
}
