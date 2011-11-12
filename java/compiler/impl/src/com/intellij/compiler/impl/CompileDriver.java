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
import com.intellij.compiler.*;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.CacheUtils;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.compiler.generic.GenericCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
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
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.JpsRemoteProto;
import org.jetbrains.jps.api.JpsServerResponseHandlerAdapter;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class CompileDriver {
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Compiler");

  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileDriver");
  // to be used in tests only for debug output
  public static volatile boolean ourDebugMode = false;

  private final Project myProject;
  private final Map<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> myGenerationCompilerModuleToOutputDirMap; // [IntermediateOutputCompiler, Module] -> [ProductionSources, TestSources]
  private final String myCachesDirectoryPath;
  private boolean myShouldClearOutputDirectory;

  private final Map<Module, String> myModuleOutputPaths = new HashMap<Module, String>();
  private final Map<Module, String> myModuleTestOutputPaths = new HashMap<Module, String>();

  @NonNls private static final String VERSION_FILE_NAME = "version.dat";
  @NonNls private static final String LOCK_FILE_NAME = "in_progress.dat";

  private static final boolean GENERATE_CLASSPATH_INDEX = "true".equals(System.getProperty("generate.classpath.index"));
  private static final String PROP_PERFORM_INITIAL_REFRESH = "compiler.perform.outputs.refresh.on.start";
  private static final Key<Boolean> REFRESH_DONE_KEY = Key.create("_compiler.initial.refresh.done_");

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
    final ProjectCompileScope projectScope = new ProjectCompileScope(myProject);
    final CompileScope compileScope = useCompileServer() ? projectScope : addAdditionalRoots(projectScope, ALL_EXCEPT_SOURCE_PROCESSING);
    doRebuild(callback, null, true, compileScope);
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    if (!useCompileServer()) {
      scope = addAdditionalRoots(scope, ALL_EXCEPT_SOURCE_PROCESSING);
    }
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, false, callback, null, true);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.getInstance());
    }
  }

  public boolean isUpToDate(CompileScope scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation started");
    }
    scope = addAdditionalRoots(scope, ALL_EXCEPT_SOURCE_PROCESSING);

    final CompilerTask task = new CompilerTask(myProject, true, "Classes up-to-date check", true);
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, task, scope, createDependencyCache(), true, false);

    checkCachesVersion(compileContext, ((PersistentFS)ManagingFS.getInstance()).getCreationTimestamp());
    if (compileContext.isRebuildRequested()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Rebuild requested, up-to-date=false");
      }
      return false;
    }

    for (Map.Entry<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> entry : myGenerationCompilerModuleToOutputDirMap.entrySet()) {
      final Pair<VirtualFile, VirtualFile> outputs = entry.getValue();
      final Pair<IntermediateOutputCompiler, Module> key = entry.getKey();
      final Module module = key.getSecond();
      compileContext.assignModule(outputs.getFirst(), module, false, key.getFirst());
      compileContext.assignModule(outputs.getSecond(), module, true, key.getFirst());
    }

    final Ref<ExitStatus> status = new Ref<ExitStatus>();

    task.start(new Runnable() {
      public void run() {
        try {
          myAllOutputDirectories = getAllOutputDirectories(compileContext);
          // need this for updating zip archives experiment, uncomment if the feature is turned on
          //myOutputFinder = new OutputPathFinder(myAllOutputDirectories);
          status.set(doCompile(compileContext, false, false, true));
        }
        finally {
          compileContext.commitZipFiles(); // just to be on the safe side; normally should do nothing if called in isUpToDate()
          CompilerCacheManager.getInstance(myProject).flushCaches();
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

  public void compile(CompileScope scope, CompileStatusNotification callback, boolean clearingOutputDirsPossible) {
    myShouldClearOutputDirectory &= clearingOutputDirsPossible;
    if (containsFileIndexScopes(scope)) {
      scope = addAdditionalRoots(scope, ALL_EXCEPT_SOURCE_PROCESSING);
    }
    if (validateCompilerConfiguration(scope, false)) {
      startup(scope, false, true, callback, null, true);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.getInstance());
    }
  }

  private static boolean containsFileIndexScopes(CompileScope scope) {
    if (scope instanceof CompositeScope) {
      for (CompileScope childScope : ((CompositeScope)scope).getScopes()) {
        if (containsFileIndexScopes(childScope)) {
          return true;
        }
      }
    }
    return scope instanceof FileIndexCompileScope;
  }

  private static class CompileStatus {
    final int CACHE_FORMAT_VERSION;
    final boolean COMPILATION_IN_PROGRESS;
    final long VFS_CREATION_STAMP;

    private CompileStatus(int cacheVersion, boolean isCompilationInProgress, long vfsStamp) {
      CACHE_FORMAT_VERSION = cacheVersion;
      COMPILATION_IN_PROGRESS = isCompilationInProgress;
      VFS_CREATION_STAMP = vfsStamp;
    }
  }

  private CompileStatus readStatus() {
    final boolean isInProgress = getLockFile().exists();
    int version = -1;
    long vfsStamp = -1L;
    try {
      final File versionFile = new File(myCachesDirectoryPath, VERSION_FILE_NAME);
      DataInputStream in = new DataInputStream(new FileInputStream(versionFile));
      try {
        version = in.readInt();
        try {
          vfsStamp = in.readLong();
        }
        catch (IOException ignored) {
        }
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
    return new CompileStatus(version, isInProgress, vfsStamp);
  }

  private void writeStatus(CompileStatus status, CompileContext context) {
    final File statusFile = new File(myCachesDirectoryPath, VERSION_FILE_NAME);

    final File lockFile = getLockFile();
    try {
      FileUtil.createIfDoesntExist(statusFile);
      DataOutputStream out = new DataOutputStream(new FileOutputStream(statusFile));
      try {
        out.writeInt(status.CACHE_FORMAT_VERSION);
        out.writeLong(status.VFS_CREATION_STAMP);
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
      startup(compileScope, true, false, callback, message, checkCachesVersion);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.getInstance());
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
      context.assignModule(vFile, module, false, null);
    }
  }

  @Nullable
  private Future compileOnServer(final CompileContext compileContext, Collection<Module> modules, boolean isMake, @Nullable final CompileStatusNotification callback)
    throws Exception {
    List<String> moduleNames = Collections.emptyList();
    if (modules != null && modules.size() > 0) {
      moduleNames = new ArrayList<String>(modules.size());
      for (Module module : modules) {
        moduleNames.add(module.getName());
      }
    }
    compileContext.getProgressIndicator().setIndeterminate(true); // todo
    return JpsServerManager.getInstance().submitCompilationTask(myProject.getLocation(), moduleNames, !isMake, new JpsServerResponseHandlerAdapter() {

      public void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compilerMessage) {
        final JpsRemoteProto.Message.Response.CompileMessage.Kind kind = compilerMessage.getKind();
        //System.out.println(compilerMessage.getText());
        if (kind == JpsRemoteProto.Message.Response.CompileMessage.Kind.PROGRESS) {
          compileContext.getProgressIndicator().setText(compilerMessage.getText());
        }
        else {
          final CompilerMessageCategory category = kind == JpsRemoteProto.Message.Response.CompileMessage.Kind.ERROR ? CompilerMessageCategory.ERROR
                                                   : kind == JpsRemoteProto.Message.Response.CompileMessage.Kind.WARNING ? CompilerMessageCategory.WARNING : CompilerMessageCategory.INFORMATION;
          Navigatable navigatable = null;

          String sourceFilePath = compilerMessage.hasSourceFilePath()? compilerMessage.getSourceFilePath() : null;
          if (sourceFilePath != null) {
            sourceFilePath = FileUtil.toSystemIndependentName(sourceFilePath);
          }
          final long offset = compilerMessage.hasProblemLocationOffset()? compilerMessage.getProblemLocationOffset() : -1L;
          if (sourceFilePath != null && offset >= 0L) {
            final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sourceFilePath);
            if (file != null) {
              navigatable = new OpenFileDescriptor(myProject, file, (int)offset);
            }
          }
          final String srcUrl = sourceFilePath != null? VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, sourceFilePath) : null;
          compileContext.addMessage(
            category, compilerMessage.getText(), srcUrl, (int)compilerMessage.getLine(), (int)compilerMessage.getColumn(), navigatable
          );
        }
      }

      @Override
      public boolean handleBuildEvent(JpsRemoteProto.Message.Response.BuildEvent event) {
        final JpsRemoteProto.Message.Response.BuildEvent.Type eventType = event.getEventType();
        switch (eventType) {
          case BUILD_STARTED:
            compileContext.getProgressIndicator().setText("Compilation started");
            break;
          case BUILD_COMPLETED:
            compileContext.getProgressIndicator().setText("Compilation completed");
            break;
          case BUILD_CANCELED:
            compileContext.getProgressIndicator().setText("Compilation cancelled");
            break;
        }
        return eventType == JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_COMPLETED || eventType == JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_CANCELED;
      }

      public void handleFailure(JpsRemoteProto.Message.Failure failure) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, failure.getDescription(), null, -1, -1);
        final String trace = failure.getStacktrace();
        if (trace != null) {
          LOG.info(trace);
          System.out.println(trace);
        }
      }

      public void sessionTerminated() {
        if (callback != null) {
          callback.finished(false, compileContext.getMessageCount(CompilerMessageCategory.ERROR), compileContext.getMessageCount(CompilerMessageCategory.WARNING), compileContext);
        }
      }
    });
  }


  public static final Key<Long> COMPILATION_START_TIMESTAMP = Key.create("COMPILATION_START_TIMESTAMP");

  private void startup(final CompileScope scope,
                       final boolean isRebuild,
                       final boolean forceCompile,
                       final CompileStatusNotification callback,
                       final CompilerMessage message,
                       final boolean checkCachesVersion) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final boolean useServer = useCompileServer();

    final String contentName =
      forceCompile ? CompilerBundle.message("compiler.content.name.compile") : CompilerBundle.message("compiler.content.name.make");
    final boolean compileInBackground = useServer? true : CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND;
    final CompilerTask compileTask =
      new CompilerTask(myProject, compileInBackground, contentName, ApplicationManager.getApplication().isUnitTestMode());

    StatusBar.Info.set("", myProject, "Compiler");

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    final DependencyCache dependencyCache = useServer? null: createDependencyCache();
    final CompileContextImpl compileContext =
      new CompileContextImpl(myProject, compileTask, scope, dependencyCache, !isRebuild && !forceCompile, isRebuild);
    compileContext.putUserData(COMPILATION_START_TIMESTAMP, LocalTimeCounter.currentTime());

    if (!useServer) {
      for (Map.Entry<Pair<IntermediateOutputCompiler, Module>, Pair<VirtualFile, VirtualFile>> entry : myGenerationCompilerModuleToOutputDirMap.entrySet()) {
        final Pair<VirtualFile, VirtualFile> outputs = entry.getValue();
        final Pair<IntermediateOutputCompiler, Module> key = entry.getKey();
        final Module module = key.getSecond();
        compileContext.assignModule(outputs.getFirst(), module, false, key.getFirst());
        compileContext.assignModule(outputs.getSecond(), module, true, key.getFirst());
      }
      attachAnnotationProcessorsOutputDirectories(compileContext);
    }

    final Runnable compileWork;
    if (useServer) {
      compileWork = new Runnable() {
        public void run() {
          if (compileContext.getProgressIndicator().isCanceled()) {
            if (callback != null) {
              callback.finished(true, 0, 0, compileContext);
            }
            return;
          }
          long start = System.currentTimeMillis();
          try {
            if (myProject.isDisposed()) {
              return;
            }
            LOG.info("COMPILATION STARTED (JPS SERVER)");
            if (message != null) {
              compileContext.addMessage(message);
            }
            final Future future = compileOnServer(compileContext, Arrays.asList(compileContext.getCompileScope().getAffectedModules()), compileContext.isMake(), callback);
            if (future != null) {
              try {
                future.get();
              }
              catch (InterruptedException e) {
                LOG.error(e); // todo
              }
              catch (ExecutionException e) {
                LOG.error(e); // todo
              }
            }
            else {
              callback.finished(false, compileContext.getMessageCount(CompilerMessageCategory.ERROR), compileContext.getMessageCount(CompilerMessageCategory.WARNING), compileContext);
            }
          }
          catch (Exception e) {
            LOG.error(e); // todo
            callback.finished(false, compileContext.getMessageCount(CompilerMessageCategory.ERROR), compileContext.getMessageCount(CompilerMessageCategory.WARNING), compileContext);
          }
          finally {
            final long finish = System.currentTimeMillis();
            CompilerUtil.logDuration(
              "\tCOMPILATION FINISHED; Errors: " +
              compileContext.getMessageCount(CompilerMessageCategory.ERROR) +
              "; warnings: " +
              compileContext.getMessageCount(CompilerMessageCategory.WARNING),
              finish - start
            );
            CompilerCacheManager.getInstance(myProject).flushCaches();

            final Set<File> outputs = new HashSet<File>();
            for (final String path : CompilerPathsEx.getOutputPaths(ModuleManager.getInstance(myProject).getModules())) {
              outputs.add(new File(path));
            }
            CompilerUtil.refreshIOFiles(outputs);
          }
        }
      };
    }
    else {
      compileWork = new Runnable() {
        public void run() {
          if (compileContext.getProgressIndicator().isCanceled()) {
            if (callback != null) {
              callback.finished(true, 0, 0, compileContext);
            }
            return;
          }
          long start = System.currentTimeMillis();
          try {
            if (myProject.isDisposed()) {
              return;
            }
            LOG.info("COMPILATION STARTED");
            if (message != null) {
              compileContext.addMessage(message);
            }
            TranslatingCompilerFilesMonitor.getInstance().ensureInitializationCompleted(myProject, compileContext.getProgressIndicator());
            doCompile(compileContext, isRebuild, forceCompile, callback, checkCachesVersion);
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
      };
    }
    compileTask.start(compileWork, new Runnable() {
      public void run() {
        if (isRebuild) {
          final int rv = Messages.showOkCancelDialog(
              myProject, "You are about to rebuild the whole project.\nRun 'Make Project' instead?", "Confirm Project Rebuild",
              "Make", "Rebuild", Messages.getQuestionIcon()
          );
          if (rv == 0 /*yes, please, do run make*/) {
            startup(scope, false, false, callback, null, checkCachesVersion);
            return;
          }
        }
        startup(scope, isRebuild, forceCompile, callback, message, checkCachesVersion);
      }
    });
  }

  private void doCompile(final CompileContextImpl compileContext,
                         final boolean isRebuild,
                         final boolean forceCompile,
                         final CompileStatusNotification callback,
                         final boolean checkCachesVersion) {
    ExitStatus status = ExitStatus.ERRORS;
    boolean wereExceptions = false;
    final long vfsTimestamp = ((PersistentFS)ManagingFS.getInstance()).getCreationTimestamp();
    try {
      if (checkCachesVersion) {
        checkCachesVersion(compileContext, vfsTimestamp);
        if (compileContext.isRebuildRequested()) {
          return;
        }
      }
      writeStatus(new CompileStatus(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION, true, vfsTimestamp), compileContext);
      if (compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return;
      }

      myAllOutputDirectories = getAllOutputDirectories(compileContext);
      // need this for updating zip archives experiment, uncomment if the feature is turned on
      //myOutputFinder = new OutputPathFinder(myAllOutputDirectories);
      status = doCompile(compileContext, isRebuild, forceCompile, false);
    }
    catch (Throwable ex) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(ex);
      }
      wereExceptions = true;
      final PluginId pluginId = IdeErrorsDialog.findPluginId(ex);

      final StringBuilder message = new StringBuilder();
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
            final CompilerMessageImpl msg = new CompilerMessageImpl(myProject, CompilerMessageCategory.INFORMATION, compileContext.getRebuildReason());
            doRebuild(callback, msg, false, compileContext.getCompileScope());
          }
        }, ModalityState.NON_MODAL);
      }
      else {
        final long duration = System.currentTimeMillis() - compileContext.getStartCompilationStamp();
        if (!myProject.isDisposed()) {
          writeStatus(new CompileStatus(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION, wereExceptions, vfsTimestamp), compileContext);
        }
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) return;
            final int errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
            final int warningCount = compileContext.getMessageCount(CompilerMessageCategory.WARNING);
            final String statusMessage = createStatusMessage(_status, warningCount, errorCount);
            final MessageType messageType = errorCount > 0 ? MessageType.ERROR : warningCount > 0 ? MessageType.WARNING : MessageType.INFO;
            if (duration > ONE_MINUTE_MS) {
              ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.MESSAGES_WINDOW, messageType, statusMessage);
            }

            NOTIFICATION_GROUP.createNotification(_status == ExitStatus.UP_TO_DATE ? "Compilation: all files are up to date" : statusMessage, messageType).notify(myProject);

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

  private void checkCachesVersion(final CompileContextImpl compileContext, final long currentVFSTimestamp) {
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
    else if (compileStatus.VFS_CREATION_STAMP >= 0L){
      if (currentVFSTimestamp != compileStatus.VFS_CREATION_STAMP) {
        compileContext.requestRebuildNextTime(CompilerBundle.message("error.vfs.was.rebuilt"));
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

  static class ExitStatus {
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

  static class ExitException extends Exception {
    private final ExitStatus myStatus;

    ExitException(ExitStatus status) {
      myStatus = status;
    }

    public ExitStatus getExitStatus() {
      return myStatus;
    }
  }

  private ExitStatus doCompile(final CompileContextEx context, boolean isRebuild, final boolean forceCompile, final boolean onlyCheckStatus) {
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
      if (Registry.is(PROP_PERFORM_INITIAL_REFRESH) || !Boolean.valueOf(REFRESH_DONE_KEY.get(myProject, Boolean.FALSE))) {
        REFRESH_DONE_KEY.set(myProject, Boolean.TRUE);
        final long refreshStart = System.currentTimeMillis();

        //need this to make sure the VFS is built
        final List<VirtualFile> outputsToRefresh = new ArrayList<VirtualFile>();

        final VirtualFile[] all = context.getAllOutputDirectories();

        final ProgressIndicator progressIndicator = context.getProgressIndicator();

        //final int totalCount = all.length + myGenerationCompilerModuleToOutputDirMap.size() * 2;
        progressIndicator.pushState();
        progressIndicator.setText("Inspecting output directories...");
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
            outputsToRefresh.add(output);
          }
          for (Pair<IntermediateOutputCompiler, Module> pair : myGenerationCompilerModuleToOutputDirMap.keySet()) {
            final Pair<VirtualFile, VirtualFile> generated = myGenerationCompilerModuleToOutputDirMap.get(pair);
            walkChildren(generated.getFirst(), context);
            outputsToRefresh.add(generated.getFirst());
            walkChildren(generated.getSecond(), context);
            outputsToRefresh.add(generated.getSecond());
          }

          RefreshQueue.getInstance().refresh(false, true, null, VfsUtil.toVirtualFileArray(outputsToRefresh));
          if (progressIndicator.isCanceled()) {
            return ExitStatus.CANCELLED;
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
      GenericCompilerRunner runner = new GenericCompilerRunner(context, myCompilerFilter, compilerManager, isRebuild, onlyCheckStatus);
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

        didSomething |= translate(context, compilerManager, forceCompile, isRebuild, onlyCheckStatus);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassInstrumentingCompiler.class,
                                                      FILE_PROCESSING_COMPILER_ADAPTER_FACTORY, isRebuild, false, onlyCheckStatus);
        didSomething |= runner.invokeCompilers(GenericCompiler.CompileOrderPlace.CLASS_INSTRUMENTING);

        // explicitly passing forceCompile = false because in scopes that is narrower than ProjectScope it is impossible
        // to understand whether the class to be processed is in scope or not. Otherwise compiler may process its items even if
        // there were changes in completely independent files.
        didSomething |= invokeFileProcessingCompilers(compilerManager, context, ClassPostProcessingCompiler.class,
                                                      FILE_PROCESSING_COMPILER_ADAPTER_FACTORY, isRebuild, false, onlyCheckStatus);
        didSomething |= runner.invokeCompilers(GenericCompiler.CompileOrderPlace.CLASS_POST_PROCESSING);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, PackagingCompiler.class,
                                                      FILE_PACKAGING_COMPILER_ADAPTER_FACTORY,
                                                      isRebuild, false, onlyCheckStatus);
        didSomething |= runner.invokeCompilers(GenericCompiler.CompileOrderPlace.PACKAGING);

        didSomething |= invokeFileProcessingCompilers(compilerManager, context, Validator.class, FILE_PROCESSING_COMPILER_ADAPTER_FACTORY,
                                                      forceCompile, true, onlyCheckStatus);
        didSomething |= runner.invokeCompilers(GenericCompiler.CompileOrderPlace.VALIDATING);
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
    writer.write(VfsUtilCore.getRelativePath(file, root, '/'));
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
                            final boolean onlyCheckStatus) throws ExitException {

    boolean didSomething = false;

    final TranslatingCompiler[] translators = compilerManager.getCompilers(TranslatingCompiler.class, myCompilerFilter);


    final List<Chunk<Module>> sortedChunks = Collections.unmodifiableList(ApplicationManager.getApplication().runReadAction(new Computable<List<Chunk<Module>>>() {
      public List<Chunk<Module>> compute() {
        final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        return ModuleCompilerUtil.getSortedModuleChunks(myProject, Arrays.asList(moduleManager.getModules()));
      }
    }));

    final DumbService dumbService = DumbService.getInstance(myProject);
    try {
      final Set<Module> processedModules = new HashSet<Module>();
      VirtualFile[] snapshot = null;
      final Map<Chunk<Module>, Collection<VirtualFile>> chunkMap = new HashMap<Chunk<Module>, Collection<VirtualFile>>();
      int total = 0;
      int processed = 0;
      for (final Chunk<Module> currentChunk : sortedChunks) {
        final TranslatorsOutputSink sink = new TranslatorsOutputSink(context, translators);
        final Set<FileType> generatedTypes = new HashSet<FileType>();
        Collection<VirtualFile> chunkFiles = chunkMap.get(currentChunk);
        final Set<VirtualFile> filesToRecompile = new HashSet<VirtualFile>();
        final Set<VirtualFile> allDependent = new HashSet<VirtualFile>();
        try {
          int round = 0;
          boolean compiledSomethingForThisChunk = false;
          Collection<VirtualFile> dependentFiles = Collections.emptyList();
          final Function<Pair<int[], Set<VirtualFile>>, Pair<int[], Set<VirtualFile>>> dependencyFilter = new DependentClassesCumulativeFilter();
          
          do {
            for (int currentCompiler = 0, translatorsLength = translators.length; currentCompiler < translatorsLength; currentCompiler++) {
              sink.setCurrentCompilerIndex(currentCompiler);
              final TranslatingCompiler compiler = translators[currentCompiler];
              if (context.getProgressIndicator().isCanceled()) {
                throw new ExitException(ExitStatus.CANCELLED);
              }

              dumbService.waitForSmartMode();
  
              if (round == 0) {
                if (snapshot == null || ContainerUtil.intersects(generatedTypes, compilerManager.getRegisteredInputTypes(compiler))) {
                  // rescan snapshot if previously generated files may influence the input of this compiler
                  snapshot = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
                    public VirtualFile[] compute() {
                      return context.getCompileScope().getFiles(null, true);
                    }
                  });
                  recalculateChunkToFilesMap(context, sortedChunks, snapshot, chunkMap);
                  chunkFiles = chunkMap.get(currentChunk);
                  total = snapshot.length * translatorsLength;
                }
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
                compileSources(_context, currentChunk, compiler, chunkFiles, round == 0? forceCompile : true, isRebuild, onlyCheckStatus, sink);
  
              processed += chunkFiles.size();
              _context.getProgressIndicator().setFraction(((double)processed) / total);
  
              if (compiledSomething) {
                generatedTypes.addAll(compilerManager.getRegisteredOutputTypes(compiler));
              }
  
              didSomething |= compiledSomething;
              compiledSomethingForThisChunk |= didSomething;

              if (_context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
                break; // break the loop over compilers
              }
            }

            final boolean hasUnprocessedTraverseRoots = context.getDependencyCache().hasUnprocessedTraverseRoots();
            if (!isRebuild && (compiledSomethingForThisChunk || hasUnprocessedTraverseRoots)) {
              final Set<VirtualFile> compiledWithErrors = CacheUtils.getFilesCompiledWithErrors(context);
              filesToRecompile.removeAll(sink.getCompiledSources());
              filesToRecompile.addAll(compiledWithErrors);

              dependentFiles = CacheUtils.findDependentFiles(context, compiledWithErrors, dependencyFilter);
              if (!processedModules.isEmpty()) {
                for (Iterator<VirtualFile> it = dependentFiles.iterator(); it.hasNext();) {
                  final VirtualFile next = it.next();
                  final Module module = context.getModuleByFile(next);
                  if (module != null && processedModules.contains(module)) {
                    it.remove();
                  }
                }
              }
              
              if (ourDebugMode) {
                if (!dependentFiles.isEmpty()) {
                  for (VirtualFile dependentFile : dependentFiles) {
                    System.out.println("FOUND TO RECOMPILE: " + dependentFile.getPresentableUrl());
                  }
                }
                else {
                  System.out.println("NO FILES TO RECOMPILE");
                }
              }

              if (!dependentFiles.isEmpty()) {
                filesToRecompile.addAll(dependentFiles);
                allDependent.addAll(dependentFiles);
                if (context.getProgressIndicator().isCanceled() || context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
                  break;
                }
                final List<VirtualFile> filesInScope = getFilesInScope(context, currentChunk, dependentFiles);
                if (filesInScope.isEmpty()) {
                  break;
                }
                context.getDependencyCache().clearTraverseRoots();
                chunkFiles = filesInScope;
                total += chunkFiles.size() * translators.length;
              }
              
              didSomething |= (hasUnprocessedTraverseRoots != context.getDependencyCache().hasUnprocessedTraverseRoots());
            }

            round++;
          }
          while (!dependentFiles.isEmpty() && context.getMessageCount(CompilerMessageCategory.ERROR) == 0);

          if (CompilerConfiguration.MAKE_ENABLED) {
            if (!context.getProgressIndicator().isCanceled()) {
              // when cancelled pretend nothing was compiled and next compile will compile everything from the scratch
              final ProgressIndicator indicator = context.getProgressIndicator();
              final DependencyCache cache = context.getDependencyCache();

              indicator.pushState();
              indicator.setText(CompilerBundle.message("progress.updating.caches"));
              indicator.setText2("");

              cache.update();

              indicator.setText(CompilerBundle.message("progress.saving.caches"));
              cache.resetState();
              processedModules.addAll(currentChunk.getNodes());
              indicator.popState();
            }
          }

          if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            throw new ExitException(ExitStatus.ERRORS);
          }

        }
        catch (CacheCorruptedException e) {
          LOG.info(e);
          context.requestRebuildNextTime(e.getMessage());
        }
        finally {
          final int errorCount = context.getMessageCount(CompilerMessageCategory.ERROR);
          if (errorCount != 0) {
            filesToRecompile.addAll(allDependent);
          }
          if (filesToRecompile.size() > 0) {
            sink.add(null, Collections.<TranslatingCompiler.OutputItem>emptyList(), VfsUtil.toVirtualFileArray(filesToRecompile));
          }
          if (errorCount == 0) {
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
            final Collection<VirtualFile> deps = CacheUtils.findDependentFiles(context, Collections.<VirtualFile>emptySet(), null);
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

  private static List<VirtualFile> getFilesInScope(final CompileContextEx context, final Chunk<Module> chunk, final Collection<VirtualFile> files) {
    final List<VirtualFile> filesInScope = new ArrayList<VirtualFile>(files.size());
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          if (context.getCompileScope().belongs(file.getUrl())) {
            final Module module = context.getModuleByFile(file);
            if (chunk.getNodes().contains(module)) {
              filesInScope.add(file);
            }
          }
        }
      }
    });
    return filesInScope;
  }

  private static void recalculateChunkToFilesMap(CompileContextEx context, List<Chunk<Module>> allChunks, VirtualFile[] snapshot, Map<Chunk<Module>, Collection<VirtualFile>> chunkMap) {
    final Map<Module, List<VirtualFile>> moduleToFilesMap = CompilerUtil.buildModuleToFilesMap(context, snapshot);
    for (Chunk<Module> moduleChunk : allChunks) {
      List<VirtualFile> files = Collections.emptyList();
      for (Module module : moduleChunk.getNodes()) {
        final List<VirtualFile> moduleFiles = moduleToFilesMap.get(module);
        if (moduleFiles != null) {
          files = ContainerUtil.concat(files, moduleFiles);
        }
      }
      chunkMap.put(moduleChunk, files);
    }
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
                      TranslatingCompilerFilesMonitor.getInstance().collectFiles(
                        context, 
                        (TranslatingCompiler)compiler, Arrays.<VirtualFile>asList(allSources).iterator(), 
                        true /*pass true to make sure that every source in scope file is processed*/, 
                        false /*important! should pass false to enable collection of files to delete*/,
                        new ArrayList<VirtualFile>(), 
                        toDelete
                      );
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

  private Set<File> getAllOutputDirectories(CompileContext context) {
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
    if (context.isAnnotationProcessorsEnabled()) {
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

  private boolean compileSources(final CompileContextEx context,
                                 final Chunk<Module> moduleChunk,
                                 final TranslatingCompiler compiler,
                                 final Collection<VirtualFile> srcSnapshot,
                                 final boolean forceCompile,
                                 final boolean isRebuild,
                                 final boolean onlyCheckStatus,
                                 TranslatingCompiler.OutputSink sink) throws ExitException {

    final Set<VirtualFile> toCompile = new HashSet<VirtualFile>();
    final List<Trinity<File, String, Boolean>> toDelete = new ArrayList<Trinity<File, String, Boolean>>();
    context.getProgressIndicator().pushState();

    final boolean[] wereFilesDeleted = {false};
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          TranslatingCompilerFilesMonitor.getInstance().collectFiles(
            context, compiler, srcSnapshot.iterator(), forceCompile, isRebuild, toCompile, toDelete
          );
        }
      });

      if (onlyCheckStatus) {
        if (toDelete.isEmpty() && toCompile.isEmpty()) {
          return false;
        }
        if (LOG.isDebugEnabled() || ourDebugMode) {
          if (!toDelete.isEmpty()) {
            final StringBuilder message = new StringBuilder();
            message.append("Found items to delete, compiler ").append(compiler.getDescription());
            for (Trinity<File, String, Boolean> trinity : toDelete) {
              message.append("\n").append(trinity.getFirst());
            }
            LOG.debug(message.toString());
            if (ourDebugMode) {
              System.out.println(message);
            }
          }
          if (!toCompile.isEmpty()) {
            final String message = "Found items to compile, compiler " + compiler.getDescription();
            LOG.debug(message);
            if (ourDebugMode) {
              System.out.println(message);
            }
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

      if ((wereFilesDeleted[0] || !toCompile.isEmpty()) && context.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
        compiler.compile(context, moduleChunk, VfsUtil.toVirtualFileArray(toCompile), sink);
      }
    }
    finally {
      context.getProgressIndicator().popState();
    }
    return !toCompile.isEmpty() || wereFilesDeleted[0];
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
              if (isTestMode) {
                if (outputPath.exists()) {
                  LOG.error("Was not able to delete output file: " + outputPath.getPath());
                }
                else {
                  CompilerManagerImpl.addDeletedPath(outputPath.getPath());
                }
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
      public void run() {
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
    if (useCompileServer()) {
      return true;
    }
    try {
      final Module[] scopeModules = scope.getAffectedModules()/*ModuleManager.getInstance(myProject).getModules()*/;
      final List<String> modulesWithoutOutputPathSpecified = new ArrayList<String>();
      boolean isProjectCompilePathSpecified = true;
      final List<String> modulesWithoutJdkAssigned = new ArrayList<String>();
      final Set<File> nonExistingOutputPaths = new HashSet<File>();
      final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
      for (final Module module : scopeModules) {
        if (!compilerManager.isValidationEnabled(module)) {
          continue;
        }
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
        final Boolean refreshSuccess =
          new WriteAction<Boolean>() {
            @Override
            protected void run(Result<Boolean> result) throws Throwable {
              LocalFileSystem.getInstance().refreshIoFiles(nonExistingOutputPaths);
              Boolean res = Boolean.TRUE;
              for (File file : nonExistingOutputPaths) {
                if (LocalFileSystem.getInstance().findFileByIoFile(file) == null) {
                  res = Boolean.FALSE;
                  break;
                }
              }
              result.setResult(res);
            }
          }.execute().getResultObject();
  
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
      final Compiler[] allCompilers = compilerManager.getCompilers(Compiler.class);
      for (Compiler compiler : allCompilers) {
        if (!compiler.validateConfiguration(scope)) {
          return false;
        }
      }
      return true;
    }
    catch (Throwable e) {
      LOG.info(e);
      return false;
    }
  }

  private boolean useCompileServer() {
    return CompilerWorkspaceConfiguration.getInstance(myProject).useCompileServer();
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

  private void showNotSpecifiedError(@NonNls final String resourceId, List<String> modules, String editorNameToSelect) {
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
    showConfigurationDialog(nameToSelect, editorNameToSelect);
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
        if (paths.length() > 0) {
          paths.append(",\n");
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
    ProjectSettingsService.getInstance(myProject).showModuleConfigurationDialog(moduleNameToSelect, tabNameToSelect);
  }

  private static VirtualFile lookupVFile(final LocalFileSystem lfs, final String path) {
    final File file = new File(path);

    VirtualFile vFile = lfs.findFileByIoFile(file);
    if (vFile != null) {
      return vFile;
    }

    final boolean justCreated = file.mkdirs();
    vFile = lfs.refreshAndFindFileByIoFile(file);

    if (vFile == null) {
      assert false: "Virtual file not found for " + file.getPath() + "; mkdirs() exit code is " + justCreated + "; file exists()? " + file.exists();
    }

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
    private final Set<VirtualFile> myCompiledSources = new HashSet<VirtualFile>();
    //private LinkedBlockingQueue<Future> myFutures = new LinkedBlockingQueue<Future>();

    private TranslatorsOutputSink(CompileContextEx context, TranslatingCompiler[] compilers) {
      myContext = context;
      myCompilers = compilers;
    }

    public void setCurrentCompilerIndex(int index) {
      myCurrentCompilerIdx = index;
    }

    public Set<VirtualFile> getCompiledSources() {
      return Collections.unmodifiableSet(myCompiledSources);
    }

    public void add(final String outputRoot, final Collection<TranslatingCompiler.OutputItem> items, final VirtualFile[] filesToRecompile) {
      for (TranslatingCompiler.OutputItem item : items) {
        final VirtualFile file = item.getSourceFile();
        if (file != null) {
          myCompiledSources.add(file);
        }
      }
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
                shouldPostpone = myCompilers[idx].isCompilableFile(item.getSourceFile(), myContext);
                if (shouldPostpone) {
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
              shouldPostpone = myCompilers[idx].isCompilableFile(item.getSourceFile(), myContext);
              if (shouldPostpone) {
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

    private static void addItemToMap(Map<String, Collection<TranslatingCompiler.OutputItem>> map, String outputDir, TranslatingCompiler.OutputItem item) {
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

  private static class DependentClassesCumulativeFilter implements Function<Pair<int[], Set<VirtualFile>>, Pair<int[], Set<VirtualFile>>> {

    private final TIntHashSet myProcessedNames = new TIntHashSet();
    private final Set<VirtualFile> myProcessedFiles = new HashSet<VirtualFile>();

    public Pair<int[], Set<VirtualFile>> fun(Pair<int[], Set<VirtualFile>> deps) {
      final TIntHashSet currentDeps = new TIntHashSet(deps.getFirst());
      currentDeps.removeAll(myProcessedNames.toArray());
      myProcessedNames.addAll(deps.getFirst());

      final Set<VirtualFile> depFiles = new HashSet<VirtualFile>(deps.getSecond());
      depFiles.removeAll(myProcessedFiles);
      myProcessedFiles.addAll(deps.getSecond());
      return new Pair<int[], Set<VirtualFile>>(currentDeps.toArray(), depFiles);
    }
  }
}
