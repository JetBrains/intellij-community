// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.util.SystemProperties;
import com.sun.jdi.connect.AttachingConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author egor
 */
public class SAPidRemoteConnection extends RemoteConnection {
  @NonNls private static final String SA_PID_ATTACHING_CONNECTOR_NAME = "sun.jvm.hotspot.jdi.SAPIDAttachingConnector";
  private static ClassLoader BASE_SA_JDI_CLASS_LOADER;

  private final String myPid;
  private final String mySAJarPath;

  public SAPidRemoteConnection(String pid, String saJarPath) {
    super(false, null, null, false);
    myPid = pid;
    mySAJarPath = saJarPath;
  }

  public String getPid() {
    return myPid;
  }

  public AttachingConnector getConnector() throws ExecutionException {
    try {
      Class<?> connectorClass = Class.forName(SA_PID_ATTACHING_CONNECTOR_NAME,
                                              true,
                                              new JBSAJDIClassLoader(getBaseSAJDIClassLoader(mySAJarPath), mySAJarPath));
      return (AttachingConnector)connectorClass.newInstance();
    }
    catch (Exception e) {
      throw new ExecutionException("Unable to create SAPIDAttachingConnector", e);
    }
  }

  public static boolean isSAPidAttachAvailable() {
    return true;
    //return getBaseSAJDIClassLoader() != null;
  }

  @NotNull
  private static synchronized ClassLoader getBaseSAJDIClassLoader(String saJarPath) {
    if (BASE_SA_JDI_CLASS_LOADER == null) {
      File saJdiJar = new File(SystemProperties.getJavaHome(), "../lib/sa-jdi.jar");
      if (saJdiJar.exists()) {
        try {
          saJarPath = saJdiJar.getCanonicalPath();
        }
        catch (IOException ignored) {
        }
      }
      BASE_SA_JDI_CLASS_LOADER = new JBSAJDIClassLoader(SAPidRemoteConnection.class.getClassLoader(), saJarPath);
    }
    return BASE_SA_JDI_CLASS_LOADER;
  }

  private static class JBSAJDIClassLoader extends URLClassLoader {
    JBSAJDIClassLoader(ClassLoader parent, String classPath) {
      super(new URL[0], parent);
      try {
        addURL(new File(classPath).toURI().toURL());
      }
      catch (MalformedURLException mue) {
        throw new RuntimeException(mue);
      }
    }

    @Override
    public synchronized Class loadClass(String name) throws ClassNotFoundException {
      Class c = findLoadedClass(name);
      if (c == null) {
        /* to avoid loading same native library multiple times
         *  from multiple class loaders (which results in getting a
         *  UnsatisifiedLinkageError from System.loadLibrary).
         */

        if (name.startsWith("sun.jvm.hotspot.") && !name.startsWith("sun.jvm.hotspot.debugger.")) {
          return findClass(name);
        }
        return super.loadClass(name);
      }
      return c;
    }

    @Nullable
    @Override
    public URL getResource(String name) {
      if ("sa.properties".equals(name)) {
        URL resource = findResource(name);
        if (resource != null) {
          return resource;
        }
      }
      return super.getResource(name);
    }
  }
}
