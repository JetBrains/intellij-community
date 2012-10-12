package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.DataOutputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.FSState;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
* @author Eugene Zhuravlev
*         Date: 4/17/12
*/
final class BuildSession implements Runnable, CanceledStatus {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildSession");
  private static final String FS_STATE_FILE = "fs_state.dat";
  private final UUID mySessionId;
  private final Channel myChannel;
  private volatile boolean myCanceled = false;
  private String myProjectPath;
  @Nullable
  private CmdlineRemoteProto.Message.ControllerMessage.FSEvent myInitialFSDelta;
  // state
  private EventsProcessor myEventsProcessor = new EventsProcessor();
  private volatile long myLastEventOrdinal;
  private volatile ProjectDescriptor myProjectDescriptor;
  private final Map<Pair<String, String>, ConstantSearchFuture> mySearchTasks = Collections.synchronizedMap(new HashMap<Pair<String, String>, ConstantSearchFuture>());
  private final ConstantSearch myConstantSearch = new ConstantSearch();
  private final BuildRunner myBuildRunner;
  private BuildType myBuildType;

  BuildSession(UUID sessionId,
               Channel channel,
               CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage params,
               @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent delta) {
    mySessionId = sessionId;
    myChannel = channel;

    // globals
    Map<String, String> pathVars = new HashMap<String, String>();
    final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals = params.getGlobalSettings();
    for (CmdlineRemoteProto.Message.KeyValuePair variable : globals.getPathVariableList()) {
      pathVars.put(variable.getKey(), variable.getValue());
    }

    // session params
    myProjectPath = FileUtil.toCanonicalPath(params.getProjectId());
    String globalOptionsPath = FileUtil.toCanonicalPath(globals.getGlobalOptionsPath());
    myBuildType = convertCompileType(params.getBuildType());
    List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes = params.getScopeList();
    List<String> filePaths = params.getFilePathList();
    Map<String, String> builderParams = new HashMap<String, String>();
    for (CmdlineRemoteProto.Message.KeyValuePair pair : params.getBuilderParameterList()) {
      builderParams.put(pair.getKey(), pair.getValue());
    }
    myInitialFSDelta = delta;
    JpsModelLoaderImpl loader = new JpsModelLoaderImpl(myProjectPath, globalOptionsPath, pathVars, null);
    myBuildRunner = new BuildRunner(loader, scopes, filePaths, builderParams);
  }

  public void run() {
    Throwable error = null;
    final Ref<Boolean> hasErrors = new Ref<Boolean>(false);
    final Ref<Boolean> markedFilesUptodate = new Ref<Boolean>(false);
    try {
      runBuild(new MessageHandler() {
        public void processMessage(BuildMessage buildMessage) {
          final CmdlineRemoteProto.Message.BuilderMessage response;
          if (buildMessage instanceof FileGeneratedEvent) {
            final Collection<Pair<String, String>> paths = ((FileGeneratedEvent)buildMessage).getPaths();
            response = !paths.isEmpty() ? CmdlineProtoUtil.createFileGeneratedEvent(paths) : null;
          }
          else if (buildMessage instanceof UptoDateFilesSavedEvent) {
            markedFilesUptodate.set(true);
            response = null;
          }
          else if (buildMessage instanceof CompilerMessage) {
            markedFilesUptodate.set(true);
            final CompilerMessage compilerMessage = (CompilerMessage)buildMessage;
            final String text = compilerMessage.getCompilerName() + ": " + compilerMessage.getMessageText();
            final BuildMessage.Kind kind = compilerMessage.getKind();
            if (kind == BuildMessage.Kind.ERROR) {
              hasErrors.set(true);
            }
            if (Utils.IS_TEST_MODE) {
              LOG.info("Processing message: " + buildMessage);
            }
            response = CmdlineProtoUtil.createCompileMessage(
              kind, text, compilerMessage.getSourcePath(),
              compilerMessage.getProblemBeginOffset(), compilerMessage.getProblemEndOffset(),
              compilerMessage.getProblemLocationOffset(), compilerMessage.getLine(), compilerMessage.getColumn(),
              -1.0f);
          }
          else {
            float done = -1.0f;
            if (buildMessage instanceof ProgressMessage) {
              done = ((ProgressMessage)buildMessage).getDone();
            }
            response = CmdlineProtoUtil.createCompileProgressMessageResponse(buildMessage.getMessageText(), done);
          }
          if (response != null) {
            Channels.write(myChannel, CmdlineProtoUtil.toMessage(mySessionId, response));
          }
        }
      }, this);
    }
    catch (Throwable e) {
      LOG.info(e);
      error = e;
    }
    finally {
      finishBuild(error, hasErrors.get(), markedFilesUptodate.get());
    }
  }

