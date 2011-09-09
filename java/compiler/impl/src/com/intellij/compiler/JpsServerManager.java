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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.ShutDownTracker;
import org.jetbrains.jpsservice.Client;
import org.jetbrains.jpsservice.Server;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class JpsServerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.JpsServerManager");

  public static void main(String[] args) {
    ensureServerStarted();
  }

  private static boolean ensureServerStarted() {
    return Holder.ourServerProcess != null;
  }

  private static class Holder {
    private static Client ourServerClient;
    private static ProcessHandler ourServerProcess;

    static {
      try {
        final int port = Server.DEFAULT_SERVER_PORT;
        final Process process = launchServer(port);
        final OSProcessHandler processHandler = new OSProcessHandler(process, null);
        processHandler.startNotify();
        final Client client = new Client();
        client.connect("localhost", port);

        ourServerProcess = processHandler;
        ourServerClient = client;

        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          @Override
          public void run() {
            shutdownServer(client, processHandler);
          }
        });
      }
      catch (Throwable e) {
        LOG.error(e); // todo
      }
    }
  }

  private static void shutdownServer(Client client, OSProcessHandler processHandler) {
    try {
      final Future future = client.sendShutdownRequest();
      if (future != null) {
        future.get(500, TimeUnit.MILLISECONDS);
      }
    }
    catch (Throwable ignored) {
      LOG.info(ignored);
    }
    finally {
      processHandler.destroyProcess();
    }
  }

  private static Process launchServer(int port) throws ExecutionException {
    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk));

    //final StringBuilder cp = new StringBuilder();
    //cp.append(getResourcePath(Server.class));
    //cp.append(File.pathSeparator).append(getResourcePath(com.google.protobuf.Message.class));
    //cp.append(File.pathSeparator).append(getResourcePath(org.jboss.netty.bootstrap.Bootstrap.class));
    //final String jpsJar = getResourcePath(Jps.class);
    //final File parentFile = new File(jpsJar).getParentFile();
    //final File[] files = parentFile.listFiles();
    //if (files != null) {
    //  for (File file : files) {
    //    final String name = file.getName();
    //    final boolean shouldAdd =
    //      name.endsWith("jar") &&
    //      (name.startsWith("ant") ||
    //       name.startsWith("jps") ||
    //       name.startsWith("asm") ||
    //       name.startsWith("gant")||
    //       name.startsWith("groovy") ||
    //       name.startsWith("javac2")
    //      );
    //    if (shouldAdd) {
    //      cp.append(File.pathSeparator).append(file.getPath());
    //    }
    //  }
    //}
    //cmdLine.addParameter("-classpath");
    //cmdLine.addParameter(cp.toString());

    cmdLine.addParameter("org.jetbrains.jpsservice.Server");
    cmdLine.addParameter(Integer.toString(port));
    return cmdLine.createProcess();
  }

  private static String getResourcePath(Class aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

}
