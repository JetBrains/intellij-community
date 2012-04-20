package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import groovy.util.Node;
import groovy.util.XmlParser;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.Sdk;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.idea.SystemOutErrorReporter;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;

/**
* @author Eugene Zhuravlev
*         Date: 4/17/12
*/
final class BuildSession implements Runnable, CanceledStatus {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildSession");
  public static final String IDEA_PROJECT_DIRNAME = ".idea";
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

  // todo pass FSState in order not to scan FS from scratch

  BuildSession(UUID sessionId, Channel channel, CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage params) {
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
    ProjectDescriptor pd;
    final Project project = loadProject(projectPath);
    final BuildFSState fsState = new BuildFSState(false);
    ProjectTimestamps timestamps = null;
    BuildDataManager dataManager = null;
    final File dataStorageRoot = Utils.getDataStorageRoot(project);
    try {
      timestamps = new ProjectTimestamps(dataStorageRoot);
      dataManager = new BuildDataManager(dataStorageRoot, true);
    }
    catch (Exception e) {
      // second try
      e.printStackTrace(System.err);
      if (timestamps != null) {
        timestamps.close();
      }
      if (dataManager != null) {
        dataManager.close();
      }
      buildType = BuildType.PROJECT_REBUILD; // force project rebuild
      FileUtil.delete(dataStorageRoot);
      timestamps = new ProjectTimestamps(dataStorageRoot);
      dataManager = new BuildDataManager(dataStorageRoot, true);
      // second attempt succeded
      msgHandler.processMessage(new CompilerMessage("compile-server", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
    }

    pd = new ProjectDescriptor(project, fsState, timestamps, dataManager, BuildLoggingManager.DEFAULT);

    try {
      final CompileScope compileScope = createCompilationScope(buildType, pd, modules, artifacts, paths);
      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), builderParams, cs);
      if (msgHandler != null) {
        builder.addMessageHandler(msgHandler);
      }
      switch (buildType) {
        case PROJECT_REBUILD:
          builder.build(compileScope, false, true);
          break;

        case FORCED_COMPILATION:
          builder.build(compileScope, false, false);
          break;

        case MAKE:
          builder.build(compileScope, true, false);
          break;

        case CLEAN:
          //todo[nik]
  //        new ProjectBuilder(new GantBinding(), project).clean();
          break;
      }
    }
    finally {
      pd.release();
    }
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
      Channels.write(myChannel, lastMessage).addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          SharedThreadPool.INSTANCE.submit(new Runnable() {
            @Override
            public void run() {
              final ChannelFuture closeFuture = myChannel.close();
              closeFuture.awaitUninterruptibly();
            }
          });
        }
      });
    }
  }

  public void cancel() {
    myCanceled = true;
  }

  @Override
  public boolean isCanceled() {
    return myCanceled;
  }


  private Project loadProject(String projectPath) {
    final long start = System.currentTimeMillis();
    try {
      final Project project = new Project();

      initSdksAndGlobalLibraries(project);

      final File projectFile = new File(projectPath);

      //String root = dirBased ? projectPath : projectFile.getParent();

      final String loadPath = isDirectoryBased(projectFile) ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
      IdeaProjectLoader.loadFromPath(project, loadPath, myPathVars, null, new SystemOutErrorReporter(false));
      final String globalEncoding = myGlobalEncoding;
      if (globalEncoding != null && project.getProjectCharset() == null) {
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

  private void initSdksAndGlobalLibraries(Project project) {
    final MethodClosure fakeClosure = new MethodClosure(new Object(), "hashCode");
    for (GlobalLibrary library : myGlobalLibraries) {
      if (library instanceof SdkLibrary) {
        final SdkLibrary sdk = (SdkLibrary)library;
        Node additionalData = null;
        final String additionalXml = sdk.getAdditionalDataXml();
        if (additionalXml != null) {
          try {
            additionalData = new XmlParser(false, false).parseText(additionalXml);
          }
          catch (Exception e) {
            LOG.info(e);
          }
        }
        final Sdk jdk = project.createSdk(sdk.getTypeName(), sdk.getName(), sdk.getVersion(), sdk.getHomePath(), additionalData);
        if (jdk != null) {
          jdk.setClasspath(sdk.getPaths());
        }
        else {
          LOG.info("Failed to load SDK " + sdk.getName() + ", type: " + sdk.getTypeName());
        }
      }
      else {
        final Library lib = project.createGlobalLibrary(library.getName(), fakeClosure);
        if (lib != null) {
          lib.setClasspath(library.getPaths());
        }
        else {
          LOG.info("Failed to load global library " + library.getName());
        }
      }
    }
  }

  private static boolean isDirectoryBased(File projectFile) {
    return !(projectFile.isFile() && projectFile.getName().endsWith(".ipr"));
  }

  private static CompileScope createCompilationScope(BuildType buildType, ProjectDescriptor pd, Set<String> modules, Collection<String> artifactNames, Collection<String> paths) throws Exception {
    Set<Artifact> artifacts = new HashSet<Artifact>();
    if (artifactNames.isEmpty() && buildType == BuildType.PROJECT_REBUILD) {
      artifacts.addAll(pd.project.getArtifacts().values());
    }
    else {
      final Map<String, Artifact> artifactMap = pd.project.getArtifacts();
      for (String name : artifactNames) {
        final Artifact artifact = artifactMap.get(name);
        if (artifact != null && !StringUtil.isEmpty(artifact.getOutputPath())) {
          artifacts.add(artifact);
        }
      }
    }

    final CompileScope compileScope;
    if (buildType == BuildType.PROJECT_REBUILD || (modules.isEmpty() && paths.isEmpty())) {
      compileScope = new AllProjectScope(pd.project, artifacts, buildType != BuildType.MAKE);
    }
    else {
      final Set<Module> forcedModules;
      if (!modules.isEmpty()) {
        forcedModules = new HashSet<Module>();
        for (Module m : pd.project.getModules().values()) {
          if (modules.contains(m.getName())) {
            forcedModules.add(m);
          }
        }
      }
      else {
        forcedModules = Collections.emptySet();
      }

      final TimestampStorage tsStorage = pd.timestamps.getStorage();

      final Map<Module, Set<File>> filesToCompile;
      if (!paths.isEmpty()) {
        filesToCompile = new HashMap<Module, Set<File>>();
        for (String path : paths) {
          final File file = new File(path);
          final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(file);
          if (rd != null) {
            Set<File> files = filesToCompile.get(rd.module);
            if (files == null) {
              files = new HashSet<File>();
              filesToCompile.put(rd.module, files);
            }
            files.add(file);
            if (buildType == BuildType.FORCED_COMPILATION) {
              pd.fsState.markDirty(file, rd, tsStorage);
            }
          }
        }
      }
      else {
        filesToCompile = Collections.emptyMap();
      }

      if (filesToCompile.isEmpty()) {
        compileScope = new ModulesScope(pd.project, forcedModules, artifacts, buildType != BuildType.MAKE);
      }
      else {
        compileScope = new ModulesAndFilesScope(pd.project, forcedModules, filesToCompile, artifacts, buildType != BuildType.MAKE);
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

}