  private void runBuild(final MessageHandler msgHandler, CanceledStatus cs) throws Throwable{
    final File dataStorageRoot = Utils.getDataStorageRoot(myProjectPath);
    if (dataStorageRoot == null) {
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.ERROR, "Cannot determine build data storage root for project " + myProjectPath));
      return;
    }
    if (!dataStorageRoot.exists()) {
      // invoked the very first time for this project. Force full rebuild
      myBuildType = BuildType.PROJECT_REBUILD;
    }

    final DataInputStream fsStateStream = createFSDataStream(dataStorageRoot);

    if (fsStateStream != null) {
      // optimization: check whether we can skip the build
      final boolean hasWorkToDoWithModules = fsStateStream.readBoolean();
      if (myBuildType == BuildType.MAKE && !hasWorkToDoWithModules && scopeContainsModulesOnly(myBuildRunner.getScopes()) && !containsChanges(myInitialFSDelta)) {
        updateFsStateOnDisk(dataStorageRoot, fsStateStream, myInitialFSDelta.getOrdinal());
        return;
      }
    }

    final BuildFSState fsState = new BuildFSState(false);
    try {
      final ProjectDescriptor pd = myBuildRunner.load(msgHandler, dataStorageRoot, fsState);
      myProjectDescriptor = pd;
      if (fsStateStream != null) {
        try {
          try {
            fsState.load(fsStateStream, pd.getModel(), pd.getBuildRootIndex());
            applyFSEvent(pd, myInitialFSDelta);
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
      myLastEventOrdinal = myInitialFSDelta != null? myInitialFSDelta.getOrdinal() : 0L;

      // free memory
      myInitialFSDelta = null;
      // ensure events from controller are processed after FSState initialization
      myEventsProcessor.startProcessing();

      myBuildRunner.runBuild(pd, cs, myConstantSearch, msgHandler, myBuildType);
    }
    finally {
      saveData(fsState, dataStorageRoot);
    }
  }

  private static boolean scopeContainsModulesOnly(List<TargetTypeBuildScope> scopes) {
    for (TargetTypeBuildScope scope : scopes) {
      String typeId = scope.getTypeId();
      if (!typeId.equals(JavaModuleBuildTargetType.PRODUCTION.getTypeId()) && !typeId.equals(JavaModuleBuildTargetType.TEST.getTypeId())) {
        return false;
      }
    }
    return true;
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
    myEventsProcessor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          applyFSEvent(myProjectDescriptor, event);
          myLastEventOrdinal += 1;
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  public void processConstantSearchResult(CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult result) {
    final ConstantSearchFuture future = mySearchTasks.remove(Pair.create(result.getOwnerClassName(), result.getFieldName()));
    if (future != null) {
      if (result.getIsSuccess()) {
        final List<String> paths = result.getPathList();
        final List<File> files = new ArrayList<File>(paths.size());
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

  private void applyFSEvent(ProjectDescriptor pd, @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) throws IOException {
    if (event == null) {
      return;
    }

    final Timestamps timestamps = pd.timestamps.getStorage();

    for (String deleted : event.getDeletedPathsList()) {
      final File file = new File(deleted);
      Collection<BuildRootDescriptor> descriptor = pd.getBuildRootIndex().findAllParentDescriptors(file, null, null);
      if (!descriptor.isEmpty()) {
        if (Utils.IS_TEST_MODE) {
          LOG.info("Applying deleted path from fs event: " + file.getPath());
        }
        for (BuildRootDescriptor rootDescriptor : descriptor) {
          pd.fsState.registerDeleted(rootDescriptor.getTarget(), file, timestamps);
        }
      }
      else if (Utils.IS_TEST_MODE) {
        LOG.info("Skipping deleted path: " + file.getPath());
      }
    }
    for (String changed : event.getChangedPathsList()) {
      final File file = new File(changed);
      Collection<BuildRootDescriptor> descriptors = pd.getBuildRootIndex().findAllParentDescriptors(file, null, null);
      if (!descriptors.isEmpty()) {
        if (Utils.IS_TEST_MODE) {
          LOG.info("Applying dirty path from fs event: " + file.getPath());
        }
        for (BuildRootDescriptor descriptor : descriptors) {
          pd.fsState.markDirty(null, file, descriptor, timestamps);
        }
      }
      else if (Utils.IS_TEST_MODE) {
        LOG.info("Skipping dirty path: " + file.getPath());
      }
    }
  }

  private void updateFsStateOnDisk(File dataStorageRoot, DataInputStream original, final long ordinal) {
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try {
      final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
      final DataOutputStream out = new DataOutputStream(bytes);
      try {
        out.writeInt(FSState.VERSION);
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
        out.writeInt(FSState.VERSION);
        out.writeLong(myLastEventOrdinal);
        boolean hasWorkToDoWithModules = false;
        for (JpsModule module : pd.getProject().getModules()) {
          for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
            if (state.hasWorkToDo(new ModuleBuildTarget(module, type))) {
              hasWorkToDoWithModules = true;
              break;
            }
          }
          if (hasWorkToDoWithModules) {
            break;
          }
        }
        out.writeBoolean(hasWorkToDoWithModules);
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

  private static void saveOnDisk(BufferExposingByteArrayOutputStream bytes, final File file) throws IOException {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(file);
    }
    catch (FileNotFoundException e) {
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
  private DataInputStream createFSDataStream(File dataStorageRoot) {
    if (myInitialFSDelta == null) {
      // this will force FS rescan
      return null;
    }
    try {
      final File file = new File(dataStorageRoot, FS_STATE_FILE);
      final InputStream fs = new FileInputStream(file);
      byte[] bytes;
      try {
        bytes = FileUtil.loadBytes(fs, (int)file.length());
      }
      finally {
        fs.close();
      }
      final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      final int version = in.readInt();
      if (version != FSState.VERSION) {
        return null;
      }
      final long savedOrdinal = in.readLong();
      if (savedOrdinal + 1L != myInitialFSDelta.getOrdinal()) {
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

  private void finishBuild(Throwable error, boolean hadBuildErrors, boolean markedUptodateFiles) {
    CmdlineRemoteProto.Message lastMessage = null;
    try {
      if (error != null) {
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
        else if (!markedUptodateFiles){
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
        Channels.write(myChannel, lastMessage).await();
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
      case MAKE: return BuildType.MAKE;
      case REBUILD: return BuildType.PROJECT_REBUILD;
      case FORCED_COMPILATION: return BuildType.FORCED_COMPILATION;
    }
    return BuildType.MAKE; // use make by default
  }

  private static class EventsProcessor extends SequentialTaskExecutor {
    private final AtomicBoolean myProcessingEnabled = new AtomicBoolean(false);

    EventsProcessor() {
      super(SharedThreadPool.getInstance());
    }

    public void startProcessing() {
      if (!myProcessingEnabled.getAndSet(true)) {
        super.processQueue();
      }
    }

    @Override
    protected void processQueue() {
      if (myProcessingEnabled.get()) {
        super.processQueue();
      }
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
      final ConstantSearchFuture prev = mySearchTasks.put(new Pair<String, String>(ownerClassName, fieldName), future);
      if (prev != null) {
        prev.setDone();
      }
      Channels.write(myChannel,
        CmdlineProtoUtil.toMessage(
          mySessionId, CmdlineRemoteProto.Message.BuilderMessage.newBuilder().setType(CmdlineRemoteProto.Message.BuilderMessage.Type.CONSTANT_SEARCH_TASK).setConstantSearchTask(task.build()).build()
        )
      );
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
