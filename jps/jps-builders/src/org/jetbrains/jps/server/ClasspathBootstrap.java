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

import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.jgoodies.forms.layout.CellConstraints;
import com.sun.jna.Pointer;
import com.sun.jna.platform.FileMonitor;
import gnu.trove.TIntHash;
import net.n3.nanoxml.IXMLBuilder;
import org.codehaus.groovy.GroovyException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.MacroExpander;
import org.objectweb.asm.ClassWriter;

import javax.tools.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/12/11
 */
public class ClasspathBootstrap {
  public static final String JPS_RUNTIME_PATH = "rt/jps-incremental";

  private static class OptimizedFileManagerClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.incremental.java.OptimizedFileManager";
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

  private ClasspathBootstrap() {
  }

  public static List<File> getApplicationClasspath() {
    final Set<File> cp = new LinkedHashSet<File>();
    cp.add(getResourcePath(Server.class));
    cp.add(getResourcePath(com.google.protobuf.Message.class)); // protobuf
    cp.add(getResourcePath(org.jboss.netty.bootstrap.Bootstrap.class)); // netty
    cp.add(getResourcePath(TIntHash.class));  // trove
    cp.add(getResourcePath(FileUtil.class));  // util module
    cp.add(getResourcePath(Pointer.class));  // jna.jar
    cp.add(getResourcePath(com.google.common.collect.MapMaker.class));  // guava
    cp.add(getResourcePath(FileMonitor.class));  // jna-utils.jar
    cp.add(getResourcePath(ClassWriter.class));  // asm
    cp.add(getResourcePath(org.objectweb.asm.commons.EmptyVisitor.class));  // asm-commons
    cp.add(getResourcePath(MacroExpander.class));  // jps-model
    cp.add(getResourcePath(AlienFormFileException.class));  // forms-compiler
    cp.add(getResourcePath(GroovyException.class));  // groovy
    cp.add(getResourcePath(org.jdom.input.SAXBuilder.class));  // jdom
    cp.add(getResourcePath(GridConstraints.class));  // forms-rt
    cp.add(getResourcePath(CellConstraints.class));  // jgoodies-forms
    cp.add(getResourcePath(NotNullVerifyingInstrumenter.class));  // not-null
    cp.add(getResourcePath(IXMLBuilder.class));  // nano-xml
    cp.add(getResourcePath(GroovyCompilerWrapper.class));  // groovy_rt

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

    //final File jpsRuntime = new File(jpsFacadeJar.getParentFile(), JPS_RUNTIME_PATH);
    //final File[] files = jpsRuntime.listFiles();
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
    //       name.startsWith("javac2") ||
    //       name.startsWith("util") ||
    //       name.startsWith("trove")
    //      );
    //    if (shouldAdd) {
    //      cp.add(file);
    //    }
    //  }
    //}
    return new ArrayList<File>(cp);
  }

  @Nullable
  public static Class<StandardJavaFileManager> getOptimizedFileManagerClass() {
    return OptimizedFileManagerClassHolder.managerClass;
  }

  public static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

}
