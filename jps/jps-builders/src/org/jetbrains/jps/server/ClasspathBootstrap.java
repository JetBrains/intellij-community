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
package org.jetbrains.jps.server;

import com.google.protobuf.Message;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.PathManager;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.jgoodies.forms.layout.CellConstraints;
import net.n3.nanoxml.IXMLBuilder;
import org.codehaus.groovy.GroovyException;
import org.jboss.netty.util.Version;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.MacroExpander;
import org.jetbrains.jps.javac.JavacServer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.EmptyVisitor;

import javax.tools.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/12/11
 */
public class ClasspathBootstrap {
  public static final String JPS_RUNTIME_PATH = "rt/jps-incremental";

  private static class OptimizedFileManagerClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager";
    static final Class<StandardJavaFileManager> managerClass;
    static {
      Class<StandardJavaFileManager> aClass = null;
      try {
        aClass = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
      }
      catch (Throwable e) {
        aClass = null;
      }
      managerClass = aClass;
    }

    private OptimizedFileManagerClassHolder() {
    }
  }

  private static class OptimizedFileManager17ClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager17";
    static final Class<StandardJavaFileManager> managerClass;
    static {
      Class<StandardJavaFileManager> aClass = null;
      try {
        aClass = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
      }
      catch (Throwable e) {
        aClass = null;
      }
      managerClass = aClass;
    }

    private OptimizedFileManager17ClassHolder() {
    }
  }

  private ClasspathBootstrap() {
  }

  public static List<File> getCompileServerApplicationClasspath() {
    final Set<File> cp = new LinkedHashSet<File>();
    cp.add(getResourcePath(Server.class));
    for (String path : PathManager.getUtilClassPath()) { cp.add(new File(path)); } // util
    cp.add(getResourcePath(Message.class)); // protobuf
    cp.add(getResourcePath(Version.class)); // netty
    cp.add(getResourcePath(ClassWriter.class));  // asm
    cp.add(getResourcePath(EmptyVisitor.class));  // asm-commons
    cp.add(getResourcePath(MacroExpander.class));  // jps-model
    cp.add(getResourcePath(AlienFormFileException.class));  // forms-compiler
    cp.add(getResourcePath(GroovyException.class));  // groovy
    cp.add(getResourcePath(GridConstraints.class));  // forms-rt
    cp.add(getResourcePath(CellConstraints.class));  // jgoodies-forms
    cp.add(getResourcePath(NotNullVerifyingInstrumenter.class));  // not-null
    cp.add(getResourcePath(IXMLBuilder.class));  // nano-xml

    final Class<StandardJavaFileManager> optimizedFileManagerClass = getOptimizedFileManagerClass();
    if (optimizedFileManagerClass != null) {
      cp.add(getResourcePath(optimizedFileManagerClass));  // optimizedFileManager
    }

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourcePath(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable ignored) {
    }

    final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
    if (systemCompiler != null) {
      try {
        cp.add(getResourcePath(systemCompiler.getClass()));  // tools.jar
      }
      catch (Throwable ignored) {
      }
    }
    return new ArrayList<File>(cp);
  }

  public static List<File> getJavacServerClasspath() {
    final Set<File> cp = new LinkedHashSet<File>();
    cp.add(getResourcePath(JavacServer.class));
    for (String path : PathManager.getUtilClassPath()) { cp.add(new File(path)); } // util
    cp.add(getResourcePath(Message.class)); // protobuf
    cp.add(getResourcePath(Version.class)); // netty

    final Class<StandardJavaFileManager> optimizedFileManagerClass = getOptimizedFileManagerClass();
    if (optimizedFileManagerClass != null) {
      cp.add(getResourcePath(optimizedFileManagerClass));  // optimizedFileManager
    }

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourcePath(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable ignored) {
    }

    final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
    if (systemCompiler != null) {
      try {
        cp.add(getResourcePath(systemCompiler.getClass()));  // tools.jar
      }
      catch (Throwable ignored) {
      }
    }

    return new ArrayList<File>(cp);
  }

  @Nullable
  public static Class<StandardJavaFileManager> getOptimizedFileManagerClass() {
    final Class<StandardJavaFileManager> aClass = OptimizedFileManagerClassHolder.managerClass;
    if (aClass != null) {
      return aClass;
    }
    return OptimizedFileManager17ClassHolder.managerClass;
  }

  public static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }
}
