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
import com.intellij.compiler.make.CacheUtils;
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
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
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CompileDriver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileDriver");

  private final Project myProject;
  private final Map<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> myGenerationCompilerModuleToOutputDirMap; // [IntermediateOutputCompiler, Module] -> [ProductionSources, TestSources]
  private final String myCachesDirectoryPath;
  private boolean myShouldClearOutputDirectory;

  private final Map<Module, String> myModuleOutputPaths = new HashMap<Module, String>();
  private final Map<Module, String> myModuleTestOutputPaths = new HashMap<Module, String>();

  private static final String VERSION_FILE_NAME = "version.dat";
  private static final String LOCK_FILE_NAME = "in_progress.dat";

  private static final boolean GENERATE_CLASSPATH_INDEX = "true".equals(System.getProperty("generate.classpath.index"));
  private static final String PROP_PERFORM_INITIAL_REFRESH = "compiler.perform.outputs.refresh.on.start";
  private boolean myInitialRefreshPerformed = false;

  private static final FileProcessingCompilerAdapterFactory FILE_PROCESSING_COMPILER_ADAPTER_FACTORY = new FileProcessingCompilerAdapterFactory() {
    public FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler) {
      return new FileProcessingCompilerAdapter(context, compiler);
    }
  };
  private static final FileProcessingCompilerAdapterFactory FILE_PACKAGING_COMPILER_ADAPTER_FACTORY = new FileProcessingCompilerAdapterFactory() {
    public FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler) {
      return new PackagingCompilerAdapter(context, (PackagingCompiler)compiler);
    }
  };
  private CompilerFilter myCompilerFilter = CompilerFilter.ALL;
  private static final CompilerFilter SOURCE_PROCESSING_ONLY = new CompilerFilter() {
    public boolean acceptCompiler(Compiler compiler) {
      return compiler instanceof SourceProcessingCompiler;
    }
  };
  private static final CompilerFilter ALL_EXCEPT_SOURCE_PROCESSING = new CompilerFilter() {
    public boolean acceptCompiler(Compiler compiler) {
      return !SOURCE_PROCESSING_ONLY.acceptCompiler(compiler);
    }
  };

  private OutputPathFinder myOutputFinder; // need this for updating zip archives (experimental feature) 

  private Set<File> myAllOutputDirectories;
  private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;

  public CompileDriver(Project project) {
    myProject = project;
    myCachesDirectoryPath = CompilerPaths.getCacheStoreDirectory(myProject).getPath().replace('/', File.separatorChar);
    myShouldClearOutputDirectory = CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY;

    myGenerationCompilerModuleToOutputDirMap = new HashMap<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>>();

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final IntermediateOutputCompiler[] generatingCompilers = CompilerManager.getInstance(myProject).getCompilers(IntermediateOutputCompiler.class, myCompilerFilter);
    final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
    final CompilerConfiguration config = CompilerConfiguration.getInstance(project);
    for (Module module : allModules) {
      for (IntermediateOutputCompiler compiler : generatingCompilers) {
        final VirtualFile productionOutput = lookupVFile(lfs, CompilerPaths.getGenerationOutputPath(compiler, module, false));
        final VirtualFile testOutput = lookupVFile(lfs, CompilerPaths.getGenerationOutputPath(compiler, module, true));
        final Pair<IntermediateOutputCompiler, Module> pair = new Pair<IntermediateOutputCompiler, Module>(compiler, module);
        final Pair<VirtualFile, VirtualFile> outputs = new Pair<VirtualFile, VirtualFile>(productionOutput, testOutput);
        myGenerationCompilerModuleToOutputDirMap.put(pair, outputs);
      }
      if (config.isAnnotationProcessorsEnabled()) {
        if (config.isAnnotationProcessingEnabled(module)) {
          final String path = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
          if (path != null) {
            lookupVFile(lfs, path);  // ensure the file is created and added to VFS
          }
        }
      }
    }
  }

  public void setCompilerFilter(CompilerFilter compilerFilter) {
    myCompilerFilter = compilerFilter == null? CompilerFilter.ALL : compilerFilter;
  }

  public void rebuild(CompileStatusNotification callback) {
    doRebuild(callback, null, true, addAdditionalRoots(new ProjectCompileScope(myProject), ALL_EXCEPT_SOURCE_PROCESSING));
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    scope = addAdditionalRoots(scope, ALL_EXCEPT_SOURCE_PROCESSING);
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, false, callback, null, true, false);
    }
  }

  public boolean isUpToDate(CompileScope scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation started");
    }
    scope = addAdditionalRoots(scope, ALL_EXCEPT_SOURCE_PROCESSING);

    final CompilerTask task = new CompilerTask(myProject, true, "", true);
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, task, scope, createDependencyCache(), true, false);

    checkCachesVersion(compileContext);
    if (compileContext.isRebuildRequested()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Rebuild requested, up-to-date=false");
      }
      return false;
    }

    for (Map.Entry<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> entry : myGenerationCompilerModuleToOutputDirMap.entrySet()) {
      final Pair<VirtualFile, VirtualFile> outputs = entry.getValue();
      Module module = entry.getKey().getSecond();
      compileContext.assignModule(outputs.getFirst(), module, false);
      compileContext.assignModule(outputs.getSecond(), module, true);
    }

    final Ref<ExitStatus> status = new Ref<ExitStatus>();

    task.start(new Runnable() {
      public void run() {
        try {
          myAllOutputDirectories = getAllOutputDirectories();
          // need this for updating zip archives experiment, uncomment if the feature is turned on
          //myOutputFinder = new OutputPathFinder(myAllOutputDirectories);
          status.set(doCompile(compileContext, false, false, false, true));
        }
        finally {
          compileContext.commitZipFiles(); // just to be on the safe side; normally should do nothing if called in isUpToDate()
        }
      }
    }, null);

    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation finished");
    }

    return ExitStatus.UP_TO_DATE.equals(status.get());
  }

  private DependencyCache createDependencyCache() {
    return new DependencyCache(myCachesDirectoryPath + File.separator + ".dependency-info");
  }

  public void compile(CompileScope scope, CompileStatusNotification callback, boolean trackDependencies, boolean clearingOutputDirsPossible) {
    myShouldClearOutputDirectory &= clearingOutputDirsPossible;
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

    private CompileStatus(int cacheVersion, boolean isCompilationInProgress) {
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
      FileUtil.createIfDoesntExist(statusFile);
      DataOutputStream out = new DataOutputStream(new FileOutputStream(statusFile));
      try {
        out.writeInt(status.CACHE_FORMAT_VERSION);
      }
      finally {
        out.close();
      }
      if (status.COMPILATION_IN_PROGRESS) {
        FileUtil.createIfDoesntExist(lockFile);
      }
      else {
        deleteFile(lockFile);
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

  private CompileScope addAdditionalRoots(CompileScope originalScope, final CompilerFilter filter) {
    CompileScope scope = attachIntermediateOutputDirectories(originalScope, filter);

    final AdditionalCompileScopeProvider[] scopeProviders = Extensions.getExtensions(AdditionalCompileScopeProvider.EXTENSION_POINT_NAME);
    CompileScope baseScope = scope;
    for (AdditionalCompileScopeProvider scopeProvider : scopeProviders) {
      final CompileScope additionalScope = scopeProvider.getAdditionalScope(baseScope, filter, myProject);
      if (additionalScope != null) {
        scope = new CompositeScope(scope, additionalScope);
      }
    }
    return scope;
  }

  private CompileScope attachIntermediateOutputDirectories(CompileScope originalScope, CompilerFilter filter) {
    CompileScope scope = originalScope;
    final Set<Module> affected = new HashSet<Module>(Arrays.asList(originalScope.getAffectedModules()));
    for (Map.Entry<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> entry : myGenerationCompilerModuleToOutputDirMap.entrySet()) {
      final Module module = entry.getKey().getSecond();
      if (affected.contains(module) && filter.acceptCompiler(entry.getKey().getFirst())) {
        final Pair<VirtualFile, VirtualFile> outputs = entry.getValue();
        scope = new CompositeScope(scope, new FileSetCompileScope(Arrays.asList(outputs.getFirst(), outputs.getSecond()), new Module[]{module}));
      }
    }
    return scope;
  }

  private void attachAnnotationProcessorsOutputDirectories(CompileContextEx context) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
    final Set<Module> affected = new HashSet<Module>(Arrays.asList(context.getCompileScope().getAffectedModules()));
    for (Module module : affected) {
      if (!config.isAnnotationProcessingEnabled(module)) {
        continue;
      }
      final String path = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
      if (path == null) {
        continue;
      }
      final VirtualFile vFile = lfs.findFileByPath(path);
      if (vFile == null) {
        continue;
      }
      if (ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vFile)) {
        // no need to add, is already marked as source
        continue;
      }
      context.addScope(new FileSetCompileScope(Collections.singletonList(vFile), new Module[]{module}));
      context.assignModule(vFile, module, false);
    }
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

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    final DependencyCache dependencyCache = createDependencyCache();
    final CompileContextImpl compileContext =
      new CompileContextImpl(myProject, compileTask, scope, dependencyCache, !isRebuild && !forceCompile, isRebuild);
    compileContext.putUserData(COMPILATION_START_TIMESTAMP, LocalTimeCounter.currentTime());
    for (Map.Entry<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> entry : myGenerationCompilerModuleToOutputDirMap.entrySet()) {
      final Pair<VirtualFile, VirtualFile> outputs = entry.getValue();
      final Module module = entry.getKey().getSecond();
      compileContext.assignModule(outputs.getFirst(), module, false);
      compileContext.assignModule(outputs.getSecond(), module, true);
    }
    attachAnnotationProcessorsOutputDirectories(compileContext);
    
    compileTask.start(new Runnable() {
      public void run() {
        long start = System.currentTimeMillis();
        try {
          if (myProject.isDisposed()) {
            return;
          }
          LOG.info("COMPILATION STARTED");
          if (message != null) {
            compileContext.addMessage(message);
          }
          TranslatingCompilerFilesMonitor.getInstance().ensureInitializationCompleted(myProject);
          doCompile(compileContext, isRebuild, forceCompile, callback, checkCachesVersion, trackDependencies);
        }
        finally {
          compileContext.commitZipFiles();
          final long finish = System.currentTimeMillis();
          CompilerUtil.logDuration(
            "\tCOMPILATION FINISHED; Errors: " +
            compileContext.getMessageCount(CompilerMessageCategory.ERROR) +
            "; warnings: " +
            compileContext.getMessageCount(CompilerMessageCategory.WARNING),
            finish - start
          );
          CompilerCacheManager.getInstance(myProject).flushCaches();
          //if (LOG.isDebugEnabled()) {
          //  LOG.debug("COMPILATION FINISHED");
          //}
        }
      }
    }, new Runnable() {
      public void run() {
        if (isRebuild) {
          final int rv = Messages.showDialog(
              myProject, "You are about to rebuild the whole project.\nRun 'Make Project' instead?", "Confirm Project Rebuild",
              new String[]{"Make", "Rebuild"}, 0, Messages.getQuestionIcon()
          );
          if (rv == 0 /*yes, please, do run make*/) {
            startup(scope, false, false, callback, null, checkCachesVersion, trackDependencies);
            return;
          }
        }
        startup(scope, isRebuild, forceCompile, callback, message, checkCachesVersion, trackDependencies);
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

      myAllOutputDirectories = getAllOutputDirectories();
      // need this for updating zip archives experiment, uncomment if the feature is turned on
      //myOutputFinder = new OutputPathFinder(myAllOutputDirectories);
      status = doCompile(compileContext, isRebuild, forceCompile, trackDependencies, false);
    }
    catch (Throwable ex) {
      wereExceptions = true;
      final PluginId pluginId = IdeErrorsDialog.findPluginId(ex);

      final StringBuffer message = new StringBuffer();
      message.append("Internal error");
      if (pluginId != null) {
        message.append(" (Plugin: ").append(pluginId).append(")");
      }
      message.append(": ").append(ex.getMessage());
      compileContext.addMessage(CompilerMessageCategory.ERROR, message.toString(), null, -1, -1);
      
      if (pluginId != null) {
        throw new PluginException(ex, pluginId);
      }
      throw new RuntimeException(ex);
    }
    finally {
      dropDependencyCache(compileContext);
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
        final long duration = System.currentTimeMillis() - compileContext.getStartCompilationStamp();
        writeStatus(new CompileStatus(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION, wereExceptions), compileContext);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final int errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
            final int warningCount = compileContext.getMessageCount(CompilerMessageCategory.WARNING);
            final String statusMessage = createStatusMessage(_status, warningCount, errorCount);
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
            if (statusBar != null) { // because this code is in invoke later, the code may work for already closed project
              // in case another project was opened in the frame while the compiler was working (See SCR# 28591)
              StatusBar.Info.set(statusMessage, myProject);
              if (duration > ONE_MINUTE_MS) {
                final MessageType messageType = errorCount > 0 ? MessageType.ERROR : warningCount > 0 ? MessageType.WARNING : MessageType.INFO;
                ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.MESSAGES_WINDOW, messageType, statusMessage);
              }
            }
            if (_status != ExitStatus.UP_TO_DATE && compileContext.getMessageCount(null) > 0) {
              compileContext.addMessage(CompilerMessageCategory.INFORMATION, statusMessage, null, -1, -1);
            }
            if (callback != null) {
              callback.finished(_status == ExitStatus.CANCELLED, errorCount, warningCount, compileContext);
            }
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
    private final String myName;

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

    private ExitException(ExitStatus status) {
      myStatus = status;
    }

    public ExitStatus getExitStatus() {
      return myStatus;
    }
  }

  private ExitStatus doCompile(final CompileContextEx context,
                               boolean isRebuild,
                               final boolean forceCompile,
                               final boolean trackDependencies, final boolean onlyCheckStatus) {
    try {
      if (isRebuild) {
        deleteAll(context);
      }
      else if (forceCompile) {
        if (myShouldClearOutputDirectory) {
          clearAffectedOutputPathsIfPossible(context);
        }
      }
      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        if (LOG.isDebugEnabled()) {
          logErrorMessages(context);
        }
        return ExitStatus.ERRORS;
      }

      if (!onlyCheckStatus) {
          if (!executeCompileTasks(context, true)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Compilation cancelled");
            }
            return ExitStatus.CANCELLED;
          }
        }

      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        if (LOG.isDebugEnabled()) {
          logErrorMessages(context);
        }
        return ExitStatus.ERRORS;
      }

      boolean needRecalcOutputDirs = false;
      if (Registry.is(PROP_PERFORM_INITIAL_REFRESH) || !myInitialRefreshPerformed) {
        myInitialRefreshPerformed = true;
        final long refreshStart = System.currentTimeMillis();

        // need this to make sure the VFS is built
        //final List<VirtualFile> outputsToRefresh = new ArrayList<VirtualFile>();

        final VirtualFile[] all = context.getAllOutputDirectories();

        final ProgressIndicator progressIndicator = context.getProgressIndicator();

        final int totalCount = all.length + myGenerationCompilerModuleToOutputDirMap.size() * 2;
        final CountDownLatch latch = new CountDownLatch(totalCount);
        final Runnable decCount = new Runnable() {
          public void run() {
            latch.countDown();
            progressIndicator.setFraction(((double)(totalCount - latch.getCount())) / totalCount);
          }
        };
        progressIndicator.pushState();
        progressIndicator.setText("Inspecting output directories...");
        final boolean asyncMode = !ApplicationManager.getApplication().isDispatchThread(); // must not lock awt thread with latch.await()
        try {
          for (VirtualFile output : all) {
            if (output.isValid()) {
              walkChildren(output, context);
            }
            else {
              needRecalcOutputDirs = true;
              final File file = new File(output.getPath());
              if (!file.exists()) {
                final boolean created = file.mkdirs();
                if (!created) {
                  context.addMessage(CompilerMessageCategory.ERROR, "Failed to create output directory " + file.getPath(), null, 0, 0);
                  return ExitStatus.ERRORS;
                }
              }
              output = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
              if (output == null) {
                context.addMessage(CompilerMessageCategory.ERROR, "Failed to locate output directory " + file.getPath(), null, 0, 0);
                return ExitStatus.ERRORS;
              }
            }
            output.refresh(asyncMode, true, decCount);
            //outputsToRefresh.add(output);
          }
          for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
            final Pair<VirtualFile, VirtualFile> generated = myGenerationCompilerModuleToOutputDirMap.get(pair);
            walkChildren(generated.getFirst(), context);
            //outputsToRefresh.add(generated.getFirst());
            generated.getFirst().refresh(asyncMode, true, decCount);
            walkChildren(generated.getSecond(), context);
            //outputsToRefresh.add(generated.getSecond());
            generated.getSecond().refresh(asyncMode, true, decCount);
          }

          //RefreshQueue.getInstance().refresh(false, true, null, outputsToRefresh.toArray(new VirtualFile[outputsToRefresh.size()]));
          try {
            while (latch.getCount() > 0) {
              latch.await(500, TimeUnit.MILLISECONDS); // wait until all threads are refreshed
              if (progressIndicator.isCanceled()) {
                return ExitStatus.CANCELLED;
              }
            }
          }
          catch (InterruptedException e) {
            LOG.info(e);
          }
        }
        finally {
          progressIndicator.popState();
        }

        final long initialRefreshTime = System.currentTimeMillis() - refreshStart;
        CompilerUtil.logDuration("Initial VFS refresh", initialRefreshTime);
      }

      //DumbService.getInstance(myProject).waitForSmartMode();
      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      DumbService.getInstance(myProject).runWhenSmart(new Runnable() {
        public void run() {
          semaphore.up();
        }
      });
      while (!semaphore.waitFor(500)) {
        if (context.getProgressIndicator().isCanceled()) {
          return ExitStatus.CANCELLED;
        }
      }

      if (needRecalcOutputDirs) {
        context.recalculateOutputDirs();
      }

      boolean didSomething = false;

      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);

      try {
        didSomething |= generateSources(compilerManager, context, forceCompile, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, SourceInstrumentingCompiler.class,
                                                      FILE_PROCESSING_COMPILER_ADAPTER_FACTORY, forceCompile, true, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, SourceProcessingCompiler.class,
                                                      FILE_PROCESSING_COMPILER_ADAPTER_FACTORY, forceCompile, true, onlyCheckStatus);

        final CompileScope intermediateSources = attachIntermediateOutputDirectories(new CompositeScope(CompileScope.EMPTY_ARRAY) {
          @NotNull
          public Module[] getAffectedModules() {
            return context.getCompileScope().getAffectedModules();
          }
        }, SOURCE_PROCESSING_ONLY);
        context.addScope(intermediateSources);

        didSomething |= translate(context, compilerManager, forceCompile, isRebuild, trackDependencies, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassInstrumentingCompiler.class,
                                                      FILE_PROCESSING_COMPILER_ADAPTER_FACTORY, isRebuild, false, onlyCheckStatus);

        // explicitly passing forceCompile = false because in scopes that is narrower than ProjectScope it is impossible
        // to understand whether the class to be processed is in scope or not. Otherwise compiler may process its items even if
        // there were changes in completely independent files.
        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassPostProcessingCompiler.class,
                                                      FILE_PROCESSING_COMPILER_ADAPTER_FACTORY, isRebuild, false, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, PackagingCompiler.class,
                                                      FILE_PACKAGING_COMPILER_ADAPTER_FACTORY,
                                                      isRebuild, false, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, Validator.class, FILE_PROCESSING_COMPILER_ADAPTER_FACTORY,
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
          CompilerUtil.runInContext(context, "Generating classpath index...", new ThrowableRunnable<RuntimeException>(){
            public void run() {
              int count = 0;
              for (VirtualFile file : allOutputDirs) {
                context.getProgressIndicator().setFraction((double)++count / allOutputDirs.length);
                createClasspathIndex(file);
              }
            }
          });
        }

      }

      if (!onlyCheckStatus) {
          if (!executeCompileTasks(context, false)) {
            return ExitStatus.CANCELLED;
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

  private void clearAffectedOutputPathsIfPossible(CompileContextEx context) {
    final MultiMap<File, Module> outputToModulesMap = new MultiMap<File, Module>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension == null) {
        continue;
      }
      final String outputPathUrl = compilerModuleExtension.getCompilerOutputUrl();
      if (outputPathUrl != null) {
        final String path = VirtualFileManager.extractPath(outputPathUrl);
        outputToModulesMap.putValue(new File(path), module);
      }

      final String outputPathForTestsUrl = compilerModuleExtension.getCompilerOutputUrlForTests();
      if (outputPathForTestsUrl != null) {
        final String path = VirtualFileManager.extractPath(outputPathForTestsUrl);
        outputToModulesMap.putValue(new File(path), module);
      }
    }
    final Set<Module> affectedModules = new HashSet<Module>(Arrays.asList(context.getCompileScope().getAffectedModules()));
    final List<File> scopeOutputs = new ArrayList<File>(affectedModules.size() * 2);
    for (File output : outputToModulesMap.keySet()) {
      final Collection<Module> modules = outputToModulesMap.get(output);
      boolean shouldInclude = true;
      for (Module module : modules) {
        if (!affectedModules.contains(module)) {
          shouldInclude = false;
          break;
        }
      }
      if (shouldInclude) {
        scopeOutputs.add(output);
      }
    }
    if (scopeOutputs.size() > 0) {
      CompilerUtil.runInContext(context, CompilerBundle.message("progress.clearing.output"), new ThrowableRunnable<RuntimeException>() {
        public void run() {
          clearOutputDirectories(scopeOutputs);
        }
      });
    }
  }

  private static void logErrorMessages(final CompileContext context) {
    final CompilerMessage[] errors = context.getMessages(CompilerMessageCategory.ERROR);
    if (errors.length > 0) {
      LOG.debug("Errors reported: ");
      for (CompilerMessage error : errors) {
        LOG.debug("\t" + error.getMessage());
      }
    }
  }

  private static void walkChildren(VirtualFile from, final CompileContext context) {
    final VirtualFile[] files = from.getChildren();
    if (files != null && files.length > 0) {
      context.getProgressIndicator().checkCanceled();
      context.getProgressIndicator().setText2(from.getPresentableUrl());
      for (VirtualFile file : files) {
        walkChildren(file, context);
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
    CompilerUtil.runInContext(context, CompilerBundle.message("progress.saving.caches"), new ThrowableRunnable<RuntimeException>(){
      public void run() {
        context.getDependencyCache().resetState();
      }
    });
  }

  private boolean generateSources(final CompilerManager compilerManager,
                                  CompileContextEx context,
                                  final boolean forceCompile,
                                  final boolean onlyCheckStatus) throws ExitException {
    boolean didSomething = false;

    final SourceGeneratingCompiler[] sourceGenerators = compilerManager.getCompilers(SourceGeneratingCompiler.class, myCompilerFilter);
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
                            final boolean trackDependencies, final boolean onlyCheckStatus) throws ExitException {

    boolean didSomething = false;

    final TranslatingCompiler[] translators = compilerManager.getCompilers(TranslatingCompiler.class, myCompilerFilter);


    final List<Chunk<Module>> sortedChunks = Collections.unmodifiableList(ApplicationManager.getApplication().runReadAction(new Computable<List<Chunk<Module>>>() {
      public List<Chunk<Module>> compute() {
        final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        return ModuleCompilerUtil.getSortedModuleChunks(myProject, Arrays.asList(moduleManager.getModules()));
      }
    }));

    try {
      VirtualFile[] snapshot = null;
      final Map<Chunk<Module>, Collection<VirtualFile>> chunkMap = new HashMap<Chunk<Module>, Collection<VirtualFile>>();
      int total = 0;
      int processed = 0;
      for (final Chunk<Module> currentChunk : sortedChunks) {
        final TranslatorsOutputSink sink = new TranslatorsOutputSink(context, translators);
        final Set<FileType> generatedTypes = new HashSet<FileType>();
        Collection<VirtualFile> chunkFiles = chunkMap.get(currentChunk);
        try {
          for (int currentCompiler = 0, translatorsLength = translators.length; currentCompiler < translatorsLength; currentCompiler++) {
            sink.setCurrentCompilerIndex(currentCompiler);
            final TranslatingCompiler compiler = translators[currentCompiler];
            if (context.getProgressIndicator().isCanceled()) {
              throw new ExitException(ExitStatus.CANCELLED);
            }

            DumbService.getInstance(myProject).waitForSmartMode();

            if (snapshot == null || ContainerUtil.intersects(generatedTypes, compilerManager.getRegisteredInputTypes(compiler))) {
              // rescan snapshot if previously generated files may influence the input of this compiler
              snapshot = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
                public VirtualFile[] compute() {
                  return context.getCompileScope().getFiles(null, true);
                }
              });
              final Map<Module, List<VirtualFile>> moduleToFilesMap = CompilerUtil.buildModuleToFilesMap(context, snapshot);
              for (Chunk<Module> moduleChunk : sortedChunks) {
                List<VirtualFile> files = Collections.emptyList();
                for (Module module : moduleChunk.getNodes()) {
                  final List<VirtualFile> moduleFiles = moduleToFilesMap.get(module);
                  if (moduleFiles != null) {
                    files = ContainerUtil.concat(files, moduleFiles);
                  }
                }
                chunkMap.put(moduleChunk, files);
              }
              total = snapshot.length * translatorsLength;
              chunkFiles = chunkMap.get(currentChunk);
            }

            final CompileContextEx _context;
            if (compiler instanceof IntermediateOutputCompiler) {
              // wrap compile context so that output goes into intermediate directories
              final IntermediateOutputCompiler _compiler = (IntermediateOutputCompiler)compiler;
              _context = new CompileContextExProxy(context) {
                public VirtualFile getModuleOutputDirectory(final Module module) {
                  return getGenerationOutputDir(_compiler, module, false);
                }

                public VirtualFile getModuleOutputDirectoryForTests(final Module module) {
                  return getGenerationOutputDir(_compiler, module, true);
                }
              };
            }
            else {
              _context = context;
            }
            final boolean compiledSomething =
              compileSources(_context, currentChunk, compiler, chunkFiles, forceCompile, isRebuild, trackDependencies, onlyCheckStatus, sink);

            processed += chunkFiles.size();
            _context.getProgressIndicator().setFraction(((double)processed) / total);

            if (compiledSomething) {
              generatedTypes.addAll(compilerManager.getRegisteredOutputTypes(compiler));
            }

            if (_context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
              throw new ExitException(ExitStatus.ERRORS);
            }

            didSomething |= compiledSomething;
          }
        }
        finally {
          if (context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
            // perform update only if there were no errors, so it is guaranteed that the file was processd by all neccesary compilers
            sink.flushPostponedItems();
          }
        }
      }
    }
    catch (ProcessCanceledException e) {
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        public void run() {
          try {
            final Collection<VirtualFile> deps = CacheUtils.findDependentFiles(context, Collections.<VirtualFile>emptySet(), null, null);
            if (deps.size() > 0) {
              TranslatingCompilerFilesMonitor.getInstance().update(context, null, Collections.<TranslatingCompiler.OutputItem>emptyList(),
                                                                   VfsUtil.toVirtualFileArray(deps));
            }
          }
          catch (IOException ignored) {
            LOG.info(ignored);
          }
          catch (CacheCorruptedException ignored) {
            LOG.info(ignored);
          }
        }
      });
      throw e;
    }
    finally {
      dropDependencyCache(context);
      if (didSomething) {
        TranslatingCompilerFilesMonitor.getInstance().updateOutputRootsLayout(myProject);
      }
    }
    return didSomething;
  }

  private interface FileProcessingCompilerAdapterFactory {
    FileProcessingCompilerAdapter create(CompileContext context, FileProcessingCompiler compiler);
  }

  private boolean invokeFileProcessingCompilers(final CompilerManager compilerManager,
                                                CompileContextEx context,
                                                Class<? extends FileProcessingCompiler> fileProcessingCompilerClass,
                                                FileProcessingCompilerAdapterFactory factory,
                                                boolean forceCompile,
                                                final boolean checkScope,
                                                final boolean onlyCheckStatus) throws ExitException {
    boolean didSomething = false;
    final FileProcessingCompiler[] compilers = compilerManager.getCompilers(fileProcessingCompilerClass, myCompilerFilter);
    if (compilers.length > 0) {
      try {
        CacheDeferredUpdater cacheUpdater = new CacheDeferredUpdater();
        try {
          for (final FileProcessingCompiler compiler : compilers) {
            if (context.getProgressIndicator().isCanceled()) {
              throw new ExitException(ExitStatus.CANCELLED);
            }

            CompileContextEx _context = context;
            if (compiler instanceof IntermediateOutputCompiler) {
              final IntermediateOutputCompiler _compiler = (IntermediateOutputCompiler)compiler;
              _context = new CompileContextExProxy(context) {
                public VirtualFile getModuleOutputDirectory(final Module module) {
                  return getGenerationOutputDir(_compiler, module, false);
                }

                public VirtualFile getModuleOutputDirectoryForTests(final Module module) {
                  return getGenerationOutputDir(_compiler, module, true);
                }
              };
            }

            final boolean processedSomething = processFiles(factory.create(_context, compiler), forceCompile, checkScope, onlyCheckStatus, cacheUpdater);

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

  private void deleteAll(final CompileContextEx context) {
    CompilerUtil.runInContext(context, CompilerBundle.message("progress.clearing.output"), new ThrowableRunnable<RuntimeException>() {
      public void run() {
        final boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
        final VirtualFile[] allSources = context.getProjectCompileScope().getFiles(null, true);
        if (myShouldClearOutputDirectory) {
          clearOutputDirectories(myAllOutputDirectories);
        }
        else { // refresh is still required
          try {
            for (final Compiler compiler : CompilerManager.getInstance(myProject).getCompilers(Compiler.class)) {
              try {
                if (compiler instanceof GeneratingCompiler) {
                  final StateCache<ValidityState> cache = getGeneratingCompilerCache((GeneratingCompiler)compiler);
                  final Iterator<String> urlIterator = cache.getUrlsIterator();
                  while (urlIterator.hasNext()) {
                    context.getProgressIndicator().checkCanceled();
                    deleteFile(new File(VirtualFileManager.extractPath(urlIterator.next())));
                  }
                }
                else if (compiler instanceof TranslatingCompiler) {
                  final ArrayList<Trinity<File, String, Boolean>> toDelete = new ArrayList<Trinity<File, String, Boolean>>();
                  ApplicationManager.getApplication().runReadAction(new Runnable() {
                    public void run() {
                      TranslatingCompilerFilesMonitor.getInstance()
                        .collectFiles(context, (TranslatingCompiler)compiler, Arrays.<VirtualFile>asList(allSources).iterator(), true
                                      /*pass true to make sure that every source in scope file is processed*/, false
                                      /*important! should pass false to enable collection of files to delete*/,
                                      new ArrayList<VirtualFile>(), toDelete);
                    }
                  });
                  for (Trinity<File, String, Boolean> trinity : toDelete) {
                    context.getProgressIndicator().checkCanceled();
                    final File file = trinity.getFirst();
                    deleteFile(file);
                    if (isTestMode) {
                      CompilerManagerImpl.addDeletedPath(file.getPath());
                    }
                  }
                }
              }
              catch (IOException e) {
                LOG.info(e);
              }
            }
            pruneEmptyDirectories(context.getProgressIndicator(), myAllOutputDirectories); // to avoid too much files deleted events
          }
          finally {
            CompilerUtil.refreshIODirectories(myAllOutputDirectories);
          }
        }
        dropScopesCaches();

        clearCompilerSystemDirectory(context);
      }
    });
  }

  private void dropScopesCaches() {
    // hack to be sure the classpath will include the output directories
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).clearScopesCachesForModules();
      }
    });
  }

  private static void pruneEmptyDirectories(ProgressIndicator progress, final Set<File> directories) {
    for (File directory : directories) {
      doPrune(progress, directory, directories);
    }
  }

  private static boolean doPrune(ProgressIndicator progress, final File directory, final Set<File> outPutDirectories) {
    progress.checkCanceled();
    final File[] files = directory.listFiles();
    boolean isEmpty = true;
    if (files != null) {
      for (File file : files) {
        if (!outPutDirectories.contains(file)) {
          if (doPrune(progress, file, outPutDirectories)) {
            deleteFile(file);
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
    else {
      isEmpty = false;
    }

    return isEmpty;
  }

  private Set<File> getAllOutputDirectories() {
    final Set<File> outputDirs = new OrderedSet<File>((TObjectHashingStrategy<File>)TObjectHashingStrategy.CANONICAL);
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (final String path : CompilerPathsEx.getOutputPaths(modules)) {
      outputDirs.add(new File(path));
    }
    for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      outputDirs.add(new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), false)));
      outputDirs.add(new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), true)));
    }
    final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
    if (config.isAnnotationProcessorsEnabled()) {
      for (Module module : modules) {
        if (config.isAnnotationProcessingEnabled(module)) {
          final String path = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
          if (path != null) {
            outputDirs.add(new File(path));
          }
        }
      }
    }
    return outputDirs;
  }

  private static void clearOutputDirectories(final Collection<File> outputDirectories) {
    final long start = System.currentTimeMillis();
    // do not delete directories themselves, or we'll get rootsChanged() otherwise
    final Collection<File> filesToDelete = new ArrayList<File>(outputDirectories.size() * 2);
    for (File outputDirectory : outputDirectories) {
      File[] files = outputDirectory.listFiles();
      if (files != null) {
        ContainerUtil.addAll(filesToDelete, files);
      }
    }
    if (filesToDelete.size() > 0) {
      FileUtil.asyncDelete(filesToDelete);

      // ensure output directories exist
      for (final File file : outputDirectories) {
        file.mkdirs();
      }
      final long clearStop = System.currentTimeMillis();

      CompilerUtil.refreshIODirectories(outputDirectories);

      final long refreshStop = System.currentTimeMillis();

      CompilerUtil.logDuration("Clearing output dirs", clearStop - start);
      CompilerUtil.logDuration("Refreshing output directories", refreshStop - clearStop);
    }
  }

  private void clearCompilerSystemDirectory(final CompileContextEx context) {
    CompilerCacheManager.getInstance(myProject).clearCaches(context);
    FileUtil.delete(CompilerPathsEx.getZipStoreDirectory(myProject));
    dropDependencyCache(context);

    for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
      final File[] outputs = {
        new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), false)),
        new File(CompilerPaths.getGenerationOutputPath(pair.getFirst(), pair.getSecond(), true))
      };
      for (File output : outputs) {
        final File[] files = output.listFiles();
        if (files != null) {
          for (final File file : files) {
            final boolean deleteOk = deleteFile(file);
            if (!deleteOk) {
              context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.failed.to.delete", file.getPath()),
                                 null, -1, -1);
            }
          }
        }
      }
    }
  }

  /**
   * @param file a file to delete
   * @return true if and only if the file existed and was successfully deleted
   * Note: the behaviour is different from FileUtil.delete() which returns true if the file absent on the disk
   */
  private static boolean deleteFile(final File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File file1 : files) {
        deleteFile(file1);
      }
    }

    for (int i = 0; i < 10; i++){
      if (file.delete()) {
        return true;
      }
      if (!file.exists()) {
        return false;
      }
      try {
        Thread.sleep(50);
      }
      catch (InterruptedException ignored) {
      }
    }
    return false;
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
      final IOException[] ex = {null};
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
        CompilerUtil.runInContext(context, CompilerBundle.message("progress.synchronizing.output.directory"), new ThrowableRunnable<IOException>(){
          public void run() throws IOException {
            for (final String path : pathsToRemove) {
              final File file = new File(path);
              final boolean deleted = deleteFile(file);
              if (deleted) {
                cache.remove(path);
                filesToRefresh.add(file);
              }
            }
          }
        });
      }

      final Map<Module, Set<GeneratingCompiler.GenerationItem>> moduleToItemMap =
          buildModuleToGenerationItemMap(toGenerate.toArray(new GeneratingCompiler.GenerationItem[toGenerate.size()]));
      List<Module> modules = new ArrayList<Module>(moduleToItemMap.size());
      for (final Module module : moduleToItemMap.keySet()) {
        modules.add(module);
      }
      ModuleCompilerUtil.sortModules(myProject, modules);

      for (final Module module : modules) {
        CompilerUtil.runInContext(context, "Generating output from "+compiler.getDescription(),new ThrowableRunnable<IOException>(){
          public void run() throws IOException {
            final Set<GeneratingCompiler.GenerationItem> items = moduleToItemMap.get(module);
            if (items != null && !items.isEmpty()) {
              final GeneratingCompiler.GenerationItem[][] productionAndTestItems = splitGenerationItems(items);
              for (GeneratingCompiler.GenerationItem[] _items : productionAndTestItems) {
                if (_items.length == 0) continue;
                final VirtualFile outputDir = getGenerationOutputDir(compiler, module, _items[0].isTestSource());
                final GeneratingCompiler.GenerationItem[] successfullyGenerated = compiler.generate(context, _items, outputDir);

                CompilerUtil.runInContext(context, CompilerBundle.message("progress.updating.caches"), new ThrowableRunnable<IOException>() {
                  public void run() throws IOException {
                    if (successfullyGenerated.length > 0) {
                      affectedModules.add(module);
                    }
                    for (final GeneratingCompiler.GenerationItem item : successfullyGenerated) {
                      final String fullOutputPath = itemToOutputPathMap.get(item);
                      cache.update(fullOutputPath, item.getValidityState());
                      final File file = new File(fullOutputPath);
                      filesToRefresh.add(file);
                      generatedFiles.add(file);
                      context.getProgressIndicator().setText2(file.getPath());
                    }
                  }
                });
              }
            }
          }
        });
      }
    }
    catch (IOException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
      throw new ExitException(ExitStatus.ERRORS);
    }
    finally {
      CompilerUtil.refreshIOFiles(filesToRefresh);
      if (!generatedFiles.isEmpty()) {
        DumbService.getInstance(myProject).waitForSmartMode();
        List<VirtualFile> vFiles = ApplicationManager.getApplication().runReadAction(new Computable<List<VirtualFile>>() {
          public List<VirtualFile> compute() {
            final ArrayList<VirtualFile> vFiles = new ArrayList<VirtualFile>(generatedFiles.size());
            for (File generatedFile : generatedFiles) {
              final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(generatedFile);
              if (vFile != null) {
                vFiles.add(vFile);
              }
            }
            return vFiles;
          }
        });
        if (forceGenerate) {
          context.addScope(new FileSetCompileScope(vFiles, affectedModules.toArray(new Module[affectedModules.size()])));
        }
        context.markGenerated(vFiles);
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

  private boolean compileSources(final CompileContextEx context, final Chunk<Module> moduleChunk, final TranslatingCompiler compiler, final Collection<VirtualFile> srcSnapshot,
                                 final boolean forceCompile,
                                 final boolean isRebuild,
                                 final boolean trackDependencies,
                                 final boolean onlyCheckStatus,
                                 TranslatingCompiler.OutputSink sink) throws ExitException {

    final Set<VirtualFile> toCompile = new HashSet<VirtualFile>();
    final List<Trinity<File, String, Boolean>> toDelete = new ArrayList<Trinity<File, String, Boolean>>();
    context.getProgressIndicator().pushState();

    final boolean[] wereFilesDeleted = new boolean[]{false};
    boolean traverseRootsProcessed = false;
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {

          TranslatingCompilerFilesMonitor.getInstance().collectFiles(
              context, compiler, srcSnapshot.iterator(), forceCompile, isRebuild, toCompile, toDelete
          );
          if (trackDependencies && !toCompile.isEmpty()) { // should add dependent files
            // todo: drop this?
            final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
            final PsiManager psiManager = PsiManager.getInstance(myProject);
            for (final VirtualFile file : VfsUtil.toVirtualFileArray(toCompile)) {
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
          wereFilesDeleted[0] = syncOutputDir(context, toDelete);
        }
        catch (CacheCorruptedException e) {
          LOG.info(e);
          context.requestRebuildNextTime(e.getMessage());
        }
      }

      final boolean hadUnprocessedTraverseRoots = context.getDependencyCache().hasUnprocessedTraverseRoots();
      if ((wereFilesDeleted[0] || hadUnprocessedTraverseRoots || !toCompile.isEmpty()) && context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
        compiler.compile(context, moduleChunk, VfsUtil.toVirtualFileArray(toCompile), sink);
        traverseRootsProcessed = hadUnprocessedTraverseRoots != context.getDependencyCache().hasUnprocessedTraverseRoots();
      }
    }
    finally {
      context.getProgressIndicator().popState();
    }
    return !toCompile.isEmpty() || traverseRootsProcessed || wereFilesDeleted[0];
  }

  private static boolean syncOutputDir(final CompileContextEx context, final Collection<Trinity<File, String, Boolean>> toDelete) throws CacheCorruptedException {
    final DependencyCache dependencyCache = context.getDependencyCache();
    final boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();

    final List<File> filesToRefresh = new ArrayList<File>();
    final boolean[] wereFilesDeleted = {false};
    CompilerUtil.runInContext(context, CompilerBundle.message("progress.synchronizing.output.directory"), new ThrowableRunnable<CacheCorruptedException>(){
      public void run() throws CacheCorruptedException {
        final long start = System.currentTimeMillis();
        try {
          for (final Trinity<File, String, Boolean> trinity : toDelete) {
            final File outputPath = trinity.getFirst();
            context.getProgressIndicator().checkCanceled();
            context.getProgressIndicator().setText2(outputPath.getPath());
            filesToRefresh.add(outputPath);
            if (isTestMode) {
              LOG.assertTrue(outputPath.exists());
            }
            if (!deleteFile(outputPath)) {
              if (isTestMode && outputPath.exists()) {
                LOG.error("Was not able to delete output file: " + outputPath.getPath());
              }
              continue;
            }
            wereFilesDeleted[0] = true;

            // update zip here
            //final String outputDir = myOutputFinder.lookupOutputPath(outputPath);
            //if (outputDir != null) {
            //  try {
            //    context.updateZippedOuput(outputDir, FileUtil.toSystemIndependentName(outputPath.getPath()).substring(outputDir.length() + 1));
            //  }
            //  catch (IOException e) {
            //    LOG.info(e);
            //  }
            //}

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
        finally {
          CompilerUtil.logDuration("Sync output directory", System.currentTimeMillis() - start);
          CompilerUtil.refreshIOFiles(filesToRefresh);
        }
      }
    });
    return wereFilesDeleted[0];
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
    final CompileContextEx context = (CompileContextEx)adapter.getCompileContext();
    final FileProcessingCompilerStateCache cache = getFileProcessingCompilerCache(adapter.getCompiler());
    final FileProcessingCompiler.ProcessingItem[] items = adapter.getProcessingItems();
    if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      return false;
    }
    if (LOG.isDebugEnabled() && items.length > 0) {
      LOG.debug("Start processing files by " + adapter.getCompiler().getDescription());
    }
    final CompileScope scope = context.getCompileScope();
    final List<FileProcessingCompiler.ProcessingItem> toProcess = new ArrayList<FileProcessingCompiler.ProcessingItem>();
    final Set<String> allUrls = new HashSet<String>();
    final IOException[] ex = {null};
    DumbService.getInstance(myProject).waitForSmartMode();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          for (FileProcessingCompiler.ProcessingItem item : items) {
            final VirtualFile file = item.getFile();
            if (file == null) {
              LOG.error("FileProcessingCompiler.ProcessingItem.getFile() must not return null: compiler " + adapter.getCompiler().getDescription());
              continue;
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
      CompilerUtil.runInContext(context, CompilerBundle.message("progress.processing.outdated.files"), new ThrowableRunnable<IOException>(){
        public void run() throws IOException {
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
        }
      });
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

    final FileProcessingCompiler.ProcessingItem[] processed =
      adapter.process(toProcess.toArray(new FileProcessingCompiler.ProcessingItem[toProcess.size()]));

    if (processed.length == 0) {
      return true;
    }
    CompilerUtil.runInContext(context, CompilerBundle.message("progress.updating.caches"), new ThrowableRunnable<IOException>() {
      public void run() throws IOException {
        final List<VirtualFile> vFiles = new ArrayList<VirtualFile>(processed.length);
        for (FileProcessingCompiler.ProcessingItem aProcessed : processed) {
          final VirtualFile file = aProcessed.getFile();
          vFiles.add(file);
          if (LOG.isDebugEnabled()) {
            LOG.debug("\tFile processed " + file.getPresentableUrl() + "; ts=" + file.getTimeStamp());
          }

          //final String path = file.getPath();
          //final String outputDir = myOutputFinder.lookupOutputPath(path);
          //if (outputDir != null) {
          //  context.updateZippedOuput(outputDir, path.substring(outputDir.length() + 1));
          //}
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
    });
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
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, progressManagerTask, scope, null, false, false);

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
          compileContext.commitZipFiles();
          if (onTaskFinished != null) {
            onTaskFinished.run();
          }
        }
      }
    }, null);
  }

  private boolean executeCompileTasks(final CompileContext context, final boolean beforeTasks) {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    progressIndicator.pushState();
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
      progressIndicator.popState();
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

  private boolean validateCompilerConfiguration(final CompileScope scope, boolean checkOutputAndSourceIntersection) {
    final Module[] scopeModules = scope.getAffectedModules()/*ModuleManager.getInstance(myProject).getModules()*/;
    final List<String> modulesWithoutOutputPathSpecified = new ArrayList<String>();
    boolean isProjectCompilePathSpecified = true;
    final List<String> modulesWithoutJdkAssigned = new ArrayList<String>();
    final Set<File> nonExistingOutputPaths = new HashSet<File>();
    final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);

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
        if (config.isAnnotationProcessorsEnabled() && config.isAnnotationProcessingEnabled(module)) {
          final String path = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
          if (path == null) {
            final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(module.getProject());
            if (extension == null || extension.getCompilerOutputUrl() == null) {
              isProjectCompilePathSpecified = false;
            }
            else {
              modulesWithoutOutputPathSpecified.add(module.getName());
            }
          }
          else {
            final File file = new File(path);
            if (!file.exists()) {
              nonExistingOutputPaths.add(file);
            }
          }
        }
      }
    }
    if (!modulesWithoutJdkAssigned.isEmpty()) {
      showNotSpecifiedError("error.jdk.not.specified", modulesWithoutJdkAssigned, ProjectBundle.message("modules.classpath.title"));
      return false;
    }

    if (!isProjectCompilePathSpecified) {
      final String message = CompilerBundle.message("error.project.output.not.specified");
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(message);
      }

      Messages.showMessageDialog(myProject, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      ProjectSettingsService.getInstance(myProject).openProjectSettings();
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
          if (file.exists()) {
            // for overlapping paths, this one might have been created as an intermediate path on a previous iteration
            continue;
          }
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
    final List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(myProject, Arrays.asList(scopeModules));
    for (final Chunk<Module> chunk : chunks) {
      final Set<Module> chunkModules = chunk.getNodes();
      if (chunkModules.size() <= 1) {
        continue; // no need to check one-module chunks
      }
      if (config.isAnnotationProcessorsEnabled()) {
        for (Module chunkModule : chunkModules) {
          if (config.isAnnotationProcessingEnabled(chunkModule)) {
            showCyclesNotSupportedForAnnotationProcessors(chunkModules.toArray(new Module[chunkModules.size()]));
            return false;
          }
        }
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

  private void showCyclesNotSupportedForAnnotationProcessors(Module[] modulesInChunk) {
    LOG.assertTrue(modulesInChunk.length > 0);
    String moduleNameToSelect = modulesInChunk[0].getName();
    final String moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.annotation.processing.not.supported.for.module.cycles", moduleNames),
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
    ProjectSettingsService.getInstance(myProject).showModuleConfigurationDialog(moduleNameToSelect, tabNameToSelect, false);
  }

  private static VirtualFile lookupVFile(final LocalFileSystem lfs, final String path) {
    final File file = new File(path);

    VirtualFile vFile = lfs.findFileByIoFile(file);
    if (vFile != null) {
      return vFile;
    }

    final boolean justCreated = file.mkdirs();
    vFile = lfs.refreshAndFindFileByIoFile(file);

    assert vFile != null: "Virtual file not found for " + file.getPath() + "; mkdirs() exit code is " + justCreated;

    return vFile;
  }

  private static class CacheDeferredUpdater {
    private final Map<VirtualFile, List<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>>> myData = new java.util.HashMap<VirtualFile, List<Pair<FileProcessingCompilerStateCache, FileProcessingCompiler.ProcessingItem>>>();

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
      final IOException[] ex = {null};
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

  private static class TranslatorsOutputSink implements TranslatingCompiler.OutputSink {
    final Map<String, Collection<TranslatingCompiler.OutputItem>> myPostponedItems = new HashMap<String, Collection<TranslatingCompiler.OutputItem>>();
    private final CompileContextEx myContext;
    private final TranslatingCompiler[] myCompilers;
    private int myCurrentCompilerIdx;
    //private LinkedBlockingQueue<Future> myFutures = new LinkedBlockingQueue<Future>();

    private TranslatorsOutputSink(CompileContextEx context, TranslatingCompiler[] compilers) {
      myContext = context;
      this.myCompilers = compilers;
    }

    public void setCurrentCompilerIndex(int index) {
      myCurrentCompilerIdx = index;
    }

    public void add(final String outputRoot, final Collection<TranslatingCompiler.OutputItem> items, final VirtualFile[] filesToRecompile) {
      final TranslatingCompiler compiler = myCompilers[myCurrentCompilerIdx];
      if (compiler instanceof IntermediateOutputCompiler) {
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        final List<VirtualFile> outputs = new ArrayList<VirtualFile>();
        for (TranslatingCompiler.OutputItem item : items) {
          final VirtualFile vFile = lfs.findFileByPath(item.getOutputPath());
          if (vFile != null) {
            outputs.add(vFile);
          }
        }
        myContext.markGenerated(outputs);
      }
      final int nextCompilerIdx = myCurrentCompilerIdx + 1;
      try {
        if (nextCompilerIdx < myCompilers.length ) {
          final Map<String, Collection<TranslatingCompiler.OutputItem>> updateNow = new java.util.HashMap<String, Collection<TranslatingCompiler.OutputItem>>();
          // process postponed
          for (Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry : myPostponedItems.entrySet()) {
            final String outputDir = entry.getKey();
            final Collection<TranslatingCompiler.OutputItem> postponed = entry.getValue();
            for (Iterator<TranslatingCompiler.OutputItem> it = postponed.iterator(); it.hasNext();) {
              TranslatingCompiler.OutputItem item = it.next();
              boolean shouldPostpone = false;
              for (int idx = nextCompilerIdx; idx < myCompilers.length; idx++) {
                if (shouldPostpone = myCompilers[idx].isCompilableFile(item.getSourceFile(), myContext)) {
                  break;
                }
              }
              if (!shouldPostpone) {
                // the file is not compilable by the rest of compilers, so it is safe to update it now
                it.remove();
                addItemToMap(updateNow, outputDir, item);
              }
            }
          }
          // process items from current compilation
          for (TranslatingCompiler.OutputItem item : items) {
            boolean shouldPostpone = false;
            for (int idx = nextCompilerIdx; idx < myCompilers.length; idx++) {
              if (shouldPostpone = myCompilers[idx].isCompilableFile(item.getSourceFile(), myContext)) {
                break;
              }
            }
            if (shouldPostpone) {
              // the file is compilable by the next compiler in row, update should be postponed
              addItemToMap(myPostponedItems, outputRoot, item);
            }
            else {
              addItemToMap(updateNow, outputRoot, item);
            }
          }

          if (updateNow.size() == 1) {
            final Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry = updateNow.entrySet().iterator().next();
            final String outputDir = entry.getKey();
            final Collection<TranslatingCompiler.OutputItem> itemsToUpdate = entry.getValue();
            TranslatingCompilerFilesMonitor.getInstance().update(myContext, outputDir, itemsToUpdate, filesToRecompile);
          }
          else {
            for (Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry : updateNow.entrySet()) {
              final String outputDir = entry.getKey();
              final Collection<TranslatingCompiler.OutputItem> itemsToUpdate = entry.getValue();
              TranslatingCompilerFilesMonitor.getInstance().update(myContext, outputDir, itemsToUpdate, VirtualFile.EMPTY_ARRAY);
            }
            if (filesToRecompile.length > 0) {
              TranslatingCompilerFilesMonitor.getInstance().update(myContext, null, Collections.<TranslatingCompiler.OutputItem>emptyList(), filesToRecompile);
            }
          }
        }
        else {
          TranslatingCompilerFilesMonitor.getInstance().update(myContext, outputRoot, items, filesToRecompile);
        }
      }
      catch (IOException e) {
        LOG.info(e);
        myContext.requestRebuildNextTime(e.getMessage());
      }
    }

    private void addItemToMap(Map<String, Collection<TranslatingCompiler.OutputItem>> map, String outputDir, TranslatingCompiler.OutputItem item) {
      Collection<TranslatingCompiler.OutputItem> collection = map.get(outputDir);
      if (collection == null) {
        collection = new ArrayList<TranslatingCompiler.OutputItem>();
        map.put(outputDir, collection);
      }
      collection.add(item);
    }

    public void flushPostponedItems() {
      final TranslatingCompilerFilesMonitor filesMonitor = TranslatingCompilerFilesMonitor.getInstance();
      try {
        for (Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry : myPostponedItems.entrySet()) {
          final String outputDir = entry.getKey();
          final Collection<TranslatingCompiler.OutputItem> items = entry.getValue();
          filesMonitor.update(myContext, outputDir, items, VirtualFile.EMPTY_ARRAY);
        }
      }
      catch (IOException e) {
        LOG.info(e);
        myContext.requestRebuildNextTime(e.getMessage());
      }
    }
  }
}
