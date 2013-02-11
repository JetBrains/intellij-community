/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.cmdline;

import com.google.protobuf.Message;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.jgoodies.forms.layout.CellConstraints;
import net.n3.nanoxml.IXMLBuilder;
import org.jboss.netty.util.Version;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.javac.JavacServer;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsModelImpl;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import javax.tools.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/12/11
 */
public class ClasspathBootstrap {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.ClasspathBootstrap");

  private static class OptimizedFileManagerClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager";
    static final Class<StandardJavaFileManager> managerClass;
    static {
      Class<StandardJavaFileManager> aClass;
      try {
        @SuppressWarnings("unchecked") Class<StandardJavaFileManager> c = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
        aClass = c;
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
      Class<StandardJavaFileManager> aClass;
      try {
        @SuppressWarnings("unchecked") Class<StandardJavaFileManager> c = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
        aClass = c;
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

  public static List<String> getBuildProcessApplicationClasspath() {
    final Set<String> cp = ContainerUtil.newHashSet();

    cp.add(getResourcePath(BuildMain.class));

    cp.addAll(PathManager.getUtilClassPath()); // util
    cp.add(getResourcePath(Message.class)); // protobuf
    cp.add(getResourcePath(Version.class)); // netty
    cp.add(getResourcePath(ClassWriter.class));  // asm
    cp.add(getResourcePath(ClassVisitor.class));  // asm-commons
    cp.add(getResourcePath(JpsModel.class));  // jps-model-api
    cp.add(getResourcePath(JpsModelImpl.class));  // jps-model-impl
    cp.add(getResourcePath(JpsProjectLoader.class));  // jps-model-serialization
    cp.add(getResourcePath(AlienFormFileException.class));  // forms-compiler
    cp.add(getResourcePath(GridConstraints.class));  // forms-rt
    cp.add(getResourcePath(CellConstraints.class));  // jGoodies-forms
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

    for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) { // Eclipse compiler
      final String compilerResource = getResourcePath(javaCompiler.getClass());
      final String name = PathUtilRt.getFileName(compilerResource);
      if (name.startsWith("ecj-") && name.endsWith(".jar")) {
        cp.add(compilerResource);
      }
    }

    return ContainerUtil.newArrayList(cp);
  }

  public static List<File> getJavacServerClasspath(String sdkHome, boolean useEclipseCompiler) {
    final Set<File> cp = new LinkedHashSet<File>();
    cp.add(getResourceFile(JavacServer.class)); // self
    // util
    for (String path : PathManager.getUtilClassPath()) {
      cp.add(new File(path));
    }
    cp.add(getResourceFile(JpsModel.class));  // jps-model-api
    cp.add(getResourceFile(JpsModelImpl.class));  // jps-model-impl
    cp.add(getResourceFile(Message.class)); // protobuf
    cp.add(getResourceFile(Version.class)); // netty

    final Class<StandardJavaFileManager> optimizedFileManagerClass = getOptimizedFileManagerClass();
    if (optimizedFileManagerClass != null) {
      cp.add(getResourceFile(optimizedFileManagerClass));  // optimizedFileManager, if applicable
    }

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourceFile(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable th) {
      LOG.info(th);
    }

    final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
    if (systemCompiler != null) {
      try {
        final String localJarPath = FileUtil.toSystemIndependentName(getResourceFile(systemCompiler.getClass()).getPath());
        final String localJavaHome = FileUtil.toSystemIndependentName(SystemProperties.getJavaHome());
        if (FileUtil.pathsEqual(localJavaHome, FileUtil.toSystemIndependentName(sdkHome))) {
          cp.add(new File(localJarPath));
        }
        else {
          // sdkHome is not the same as the sdk used to run this process
          final File candidate = new File(sdkHome, "lib/tools.jar");
          if (candidate.exists()) {
            cp.add(candidate);
          }
          else {
            // last resort
            String relPath = FileUtil.getRelativePath(localJavaHome, localJarPath, '/');
            if (relPath != null) {
              if (relPath.contains("..")) {
                relPath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(new File(localJavaHome).getParent()), localJarPath, '/');
              }
              if (relPath != null) {
                final File targetFile = new File(sdkHome, relPath);
                cp.add(targetFile);  // tools.jar
              }
            }
          }
        }
      }
      catch (Throwable th) {
        LOG.info(th);
      }
    }

    if (useEclipseCompiler) {
      // eclipse compiler
      for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) { // Eclipse compiler
        final File compilerResource = getResourceFile(javaCompiler.getClass());
        final String name = compilerResource.getName();
        if (name.startsWith("ecj-") && name.endsWith(".jar")) {
          cp.add(compilerResource);
        }
      }
    }

    final Class<JavaSourceTransformer> transformerClass = JavaSourceTransformer.class;
    final ServiceLoader<JavaSourceTransformer> loader = ServiceLoader.load(transformerClass, transformerClass.getClassLoader());
    for (JavaSourceTransformer t : loader) {
      cp.add(getResourceFile(t.getClass()));
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

  public static String getResourcePath(Class aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

  public static File getResourceFile(Class aClass) {
    return new File(getResourcePath(aClass));
  }
}
