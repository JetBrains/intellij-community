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
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.server.GlobalLibrary;
import org.jetbrains.jps.server.SdkLibrary;
import org.jetbrains.jpsservice.Bootstrap;
import org.jetbrains.jpsservice.Client;

import java.io.File;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class JpsServerManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.JpsServerManager");
  private static final String COMPILE_SERVER_SYSTEM_ROOT = "compile-server";
  private volatile Client myServerClient;
  private volatile OSProcessHandler myProcessHandler;

  public JpsServerManager(ProjectManager projectManager) {
    projectManager.addProjectManagerListener(new ProjectWatcher());
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        shutdownServer(myServerClient, myProcessHandler);
      }
    });
  }

  public static JpsServerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(JpsServerManager.class);
  }

  @Nullable
  public Client getClient() {
    if (!ensureServerStarted()) {
      return null;
    }
    return myServerClient;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    shutdownServer(myServerClient, myProcessHandler);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "com.intellij.compiler.JpsServerManager";
  }

  private volatile boolean myStartupFailed = false;

  private boolean ensureServerStarted() {
    if (myProcessHandler != null) {
      return true;
    }
    if (!myStartupFailed) {
      try {
        final int port = NetUtils.findAvailableSocketPort();
        final Process process = launchServer(port);
        final OSProcessHandler processHandler = new OSProcessHandler(process, null);
        processHandler.startNotify();
        myServerClient = new Client();

        if (myServerClient.connect(NetUtils.getLocalHostString(), port)) {

          final PathMacros pathVars = PathMacros.getInstance();
          final Map<String, String> data = new HashMap<String, String>();
          for (String name : pathVars.getAllMacroNames()) {
            final String path = pathVars.getValue(name);
            if (path != null) {
              data.put(name, FileUtil.toSystemIndependentName(path));
            }
          }

          final List<GlobalLibrary> globals = new ArrayList<GlobalLibrary>();

          fillSdks(globals);
          fillGlobalLibraries(globals);

          myServerClient.sendSetupRequest(data, globals);
        }

        myProcessHandler = processHandler;
        return true;
      }
      catch (Throwable e) {
        myStartupFailed = true;
        LOG.error(e); // todo
      }
    }
    return false;
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

  private static Process launchServer(int port) throws ExecutionException {
    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk));
    cmdLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5007");
    cmdLine.addParameter("-Xmx256m");
    cmdLine.addParameter("-classpath");

    final List<File> cp = Bootstrap.buildServerProcessClasspath();
    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter("org.jetbrains.jpsservice.Server");
    cmdLine.addParameter(Integer.toString(port));

    final File workDirectory = new File(PathManager.getSystemPath(), COMPILE_SERVER_SYSTEM_ROOT);
    workDirectory.mkdirs();
    cmdLine.setWorkDirectory(workDirectory);

    return cmdLine.createProcess();
  }

  private static void shutdownServer(final Client client, final OSProcessHandler processHandler) {
    try {
      if (client != null) {
        final Future future = client.sendShutdownRequest();
        if (future != null) {
          future.get(500, TimeUnit.MILLISECONDS);
        }
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

    private void sendReloadRequest(Project project) {
      final Client client = myServerClient;
      if (client != null) {
        try {
          client.sendProjectReloadRequest(Collections.singletonList(project.getLocation()));
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }
    }
  }
}
