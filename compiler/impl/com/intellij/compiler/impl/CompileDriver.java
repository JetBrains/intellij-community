/**
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 1:42:26 PM
 */
package com.intellij.compiler.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.problems.WolfTheProblemSolver;
import com.intellij.analysis.AnalysisScope;
import com.intellij.compiler.*;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.compiler.progress.CompilerProgressIndicator;
import com.intellij.javaee.module.J2EEModuleUtilEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ProfilingUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.*;

public class CompileDriver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileDriver");

  private final Project myProject;
  private final Map<Compiler,Object> myCompilerToCacheMap = new THashMap<Compiler, Object>();
  private Map<Pair<Compiler, Module>, VirtualFile> myGenerationCompilerModuleToOutputDirMap;
  private String myCachesDirectoryPath;
  private Set<String> myOutputFilesOnDisk = null;
  private boolean myShouldClearOutputDirectory;

  private Map<Module, String> myModuleOutputPaths = new HashMap<Module, String>();
  private Map<Module, String> myModuleTestOutputPaths = new HashMap<Module, String>();

  private ProjectRootManager myProjectRootManager;
  private static final @NonNls String VERSION_FILE_NAME = "version.dat";
  private static final @NonNls String LOCK_FILE_NAME = "in_progress.dat";
  private final FileProcessingCompilerAdapterFactory myProcessingCompilerAdapterFactory;
  private final FileProcessingCompilerAdapterFactory myPackagingCompilerAdapterFactory;
  final ProjectCompileScope myProjectCompileScope;

  public CompileDriver(Project project) {
    myProject = project;
    myCachesDirectoryPath = CompilerPaths.getCacheStoreDirectory(myProject).getPath().replace('/', File.separatorChar);
    myShouldClearOutputDirectory = CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY;

    myGenerationCompilerModuleToOutputDirMap = new com.intellij.util.containers.HashMap<Pair<Compiler, Module>, VirtualFile>();

    final GeneratingCompiler[] compilers = CompilerManager.getInstance(myProject).getCompilers(GeneratingCompiler.class);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
        for (GeneratingCompiler compiler : compilers) {
          for (final Module module : allModules) {
            final String path = getGenerationOutputPath(compiler, module);
            final File file = new File(path);
            final VirtualFile vFile;
            if (file.mkdirs()) {
              vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            }
            else {
              vFile = LocalFileSystem.getInstance().findFileByPath(path);
            }
            Pair<Compiler, Module> pair = new Pair<Compiler, Module>(compiler, module);
            myGenerationCompilerModuleToOutputDirMap.put(pair, vFile);
          }
        }
      }
    });

    myProjectRootManager = ProjectRootManager.getInstance(myProject);
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
    myProjectCompileScope = new ProjectCompileScope(myProject);
  }

  public void rebuild(CompileStatusNotification callback) {
    doRebuild(callback, null, true, addAdditionalRoots(myProjectCompileScope));
  }

  public void make(CompileStatusNotification callback) {
    make(myProjectCompileScope, callback);
  }

  public void make(Project project, Module[] modules, CompileStatusNotification callback) {
    make(new ModuleCompileScope(project, modules, true), callback);
  }

  public void make(Module module, CompileStatusNotification callback) {
    make(new ModuleCompileScope(module, true), callback);
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    scope = addAdditionalRoots(scope);
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, false, callback, null, true, false);
    }
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
    final boolean isInProgress = new File(myCachesDirectoryPath, LOCK_FILE_NAME).exists();
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
    final File lockFile = new File(myCachesDirectoryPath, LOCK_FILE_NAME);
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

  private void doRebuild(CompileStatusNotification callback, CompilerMessage message, final boolean checkCachesVersion, final CompileScope compileScope) {
    if (validateCompilerConfiguration(compileScope, true)) {
      startup(compileScope, true, false, callback, message, checkCachesVersion, false);
    }
  }

  private CompileScope addAdditionalRoots(CompileScope originalScope) {
    CompileScope scope = originalScope;
    for (final Pair<Compiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      final VirtualFile outputDir = myGenerationCompilerModuleToOutputDirMap.get(pair);
      scope = new CompositeScope(scope, new FileSetCompileScope(new VirtualFile[]{outputDir}, new Module[]{pair.getSecond()}));
    }
    CompileScope additionalJ2eeScope = com.intellij.javaee.make.MakeUtil.getInstance().getOutOfSourceJ2eeCompileScope(scope);
    if (additionalJ2eeScope != null) {
      scope = new CompositeScope(scope, additionalJ2eeScope);
    }
    return scope;
  }

  private void startup(final CompileScope scope,
                       final boolean isRebuild,
                       final boolean forceCompile,
                       final CompileStatusNotification callback,
                       CompilerMessage message,
                       final boolean checkCachesVersion,
                       final boolean trackDependencies) {
    final CompilerProgressIndicator indicator = new CompilerProgressIndicator(
      myProject,
      CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND,
      forceCompile ? CompilerBundle.message("compiler.content.name.compile") : CompilerBundle.message("compiler.content.name.make")
    );
    WindowManager.getInstance().getStatusBar(myProject).setInfo("");

    final DependencyCache dependencyCache = new DependencyCache(myCachesDirectoryPath, myProject);
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, indicator, scope, dependencyCache, this, !isRebuild && !forceCompile);
    for (Pair<Compiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      compileContext.assignModule(myGenerationCompilerModuleToOutputDirMap.get(pair), pair.getSecond());
    }

    if (message != null) {
      compileContext.addMessage(message);
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    final Thread compileThread = new Thread("Compile Thread") {
      public void run() {
        synchronized (CompilerManager.getInstance(myProject)) {
          ProgressManager.getInstance().runProcess(new Runnable() {
            public void run() {
              try {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("COMPILATION STARTED");
                }
                doCompile(compileContext, isRebuild, forceCompile, callback, checkCachesVersion, trackDependencies);
              }
              finally {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("COMPILATION FINISHED");
                }
              }
            }
          }, compileContext.getProgressIndicator());
        }
      }
    };
    compileThread.setPriority(Thread.NORM_PRIORITY);
    compileThread.start();
  }

  private void doCompile(final CompileContextImpl compileContext,
                         final boolean isRebuild,
                         final boolean forceCompile,
                         final CompileStatusNotification callback, final boolean checkCachesVersion, final boolean trackDependencies) {
    ExitStatus status = ExitStatus.ERRORS;
    boolean wereExceptions = false;
    try {
      compileContext.getProgressIndicator().pushState();
      if (checkCachesVersion) {
        final CompileStatus compileStatus = readStatus();
        if (compileStatus == null) {
          compileContext.requestRebuildNextTime(CompilerBundle.message("error.compiler.caches.corrupted"));
        }
        else if (compileStatus.CACHE_FORMAT_VERSION != -1 && compileStatus.CACHE_FORMAT_VERSION != CompilerConfiguration.DEPENDENCY_FORMAT_VERSION) {
          compileContext.requestRebuildNextTime(CompilerBundle.message("error.caches.old.format"));
        }
        else if (compileStatus.COMPILATION_IN_PROGRESS) {
          compileContext.requestRebuildNextTime(CompilerBundle.message("error.previous.compilation.failed"));
        }
        if (compileContext.isRebuildRequested()) {
          return;
        }
      }
      writeStatus(new CompileStatus(CompilerConfiguration.DEPENDENCY_FORMAT_VERSION, true), compileContext);
      if (compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return;
      }

      status = doCompile(compileContext, isRebuild, forceCompile, trackDependencies, getAllOutputDirectories());
    }
    catch (Throwable ex) {
      wereExceptions = true;
      throw new RuntimeException(ex);
    }
    finally {
      dropDependencyCache(compileContext);
      compileContext.getProgressIndicator().popState();
      final ExitStatus _status = status;
      if (compileContext.isRebuildRequested()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doRebuild(
              callback,
              new CompilerMessageImpl(myProject, CompilerMessageCategory.INFORMATION, compileContext.getRebuildReason(), null, -1, -1,
                                      null), false, compileContext.getCompileScope()
            );
          }
        }, ModalityState.NON_MMODAL);
      }
      else {
        writeStatus(new CompileStatus(CompilerConfiguration.DEPENDENCY_FORMAT_VERSION, wereExceptions), compileContext);
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
        }, ModalityState.NON_MMODAL);
      }
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

  private static class ExitException extends Exception{
    private final ExitStatus myStatus;
    public ExitException(ExitStatus status) {
      myStatus = status;
    }
    public ExitStatus getExitStatus() {
      return myStatus;
    }
  }

  private ExitStatus doCompile(CompileContextImpl context, boolean isRebuild, final boolean forceCompile, final boolean trackDependencies,
                               final Set<File> outputDirectories) {
    try {
      WolfTheProblemSolver.getInstance(myProject).startUpdatingProblemsInScope(context.getCompileScope());
      if (isRebuild) {
        deleteAll(context, outputDirectories);
        if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
          return ExitStatus.ERRORS;
        }
      }

      try {
        context.getProgressIndicator().pushState();
        if (!executeCompileTasks(context, true)) {
          return ExitStatus.CANCELLED;
        }
      }
      finally {
        context.getProgressIndicator().popState();
      }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return ExitStatus.ERRORS;
      }

      if (!isRebuild) {
        // compile tasks may change the contents of the output dirs so it is more safe to gather output files here
        context.getProgressIndicator().setText(CompilerBundle.message("progress.scanning.output"));
        myOutputFilesOnDisk = CompilerPathsEx.getOutputFiles(myProject);
      }

      boolean didSomething = false;

      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);

      try {
        didSomething |= generateSources(compilerManager, context, forceCompile);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, SourceInstrumentingCompiler.class, myProcessingCompilerAdapterFactory, forceCompile, true);

        didSomething |= translate(context, compilerManager, forceCompile, isRebuild, trackDependencies, outputDirectories);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassInstrumentingCompiler.class, myProcessingCompilerAdapterFactory, isRebuild, false);

        // explicitly passing forceCompile = false because in scopes that is narrower than ProjectScope it is impossible
        // to understand whether the class to be processed is in scope or not. Otherwise compiler may process its items even if
        // there were changes in completely independent files.
        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassPostProcessingCompiler.class, myProcessingCompilerAdapterFactory, isRebuild, false);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, PackagingCompiler.class, myPackagingCompilerAdapterFactory, isRebuild, true);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, Validator.class, myProcessingCompilerAdapterFactory, forceCompile, true);
      }
      catch (ExitException e) {
        return e.getExitStatus();
      }
      finally {
        // drop in case it has not been dropped yet.
        dropDependencyCache(context);
      }

      try {
        context.getProgressIndicator().pushState();
        if (!executeCompileTasks(context, false)) {
          return ExitStatus.CANCELLED;
        }
      }
      finally {
        context.getProgressIndicator().popState();
      }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
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
    finally{
      WolfTheProblemSolver.getInstance(myProject).finishUpdatingProblems();
    }
  }

  private static void dropDependencyCache(final CompileContextImpl context) {
    context.getProgressIndicator().pushState();
    try {
      context.getProgressIndicator().setText(CompilerBundle.message("progress.saving.caches"));
      context.getDependencyCache().dispose();
    }
    finally {
      context.getProgressIndicator().popState();
    }
  }

  private boolean generateSources(final CompilerManager compilerManager, CompileContextImpl context, final boolean forceCompile) throws ExitException{
    boolean didSomething = false;

    final SourceGeneratingCompiler[] sourceGenerators = compilerManager.getCompilers(SourceGeneratingCompiler.class);
    for (final SourceGeneratingCompiler sourceGenerator : sourceGenerators) {
      if (context.getProgressIndicator().isCanceled()) {
        throw new ExitException(ExitStatus.CANCELLED);
      }

      final boolean generatedSomething = generateOutput(context, sourceGenerator, forceCompile);

      dropInternalCache(sourceGenerator);

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        throw new ExitException(ExitStatus.ERRORS);
      }
      didSomething |= generatedSomething;
    }
    return didSomething;
  }

  private boolean translate(final CompileContextImpl context,
                            final CompilerManager compilerManager,
                            final boolean forceCompile,
                            boolean isRebuild,
                            final boolean trackDependencies, final Set<File> outputDirectories) throws ExitException {

    boolean didSomething = false;

    final TranslatingCompiler[] translators = compilerManager.getCompilers(TranslatingCompiler.class);
    final VfsSnapshot snapshot = ApplicationManager.getApplication().runReadAction(new Computable<VfsSnapshot>() {
      public VfsSnapshot compute() {
        return new VfsSnapshot(context.getCompileScope().getFiles(null, true));
      }
    });

    for (final TranslatingCompiler translator : translators) {
      if (context.getProgressIndicator().isCanceled()) {
        throw new ExitException(ExitStatus.CANCELLED);
      }

      final boolean compiledSomething = compileSources(context, snapshot, translator, forceCompile, isRebuild, trackDependencies, outputDirectories);

      // free memory earlier to leave other compilers more space
      dropDependencyCache(context);
      dropInternalCache(translator);

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
                                                CompileContextImpl context,
                                                Class<? extends FileProcessingCompiler> fileProcessingCompilerClass,
                                                FileProcessingCompilerAdapterFactory factory,
                                                boolean forceCompile,
                                                final boolean checkScope) throws ExitException {
    LOG.assertTrue(FileProcessingCompiler.class.isAssignableFrom(fileProcessingCompilerClass));
    boolean didSomething = false;
    final FileProcessingCompiler[] compilers = compilerManager.getCompilers(fileProcessingCompilerClass);
    if (compilers.length > 0) {
      try {
        for (final FileProcessingCompiler compiler : compilers) {
          if (context.getProgressIndicator().isCanceled()) {
            throw new ExitException(ExitStatus.CANCELLED);
          }

          final boolean processedSomething = processFiles(factory.create(context, compiler), forceCompile, checkScope);

          dropInternalCache(compiler);

          if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            throw new ExitException(ExitStatus.ERRORS);
          }

          didSomething |= processedSomething;
        }
      }
      catch(ProcessCanceledException e) {
        throw e;
      }
      catch(ExitException e) {
        throw e;
      }
      catch (Exception e) {
        context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.exception", e.getMessage()), null, -1, -1);
        LOG.error(e);
      }
    }

    return didSomething;
  }

  private static Map<Module, Set<GeneratingCompiler.GenerationItem>> buildModuleToGenerationItemMap(
    GeneratingCompiler.GenerationItem[] items) {
    final Map<Module, Set<GeneratingCompiler.GenerationItem>> map = new THashMap<Module, Set<GeneratingCompiler.GenerationItem>>();
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
      context.getProgressIndicator().setText(CompilerBundle.message("progress.clearing.output"));
      for (final Compiler compiler : allCompilers) {
        if (compiler instanceof GeneratingCompiler) {
          final StateCache<ValidityState> cache = getGeneratingCompilerCache((GeneratingCompiler)compiler);
          if (!myShouldClearOutputDirectory) {
            final Iterator<String> urlIterator = cache.getUrlsIterator();
            while(urlIterator.hasNext()) {
              new File(VirtualFileManager.extractPath(urlIterator.next())).delete();
            }
          }
          cache.wipe();
        }
        else if (compiler instanceof FileProcessingCompiler) {
          final FileProcessingCompilerStateCache cache = getFileProcessingCompilerCache((FileProcessingCompiler)compiler);
          cache.wipe();
        }
        else if (compiler instanceof TranslatingCompiler) {
          final TranslatingCompilerStateCache cache = getTranslatingCompilerCache((TranslatingCompiler)compiler);
          if (!myShouldClearOutputDirectory) {
            final Iterator<String> urlIterator = cache.getOutputUrlsIterator();
            while(urlIterator.hasNext()) {
              final String outputPath = urlIterator.next();
              final String sourceUrl = cache.getSourceUrl(outputPath);
              if (sourceUrl == null || !FileUtil.pathsEqual(outputPath, VirtualFileManager.extractPath(sourceUrl))) {
                new File(outputPath).delete();
                if (isTestMode) {
                  CompilerManagerImpl.addDeletedPath(outputPath);
                }
              }
            }
          }
          cache.wipe();
        }
      }
      if (myShouldClearOutputDirectory) {
        clearOutputDirectories(outputDirectories);
      }
      else { // refresh is still required
        CompilerUtil.doRefresh(new Runnable() {
          public void run() {
            final VirtualFile[] outputDirectories = CompilerPathsEx.getOutputDirectories(ModuleManager.getInstance(myProject).getModules());
            for (final VirtualFile outputDirectory : outputDirectories) {
              outputDirectory.refresh(false, true);
            }
          }
        });
      }

      clearCompilerSystemDirectory(context);
    }
    finally {
      context.getProgressIndicator().popState();
    }
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

  private static void clearOutputDirectories(final Set<File> outputDirectories) {
    // do not delete directories themselves, or we'll get rootsChanged() otherwise
    Collection<File> filesToDelete = new ArrayList<File>(outputDirectories.size()*2);
    for (File outputDirectory : outputDirectories) {
      File[] files = outputDirectory.listFiles();
      if (files != null) {
        filesToDelete.addAll(Arrays.asList(files));
      }
    }
    FileUtil.asyncDelete(filesToDelete);

    // ensure output directories exist, create and refresh if not exist
    final List<File> createdFiles = new ArrayList<File>(outputDirectories.size());
    for (final File file : outputDirectories) {
      if (file.mkdirs()) {
        createdFiles.add(file);
      }
    }
    CompilerUtil.refreshIOFiles(createdFiles);
  }

  private void clearCompilerSystemDirectory(final CompileContext context) {
    final File[] children = new File(myCachesDirectoryPath).listFiles();
    if (children != null) {
      for (final File child : children) {
        final boolean deleteOk = FileUtil.delete(child);
        if (!deleteOk) {
          context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.failed.to.delete", child.getPath()),
                             null, -1, -1);
        }
      }
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (Pair<Compiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
          final VirtualFile dir = myGenerationCompilerModuleToOutputDirMap.get(pair);
          final File[] files = VfsUtil.virtualToIoFile(dir).listFiles();
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
    });
  }

  private VirtualFile getGenerationOutputDir(final GeneratingCompiler compiler, final Module module) {
    return myGenerationCompilerModuleToOutputDirMap.get(new Pair<Compiler, Module>(compiler, module));
  }

  private static String getGenerationOutputPath(GeneratingCompiler compiler, Module module) {
    final String generatedCompilerDirectoryPath = CompilerPaths.getGeneratedDataDirectory(module.getProject(), compiler).getPath();
    return generatedCompilerDirectoryPath.replace(File.separatorChar, '/') + "/" +
           (module.getName().replace(' ', '_') + "." + Integer.toHexString(module.getModuleFilePath().hashCode()));
  }

  private boolean generateOutput(final CompileContextImpl context, final GeneratingCompiler compiler, final boolean forceGenerate) {
    final GeneratingCompiler.GenerationItem[] allItems = compiler.getGenerationItems(context);
    final List<GeneratingCompiler.GenerationItem> toGenerate = new ArrayList<GeneratingCompiler.GenerationItem>();
    final StateCache<ValidityState> cache = getGeneratingCompilerCache(compiler);
    final Set<String> pathsToRemove = new HashSet<String>(Arrays.asList(cache.getUrls()));

    final Map<GeneratingCompiler.GenerationItem, String> itemToOutputPathMap = new THashMap<GeneratingCompiler.GenerationItem, String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (final GeneratingCompiler.GenerationItem item : allItems) {
          final Module itemModule = item.getModule();
          final String outputDirPath = getGenerationOutputPath(compiler, itemModule);
          final String outputPath = outputDirPath + "/" + item.getPath();
          itemToOutputPathMap.put(item, outputPath);

          final ValidityState savedState = cache.getState(outputPath);

          if (forceGenerate || savedState == null || !savedState.equalsTo(item.getValidityState())) {
            toGenerate.add(item);
          }
          else {
            pathsToRemove.remove(outputPath);
          }
        }
      }
    });

    final List<File> filesToRefresh = new ArrayList<File>();
    final List<File> generatedFiles = new ArrayList<File>();
    final List<Module> affectedModules = new ArrayList<Module>();
    try {
      if (pathsToRemove.size() > 0) {
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
          if (items != null && items.size() > 0) {
            final VirtualFile outputDir = getGenerationOutputDir(compiler, module);
            final GeneratingCompiler.GenerationItem[] successfullyGenerated =
              compiler.generate(context, items.toArray(new GeneratingCompiler.GenerationItem[items.size()]), outputDir);
            context.getProgressIndicator().setText(CompilerBundle.message("progress.updating.caches"));

            if (successfullyGenerated.length > 0) {
              affectedModules.add(module);
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
        finally {
          context.getProgressIndicator().popState();
        }
      }
    }
    finally {
      context.getProgressIndicator().pushState();
      CompilerUtil.refreshIOFiles(filesToRefresh);
      if (forceGenerate && generatedFiles.size() > 0) {
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
              vFiles.toArray(new VirtualFile[vFiles.size()]),
              affectedModules.toArray(new Module[affectedModules.size()])
            );
            context.addScope(additionalScope);
          }
        });
      }
      if (cache.isDirty()) {
        context.getProgressIndicator().setText(CompilerBundle.message("progress.saving.caches"));
        cache.save();
      }
      context.getProgressIndicator().popState();
    }
    return toGenerate.size() > 0 || filesToRefresh.size() > 0;
  }

  private boolean compileSources(final CompileContextImpl context,
                                 final VfsSnapshot snapshot,
                                 final TranslatingCompiler compiler,
                                 final boolean forceCompile,
                                 final boolean isRebuild,
                                 final boolean trackDependencies, final Set<File> outputDirectories) {
    final TranslatingCompilerStateCache cache = getTranslatingCompilerCache(compiler);
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);

    context.getProgressIndicator().pushState();
    final boolean[] wereFilesDeleted = new boolean[]{false};
    final Set<VirtualFile> toCompile = new HashSet<VirtualFile>();
    try {
      final Set<String> toDelete = new HashSet<String>();

      final Set<String> urlsWithSourceRemoved = new HashSet<String>();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          findOutOfDateFiles(compiler, snapshot, forceCompile, cache, toCompile, context);

          if (trackDependencies && toCompile.size() > 0) { // should add dependent files
            final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
            final PsiManager psiManager = PsiManager.getInstance(myProject);
            final VirtualFile[] filesToCompile = toCompile.toArray(new VirtualFile[toCompile.size()]);
            Set<String> sourcesWithOutputRemoved = getSourcesWithOutputRemoved(cache);
            for (final VirtualFile file : filesToCompile) {
              if (fileTypeManager.getFileTypeByFile(file) == StdFileTypes.JAVA) {
                final PsiFile psiFile = psiManager.findFile(file);
                if (psiFile != null) {
                  addDependentFiles(psiFile, toCompile, cache, snapshot, sourcesWithOutputRemoved, compiler, context);
                }
              }
            }
          }

          if (!isRebuild) {
            final ProgressIndicator progressIndicator = context.getProgressIndicator();
            progressIndicator.pushState();
            progressIndicator.setText(CompilerBundle.message("progress.searching.for.files.to.delete"));

            findFilesToDelete(snapshot, urlsWithSourceRemoved, cache, toCompile, context, toDelete, compilerConfiguration);

            progressIndicator.popState();
          }

        }
      });

      if (toDelete.size() > 0) {
        try {
          wereFilesDeleted[0] = syncOutputDir(urlsWithSourceRemoved, context, toDelete, cache, outputDirectories);
        }
        catch (CacheCorruptedException e) {
          LOG.info(e);
          context.requestRebuildNextTime(e.getMessage());
        }
      }

      if (wereFilesDeleted[0] && toDelete.size() > 0) {
        CompilerUtil.refreshPaths(toDelete.toArray(new String[toDelete.size()]));
      }

      if ((wereFilesDeleted[0] || toCompile.size() > 0) && context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
        final TranslatingCompiler.ExitStatus exitStatus = compiler.compile(context, toCompile.toArray(new VirtualFile[toCompile.size()]));
        updateInternalCaches(cache, context, exitStatus.getSuccessfullyCompiled(), exitStatus.getFilesToRecompile());
      }
    }
    finally {
      if (cache.isDirty()) {
        context.getProgressIndicator().setText(CompilerBundle.message("progress.saving.caches"));
        if (cache.isDirty()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("--Saving translating cache for compiler " + compiler.getDescription());
          }
          cache.save();
          if (LOG.isDebugEnabled()) {
            LOG.debug("--Done");
          }
        }
      }
      context.getProgressIndicator().popState();
    }
    return toCompile.size() > 0 || wereFilesDeleted[0];
  }

  private Set<String> getSourcesWithOutputRemoved(TranslatingCompilerStateCache cache) {
    //final String[] outputUrls = cache.getOutputUrls();
    final Set<String> set = new HashSet<String>();
    for (Iterator<String> it = cache.getOutputUrlsIterator(); it.hasNext();) {
      String outputUrl = it.next();
      if (!myOutputFilesOnDisk.contains(outputUrl)) {
        set.add(cache.getSourceUrl(outputUrl));
      }
    }
    return set;
  }

  private void findFilesToDelete(VfsSnapshot snapshot,
                                 final Set<String> urlsWithSourceRemoved,
                                 final TranslatingCompilerStateCache cache,
                                 final Set<VirtualFile> toCompile,
                                 final CompileContextImpl context,
                                 final Set<String> toDelete,
                                 final CompilerConfiguration compilerConfiguration) {
    final List<String> toRemove = new ArrayList<String>();
    final CompileScope scope = context.getCompileScope();
    for (Iterator<String> it = cache.getOutputUrlsIterator(); it.hasNext();) {
      final String outputPath = it.next();
      final String sourceUrl = cache.getSourceUrl(outputPath);
      final VirtualFile sourceFile = snapshot.getFileByUrl(sourceUrl);

      boolean needRecompile = false;
      boolean shouldDelete;
      if (myOutputFilesOnDisk.contains(outputPath)) {
        if (sourceFile == null) {
          shouldDelete = scope.belongs(sourceUrl);
        }
        else {
          if (toCompile.contains(sourceFile)) {
            // some crazy users store their resources (which is source file for us) directly in the output dir
            // we should not delete files which are both output and source files
            shouldDelete = !FileUtil.pathsEqual(outputPath, VirtualFileManager.extractPath(sourceUrl));
          }
          else {
            final String currentOutputDir = getModuleOutputDirForFile(context, sourceFile);
            if (currentOutputDir != null) {
              final String className = cache.getClassName(outputPath);
              //noinspection HardCodedStringLiteral
              final boolean pathsEqual = className == null || currentOutputDir.regionMatches(
                !SystemInfo.isFileSystemCaseSensitive, 0, outputPath, 0, outputPath.length() - className.length() - ".class".length() - 1
              );
              if (pathsEqual) {
                shouldDelete = false;
              }
              else {
                // output for this source has been changed or the output dir was changed, need to recompile to the new output dir
                shouldDelete = true;
                needRecompile = true;
              }
            }
            else {
              shouldDelete = true;
            }
          }
        }
      }
      else {
        // output for this source has been deleted or the output dir was changed, need to recompile
        needRecompile = true;
        shouldDelete = true;  // in case the output dir was changed, should delete from the previous location
      }

      if (shouldDelete) {
        toDelete.add(outputPath);
      }

      if (needRecompile) {
        if (sourceFile != null && scope.belongs(sourceUrl)) {
          if (!compilerConfiguration.isExcludedFromCompilation(sourceFile)) {
            toCompile.add(sourceFile);
            toRemove.add(outputPath);
          }
        }
      }
      if (sourceFile == null) {
        urlsWithSourceRemoved.add(outputPath);
      }
    }
    for (final String aToRemove : toRemove) {
      cache.remove(aToRemove);
    }
  }

  private static void updateInternalCaches(final TranslatingCompilerStateCache cache, final CompileContextImpl context, final TranslatingCompiler.OutputItem[] successfullyCompiled, final VirtualFile[] filesToRecompile) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        context.getProgressIndicator().setText(CompilerBundle.message("progress.updating.caches"));
        final FileTypeManager typeManager = FileTypeManager.getInstance();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Updating internal caches: successfully compiled " + successfullyCompiled.length + " files; toRecompile: " + filesToRecompile.length + " files");
        }
        for (final TranslatingCompiler.OutputItem item : successfullyCompiled) {
          final String outputPath = item.getOutputPath();
          final VirtualFile sourceFile = item.getSourceFile();
          final String className;
          if (outputPath != null && StdFileTypes.JAVA.equals(typeManager.getFileTypeByFile(sourceFile))) {
            final String outputDir = item.getOutputRootDirectory();

            if (outputDir != null) {
              if (!FileUtil.startsWith(outputPath, outputDir)) {
                LOG.error(outputPath + " does not start with " + outputDir);
              }
              className = MakeUtil.relativeClassPathToQName(outputPath.substring(outputDir.length(), outputPath.length()), '/');
            }
            else {
              // outputDir might be null for package-info.java (package annotation)
              className = null;
            }
          }
          else {
            className = null;
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Putting: [outputPath, className, sourceFile] = [" + outputPath + ";" + className + ";" +
                      sourceFile.getPresentableUrl() + "]");
          }
          cache.update(outputPath, className, sourceFile);
        }
        for (VirtualFile aFilesToRecompile : filesToRecompile) {
          cache.markAsModified(aFilesToRecompile);
        }
      }
    });
  }

  private static boolean syncOutputDir(final Set<String> urlsWithSourceRemoved, final CompileContextImpl context, final Set<String> toDelete,
                                final TranslatingCompilerStateCache cache, final Set<File> outputDirectories) throws CacheCorruptedException {

    DeleteHelper deleteHelper = new DeleteHelper(outputDirectories);
    int total = toDelete.size();
    final DependencyCache dependencyCache = context.getDependencyCache();
    final boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();

    context.getProgressIndicator().pushState();
    try {
      context.getProgressIndicator().setText(CompilerBundle.message("progress.synchronizing.output.directory"));
      int current = 0;
      boolean wereFilesDeleted = false;
      for (final String outputPath : toDelete) {
        context.getProgressIndicator().setFraction(((double)(++current)) / total);
        if (deleteHelper.delete(outputPath)) {
          wereFilesDeleted = true;
          String qName = cache.getClassName(outputPath);
          if (qName != null) {
            final int id = dependencyCache.getSymbolTable().getId(qName);
            dependencyCache.addTraverseRoot(id);
            if (urlsWithSourceRemoved.contains(outputPath)) {
              dependencyCache.markSourceRemoved(id);
            }
          }
          if (isTestMode) {
            CompilerManagerImpl.addDeletedPath(outputPath);
          }
          cache.remove(outputPath);
        }
      }
      return wereFilesDeleted;
    }
    finally {
      deleteHelper.finish();
      context.getProgressIndicator().popState();
    }
  }

  private void findOutOfDateFiles(final TranslatingCompiler compiler,
                                  final VfsSnapshot snapshot,
                                  final boolean forceCompile,
                                  final TranslatingCompilerStateCache cache,
                                  final Set<VirtualFile> toCompile,
                                  final CompileContext context) {
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);

    snapshot.forEachUrl(new TObjectProcedure<String>() {
      public boolean execute(final String url) {
        final VirtualFile file = snapshot.getFileByUrl(url);
        if (compiler.isCompilableFile(file, context)) {
          if (!forceCompile && compilerConfiguration.isExcludedFromCompilation(file)) {
            return true;
          }
          if (forceCompile || file.getTimeStamp() != cache.getSourceTimestamp(url)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("File is out-of-date: " + url + "; current timestamp = " + file.getTimeStamp() + "; stored timestamp = " +
                        cache.getSourceTimestamp(url));
            }
            toCompile.add(file);
          }
        }
        return true;
      }
    });
  }


  private void addDependentFiles(final PsiFile psiFile,
                                 Set<VirtualFile> toCompile,
                                 final TranslatingCompilerStateCache cache,
                                 VfsSnapshot snapshot,
                                 Set<String> sourcesWithOutputRemoved, TranslatingCompiler compiler, CompileContextImpl context) {
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(psiFile));
    builder.analyze();
    final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
    final Set<PsiFile> dependentFiles = dependencies.get(psiFile);
    if (dependentFiles != null && dependentFiles.size() > 0) {
      for (final PsiFile dependentFile : dependentFiles) {
        if (dependentFile instanceof PsiCompiledElement) {
          continue;
        }
        final VirtualFile vFile = dependentFile.getVirtualFile();
        if (toCompile.contains(vFile)) {
          continue;
        }
        String url = snapshot.getUrlByFile(vFile);
        if (url == null) { // the file does not belong to this snapshot
          url = vFile.getUrl();
        }
        if (!sourcesWithOutputRemoved.contains(url)) {
          if (vFile.getTimeStamp() == cache.getSourceTimestamp(url)) {
            continue;
          }
        }
        if (!compiler.isCompilableFile(vFile, context)) {
          continue;
        }
        toCompile.add(vFile);
        addDependentFiles(dependentFile, toCompile, cache, snapshot, sourcesWithOutputRemoved, compiler, context);
      }
    }
  }

  private String getModuleOutputDirForFile(CompileContext context, VirtualFile file) {
    final Module module = context.getModuleByFile(file);
    if (module == null) {
      return null; // looks like file invalidated
    }
    final ProjectFileIndex fileIndex = myProjectRootManager.getFileIndex();
    return getModuleOutputPath(module, fileIndex.isInTestSourceContent(file));
  }


  // [mike] performance optimization - this method is accessed > 15,000 times in Aurora
  private String getModuleOutputPath(final Module module, boolean inTestSourceContent) {
    final Map<Module, String> map = inTestSourceContent? myModuleTestOutputPaths : myModuleOutputPaths;
    String path = map.get(module);
    if (path == null) {
      path = CompilerPaths.getModuleOutputPath(module, inTestSourceContent);
      map.put(module, path);
    }

    return path;
  }

  private boolean processFiles(final FileProcessingCompilerAdapter adapter, final boolean forceCompile, final boolean checkScope) {
    final CompileContext context = adapter.getCompileContext();
    final FileProcessingCompilerStateCache cache = getFileProcessingCompilerCache(adapter.getCompiler());
    final FileProcessingCompiler.ProcessingItem[] items = adapter.getProcessingItems();
    if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      return false;
    }
    final CompileScope scope = context.getCompileScope();
    final List<FileProcessingCompiler.ProcessingItem> toProcess = new ArrayList<FileProcessingCompiler.ProcessingItem>();
    final Set<String> allUrls = new HashSet<String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (FileProcessingCompiler.ProcessingItem item : items) {
          final VirtualFile file = item.getFile();
          final String url = file.getUrl();
          allUrls.add(url);
          if (!forceCompile && cache.getTimestamp(url) == file.getTimeStamp()) {
            final ValidityState state = cache.getExtState(url);
            final ValidityState itemState = item.getValidityState();
            if (state != null ? state.equalsTo(itemState) : itemState == null) {
              continue;
            }
          }
          toProcess.add(item);
        }
      }
    });

    final String[] urls = cache.getUrls();
    if (urls.length > 0) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText(CompilerBundle.message("progress.processing.outdated.files"));
      final List<String> urlsToRemove = new ArrayList<String>();
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
      if (urlsToRemove.size() > 0) {
        for (final String url : urlsToRemove) {
          adapter.processOutdatedItem(context, url, cache.getExtState(url));
          cache.remove(url);
        }
      }
      context.getProgressIndicator().popState();
    }

    if (toProcess.size() == 0) {
      return false;
    }

    context.getProgressIndicator().pushState();
    final FileProcessingCompiler.ProcessingItem[] processed =
      adapter.process(toProcess.toArray(new FileProcessingCompiler.ProcessingItem[toProcess.size()]));
    context.getProgressIndicator().popState();

    if (processed.length > 0) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText(CompilerBundle.message("progress.updating.caches"));
      try {
        final VirtualFile[] vFiles = new VirtualFile[processed.length];
        for (int idx = 0; idx < processed.length; idx++) {
          vFiles[idx] = processed[idx].getFile();
        }
        CompilerUtil.refreshVirtualFiles(vFiles);
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            for (FileProcessingCompiler.ProcessingItem item : processed) {
              cache.update(item.getFile(), item.getValidityState());
            }
          }
        });
      }
      finally {
        if (cache.isDirty()) {
          context.getProgressIndicator().setText(CompilerBundle.message("progress.saving.caches"));
          cache.save();
        }
        context.getProgressIndicator().popState();
      }
    }
    return true;
  }

  public TranslatingCompilerStateCache getTranslatingCompilerCache(TranslatingCompiler compiler) {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      cache = new TranslatingCompilerStateCache(myCachesDirectoryPath, getIdPrefix(compiler));
      myCompilerToCacheMap.put(compiler, cache);
    }
    else {
      LOG.assertTrue(cache instanceof TranslatingCompilerStateCache);
    }
    return (TranslatingCompilerStateCache)cache;
  }

  private FileProcessingCompilerStateCache getFileProcessingCompilerCache(FileProcessingCompiler compiler) {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      cache = new FileProcessingCompilerStateCache(myCachesDirectoryPath, getIdPrefix(compiler), compiler);
      myCompilerToCacheMap.put(compiler, cache);
    }
    else {
      LOG.assertTrue(cache instanceof FileProcessingCompilerStateCache);
    }
    return (FileProcessingCompilerStateCache)cache;
  }

  private StateCache<ValidityState> getGeneratingCompilerCache(final GeneratingCompiler compiler) {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      cache = new StateCache<ValidityState>(myCachesDirectoryPath + File.separator + getIdPrefix(compiler) + "_timestamp.dat") {
        public ValidityState read(DataInputStream stream) throws IOException {
          return compiler.createValidityState(stream);
        }

        public void write(ValidityState validityState, DataOutputStream stream) throws IOException {
          validityState.save(stream);
        }
      };
      myCompilerToCacheMap.put(compiler, cache);
    }
    return (StateCache<ValidityState>)cache;
  }

  private void dropInternalCache(Compiler compiler) {
    myCompilerToCacheMap.remove(compiler);
  }

  private static String getIdPrefix(Compiler compiler) {
    @NonNls String description = compiler.getDescription();
    return description.replaceAll("\\s+", "_").toLowerCase();
  }

  public void executeCompileTask(final CompileTask task, final CompileScope scope, final String contentName, final Runnable onTaskFinished) {
    final CompilerProgressIndicator indicator = new CompilerProgressIndicator(
      myProject,
      CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND,
      contentName
    );
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, indicator, scope, null, this, false);

    FileDocumentManager.getInstance().saveAllDocuments();

    //noinspection HardCodedStringLiteral
    new Thread("Compile Task Thread") {
      public void run() {
        synchronized (CompilerManager.getInstance(myProject)) {
          ProgressManager.getInstance().runProcess(new Runnable() {
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
          }, compileContext.getProgressIndicator());
        }
      }
    }.start();
  }

  private boolean executeCompileTasks(CompileContext context, boolean beforeTasks) {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    try {
      CompileTask[] tasks = beforeTasks ? manager.getBeforeTasks() : manager.getAfterTasks();
      if (tasks.length > 0) {
        progressIndicator.setText(
          beforeTasks ? CompilerBundle.message("progress.executing.precompile.tasks") : CompilerBundle.message("progress.executing.postcompile.tasks")
        );
        for (CompileTask task : tasks) {
          if (!task.execute(context)) {
            return false;
          }
        }
      }
    }
    finally {
      WindowManager.getInstance().getStatusBar(myProject).setInfo("");
      if (progressIndicator instanceof CompilerProgressIndicator) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ((CompilerProgressIndicator)progressIndicator).showCompilerContent();
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
      if (ModuleType.J2EE_APPLICATION.equals(module.getModuleType())) {
        continue; // makes no sence to demand jdk & output paths for such modules
      }
      final boolean hasSources = hasSources(module, false);
      final boolean hasTestSources = hasSources(module, true);
      if (!hasSources && !hasTestSources) {
        // If module contains no sources, shouldn't have to select JDK or output directory (SCR #19333)
        // todo still there may be problems with this approach if some generated files are attributed by this module
        continue;
      }
      final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
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
    if (modulesWithoutJdkAssigned.size() > 0) {
      showNotSpecifiedError("error.jdk.not.specified", modulesWithoutJdkAssigned, ClasspathEditor.NAME);
      return false;
    }

    if (modulesWithoutOutputPathSpecified.size() > 0) {
      showNotSpecifiedError("error.output.not.specified", modulesWithoutOutputPathSpecified, ContentEntriesEditor.NAME);
      return false;
    }

    if (nonExistingOutputPaths.size() > 0) {
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
            for (File file : nonExistingOutputPaths) {
              final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
              if (vFile == null) {
                return Boolean.FALSE;
              }
            }
            return Boolean.TRUE;
          }
        });
      if (!refreshSuccess.booleanValue()) {
        return false;
      }
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
      ProjectJdk jdk = null;
      LanguageLevel languageLevel = null;
      for (final Module module : chunkModules) {
        final ProjectJdk moduleJdk = ModuleRootManager.getInstance(module).getJdk();
        if (jdk == null) {
          jdk = moduleJdk;
        }
        else {
          if (!jdk.equals(moduleJdk)) {
            showCyclicModulesHaveDifferentJdksError(chunkModules.toArray(new Module[chunkModules.size()]));
            return false;
          }
        }

        LanguageLevel moduleLanguageLevel = module.getEffectiveLanguageLevel();
        if (languageLevel == null) {
          languageLevel = moduleLanguageLevel;
        } else {
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
    return J2EEModuleUtilEx.checkDependentModulesOutputPathConsistency(myProject, scopeModules, true);
  }

  private void showCyclicModulesHaveDifferentLanguageLevel(Module[] modulesInChunk) {
    LOG.assertTrue(modulesInChunk.length > 0);
    String moduleNameToSelect = modulesInChunk[0].getName();
    final StringBuffer moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.chunk.modules.must.have.same.language.level", moduleNames.toString()), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private void showCyclicModulesHaveDifferentJdksError(Module[] modulesInChunk) {
    LOG.assertTrue(modulesInChunk.length > 0);
    String moduleNameToSelect = modulesInChunk[0].getName();
    final StringBuffer moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.chunk.modules.must.have.same.jdk", moduleNames.toString()), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private StringBuffer getModulesString(Module[] modulesInChunk) {
    final StringBuffer moduleNames = new StringBuffer();
    for (Module module : modulesInChunk) {
      if (moduleNames.length() > 0) {
        moduleNames.append("\n");
      }
      moduleNames.append("\"").append(module.getName()).append("\"");
    }
    return moduleNames;
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

  private void showNotSpecifiedError(final @NonNls String resourceId, List<String> modules, String tabNameToSelect) {
    final StringBuffer names = new StringBuffer();
    String nameToSelect = null;
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
    final String message = CompilerBundle.message(resourceId, modules.size(), names);

    if(ApplicationManager.getApplication().isUnitTestMode()) {
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
    if (affectedOutputPaths.size() > 0) {
      final StringBuffer paths = new StringBuffer();
      for (final VirtualFile affectedOutputPath : affectedOutputPaths) {
        if (paths.length() < 0) {
          paths.append("\n");
        }
        paths.append(affectedOutputPath.getPath().replace('/', File.separatorChar));
      }
      final int answer = Messages.showOkCancelDialog(myProject, CompilerBundle.message("warning.sources.under.output.paths", paths.toString()), CommonBundle.getErrorTitle(), Messages.getWarningIcon());
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

}
