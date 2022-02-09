// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcherManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tracing.Tracer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.DataOutputStream;
import io.netty.channel.Channel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.TimingLog;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cache.loader.JpsOutputLoaderManager;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
import org.jetbrains.jps.incremental.storage.StampsStorage;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.CannotLoadJpsModelException;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
* @author Eugene Zhuravlev
*/
final class BuildSession implements Runnable, CanceledStatus {
  private static final Logger LOG = Logger.getInstance(BuildSession.class);
  public static final String FS_STATE_FILE = "fs_state.dat";
  private static final Boolean REPORT_BUILD_STATISTICS = Boolean.valueOf(System.getProperty(GlobalOptions.REPORT_BUILD_STATISTICS, "false"));

  private final UUID mySessionId;
  private final Channel myChannel;
  @Nullable
  private final PreloadedData myPreloadedData;
  private volatile boolean myCanceled;
  private final String myProjectPath;
  @Nullable
  private CmdlineRemoteProto.Message.ControllerMessage.FSEvent myInitialFSDelta;
  // state
  private final EventsProcessor myEventsProcessor = new EventsProcessor();
  private volatile long myLastEventOrdinal;
  private volatile ProjectDescriptor myProjectDescriptor;
  @NotNull
  private final BuildRunner myBuildRunner;
  private final boolean myForceModelLoading;
  private final BuildType myBuildType;
  private final List<TargetTypeBuildScope> myScopes;
  private final boolean myLoadUnloadedModules;
  @Nullable
  private JpsOutputLoaderManager myCacheLoadManager;
  @Nullable
  private CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings myCacheDownloadSettings;

  BuildSession(UUID sessionId,
               Channel channel,
               CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage params,
               @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent delta, @Nullable PreloadedData preloaded) {
    mySessionId = sessionId;
    myChannel = channel;

    final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals = params.getGlobalSettings();
    myProjectPath = FileUtil.toCanonicalPath(params.getProjectId());
    String globalOptionsPath = FileUtil.toCanonicalPath(globals.getGlobalOptionsPath());
    myBuildType = convertCompileType(params.getBuildType());
    myScopes = params.getScopeList();
    myCacheDownloadSettings = params.hasCacheDownloadSettings() ? params.getCacheDownloadSettings() : null;
    List<String> filePaths = params.getFilePathList();
    final Map<String, String> builderParams = new HashMap<>();
    for (CmdlineRemoteProto.Message.KeyValuePair pair : params.getBuilderParameterList()) {
      builderParams.put(pair.getKey(), pair.getValue());
    }
    myInitialFSDelta = delta;
    myLoadUnloadedModules = Boolean.parseBoolean(builderParams.get(BuildParametersKeys.LOAD_UNLOADED_MODULES));
    if (myLoadUnloadedModules && preloaded != null) {
      myPreloadedData = null;
      ProjectDescriptor projectDescriptor = preloaded.getProjectDescriptor();
      if (projectDescriptor != null) {
        projectDescriptor.release();
        preloaded.setProjectDescriptor(null);
      }
      JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class).forEach(ext-> ext.discardPreloadedData(preloaded));
    }
    else {
      myPreloadedData = preloaded;
    }

    if (myPreloadedData == null || myPreloadedData.getRunner() == null) {
      myBuildRunner = new BuildRunner(new JpsModelLoaderImpl(myProjectPath, globalOptionsPath, myLoadUnloadedModules, null));
    }
    else {
      myBuildRunner = myPreloadedData.getRunner();
    }
    myBuildRunner.setFilePaths(filePaths);
    myBuildRunner.setBuilderParams(builderParams);
    myForceModelLoading =  Boolean.parseBoolean(builderParams.get(BuildParametersKeys.FORCE_MODEL_LOADING));

