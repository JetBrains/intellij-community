package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.DataOutputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.idea.SystemOutErrorReporter;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsSdkProperties;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.model.serialization.JpsSdkPropertiesLoader;
import org.jetbrains.jps.model.serialization.JpsSdkTableLoader;

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
  public static final String IDEA_PROJECT_DIRNAME = ".idea";
  private static final String FS_STATE_FILE = "fs_state.dat";
  private final UUID mySessionId;
  private final Channel myChannel;
  private volatile boolean myCanceled = false;
  // globals
  private final Map<String, String> myPathVars;
  private final List<GlobalLibrary> myGlobalLibraries;
  private final String myGlobalEncoding;
  private final String myIgnorePatterns;
  // build params
  private final BuildType myBuildType;
  private final Set<String> myModules;
  private final List<String> myArtifacts;
  private final List<String> myFilePaths;
  private final Map<String, String> myBuilderParams;
  private String myProjectPath;
  @Nullable
  private CmdlineRemoteProto.Message.ControllerMessage.FSEvent myInitialFSDelta;
  // state
  private EventsProcessor myEventsProcessor = new EventsProcessor();
  private volatile long myLastEventOrdinal;
  private volatile ProjectDescriptor myProjectDescriptor;
  private final Map<Pair<String, String>, ConstantSearchFuture> mySearchTasks = Collections.synchronizedMap(new HashMap<Pair<String, String>, ConstantSearchFuture>());
  private final ConstantSearch myConstantSearch = new ConstantSearch();

  BuildSession(UUID sessionId,
               Channel channel,
               CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage params,
               @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent delta) {
    mySessionId = sessionId;
    myChannel = channel;

    // globals
    myPathVars = new HashMap<String, String>();
    final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals = params.getGlobalSettings();
    for (CmdlineRemoteProto.Message.KeyValuePair variable : globals.getPathVariableList()) {
      myPathVars.put(variable.getKey(), variable.getValue());
    }
    myGlobalLibraries = new ArrayList<GlobalLibrary>();
    for (CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.GlobalLibrary library : globals.getGlobalLibraryList()) {
      myGlobalLibraries.add(
        library.hasHomePath() ?
        new SdkLibrary(library.getName(), library.getTypeName(), library.hasVersion() ? library.getVersion() : null, library.getHomePath(), library.getPathList(), library.hasAdditionalDataXml() ? library.getAdditionalDataXml() : null) :
        new GlobalLibrary(library.getName(), library.getPathList())
      );
    }
    myGlobalEncoding = globals.hasGlobalEncoding()? globals.getGlobalEncoding() : null;
    myIgnorePatterns = globals.hasIgnoredFilesPatterns()? globals.getIgnoredFilesPatterns() : null;

    // session params
    myProjectPath = FileUtil.toCanonicalPath(params.getProjectId());
    myBuildType = convertCompileType(params.getBuildType());
    myModules = new HashSet<String>(params.getModuleNameList());
    myArtifacts = params.getArtifactNameList();
    myFilePaths = params.getFilePathList();
    myBuilderParams = new HashMap<String, String>();
    for (CmdlineRemoteProto.Message.KeyValuePair pair : params.getBuilderParameterList()) {
      myBuilderParams.put(pair.getKey(), pair.getValue());
    }
    myInitialFSDelta = delta;
  }

  public void run() {
    Throwable error = null;
    final Ref<Boolean> hasErrors = new Ref<Boolean>(false);
    final Ref<Boolean> markedFilesUptodate = new Ref<Boolean>(false);
    try {
      runBuild(myProjectPath, myBuildType, myModules, myArtifacts, myBuilderParams, myFilePaths, new MessageHandler() {
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

  private void runBuild(String projectPath, BuildType buildType, Set<String> modules, Collection<String> artifacts, Map<String, String> builderParams, Collection<String> paths, final MessageHandler msgHandler, CanceledStatus cs) throws Throwable{
    boolean forceCleanCaches = false;

    final File dataStorageRoot = Utils.getDataStorageRoot(projectPath);
    if (dataStorageRoot == null) {
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.ERROR, "Cannot determine build data storage root for project " + projectPath));
      return;
    }
    final BuildFSState fsState = new BuildFSState(false);

    try {
      final boolean shouldApplyEvent = loadFsState(fsState, dataStorageRoot, myInitialFSDelta);
      if (shouldApplyEvent && buildType == BuildType.MAKE && !containsChanges(myInitialFSDelta) && !fsState.hasWorkToDo()) {
        applyFSEvent(null, myInitialFSDelta);
        return;
      }
      if (!dataStorageRoot.exists()) {
        // invoked the very first time for this project. Force full rebuild
        buildType = BuildType.PROJECT_REBUILD;
      }

      final boolean inMemoryMappingsDelta = System.getProperty(GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION) != null;
      ProjectTimestamps projectTimestamps = null;
      BuildDataManager dataManager = null;
      try {
        projectTimestamps = new ProjectTimestamps(dataStorageRoot);
        dataManager = new BuildDataManager(dataStorageRoot, inMemoryMappingsDelta);
        if (dataManager.versionDiffers()) {
          forceCleanCaches = true;
          msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.INFO, "Dependency data format has changed, project rebuild required"));
        }
      }
      catch (Exception e) {
        // second try
        LOG.info(e);
        if (projectTimestamps != null) {
          projectTimestamps.close();
        }
        if (dataManager != null) {
          dataManager.close();
        }
        forceCleanCaches = true;
        FileUtil.delete(dataStorageRoot);
        projectTimestamps = new ProjectTimestamps(dataStorageRoot);
        dataManager = new BuildDataManager(dataStorageRoot, inMemoryMappingsDelta);
        // second attempt succeded
        msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
      }

      final JpsModel jpsModel = loadJpsProject(projectPath);
      final Project project = loadProject(projectPath);
      final ProjectDescriptor pd = new ProjectDescriptor(project, jpsModel, fsState, projectTimestamps, dataManager, BuildLoggingManager.DEFAULT);
      myProjectDescriptor = pd;
      if (shouldApplyEvent) {
        applyFSEvent(pd, myInitialFSDelta);
      }


      // free memory
      myInitialFSDelta = null;
      // ensure events from controller are processed after FSState initialization
      myEventsProcessor.startProcessing();

      for (int attempt = 0; attempt < 2; attempt++) {
        if (forceCleanCaches && modules.isEmpty() && paths.isEmpty()) {
          // if compilation scope is the whole project and cache rebuild is forced, use PROJECT_REBUILD for faster compilation
          buildType = BuildType.PROJECT_REBUILD;
        }

        final CompileScope compileScope = createCompilationScope(buildType, pd, modules, artifacts, paths);
        final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), builderParams, cs, myConstantSearch);
        builder.addMessageHandler(msgHandler);
        try {
          switch (buildType) {
            case PROJECT_REBUILD:
              builder.build(compileScope, false, true, forceCleanCaches);
              break;

            case FORCED_COMPILATION:
              builder.build(compileScope, false, false, forceCleanCaches);
              break;

            case MAKE:
              builder.build(compileScope, true, false, forceCleanCaches);
              break;

            case CLEAN:
              //todo[nik]
      //        new ProjectBuilder(new GantBinding(), project).clean();
              break;
          }
          break; // break attempts loop
        }
        catch (RebuildRequestedException e) {
          if (attempt == 0) {
            LOG.info(e);
            forceCleanCaches = true;
          }
          else {
            throw e;
          }
        }
      }
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
      }
      else {
        future.setDone();
      }
    }
  }

  private void applyFSEvent(ProjectDescriptor pd, @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) throws IOException {
    if (event == null) {
      return;
    }

    if (pd != null) {
      final Timestamps timestamps = pd.timestamps.getStorage();

      for (String deleted : event.getDeletedPathsList()) {
        final File file = new File(deleted);
        final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(null, file);
        if (rd != null) {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Applying deleted path from fs event: " + file.getPath());
          }
          pd.fsState.registerDeleted(rd.module, file, rd.isTestRoot, timestamps);
        }
        else {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Skipping deleted path: " + file.getPath());
          }
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
        else {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Skipping dirty path: " + file.getPath());
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

  private boolean loadFsState(final BuildFSState fsState, File dataStorageRoot, CmdlineRemoteProto.Message.ControllerMessage.FSEvent initialEvent) {
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
        final long savedOrdinal = in.readLong();
        if (initialEvent != null && (savedOrdinal + 1L == initialEvent.getOrdinal())) {
          fsState.load(in);
          myLastEventOrdinal = savedOrdinal;
          shouldApplyEvent = true;
          //applyFSEvent(pd, initialEvent);
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
      return shouldApplyEvent; // successfully initialized

    }
    catch (FileNotFoundException ignored) {
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    myLastEventOrdinal = initialEvent != null? initialEvent.getOrdinal() : 0L;
    fsState.clearAll();
    return shouldApplyEvent;
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
        cause.printStackTrace(new PrintStream(out));

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

  private JpsModel loadJpsProject(String projectPath) {
    final long start = System.currentTimeMillis();
    try {
      final JpsModel model = JpsElementFactory.getInstance().createModel();
      try {
        for (GlobalLibrary library : myGlobalLibraries) {
          JpsLibrary jpsLibrary = null;
          if (library instanceof SdkLibrary) {
            final SdkLibrary sdkLibrary = (SdkLibrary)library;
            final JpsSdkPropertiesLoader<?> loader = JpsSdkTableLoader.getSdkPropertiesLoader(sdkLibrary.getTypeName());
            if (loader != null) {
              jpsLibrary = addLibrary(model, sdkLibrary, loader);
            }
            else {
              LOG.info("Sdk type " + sdkLibrary.getTypeName() + " not registered");
            }
          }
          else {
            jpsLibrary = model.getGlobal().getLibraryCollection().addLibrary(library.getName(), JpsJavaLibraryType.INSTANCE);
          }
          if (jpsLibrary != null) {
            for (String path : library.getPaths()) {
              jpsLibrary.addRoot(JpsPathUtil.pathToUrl(path), JpsOrderRootType.COMPILED);
            }
          }
        }
        JpsProjectLoader.loadProject(model.getProject(), myPathVars, projectPath);
        LOG.info("New JPS model: " + model.getProject().getModules().size() + " modules, " + model.getProject().getLibraryCollection().getLibraries().size() + " libraries");
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return model;
    }
    finally {
      final long loadTime = System.currentTimeMillis() - start;
      LOG.info("New JPS model: project " + projectPath + " loaded in " + loadTime + " ms");
    }
  }

  private static <P extends JpsSdkProperties> JpsLibrary addLibrary(JpsModel model, SdkLibrary sdkLibrary, JpsSdkPropertiesLoader<P> loader) {
    try {
      final String xml = sdkLibrary.getAdditionalDataXml();
      final Element element = xml != null ? JDOMUtil.loadDocument(xml).getRootElement() : null;
      return model.getGlobal().getLibraryCollection().addLibrary(sdkLibrary.getName(), loader.getType(), loader.loadProperties(sdkLibrary.getHomePath(), sdkLibrary.getVersion(),
                                                                                                                        element));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
  }

  private Project loadProject(String projectPath) {
    final long start = System.currentTimeMillis();
    try {
      final Project project = new Project();

      final File projectFile = new File(projectPath);

      //String root = dirBased ? projectPath : projectFile.getParent();

      final String loadPath = isDirectoryBased(projectFile) ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
      IdeaProjectLoader.loadFromPath(project, loadPath, myPathVars, null, new SystemOutErrorReporter(false));
      final String globalEncoding = myGlobalEncoding;
      if (!StringUtil.isEmpty(globalEncoding) && project.getProjectCharset() == null) {
        project.setProjectCharset(globalEncoding);
      }
      project.getIgnoredFilePatterns().loadFromString(myIgnorePatterns);
      return project;
    }
    finally {
      final long loadTime = System.currentTimeMillis() - start;
      LOG.info("Project " + projectPath + " loaded in " + loadTime + " ms");
    }
  }

  private static boolean isDirectoryBased(File projectFile) {
    return !(projectFile.isFile() && projectFile.getName().endsWith(".ipr"));
  }

  private static CompileScope createCompilationScope(BuildType buildType,
                                                     ProjectDescriptor pd,
                                                     Set<String> modules,
                                                     Collection<String> artifactNames,
                                                     Collection<String> paths) throws Exception {
    final Timestamps timestamps = pd.timestamps.getStorage();
    Set<JpsArtifact> artifacts = new HashSet<JpsArtifact>();
    if (artifactNames.isEmpty() && buildType == BuildType.PROJECT_REBUILD) {
      artifacts.addAll(JpsArtifactService.getInstance().getArtifacts(pd.jpsProject));
    }
    else {
      for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(pd.jpsProject)) {
        if (artifactNames.contains(artifact.getName()) && !StringUtil.isEmpty(artifact.getOutputPath())) {
          artifacts.add(artifact);
        }
      }
    }

    final CompileScope compileScope;
    if (buildType == BuildType.PROJECT_REBUILD || (modules.isEmpty() && paths.isEmpty())) {
      compileScope = new AllProjectScope(pd.project, pd.jpsProject, artifacts, buildType != BuildType.MAKE);
    }
    else {
      final Set<JpsModule> forcedModules;
      if (!modules.isEmpty()) {
        forcedModules = new HashSet<JpsModule>();
        for (JpsModule m : pd.jpsProject.getModules()) {
          if (modules.contains(m.getName())) {
            forcedModules.add(m);
          }
        }
      }
      else {
        forcedModules = Collections.emptySet();
      }

      final Map<String, Set<File>> filesToCompile;
      if (!paths.isEmpty()) {
        filesToCompile = new HashMap<String, Set<File>>();
        for (String path : paths) {
          final File file = new File(path);
          final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(null, file);
          if (rd != null) {
            Set<File> files = filesToCompile.get(rd.module);
            if (files == null) {
              files = new HashSet<File>();
              filesToCompile.put(rd.module, files);
            }
            files.add(file);
            if (buildType == BuildType.FORCED_COMPILATION) {
              pd.fsState.markDirty(null, file, rd, timestamps);
            }
          }
        }
      }
      else {
        filesToCompile = Collections.emptyMap();
      }

      if (filesToCompile.isEmpty()) {
        compileScope = new ModulesScope(pd.project, pd.jpsProject, forcedModules, artifacts, buildType != BuildType.MAKE);
      }
      else {
        compileScope = new ModulesAndFilesScope(pd.project, pd.jpsProject, forcedModules, filesToCompile, artifacts, buildType != BuildType.MAKE);
      }
    }
    return compileScope;
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
      super(SharedThreadPool.INSTANCE);
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
