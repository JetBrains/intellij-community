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
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceTimestampStorage;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.FSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    String ignorePatterns = globals.hasIgnoredFilesPatterns() ? globals.getIgnoredFilesPatterns() : null;

    // session params
    myProjectPath = FileUtil.toCanonicalPath(params.getProjectId());
    String globalOptionsPath = FileUtil.toCanonicalPath(globals.getGlobalOptionsPath());
    myBuildType = convertCompileType(params.getBuildType());
    Set<String> modules = new HashSet<String>(params.getModuleNameList());
    List<String> artifacts = params.getArtifactNameList();
    List<String> filePaths = params.getFilePathList();
    Map<String, String> builderParams = new HashMap<String, String>();
    for (CmdlineRemoteProto.Message.KeyValuePair pair : params.getBuilderParameterList()) {
      builderParams.put(pair.getKey(), pair.getValue());
    }
    myInitialFSDelta = delta;
    JpsModelLoaderImpl loader = new JpsModelLoaderImpl(myProjectPath, globalOptionsPath, pathVars, ignorePatterns, null);
    myBuildRunner = new BuildRunner(loader, modules, artifacts, filePaths, builderParams);
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
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.ERROR, "Cannot determine build data storage root for project " +
                                                                                      myProjectPath));
      return;
    }
    final BuildFSState fsState = new BuildFSState(false);

    try {
      if (!dataStorageRoot.exists()) {
        // invoked the very first time for this project. Force full rebuild
        myBuildType = BuildType.PROJECT_REBUILD;
      }
      final ProjectDescriptor pd = myBuildRunner.load(msgHandler, dataStorageRoot, fsState);
      myProjectDescriptor = pd;
      loadFsState(fsState, dataStorageRoot, myInitialFSDelta, pd);

      // free memory
      myInitialFSDelta = null;
      // ensure events from controller are processed after FSState initialization
      myEventsProcessor.startProcessing();

      myBuildRunner.runBuild(pd, cs, myConstantSearch, msgHandler, true, myBuildType);
    }
    finally {
      saveData(fsState, dataStorageRoot);
    }
  }

  private void saveData(final BuildFSState fsState, File dataStorageRoot) {
    final boolean wasInterrupted = Thread.interrupted();
    try {
      saveFsState(dataStorageRoot, fsState, myLastEventOrdinal);
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

    if (pd != null) {
      final Timestamps timestamps = pd.timestamps.getStorage();
      ArtifactSourceTimestampStorage artifactTimestamps = pd.dataManager.getArtifactsBuildData().getTimestampStorage();

      for (String deleted : event.getDeletedPathsList()) {
        final File file = new File(deleted);
        final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(null, file);
        if (rd != null) {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Applying deleted path from fs event: " + file.getPath());
          }
          pd.fsState.registerDeleted(rd.target, file, timestamps);
        }
        else if (Utils.IS_TEST_MODE) {
          LOG.info("Skipping deleted path: " + file.getPath());
        }

        Collection<ArtifactRootDescriptor> descriptor = pd.getArtifactRootsIndex().getDescriptors(file);
        if (!descriptor.isEmpty()) {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Applying deleted path from fs event to artifacts: " + file.getPath());
          }
          for (ArtifactRootDescriptor rootDescriptor : descriptor)
            pd.fsState.registerDeleted(rootDescriptor.getArtifactName(), rootDescriptor.getArtifactId(), deleted,
                                       artifactTimestamps);
        }
      }
      for (String changed : event.getChangedPathsList()) {
        final File file = new File(changed);
        final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(null, file);
        if (rd != null) {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Applying dirty path from fs event: " + file.getPath());
          }
          pd.fsState.markDirty(null, file, rd, timestamps);
        }
        else if (Utils.IS_TEST_MODE) {
          LOG.info("Skipping dirty path: " + file.getPath());
        }

        Collection<ArtifactRootDescriptor> descriptors = pd.getArtifactRootsIndex().getDescriptors(file);
        if (!descriptors.isEmpty()) {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Applying dirty path from fs event to artifacts: " + file.getPath());
          }
          for (ArtifactRootDescriptor descriptor : descriptors) {
            pd.fsState.markDirty(descriptor, changed, artifactTimestamps);
          }
        }
      }
    }

    myLastEventOrdinal += 1;
  }

  private static void saveFsState(File dataStorageRoot, BuildFSState state, long lastEventOrdinal) {
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try {
      final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
      final DataOutputStream out = new DataOutputStream(bytes);
      try {
        out.writeInt(FSState.VERSION);
        out.writeLong(lastEventOrdinal);
        state.save(out);
      }
      finally {
        out.close();
      }

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
    catch (Throwable e) {
      LOG.error(e);
      FileUtil.delete(file);
    }
  }

  private void loadFsState(final BuildFSState fsState,
                           File dataStorageRoot,
                           CmdlineRemoteProto.Message.ControllerMessage.FSEvent initialEvent,
                           ProjectDescriptor pd) {
    boolean shouldApplyEvent = false;
    final File file = new File(dataStorageRoot, FS_STATE_FILE);
    try {
      final InputStream fs = new FileInputStream(file);
      byte[] bytes;
      try {
        bytes = FileUtil.loadBytes(fs, (int)file.length());
      }
      finally {
        fs.close();
      }

      final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      try {
        final int version = in.readInt();
        if (version == FSState.VERSION) {
          final long savedOrdinal = in.readLong();
          if (initialEvent != null && (savedOrdinal + 1L == initialEvent.getOrdinal())) {
            fsState.load(in, pd);
            myLastEventOrdinal = savedOrdinal;
            shouldApplyEvent = true;
          }
        }
        if (shouldApplyEvent) {
          applyFSEvent(pd, initialEvent);
        }
        else {
          // either the first start or some events were lost, forcing scan
          fsState.clearAll();
          myLastEventOrdinal = initialEvent != null? initialEvent.getOrdinal() : 0L;
        }
      }
      finally {
        in.close();
      }
      return; // successfully initialized
    }
    catch (FileNotFoundException ignored) {
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    myLastEventOrdinal = initialEvent != null? initialEvent.getOrdinal() : 0L;
    fsState.clearAll();
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
