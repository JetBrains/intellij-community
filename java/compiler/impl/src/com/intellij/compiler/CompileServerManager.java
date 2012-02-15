/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.ProjectTopics;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.compiler.server.impl.CompileServerClasspathManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.notification.Notification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.client.CompileServerClient;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.jetbrains.jps.server.Server;

import javax.tools.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class CompileServerManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompileServerManager");
  private static final String COMPILE_SERVER_SYSTEM_ROOT = "compile-server";
  private static final String LOGGER_CONFIG = "log.xml";
  private static final String DEFAULT_LOGGER_CONFIG = "defaultLogConfig.xml";
  private volatile OSProcessHandler myProcessHandler;
  private final File mySystemDirectory;
  private volatile CompileServerClient myClient = new CompileServerClient();
  private final SequentialTaskExecutor myTaskExecutor = new SequentialTaskExecutor(new SequentialTaskExecutor.AsyncTaskExecutor() {
    public void submit(Runnable runnable) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
  });
  private final ProjectManager myProjectManager;
  private static final int MAKE_TRIGGER_DELAY = 5 * 1000 /*5 seconds*/;
  private final Map<RequestFuture, Project> myAutomakeFutures = new HashMap<RequestFuture, Project>();
  private final CompileServerClasspathManager myClasspathManager = new CompileServerClasspathManager();

  public CompileServerManager(final ProjectManager projectManager) {
    myProjectManager = projectManager;
    final String systemPath = PathManager.getSystemPath();
    File system = new File(systemPath);
    try {
      system = system.getCanonicalFile();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    mySystemDirectory = system;

    projectManager.addProjectManagerListener(new ProjectWatcher());
    final MessageBusConnection conn = ApplicationManager.getApplication().getMessageBus().connect();
    conn.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
      private final AtomicBoolean myAutoMakeInProgress = new AtomicBoolean(false);
      @Override
      public void before(List<? extends VFileEvent> events) {
      }

      @Override
      public void after(List<? extends VFileEvent> events) {
        if (shouldTriggerMake(events)) {
          scheduleMake(new Runnable() {
            @Override
            public void run() {
              if (!myAutoMakeInProgress.getAndSet(true)) {
                try {
                  ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                    @Override
                    public void run() {
                      try {
                        runAutoMake();
                      }
                      finally {
                        myAutoMakeInProgress.set(false);
                      }
                    }
                  });
                }
                catch (RejectedExecutionException ignored) {
                  // we were shut down
                }
              }
              else {
                scheduleMake(this);
              }
            }
          });
        }
      }

      private void scheduleMake(Runnable runnable) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(runnable, MAKE_TRIGGER_DELAY);
      }

      private boolean shouldTriggerMake(List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event.isFromRefresh() || event.getRequestor() instanceof SavingRequestor) {
            return true;
          }
        }
        return false;
      }
    });

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        shutdownServer(myClient, myProcessHandler);
      }
    });
  }

  public static CompileServerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CompileServerManager.class);
  }

  public void notifyFilesChanged(Collection<String> paths) {
    sendNotification(paths, false);
  }

  public void notifyFilesDeleted(Collection<String> paths) {
    sendNotification(paths, true);
  }

  public void sendReloadRequest(final Project project) {
    if (!project.isDefault() && project.isOpen()) {
      myTaskExecutor.submit(new Runnable() {
        @Override
        public void run() {
          try {
            if (!project.isDisposed()) {
              final CompileServerClient client = ensureServerRunningAndClientConnected(false);
              if (client != null) {
                client.sendProjectReloadRequest(Collections.singletonList(project.getLocation()));
              }
            }
          }
          catch (Throwable e) {
            LOG.info(e);
          }
        }
      });
    }
  }

  public void sendCancelBuildRequest(final UUID sessionId) {
    myTaskExecutor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          final CompileServerClient client = ensureServerRunningAndClientConnected(false);
          if (client != null) {
            client.sendCancelBuildRequest(sessionId);
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
      }
    });
  }

  private void sendNotification(final Collection<String> paths, final boolean isDeleted) {
    if (paths.isEmpty()) {
      return;
    }
    try {
      final CompileServerClient client = ensureServerRunningAndClientConnected(false);
      if (client != null) {
        myTaskExecutor.submit(new Runnable() {
          public void run() {
            final Project[] openProjects = myProjectManager.getOpenProjects();
            if (openProjects.length > 0) {
              final Collection<String> changed, deleted;
              if (isDeleted) {
                changed = Collections.emptyList();
                deleted = paths;
              }
              else {
                changed = paths;
                deleted = Collections.emptyList();
              }
              for (Project project : openProjects) {
                try {
                  client.sendFSEvent(project.getLocation(), changed, deleted);
                }
                catch (Exception e) {
                  LOG.info(e);
                }
              }
            }
          }
        });
      }
    }
    catch (Throwable th) {
      LOG.error(th); // should not happen
    }
  }

  private void runAutoMake() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      final List<RequestFuture> futures = new ArrayList<RequestFuture>();
      for (final Project project : openProjects) {
        if (project.isDefault()) {
          continue;
        }
        final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
        if (!config.useCompileServer() || !config.MAKE_PROJECT_ON_SAVE) {
          continue;
        }
        final RequestFuture future = submitCompilationTask(project, false, true, Collections.<String>emptyList(), Collections.<String>emptyList(),
                                                           Collections.<String>emptyList(), new AutoMakeResponseHandler(project));
        if (future != null) {
          futures.add(future);
          synchronized (myAutomakeFutures) {
            myAutomakeFutures.put(future, project);
          }
        }
      }
      try {
        for (RequestFuture future : futures) {
          future.waitFor();
        }
      }
      finally {
        synchronized (myAutomakeFutures) {
          myAutomakeFutures.keySet().removeAll(futures);
        }
      }
    }
  }

  public void cancelAutoMakeTasks(Project project) {
    synchronized (myAutomakeFutures) {
      for (Map.Entry<RequestFuture, Project> entry : myAutomakeFutures.entrySet()) {
        if (entry.getValue().equals(project)) {
          entry.getKey().cancel(true);
        }
      }
    }
  }

  @Nullable
  public RequestFuture submitCompilationTask(final Project project, final boolean isRebuild, final boolean isMake, 
                                             final Collection<String> modules, final Collection<String> artifacts, 
                                             final Collection<String> paths, final JpsServerResponseHandler handler) {
    final String projectId = project.getLocation();
    final Ref<RequestFuture> futureRef = new Ref<RequestFuture>(null);
    final RunnableFuture future = myTaskExecutor.submit(new Runnable() {
      public void run() {
        try {
          final CompileServerClient client = ensureServerRunningAndClientConnected(true);
          if (client != null) {
            final RequestFuture requestFuture = isRebuild ?
              client.sendRebuildRequest(projectId, handler) :
              client.sendCompileRequest(isMake, projectId, modules, artifacts, paths, handler);
            futureRef.set(requestFuture);
          }
          else {
            handler.sessionTerminated();
          }
        }
        catch (Throwable e) {
          try {
            handler.handleFailure(ProtoUtil.createFailure(e.getMessage(), e));
          }
          finally {
            handler.sessionTerminated();
          }
        }
      }
    });
    try {
      future.get();
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return futureRef.get();
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    shutdownServer(myClient, myProcessHandler);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "com.intellij.compiler.JpsServerManager";
  }

  // executed in one thread at a time
  @Nullable
  private CompileServerClient ensureServerRunningAndClientConnected(boolean forceRestart) throws Throwable {
    final OSProcessHandler ph = myProcessHandler;
    final CompileServerClient cl = myClient;
    final boolean processNotRunning = ph == null || ph.isProcessTerminated() || ph.isProcessTerminating();
    final boolean clientNotConnected = cl == null || !cl.isConnected();

    if (processNotRunning || clientNotConnected) {
      // cleanup; ensure the process is not running
      shutdownServer(cl, ph);
      myProcessHandler = null;
      myClient = null;

      if (!forceRestart) {
        return null;
      }

      final int port = NetUtils.findAvailableSocketPort();
      final Process process = launchServer(port);

      final OSProcessHandler processHandler = new OSProcessHandler(process, null) {
        protected boolean shouldDestroyProcessRecursively() {
          return true;
        }
      };
      final StringBuilder serverStartMessage = new StringBuilder();
      final Semaphore semaphore  = new Semaphore();
      semaphore.down();
      processHandler.addProcessListener(new ProcessAdapter() {
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          // re-translate server's output to idea.log
          final String text = event.getText();
          if (!StringUtil.isEmpty(text)) {
            LOG.info("COMPILE_SERVER [" +outputType.toString() +"]: "+ text.trim());
          }
        }
      });
      processHandler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(ProcessEvent event) {
          try {
            processHandler.removeProcessListener(this);
          }
          finally {
            semaphore.up();
          }
        }

        public void onTextAvailable(ProcessEvent event, Key outputType) {
          if (outputType == ProcessOutputTypes.STDERR) {
            try {
              final String text = event.getText();
              if (text != null) {
                if (text.contains(Server.SERVER_SUCCESS_START_MESSAGE) || text.contains(Server.SERVER_ERROR_START_MESSAGE)) {
                  processHandler.removeProcessListener(this);
                }
                if (serverStartMessage.length() > 0) {
                  serverStartMessage.append("\n");
                }
                serverStartMessage.append(text);
              }
            }
            finally {
              semaphore.up();
            }
          }
        }
      });
      processHandler.startNotify();
      semaphore.waitFor();

      final String startupMsg = serverStartMessage.toString();
      if (!startupMsg.contains(Server.SERVER_SUCCESS_START_MESSAGE)) {
        throw new Exception("Server startup failed: " + startupMsg);
      }

      CompileServerClient client = new CompileServerClient();
      boolean connected = false;
      try {
        connected = client.connect(NetUtils.getLocalHostString(), port);
        if (connected) {
          final RequestFuture setupFuture = sendSetupRequest(client);
          setupFuture.waitFor();
          myProcessHandler = processHandler;
          myClient = client;
        }
      }
      finally {
        if (!connected) {
          shutdownServer(cl, processHandler);
        }
      }
    }
    return myClient;
  }

  private static RequestFuture sendSetupRequest(final @NotNull CompileServerClient client) throws Exception {
    final Map<String, String> data = new HashMap<String, String>();

    for (Map.Entry<String, String> entry : PathMacrosImpl.getGlobalSystemMacros().entrySet()) {
      data.put(entry.getKey(), FileUtil.toSystemIndependentName(entry.getValue()));
    }

    final PathMacros pathVars = PathMacros.getInstance();
    for (String name : pathVars.getAllMacroNames()) {
      final String path = pathVars.getValue(name);
      if (path != null) {
        data.put(name, FileUtil.toSystemIndependentName(path));
      }
    }

    final List<GlobalLibrary> globals = new ArrayList<GlobalLibrary>();

    fillSdks(globals);
    fillGlobalLibraries(globals);

    return client.sendSetupRequest(data, globals, EncodingManager.getInstance().getDefaultCharsetName());
  }

  private static void fillSdks(List<GlobalLibrary> globals) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final String name = sdk.getName();
      final String homePath = sdk.getHomePath();
      if (homePath == null) {
        continue;
      }
      final SdkAdditionalData data = sdk.getSdkAdditionalData();
      final String additionalDataXml;
      final SdkType sdkType = sdk.getSdkType();
      if (data == null) {
        additionalDataXml = null;
      }
      else {
        final Element element = new Element("additional");
        sdkType.saveAdditionalData(data, element);
        additionalDataXml = JDOMUtil.writeElement(element, "\n");
      }
      final List<String> paths = convertToLocalPaths(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
      globals.add(new SdkLibrary(name, sdkType.getName(), homePath, paths, additionalDataXml));
    }
  }

  private static void fillGlobalLibraries(List<GlobalLibrary> globals) {
    final Iterator<Library> iterator = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryIterator();
    while (iterator.hasNext()) {
      Library library = iterator.next();
      final String name = library.getName();

      if (name != null) {
        final List<String> paths = convertToLocalPaths(library.getFiles(OrderRootType.CLASSES));
        globals.add(new GlobalLibrary(name, paths));
      }
    }
  }

  private static List<String> convertToLocalPaths(VirtualFile[] files) {
    final List<String> paths = new ArrayList<String>();
    for (VirtualFile file : files) {
      if (file.isValid()) {
        paths.add(StringUtil.trimEnd(FileUtil.toSystemIndependentName(file.getPath()), JarFileSystem.JAR_SEPARATOR));
      }
    }
    return paths;
  }

  //public static void addLocaleOptions(final List<String> commandLine, final boolean launcherUsed) {
  //  // need to specify default encoding so that javac outputs messages in 'correct' language
  //  commandLine.add((launcherUsed? "-J" : "") + "-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + CharsetToolkit.getDefaultSystemCharset().name());
  //}

  private Process launchServer(final int port) throws ExecutionException {
    // validate tools.jar presence
    final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
    if (systemCompiler == null) {
      throw new ExecutionException("No system java compiler is provided by the JRE. Make sure tools.jar is present in IntelliJ IDEA classpath.");
    }

    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    final String vmExecutablePath = ((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk);
    cmdLine.setExePath(vmExecutablePath);
    cmdLine.addParameter("-server");
    cmdLine.addParameter("-XX:MaxPermSize=150m");
    cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");
    cmdLine.addParameter("-Xmx" + Registry.intValue("compiler.server.heap.size") + "m");
    cmdLine.addParameter("-Djava.awt.headless=true");
    //cmdLine.addParameter("-DuseJavaUtilZip");
    final String additionalOptions = Registry.stringValue("compiler.server.vm.options");
    if (!StringUtil.isEmpty(additionalOptions)) {
      final StringTokenizer tokenizer = new StringTokenizer(additionalOptions, " ", false);
      while (tokenizer.hasMoreTokens()) {
        cmdLine.addParameter(tokenizer.nextToken());
      }
    }

    // debugging
    final int debugPort = Registry.intValue("compiler.server.debug.port");
    if (debugPort > 0) {
      cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
      cmdLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + debugPort);
    }

    if (Registry.is("compiler.server.use.memory.temp.cache")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION + "=true");
    }
    if (Registry.is("compiler.server.use.external.javac.process")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_EXTERNAL_JAVAC_OPTION + "=true");
    }
    cmdLine.addParameter("-D"+ GlobalOptions.HOSTNAME_OPTION + "=" + NetUtils.getLocalHostString());
    cmdLine.addParameter("-D"+ GlobalOptions.VM_EXE_PATH_OPTION + "=" + FileUtil.toSystemIndependentName(vmExecutablePath));

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      cmdLine.addParameter("-Duser.language=" + lang);
    }
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      cmdLine.addParameter("-Duser.country=" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      cmdLine.addParameter("-Duser.region=" + region);
    }

    cmdLine.addParameter("-classpath");

    final List<File> cp = ClasspathBootstrap.getCompileServerApplicationClasspath();
    cp.addAll(myClasspathManager.getCompileServerPluginsClasspath());

    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter(org.jetbrains.jps.server.Server.class.getName());
    cmdLine.addParameter(Integer.toString(port));

    final File workDirectory = new File(mySystemDirectory, COMPILE_SERVER_SYSTEM_ROOT);
    workDirectory.mkdirs();
    ensureLogConfigExists(workDirectory);

    cmdLine.addParameter(FileUtil.toSystemIndependentName(workDirectory.getPath()));

    cmdLine.setWorkDirectory(workDirectory);


    return cmdLine.createProcess();
  }

  private static void ensureLogConfigExists(File workDirectory) {
    final File logConfig = new File(workDirectory, LOGGER_CONFIG);
    if (!logConfig.exists()) {
      FileUtil.createIfDoesntExist(logConfig);
      try {
        final InputStream in = Server.class.getResourceAsStream("/" + DEFAULT_LOGGER_CONFIG);
        if (in != null) {
          try {
            final FileOutputStream out = new FileOutputStream(logConfig);
            try {
              FileUtil.copy(in, out);
            }
            finally {
              out.close();
            }
          }
          finally {
            in.close();
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public void shutdownServer() {
    shutdownServer(myClient, myProcessHandler);
  }

  private static void shutdownServer(final CompileServerClient client, final OSProcessHandler processHandler) {
    try {
      if (client != null && client.isConnected()) {
        final Future future = client.sendShutdownRequest();
        future.get(500, TimeUnit.MILLISECONDS);
        client.disconnect();
      }
    }
    catch (Throwable ignored) {
      LOG.info(ignored);
    }
    finally {
      if (processHandler != null) {
        processHandler.destroyProcess();
      }
    }
  }

  private static String classpathToString(List<File> cp) {
    StringBuilder builder = new StringBuilder();
    for (File file : cp) {
      if (builder.length() > 0) {
        builder.append(File.pathSeparator);
      }
      builder.append(FileUtil.toCanonicalPath(file.getPath()));
    }
    return builder.toString();
  }

  private static class AutoMakeResponseHandler extends JpsServerResponseHandler {
    private JpsRemoteProto.Message.Response.BuildEvent.Status myBuildStatus;
    private final Project myProject;
    private final WolfTheProblemSolver myWolf;

    public AutoMakeResponseHandler(Project project) {
      myProject = project;
      myBuildStatus = JpsRemoteProto.Message.Response.BuildEvent.Status.SUCCESS;
      myWolf = WolfTheProblemSolver.getInstance(project);
    }

    @Override
    public boolean handleBuildEvent(JpsRemoteProto.Message.Response.BuildEvent event) {
      if (myProject.isDisposed()) {
        return true;
      }
      switch (event.getEventType()) {
        case BUILD_COMPLETED:
          if (event.hasCompletionStatus()) {
            myBuildStatus = event.getCompletionStatus();
          }
          return true;

        case FILES_GENERATED:
          final CompilationStatusListener publisher = myProject.getMessageBus().syncPublisher(CompilerTopics.COMPILATION_STATUS);
          for (JpsRemoteProto.Message.Response.BuildEvent.GeneratedFile generatedFile : event.getGeneratedFilesList()) {
            final String root = FileUtil.toSystemIndependentName(generatedFile.getOutputRoot());
            final String relativePath = FileUtil.toSystemIndependentName(generatedFile.getRelativePath());
            publisher.fileGenerated(root, relativePath);
          }
          return false;

        default:
          return false;
      }
    }

    @Override
    public void handleCompileMessage(JpsRemoteProto.Message.Response.CompileMessage compileResponse) {
      if (myProject.isDisposed()) {
        return;
      }
      final JpsRemoteProto.Message.Response.CompileMessage.Kind kind = compileResponse.getKind();
      if (kind == JpsRemoteProto.Message.Response.CompileMessage.Kind.ERROR) {
        informWolf(myProject, compileResponse);
      }
    }

    @Override
    public void handleFailure(JpsRemoteProto.Message.Failure failure) {
      CompilerManager.NOTIFICATION_GROUP.createNotification("Auto make failure: " + failure.getDescription(), MessageType.INFO);
    }

    @Override
    public void sessionTerminated() {
      String statusMessage = null/*"Auto make completed"*/;
      switch (myBuildStatus) {
        case SUCCESS:
          //statusMessage = "Auto make completed successfully";
          break;
        case UP_TO_DATE:
          //statusMessage = "All files are up-to-date";
          break;
        case ERRORS:
          statusMessage = "Auto make completed with errors";
          break;
        case CANCELED:
          statusMessage = "Auto make has been canceled";
          break;
      }
      if (statusMessage != null) {
        final Notification notification = CompilerManager.NOTIFICATION_GROUP.createNotification(statusMessage, MessageType.INFO);
        if (!myProject.isDisposed()) {
          notification.notify(myProject);
        }
      }
    }

    private void informWolf(Project project, JpsRemoteProto.Message.Response.CompileMessage message) {
      final String srcPath = message.getSourceFilePath();
      if (srcPath != null && !project.isDisposed()) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(srcPath);
        if (vFile != null) {
          final int line = (int)message.getLine();
          final int column = (int)message.getColumn();
          if (line > 0 && column > 0) {
            final Problem problem = myWolf.convertToProblem(vFile, line, column, new String[]{message.getText()});
            myWolf.weHaveGotProblems(vFile, Collections.singletonList(problem));
          }
          else {
            myWolf.queue(vFile);
          }
        }
      }
    }
  }

  private class ProjectWatcher extends ProjectManagerAdapter {
    private final Map<Project, MessageBusConnection> myConnections = new HashMap<Project, MessageBusConnection>();

    public void projectOpened(final Project project) {
      final MessageBusConnection conn = project.getMessageBus().connect();
      myConnections.put(project, conn);
      conn.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        public void beforeRootsChange(final ModuleRootEvent event) {
          sendReloadRequest(project);
        }

        public void rootsChanged(final ModuleRootEvent event) {
          myTaskExecutor.submit(new Runnable() {
            public void run() {
              try {
                // this will reload sdks and global libraries
                final CompileServerClient client = ensureServerRunningAndClientConnected(false);
                if (client != null) {
                  sendSetupRequest(client);
                }
              }
              catch (Throwable e) {
                LOG.info(e);
              }
            }
          });
        }
      });
    }

    public void projectClosing(Project project) {
      sendReloadRequest(project);
    }

    public void projectClosed(Project project) {
      final MessageBusConnection conn = myConnections.remove(project);
      if (conn != null) {
        conn.disconnect();
      }
    }
  }
}
