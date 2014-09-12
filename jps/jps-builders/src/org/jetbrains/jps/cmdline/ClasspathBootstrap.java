/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.jgoodies.forms.layout.CellConstraints;
import io.netty.util.NetUtil;
import jsr166e.extra.SequenceLock;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.javac.ExternalJavacProcess;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsModelImpl;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import javax.tools.*;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/12/11
 */
public class ClasspathBootstrap {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.ClasspathBootstrap");

  private static class OptimizedFileManagerClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager";
    @Nullable
    static final Class<StandardJavaFileManager> managerClass;
    static final Method directoryCacheClearMethod;
    @Nullable
    static final String initError;
    static {
      Class<StandardJavaFileManager> aClass = null;
      Method cacheClearMethod = null;
      String error = null;
      try {
        @SuppressWarnings("unchecked")
        Class<StandardJavaFileManager> c = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
        aClass = c;
        try {
          cacheClearMethod = c.getMethod("fileGenerated", File.class);
          cacheClearMethod.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
          LOG.info(e);
        }
      }
      catch (Throwable ex) {
        aClass = null;
        error = ex.getClass().getName() + ": " + ex.getMessage();
      }
      managerClass = aClass;
      directoryCacheClearMethod = cacheClearMethod;
      initError = error;
    }

    private OptimizedFileManagerClassHolder() {
    }
  }

  private static class OptimizedFileManager17ClassHolder {
    static final String CLASS_NAME = "org.jetbrains.jps.javac.OptimizedFileManager17";
    @Nullable
    static final Class<StandardJavaFileManager> managerClass;
    static final Method directoryCacheClearMethod;
    @Nullable
    static final String initError;
    static {
      Class<StandardJavaFileManager> aClass;
      Method cacheClearMethod = null;
      String error = null;
      try {
        @SuppressWarnings("unchecked")
        Class<StandardJavaFileManager> c = (Class<StandardJavaFileManager>)Class.forName(CLASS_NAME);
        aClass = c;
        try {
          cacheClearMethod = c.getMethod("fileGenerated", File.class);
          cacheClearMethod.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
          LOG.info(e);
        }
      }
      catch (Throwable ex) {
        aClass = null;
        error = ex.getClass().getName() + ": " + ex.getMessage();
      }
      managerClass = aClass;
      directoryCacheClearMethod = cacheClearMethod;
      initError = error;
    }

    private OptimizedFileManager17ClassHolder() {
    }
  }

  private ClasspathBootstrap() {
  }

  public static List<String> getBuildProcessApplicationClasspath(boolean isLauncherUsed) {
    final Set<String> cp = ContainerUtil.newHashSet();

    cp.add(getResourcePath(BuildMain.class));

    cp.addAll(PathManager.getUtilClassPath()); // util
    cp.add(getResourcePath(Message.class)); // protobuf
    cp.add(getResourcePath(NetUtil.class)); // netty
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
    cp.add(getResourcePath(SequenceLock.class));  // jsr166
    cp.add(getJpsPluginSystemClassesPath().getAbsolutePath().replace('\\', '/'));
    
    //don't forget to update layoutCommunityJps() in layouts.gant accordingly

    if (!isLauncherUsed) {
      appendJavaCompilerClasspath(cp);
    }

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourcePath(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable ignored) {
    }

    return ContainerUtil.newArrayList(cp);
  }

  public static void appendJavaCompilerClasspath(Collection<String> cp) {
    final Class<StandardJavaFileManager> optimizedFileManagerClass = getOptimizedFileManagerClass();
    if (optimizedFileManagerClass != null) {
      cp.add(getResourcePath(optimizedFileManagerClass));  // optimizedFileManager
    }

    File file = EclipseCompilerTool.findEcjJarFile();
    if (file != null) {
      cp.add(file.getAbsolutePath());
    }
  }

  public static List<File> getExternalJavacProcessClasspath(String sdkHome, JavaCompilingTool compilingTool) {
    final Set<File> cp = new LinkedHashSet<File>();
    cp.add(getResourceFile(ExternalJavacProcess.class)); // self
    // util
    for (String path : PathManager.getUtilClassPath()) {
      cp.add(new File(path));
    }
    cp.add(getResourceFile(JpsModel.class));  // jps-model-api
    cp.add(getResourceFile(JpsModelImpl.class));  // jps-model-impl
    cp.add(getResourceFile(Message.class)); // protobuf
    cp.add(getResourceFile(NetUtil.class)); // netty
    cp.add(getJpsPluginSystemClassesPath());
    
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

    try {
      final String localJavaHome = FileUtil.toSystemIndependentName(SystemProperties.getJavaHome());
      // sdkHome is not the same as the sdk used to run this process
      final File candidate = new File(sdkHome, "lib/tools.jar");
      if (candidate.exists()) {
        cp.add(candidate);
      }
      else {
        // last resort
        final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
        if (systemCompiler != null) {
          final String localJarPath = FileUtil.toSystemIndependentName(getResourceFile(systemCompiler.getClass()).getPath());
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

    cp.addAll(compilingTool.getAdditionalClasspath());

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

  @Nullable
  public static Method getOptimizedFileManagerCacheClearMethod() {
    final Method method = OptimizedFileManagerClassHolder.directoryCacheClearMethod;
    if (method != null) {
      return method;
    }
    return OptimizedFileManager17ClassHolder.directoryCacheClearMethod;
  }

  @Nullable
  public static String getOptimizedFileManagerLoadError() {
    StringBuilder builder = new StringBuilder();
    if (OptimizedFileManagerClassHolder.initError != null) {
      builder.append(OptimizedFileManagerClassHolder.initError);
    }
    if (OptimizedFileManager17ClassHolder.initError != null) {
      if (builder.length() > 0) {
        builder.append("\n");
      }
      builder.append(OptimizedFileManager17ClassHolder.initError);
    }
    return builder.toString();
  }

  public static String getResourcePath(Class aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

  public static File getResourceFile(Class aClass) {
    return new File(getResourcePath(aClass));
  }
  
  private static File getJpsPluginSystemClassesPath() {
    File classesRoot = new File(getResourcePath(ClasspathBootstrap.class));
    if (classesRoot.isDirectory()) {
      //running from sources: load classes from .../out/production/jps-plugin-system
      return new File(classesRoot.getParentFile(), "jps-plugin-system");
    }
    else {
      return new File(classesRoot.getParentFile(), "rt/jps-plugin-system.jar");
    }
  }
  
}
