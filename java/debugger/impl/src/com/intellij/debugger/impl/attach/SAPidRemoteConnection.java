// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.util.SystemProperties;
import com.sun.jdi.connect.AttachingConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SAPidRemoteConnection extends PidRemoteConnection {
  private static ClassLoader BASE_SA_JDI_CLASS_LOADER;

  private final String mySAJarPath;

  public SAPidRemoteConnection(String pid, String saJarPath) {
    super(pid);
    mySAJarPath = saJarPath;
  }

  @Override
  public AttachingConnector getConnector(DebugProcessImpl debugProcess) throws ExecutionException {
    try {
      Path saJarPath = Paths.get(mySAJarPath);
      Class<?> connectorClass = Class.forName("sun.jvm.hotspot.jdi.SAPIDAttachingConnector",
                                              true,
                                              new JBSAJDIClassLoader(getBaseSAJDIClassLoader(saJarPath), saJarPath));
      return (AttachingConnector)connectorClass.getDeclaredConstructor().newInstance();
    }
    catch (Exception e) {
      throw new ExecutionException(JavaDebuggerBundle.message("error.unable.to.create.sapidattachingconnector"), e);
    }
  }

  @NotNull
  private static synchronized ClassLoader getBaseSAJDIClassLoader(Path fallback) {
    if (BASE_SA_JDI_CLASS_LOADER == null) {
      Path saJdiJar = Paths.get(SystemProperties.getJavaHome(), "lib/sa-jdi.jar");
      if (!Files.exists(saJdiJar)) {
        saJdiJar = Paths.get(SystemProperties.getJavaHome(), "../lib/sa-jdi.jar"); // MacOS
        if (!Files.exists(saJdiJar)) {
          saJdiJar = fallback;
        }
      }
      BASE_SA_JDI_CLASS_LOADER = new JBSAJDIClassLoader(SAPidRemoteConnection.class.getClassLoader(), saJdiJar);
    }
    return BASE_SA_JDI_CLASS_LOADER;
  }

  private static class JBSAJDIClassLoader extends URLClassLoader {
    JBSAJDIClassLoader(ClassLoader parent, Path classPath) {
      super(new URL[0], parent);
      try {
        addURL(classPath.toUri().toURL());
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