    if (myPreloadedData != null) {
      JpsServiceManager.getInstance().getExtensions(PreloadedDataExtension.class).forEach(ext-> ext.buildSessionInitialized(myPreloadedData));
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Starting build:");
      LOG.debug(" initial delta = " + (delta == null ? null : "FSEvent(ordinal = " + delta.getOrdinal() + ", changed = " + showFirstItemIfAny(delta.getChangedPathsList()) + ", deleted = " + delta.getDeletedPathsList() + ")"));
      LOG.debug(" forceModelLoading = " + myForceModelLoading);
      LOG.debug(" loadUnloadedModules = " + myLoadUnloadedModules);
      LOG.debug(" preloadedData = " + myPreloadedData);
      LOG.debug(" buildType = " + myBuildType);
    }

    try {
      String tracingFile = System.getProperty("tracingFile");
      if (tracingFile != null) {
        LOG.debug("Tracing enabled, file: " + tracingFile);
        Path tracingFilePath = Paths.get(tracingFile);
        Tracer.runTracer(1, tracingFilePath, 1, e -> {
          LOG.warn(e);
        });
      }
    } catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static @NonNls String showFirstItemIfAny(List<String> list) {
    switch (list.size()) {
      case 0: return "[]";
      case 1: return "[" + list.get(0) + "]";
      default: return "[" + list.get(0) + " and " + (list.size() - 1) + " more]";
    }
  }

  @Override
  public void run() {
    final LowMemoryWatcherManager memWatcher = new LowMemoryWatcherManager(SharedThreadPool.getInstance());
    Throwable error = null;
    final Ref<Boolean> hasErrors = new Ref<>(false);
    final Ref<Boolean> doneSomething = new Ref<>(false);
    try {
      ProfilingHelper profilingHelper = null;
      if (Utils.IS_PROFILING_MODE) {
        profilingHelper = new ProfilingHelper();
        profilingHelper.startProfiling();
      }

      myCacheLoadManager = null;
      if (ProjectStamps.PORTABLE_CACHES && myCacheDownloadSettings != null) {
        LOG.info("Trying to download JPS caches before build");
        myCacheLoadManager = new JpsOutputLoaderManager(myBuildRunner.loadModelAndGetJpsProject(), this, myProjectPath, myChannel,
                                                        mySessionId, myCacheDownloadSettings);
        myCacheLoadManager.load(myBuildRunner, true, myScopes);
      }

      runBuild(new MessageHandler() {
        @Override
        public void processMessage(BuildMessage buildMessage) {
          final CmdlineRemoteProto.Message.BuilderMessage response;
          if (buildMessage instanceof FileGeneratedEvent) {
            final Collection<Pair<String, String>> paths = ((FileGeneratedEvent)buildMessage).getPaths();
            response = !paths.isEmpty() ? CmdlineProtoUtil.createFileGeneratedEvent(paths) : null;
          }
          else if (buildMessage instanceof DoneSomethingNotification) {
            doneSomething.set(true);
            response = null;
          }
          else if (buildMessage instanceof CompilerMessage) {
            doneSomething.set(true);
            final CompilerMessage compilerMessage = (CompilerMessage)buildMessage;
            final String compilerName = compilerMessage.getCompilerName();
            final String text = !StringUtil.isEmptyOrSpaces(compilerName)? compilerName + ": " + compilerMessage.getMessageText() : compilerMessage.getMessageText();
            final BuildMessage.Kind kind = compilerMessage.getKind();
            if (kind == BuildMessage.Kind.ERROR) {
              hasErrors.set(true);
            }
            response = CmdlineProtoUtil.createCompileMessage(
              kind, text, compilerMessage.getSourcePath(),
              compilerMessage.getProblemBeginOffset(), compilerMessage.getProblemEndOffset(),
              compilerMessage.getProblemLocationOffset(), compilerMessage.getLine(), compilerMessage.getColumn(),
              -1.0f, compilerMessage.getModuleNames());
          }
          else if (buildMessage instanceof CustomBuilderMessage) {
            CustomBuilderMessage builderMessage = (CustomBuilderMessage)buildMessage;
            response = CmdlineProtoUtil.createCustomBuilderMessage(builderMessage.getBuilderId(), builderMessage.getMessageType(), builderMessage.getMessageText());
          }
          else if (buildMessage instanceof BuilderStatisticsMessage) {
            final BuilderStatisticsMessage message = (BuilderStatisticsMessage)buildMessage;
            final boolean worthReporting = message.getNumberOfProcessedSources() != 0 || message.getElapsedTimeMs() > 50;
            if (worthReporting) {
              LOG.info(message.getMessageText());
            }
            //noinspection HardCodedStringLiteral
            response = worthReporting && REPORT_BUILD_STATISTICS ?
              CmdlineProtoUtil.createCompileMessage(BuildMessage.Kind.JPS_INFO, message.getMessageText(), null, -1, -1, -1, -1, -1, -1.0f, Collections.emptyList()) : null;
          }
          else if (!(buildMessage instanceof BuildingTargetProgressMessage)) {
            float done = -1.0f;
            if (buildMessage instanceof ProgressMessage) {
              done = ((ProgressMessage)buildMessage).getDone();
            }
            //noinspection HardCodedStringLiteral
            response = CmdlineProtoUtil.createCompileProgressMessageResponse(buildMessage.getMessageText(), done);
          }
          else {
            response = null;
          }
          if (response != null) {
            myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, response));
          }
        }
      }, this);

