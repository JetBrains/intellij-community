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
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.client.Client;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.jetbrains.jps.server.Server;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class JpsServerManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.JpsServerManager");
  private static final String COMPILE_SERVER_SYSTEM_ROOT = "compile-server";
  private volatile OSProcessHandler myProcessHandler;
  private final File mySystemDirectory;
  private volatile Client myClient = new Client();
  private final SequentialTaskExecutor myTaskExecutor = new SequentialTaskExecutor(new SequentialTaskExecutor.AsyncTaskExecutor() {
    public void submit(Runnable runnable) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
  });
  private final ProjectManager myProjectManager;

  public JpsServerManager(final ProjectManager projectManager) {
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
    final MessageBusConnection appConnection = ApplicationManager.getApplication().getMessageBus().connect();
    appConnection.subscribe(ProjectEx.ProjectSaved.TOPIC, new ProjectEx.ProjectSaved() {
      public void saved(@NotNull Project project) {
        sendReloadRequest(project);
      }
    });

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        shutdownServer(myClient, myProcessHandler);
      }
    });
  }

  public static JpsServerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(JpsServerManager.class);
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
              final Client client = ensureServerRunningAndClientConnected(false);
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

  private void sendNotification(final Collection<String> paths, final boolean isDeleted) {
    try {
      final Client client = ensureServerRunningAndClientConnected(false);
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

  @Nullable
  public Future submitCompilationTask(final String projectId, final List<String> modules, final boolean rebuild, final JpsServerResponseHandler handler) {
    final Ref<RequestFuture> futureRef = new Ref<RequestFuture>(null);
    final RunnableFuture future = myTaskExecutor.submit(new Runnable() {
      public void run() {
        try {
          final Client client = ensureServerRunningAndClientConnected(true);
          if (client != null) {
            final RequestFuture requestFuture = client.sendCompileRequest(projectId, modules, rebuild, handler);
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
  private Client ensureServerRunningAndClientConnected(boolean forceRestart) throws Throwable {
    final OSProcessHandler ph = myProcessHandler;
    final Client cl = myClient;
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
      final Ref<String> serverStartMessage = new Ref<String>(null);
      final Semaphore semaphore  = new Semaphore();
      semaphore.down();
      processHandler.addProcessListener(new ProcessAdapter() {
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          // re-translate server's output to idea.log
          LOG.info("COMPILE_SERVER [" +outputType.toString() +"]: "+ event.getText());
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
                  serverStartMessage.set(text);
                }
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

      final String startupMsg = serverStartMessage.get();
      if (startupMsg == null || !startupMsg.contains(Server.SERVER_SUCCESS_START_MESSAGE)) {
        throw new Exception("Server startup failed: " + startupMsg);
      }

      Client client = new Client();
      boolean connected = false;
      try {
        connected = client.connect(NetUtils.getLocalHostString(), port);
        if (connected) {
          final RequestFuture setupFuture = sendSetupRequest(client);
          setupFuture.get();
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

  private static RequestFuture sendSetupRequest(final @NotNull Client client) throws Exception {
    final Map<String, String> data = new HashMap<String, String>();

    // need this for tests and when this macro is missing from PathMacros registry
    data.put(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, FileUtil.toSystemIndependentName(PathManager.getHomePath()));

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

    return client.sendSetupRequest(data, globals);
  }

  private static void fillSdks(List<GlobalLibrary> globals) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final String name = sdk.getName();
      final String homePath = sdk.getHomePath();
      if (homePath == null) {
        continue;
      }
      final List<String> paths = convertToLocalPaths(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
      globals.add(new SdkLibrary(name, homePath, paths));
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

  private Process launchServer(int port) throws ExecutionException {
    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk));
    cmdLine.addParameter("-server");
    cmdLine.addParameter("-ea");
    cmdLine.addParameter("-XX:MaxPermSize=150m");
    cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");
    cmdLine.addParameter("-Djava.awt.headless=true");
    //cmdLine.addParameter("-DuseJavaUtilZip");
    // todo: get xmx value from settings
    if (SystemInfo.is64Bit) {
      cmdLine.addParameter("-Xmx700m");
    }
    else {
      cmdLine.addParameter("-Xmx512m");
    }

    // debugging
    cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
    //cmdLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5008");

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

    final List<File> cp = ClasspathBootstrap.getApplicationClasspath();

    // append tools.jar
    final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
    if (systemCompiler == null) {
      throw new ExecutionException("No system java compiler is provided by the JRE. Make sure tools.jar is present in IntelliJ IDEA classpath.");
    }
    try {
      cp.add(ClasspathBootstrap.getResourcePath(systemCompiler.getClass()));  // tools.jar
    }
    catch (Throwable ignored) {
    }

    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter(org.jetbrains.jps.server.Server.class.getName());
    cmdLine.addParameter(Integer.toString(port));

    final File workDirectory = new File(mySystemDirectory, COMPILE_SERVER_SYSTEM_ROOT);
    workDirectory.mkdirs();

    cmdLine.addParameter(FileUtil.toSystemIndependentName(workDirectory.getPath()));

    cmdLine.setWorkDirectory(workDirectory);

    return cmdLine.createProcess();
  }

  public void shutdownServer() {
    shutdownServer(myClient, myProcessHandler);
  }

  private static void shutdownServer(final Client client, final OSProcessHandler processHandler) {
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
      builder.append(file.getAbsolutePath());
    }
    return builder.toString();
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
                final Client client = ensureServerRunningAndClientConnected(false);
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
