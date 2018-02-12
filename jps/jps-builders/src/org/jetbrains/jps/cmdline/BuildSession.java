/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.DataOutputStream;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.TimingLog;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.RebuildRequestedException;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.CannotLoadJpsModelException;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
* @author Eugene Zhuravlev
*/
final class BuildSession implements Runnable, CanceledStatus {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildSession");
  public static final String FS_STATE_FILE = "fs_state.dat";
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
  private final Map<Pair<String, String>, ConstantSearchFuture> mySearchTasks = Collections.synchronizedMap(new HashMap<Pair<String, String>, ConstantSearchFuture>());
  private final ConstantSearch myConstantSearch = new ConstantSearch();
  @NotNull
  private final BuildRunner myBuildRunner;
  private final boolean myForceModelLoading;
  private final BuildType myBuildType;
  private final List<TargetTypeBuildScope> myScopes;
  private final boolean myLoadUnloadedModules;

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
  }

  @Override
  public void run() {
    Throwable error = null;
    final Ref<Boolean> hasErrors = new Ref<>(false);
    final Ref<Boolean> doneSomething = new Ref<>(false);
    try {
      ProfilingHelper profilingHelper = null;
      if (Utils.IS_PROFILING_MODE) {
        profilingHelper = new ProfilingHelper();
        profilingHelper.startProfiling();
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
              -1.0f);
          }
          else if (buildMessage instanceof CustomBuilderMessage) {
            CustomBuilderMessage builderMessage = (CustomBuilderMessage)buildMessage;
            response = CmdlineProtoUtil.createCustomBuilderMessage(builderMessage.getBuilderId(), builderMessage.getMessageType(), builderMessage.getMessageText());
          }
          else if (buildMessage instanceof BuilderStatisticsMessage) {
            BuilderStatisticsMessage message = (BuilderStatisticsMessage)buildMessage;
            int srcCount = message.getNumberOfProcessedSources();
            long time = message.getElapsedTimeMs();
            if (srcCount != 0 || time > 50) {
              LOG.info("Build duration: '" + message.getBuilderName() + "' builder took " + time + " ms, " + srcCount + " sources processed" +
                       (srcCount == 0 ? "" : " ("+ time/srcCount +"ms per file)"));
            }
            response = null;
          }
          else if (!(buildMessage instanceof BuildingTargetProgressMessage)) {
            float done = -1.0f;
            if (buildMessage instanceof ProgressMessage) {
              done = ((ProgressMessage)buildMessage).getDone();
            }
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
    }
  }

  private void runBuild(final MessageHandler msgHandler, CanceledStatus cs) throws Throwable{
    final File dataStorageRoot = Utils.getDataStorageRoot(myProjectPath);
    if (dataStorageRoot == null) {
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.ERROR, "Cannot determine build data storage root for project " + myProjectPath));
      return;
    }
    final boolean storageFilesAbsent = !dataStorageRoot.exists() || !new File(dataStorageRoot, FS_STATE_FILE).exists();
    if (storageFilesAbsent) {
      // invoked the very first time for this project
      myBuildRunner.setForceCleanCaches(true);
    }
    final ProjectDescriptor preloadedProject = myPreloadedData != null? myPreloadedData.getProjectDescriptor() : null;
    final DataInputStream fsStateStream = 
      storageFilesAbsent || preloadedProject != null || myLoadUnloadedModules || myInitialFSDelta == null /*this will force FS rescan*/? null : createFSDataStream(dataStorageRoot, myInitialFSDelta.getOrdinal());

    if (fsStateStream != null || myPreloadedData != null) {
      // optimization: check whether we can skip the build
      final boolean hasWorkFlag = fsStateStream != null? fsStateStream.readBoolean() : myPreloadedData.hasWorkToDo();
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
            try {
              fsState.load(fsStateStream, pd.getModel(), pd.getBuildRootIndex());
              applyFSEvent(pd, myInitialFSDelta, false);
              TimingLog.LOG.debug("FS Delta loaded");
            }
            finally {
              fsStateStream.close();
            }
          }
          catch (Throwable e) {
            LOG.error(e);
            fsState.clearAll();
          }
        }
      }
      myProjectDescriptor = pd;
      
      myLastEventOrdinal = myInitialFSDelta != null? myInitialFSDelta.getOrdinal() : 0L;

      // free memory
      myInitialFSDelta = null;
      // ensure events from controller are processed after FSState initialization
      myEventsProcessor.startProcessing();

      myBuildRunner.runBuild(pd, cs, myConstantSearch, msgHandler, myBuildType, myScopes, false);
      TimingLog.LOG.debug("Build finished");
    }
    finally {
      saveData(fsState, dataStorageRoot);
    }
  }

  private static boolean scopeContainsModulesOnlyForIncrementalMake(List<TargetTypeBuildScope> scopes) {
    TargetTypeRegistry typeRegistry = null;
    for (TargetTypeBuildScope scope : scopes) {
      if (scope.getForceBuild()) return false;
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

  public void processConstantSearchResult(CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult result) {
    final ConstantSearchFuture future = mySearchTasks.remove(Pair.create(result.getOwnerClassName(), result.getFieldName()));
    if (future != null) {
      if (result.getIsSuccess()) {
        final List<String> paths = result.getPathList();
        final List<File> files = new ArrayList<>(paths.size());
        for (String path : paths) {
          files.add(new File(path));
        }
        future.setResult(files);
        LOG.debug("Constant search result: " + files.size() + " affected files found");
      }
      else {
        future.setDone();
        LOG.debug("Constant search failed");
      }
    }
  }

  private static void applyFSEvent(ProjectDescriptor pd, @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event, final boolean saveEventStamp) throws IOException {
    if (event == null) {
      return;
    }

    final Timestamps timestamps = pd.timestamps.getStorage();
    boolean cacheCleared = false;
    for (String deleted : event.getDeletedPathsList()) {
      final File file = new File(deleted);
      Collection<BuildRootDescriptor> descriptor = pd.getBuildRootIndex().findAllParentDescriptors(file, null, null);
      if (!descriptor.isEmpty()) {
        if (!cacheCleared) {
          pd.getFSCache().clear();
          cacheCleared = true;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Applying deleted path from fs event: " + file.getPath());
        }
        for (BuildRootDescriptor rootDescriptor : descriptor) {
          pd.fsState.registerDeleted(null, rootDescriptor.getTarget(), file, timestamps);
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
        long fileStamp = -1L;
        for (BuildRootDescriptor descriptor : descriptors) {
          if (!descriptor.isGenerated()) { // ignore generates sources as they are processed at the time of generation
            if (fileStamp == -1L) {
              fileStamp = FileSystemUtil.lastModified(file); // lazy init
            }
            final long stamp = timestamps.getStamp(file, descriptor.getTarget());
            if (stamp != fileStamp) {
              if (!cacheCleared) {
                pd.getFSCache().clear();
                cacheCleared = true;
              }
              pd.fsState.markDirty(null, file, descriptor, timestamps, saveEventStamp);
            }
            else {
              if (LOG.isDebugEnabled()) {
                LOG.debug(descriptor.getTarget() + ": Path considered up-to-date: " + changed + "; timestamp= " + stamp);
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
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try {
      final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
      final DataOutputStream out = new DataOutputStream(bytes);
      try {
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
      finally {
        out.close();
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
      final DataOutputStream out = new DataOutputStream(bytes);
      try {
        out.writeInt(BuildFSState.VERSION);
        out.writeLong(myLastEventOrdinal);
        out.writeBoolean(hasWorkToDo(state, pd));
        state.save(out);
      }
      finally {
        out.close();
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
          return true;
        }
      }
    }
    return false;
  }

  private static void saveOnDisk(BufferExposingByteArrayOutputStream bytes, final File file) throws IOException {
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
    try {
      fos.write(bytes.getInternalBuffer(), 0, bytes.size());
    }
    finally {
      fos.close();
    }
  }

  @Nullable
  private static DataInputStream createFSDataStream(File dataStorageRoot, final long currentEventOrdinal) {
    try {
      final File file = new File(dataStorageRoot, FS_STATE_FILE);
      byte[] bytes;
      final InputStream fs = new FileInputStream(file);
      try {
        bytes = FileUtil.loadBytes(fs, (int)file.length());
      }
      finally {
        fs.close();
      }
      final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      final int version = in.readInt();
      if (version != BuildFSState.VERSION) {
        return null;
      }
      final long savedOrdinal = in.readLong();
      if (savedOrdinal + 1L != currentEventOrdinal) {
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
        String text = "Failed to load project configuration: " + StringUtil.decapitalize(error.getMessage());
        String path = ((CannotLoadJpsModelException)error).getFile().getAbsolutePath();
        lastMessage = CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createCompileMessage(BuildMessage.Kind.ERROR, text, path, -1, -1, -1, -1, -1, -1.0f));
      }
      else if (error != null) {
        Throwable cause = error.getCause();
        if (cause == null) {
          cause = error;
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        try {
          cause.printStackTrace(stream);
        }
        finally {
          stream.close();
        }

        final StringBuilder messageText = new StringBuilder();
        messageText.append("Internal error: (").append(cause.getClass().getName()).append(") ").append(cause.getMessage());
        final String trace = out.toString();
        if (!trace.isEmpty()) {
          messageText.append("\n").append(trace);
        }
        if (error instanceof RebuildRequestedException || cause instanceof IOException) {
          messageText.append("\n").append("Please perform full project rebuild (Build | Rebuild Project)");
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

  private static class EventsProcessor extends SequentialTaskExecutor {
    private final Semaphore myProcessingEnabled = new Semaphore();

    private EventsProcessor() {
      super("BuildSession.EventsProcessor.EventsProcessor pool", SharedThreadPool.getInstance());
      myProcessingEnabled.down();
      execute(() -> myProcessingEnabled.waitFor());
    }

    private void startProcessing() {
      myProcessingEnabled.up();
    }
  }

  private class ConstantSearch implements Callbacks.ConstantAffectionResolver {

    private ConstantSearch() {
    }

    @Nullable @Override
    public Future<Callbacks.ConstantAffection> request(String ownerClassName, String fieldName, int accessFlags, boolean fieldRemoved, boolean accessChanged) {
      final CmdlineRemoteProto.Message.BuilderMessage.ConstantSearchTask.Builder task =
        CmdlineRemoteProto.Message.BuilderMessage.ConstantSearchTask.newBuilder();
      task.setOwnerClassName(ownerClassName);
      task.setFieldName(fieldName);
      task.setAccessFlags(accessFlags);
      task.setIsAccessChanged(accessChanged);
      task.setIsFieldRemoved(fieldRemoved);
      final ConstantSearchFuture future = new ConstantSearchFuture(BuildSession.this);
      final ConstantSearchFuture prev = mySearchTasks.put(Pair.create(ownerClassName, fieldName), future);
      if (prev != null) {
        prev.setDone();
      }
      myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, CmdlineRemoteProto.Message.BuilderMessage.newBuilder()
        .setType(CmdlineRemoteProto.Message.BuilderMessage.Type.CONSTANT_SEARCH_TASK).setConstantSearchTask(task.build()).build()));
      return future;
    }
  }

  private static class ConstantSearchFuture extends BasicFuture<Callbacks.ConstantAffection> {
    private volatile Callbacks.ConstantAffection myResult = Callbacks.ConstantAffection.EMPTY;
    private final CanceledStatus myCanceledStatus;

    private ConstantSearchFuture(CanceledStatus canceledStatus) {
      myCanceledStatus = canceledStatus;
    }

    public void setResult(final Collection<File> affectedFiles) {
      myResult = new Callbacks.ConstantAffection(affectedFiles);
      setDone();
    }

    @Override
    public Callbacks.ConstantAffection get() throws InterruptedException, ExecutionException {
      while (true) {
        try {
          return get(300L, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ignored) {
        }
        if (myCanceledStatus.isCanceled()) {
          return myResult;
        }
      }
    }

    @Override
    public Callbacks.ConstantAffection get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      super.get(timeout, unit);
      return myResult;
    }
  }
}