      if (profilingHelper != null) {
        profilingHelper.stopProfiling();
      }
    }
    catch (Throwable e) {
      LOG.info(e);
      error = e;
    }
    finally {
      finishBuild(error, hasErrors.get(), doneSomething.get());
      Disposer.dispose(memWatcher);
    }
  }

  private void runBuild(final MessageHandler msgHandler, CanceledStatus cs) throws Throwable{
    final File dataStorageRoot = Utils.getDataStorageRoot(myProjectPath);
    if (dataStorageRoot == null) {
      msgHandler.processMessage(new CompilerMessage(BuildRunner.getRootCompilerName(), BuildMessage.Kind.ERROR,
                                                    JpsBuildBundle.message("build.message.cannot.determine.build.data.storage.root.for.project.0", myProjectPath)));
      return;
    }
    final boolean storageFilesAbsent = !dataStorageRoot.exists() || !new File(dataStorageRoot, FS_STATE_FILE).exists();
    if (storageFilesAbsent) {
      // invoked the very first time for this project
      myBuildRunner.setForceCleanCaches(true);
      LOG.debug("Storage files are absent");
    }
    final ProjectDescriptor preloadedProject = myPreloadedData != null? myPreloadedData.getProjectDescriptor() : null;
    final DataInputStream fsStateStream =
      storageFilesAbsent || preloadedProject != null || myLoadUnloadedModules || myInitialFSDelta == null /*this will force FS rescan*/? null : createFSDataStream(dataStorageRoot, myInitialFSDelta.getOrdinal());

    if (fsStateStream != null || myPreloadedData != null) {
      // optimization: check whether we can skip the build
      final boolean hasWorkFlag = fsStateStream != null? fsStateStream.readBoolean() : myPreloadedData.hasWorkToDo();
      LOG.debug("hasWorkFlag = " + hasWorkFlag);
      final boolean hasWorkToDoWithModules = hasWorkFlag || myInitialFSDelta == null;
      if (!myForceModelLoading && (myBuildType == BuildType.BUILD || myBuildType == BuildType.UP_TO_DATE_CHECK) && !hasWorkToDoWithModules
          && scopeContainsModulesOnlyForIncrementalMake(myScopes) && !containsChanges(myInitialFSDelta)) {

        final DataInputStream storedFsData;
        if (myPreloadedData != null) {
          storedFsData = createFSDataStream(dataStorageRoot, myInitialFSDelta.getOrdinal());
          if (storedFsData != null) {
            storedFsData.readBoolean(); // skip hasWorkToDo flag
          }
        }
        else {
          storedFsData = fsStateStream;
        }

        if (storedFsData != null) {
          updateFsStateOnDisk(dataStorageRoot, storedFsData, myInitialFSDelta.getOrdinal());
          LOG.info("No changes found since last build. Exiting.");
          if (preloadedProject != null) {
            preloadedProject.release();
          }
          return;
        }
      }
    }
    LOG.debug("Fast up-to-date check didn't work, continue to regular build");

    final BuildFSState fsState = preloadedProject != null? preloadedProject.fsState : new BuildFSState(false);
    try {
      final ProjectDescriptor pd;
      if (preloadedProject != null) {
        pd = preloadedProject;
        final List<BuildMessage> preloadMessages = myPreloadedData.getLoadMessages();
        if (!preloadMessages.isEmpty()) {
          // replay preload-time messages, so that they are delivered to the IDE
          for (BuildMessage message : preloadMessages) {
            msgHandler.processMessage(message);
          }
        }
        if (myInitialFSDelta == null || myPreloadedData.getFsEventOrdinal() + 1L != myInitialFSDelta.getOrdinal()) {
          // FS rescan was forced
          fsState.clearAll();
        }
        else {
          // apply events to already loaded state
          try {
            applyFSEvent(pd, myInitialFSDelta, false);
          }
          catch (Throwable e) {
            LOG.error(e);
            fsState.clearAll();
          }
        }
      }
      else {
        // standard case
        pd = myBuildRunner.load(msgHandler, dataStorageRoot, fsState);
        TimingLog.LOG.debug("Project descriptor loaded");
        if (fsStateStream != null) {
          try {
            fsState.load(fsStateStream, pd.getModel(), pd.getBuildRootIndex());
            applyFSEvent(pd, myInitialFSDelta, false);
            TimingLog.LOG.debug("FS Delta loaded");
          }
          catch (Throwable e) {
            LOG.error(e);
            fsState.clearAll();
          }
        }
      }
      myProjectDescriptor = pd;
      if (myCacheLoadManager != null) myCacheLoadManager.updateBuildStatistic(myProjectDescriptor);

      myLastEventOrdinal = myInitialFSDelta != null? myInitialFSDelta.getOrdinal() : 0L;

      // free memory
      myInitialFSDelta = null;
      // ensure events from controller are processed after FSState initialization
      myEventsProcessor.startProcessing();

      myBuildRunner.runBuild(pd, cs, msgHandler, myBuildType, myScopes, false);
      TimingLog.LOG.debug("Build finished");
    }
    finally {
      saveData(fsState, dataStorageRoot);
    }
  }

  private static boolean scopeContainsModulesOnlyForIncrementalMake(List<TargetTypeBuildScope> scopes) {
    TargetTypeRegistry typeRegistry = null;
    for (TargetTypeBuildScope scope : scopes) {
      if (scope.getForceBuild()) {
        LOG.debug("Build scope forces compilation for targets of type " + scope.getTypeId());
        return false;
      }
      final String typeId = scope.getTypeId();
      if (isJavaModuleBuildType(typeId)) { // fast check
        continue;
      }
      if (typeRegistry == null) {
        // lazy init
        typeRegistry = TargetTypeRegistry.getInstance();
      }
      final BuildTargetType<?> targetType = typeRegistry.getTargetType(typeId);
      if (targetType != null && !(targetType instanceof ModuleInducedTargetType)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Build scope contains target of type " + targetType + " which isn't eligible for fast up-to-date check");
        }
        return false;
      }
    }
    return true;
  }

  private static boolean isJavaModuleBuildType(String typeId) {
    for (JavaModuleBuildTargetType moduleBuildTargetType : JavaModuleBuildTargetType.ALL_TYPES) {
      if (moduleBuildTargetType.getTypeId().equals(typeId)) {
        return true;
      }
    }
    return false;
  }

  private void saveData(final BuildFSState fsState, File dataStorageRoot) {
    final boolean wasInterrupted = Thread.interrupted();
    try {
      saveFsState(dataStorageRoot, fsState);
      final ProjectDescriptor pd = myProjectDescriptor;
      if (pd != null) {
        pd.release();
      }
    }
    finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public void processFSEvent(final CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) {
    myEventsProcessor.execute(() -> {
      try {
        applyFSEvent(myProjectDescriptor, event, true);
        myLastEventOrdinal += 1;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    });
  }

  private static void applyFSEvent(ProjectDescriptor pd, @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event, final boolean saveEventStamp) throws IOException {
    if (event == null) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("applyFSEvent ordinal=" + event.getOrdinal());
    }

    final StampsStorage<? extends StampsStorage.Stamp> stampsStorage = pd.getProjectStamps().getStampStorage();
    boolean cacheCleared = false;
    for (String deleted : event.getDeletedPathsList()) {
      final File file = new File(deleted);
      Collection<BuildRootDescriptor> descriptor = pd.getBuildRootIndex().findAllParentDescriptors(file, null, null);
      if (!descriptor.isEmpty()) {
        if (!cacheCleared) {
          cacheCleared = true;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Applying deleted path from fs event: " + file.getPath());
        }
        for (BuildRootDescriptor rootDescriptor : descriptor) {
          pd.fsState.registerDeleted(null, rootDescriptor.getTarget(), file, stampsStorage);
        }
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipping deleted path: " + file.getPath());
        }
      }
    }
    for (String changed : event.getChangedPathsList()) {
      final File file = new File(changed);
      Collection<BuildRootDescriptor> descriptors = pd.getBuildRootIndex().findAllParentDescriptors(file, null, null);
      if (!descriptors.isEmpty()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Applying dirty path from fs event: " + changed);
        }
        for (BuildRootDescriptor descriptor : descriptors) {
          if (!descriptor.isGenerated()) { // ignore generates sources as they are processed at the time of generation
            StampsStorage.Stamp stamp = stampsStorage.getPreviousStamp(file, descriptor.getTarget());
            if (stampsStorage.isDirtyStamp(stamp, file)) {
              if (!cacheCleared) {
                cacheCleared = true;
              }
              pd.fsState.markDirty(null, file, descriptor, stampsStorage, saveEventStamp);
            }
            else {
              if (LOG.isDebugEnabled()) {
                LOG.debug(descriptor.getTarget() + ": Path considered up-to-date: " + changed + "; stamp= " + stamp);
              }
            }
          }
        }
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipping dirty path: " + file.getPath());
        }
      }
    }
  }

  private static void updateFsStateOnDisk(File dataStorageRoot, DataInputStream original, final long ordinal) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("updateFsStateOnDisk, ordinal=" + ordinal);
    }
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try {
      final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeInt(BuildFSState.VERSION);
        out.writeLong(ordinal);
        out.writeBoolean(false);
        while (true) {
          final int b = original.read();
          if (b == -1) {
            break;
          }
          out.write(b);
        }
      }

      saveOnDisk(bytes, file);
    }
    catch (Throwable e) {
      LOG.error(e);
      FileUtil.delete(file);
    }
  }

  private void saveFsState(File dataStorageRoot, BuildFSState state) {
    final ProjectDescriptor pd = myProjectDescriptor;
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try {
      final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeInt(BuildFSState.VERSION);
        out.writeLong(myLastEventOrdinal);
        out.writeBoolean(hasWorkToDo(state, pd));
        state.save(out);
      }

      saveOnDisk(bytes, file);
    }
    catch (Throwable e) {
      LOG.error(e);
      FileUtil.delete(file);
    }
  }

  private static boolean hasWorkToDo(BuildFSState state, @Nullable ProjectDescriptor pd) {
    if (pd == null) {
      return true; // assuming worst case
    }
    final BuildTargetIndex targetIndex = pd.getBuildTargetIndex();
    for (JpsModule module : pd.getProject().getModules()) {
      for (ModuleBasedTarget<?> target : targetIndex.getModuleBasedTargets(module, BuildTargetRegistry.ModuleTargetSelector.ALL)) {
        if (!pd.getBuildTargetIndex().isDummy(target) && state.hasWorkToDo(target)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Has work to do in " + target);
          }
          return true;
        }
      }
    }
    return false;
  }

  private static void saveOnDisk(BufferExposingByteArrayOutputStream bytes, final File file) throws IOException {
    try (FileOutputStream fos = writeOrCreate(file)) {
      fos.write(bytes.getInternalBuffer(), 0, bytes.size());
    }
  }

  @NotNull
  private static FileOutputStream writeOrCreate(@NotNull File file) throws FileNotFoundException {
    FileOutputStream fos = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fos = new FileOutputStream(file);
    }
    catch (FileNotFoundException ignored) {
      FileUtil.createIfDoesntExist(file);
    }

    if (fos == null) {
      fos = new FileOutputStream(file);
    }
    return fos;
  }

  @Nullable
  private static DataInputStream createFSDataStream(File dataStorageRoot, final long currentEventOrdinal) {
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try (InputStream fs = new FileInputStream(file)) {
      byte[] bytes = FileUtil.loadBytes(fs, (int)file.length());
      final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      final int version = in.readInt();
      if (version != BuildFSState.VERSION) {
        return null;
      }
      final long savedOrdinal = in.readLong();
      if (savedOrdinal + 1L != currentEventOrdinal) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Discarding FS data: savedOrdinal=" + savedOrdinal + "; currentEventOrdinal=" + currentEventOrdinal);
        }
        return null;
      }
      return in;
    }
    catch (FileNotFoundException ignored) {
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    return null;
  }

  private static boolean containsChanges(CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) {
    return event.getChangedPathsCount() != 0 || event.getDeletedPathsCount() != 0;
  }

  private void finishBuild(final Throwable error, boolean hadBuildErrors, boolean doneSomething) {
    CmdlineRemoteProto.Message lastMessage = null;
    try {
      if (error instanceof CannotLoadJpsModelException) {
        String text = JpsBuildBundle.message("build.message.failed.to.load.project.configuration.0", StringUtil.decapitalize(error.getMessage()));
        String path = ((CannotLoadJpsModelException)error).getFile().getAbsolutePath();
        lastMessage = CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createCompileMessage(BuildMessage.Kind.ERROR, text, path, -1, -1, -1, -1, -1, -1.0f, Collections.emptyList()));
      }
      else if (error != null) {
        Throwable cause = error.getCause();
        if (cause == null) {
          cause = error;
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(out)) {
          cause.printStackTrace(stream);
        }

        @Nls StringBuilder messageText = new StringBuilder();
        messageText.append(JpsBuildBundle.message("build.message.internal.error.0.1", cause.getClass().getName(),cause.getMessage()));
        final String trace = out.toString();
        if (!trace.isEmpty()) {
          messageText.append("\n").append(trace);
        }
        if (error instanceof RebuildRequestedException || cause instanceof IOException) {
          messageText.append("\n").append(JpsBuildBundle.message("build.message.perform.full.project.rebuild"));
        }
        lastMessage = CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createFailure(messageText.toString(), cause));
      }
      else {
        CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status status = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.SUCCESS;
        if (myCanceled) {
          status = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.CANCELED;
        }
        else if (hadBuildErrors) {
          status = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.ERRORS;
        }
        else if (!doneSomething){
          status = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.UP_TO_DATE;
        }
        if (myCacheLoadManager != null) myCacheLoadManager.saveLatestBuiltCommitId(status);
        lastMessage = CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createBuildCompletedEvent("build completed", status));
      }
    }
    catch (Throwable e) {
      lastMessage = CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
    }
    finally {
      try {
        myChannel.writeAndFlush(lastMessage).await();
      }
      catch (InterruptedException e) {
        LOG.info(e);
      }
    }
  }

  public void cancel() {
    myCanceled = true;
  }

  @Override
  public boolean isCanceled() {
    return myCanceled;
  }

  private static BuildType convertCompileType(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type compileType) {
    switch (compileType) {
      case CLEAN: return BuildType.CLEAN;
      case BUILD: return BuildType.BUILD;
      case UP_TO_DATE_CHECK: return BuildType.UP_TO_DATE_CHECK;
    }
    return BuildType.BUILD;
  }

  private static final class EventsProcessor {
    private final Semaphore myProcessingEnabled = new Semaphore();
    private final Executor myExecutorService = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("BuildSession.EventsProcessor.EventsProcessor Pool", SharedThreadPool.getInstance());

    private EventsProcessor() {
      myProcessingEnabled.down();
      execute(() -> myProcessingEnabled.waitFor());
    }

    private void startProcessing() {
      myProcessingEnabled.up();
    }

    public void execute(@NotNull Runnable task) {
      myExecutorService.execute(task);
    }
  }
}
