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

import com.google.common.base.Predicate;
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
import net.n3.nanoxml.IXMLBuilder;
import org.apache.http.HttpConnection;
import org.apache.http.client.HttpClient;
import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.javac.ExternalJavacProcess;
import org.jetbrains.jps.javac.OptimizedFileManagerUtil;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsModelImpl;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import javax.tools.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class ClasspathBootstrap {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.ClasspathBootstrap");

  private ClasspathBootstrap() {
  }

  public static List<String> getBuildProcessApplicationClasspath() {
    final Set<String> cp = ContainerUtil.newHashSet();

    cp.add(getResourcePath(BuildMain.class));
    cp.add(getResourcePath(ExternalJavacProcess.class));  // jps-builders-6 part

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
    cp.addAll(getInstrumentationUtilRoots());
    cp.add(getResourcePath(IXMLBuilder.class));  // nano-xml

    // aether-based repository libraries support
    cp.add(getResourcePath(ArtifactRepositoryManager.class));  // aether-dependency-resolver
    final String aetherPath = getResourcePath(Artifact.class); // aether-1.1.0-all.jar
    cp.add(aetherPath);
    cp.add(FileUtil.toSystemIndependentName(new File(new File(aetherPath).getParentFile(), "maven-aether-provider-3.3.9-all.jar").getAbsolutePath()));
    cp.add(getResourcePath(Predicate.class));  // guava
    cp.add(getResourcePath(HttpClient.class));  // httpclient
    cp.add(getResourcePath(HttpConnection.class));  // httpcore
    //noinspection UnnecessaryFullyQualifiedName
    cp.add(getResourcePath(org.apache.commons.codec.binary.Base64.class));  // commons-codec
    //noinspection UnnecessaryFullyQualifiedName
    cp.add(getResourcePath(org.apache.commons.logging.LogFactory.class));  // commons-logging
    //noinspection UnnecessaryFullyQualifiedName
    cp.add(getResourcePath(org.slf4j.Marker.class));  // slf4j

    cp.addAll(getJavac8RefScannerClasspath());
    //don't forget to update CommunityStandaloneJpsBuilder.layoutJps accordingly

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourcePath(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable ignored) {
    }

    return ContainerUtil.newArrayList(cp);
  }

  public static void appendJavaCompilerClasspath(Collection<String> cp, boolean includeEcj) {
    final Class<StandardJavaFileManager> optimizedFileManagerClass = OptimizedFileManagerUtil.getManagerClass();
    if (optimizedFileManagerClass != null) {
      cp.add(getResourcePath(optimizedFileManagerClass));  // optimizedFileManager
    }

    if (includeEcj) {
      File file = EclipseCompilerTool.findEcjJarFile();
      if (file != null) {
        cp.add(file.getAbsolutePath());
      }
    }
  }

  public static List<File> getExternalJavacProcessClasspath(String sdkHome, JavaCompilingTool compilingTool) {
    final Set<File> cp = new LinkedHashSet<>();
    cp.add(getResourceFile(ExternalJavacProcess.class)); // self
    // util
    for (String path : PathManager.getUtilClassPath()) {
      cp.add(new File(path));
    }
    cp.add(getResourceFile(Message.class)); // protobuf
    cp.add(getResourceFile(NetUtil.class)); // netty

    final Class<StandardJavaFileManager> optimizedFileManagerClass = OptimizedFileManagerUtil.getManagerClass();
    if (optimizedFileManagerClass != null) {
      cp.add(getResourceFile(optimizedFileManagerClass));  // optimizedFileManager, if applicable
    }
    else {
      // last resort
      final File f = new File(PathManager.getLibPath(), "optimizedFileManager.jar");
      if (f.exists()) {
        cp.add(f);
      }
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
        Class compilerClass;
        if (systemCompiler != null) {
          compilerClass = systemCompiler.getClass();
        }
        else {
          compilerClass = Class.forName("com.sun.tools.javac.api.JavacTool", false, ClasspathBootstrap.class.getClassLoader());
        }
        String localJarPath = FileUtil.toSystemIndependentName(getResourceFile(compilerClass).getPath());
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
    catch (Throwable th) {
      LOG.info(th);
    }

    cp.addAll(compilingTool.getAdditionalClasspath());

    for (JavaSourceTransformer t : JavaSourceTransformer.getTransformers()) {
      cp.add(getResourceFile(t.getClass()));
    }

    return new ArrayList<>(cp);
  }

  public static String getResourcePath(Class aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

  public static File getResourceFile(Class aClass) {
    return new File(getResourcePath(aClass));
  }

  private static List<String> getInstrumentationUtilRoots() {
    String instrumentationUtilPath = getResourcePath(NotNullVerifyingInstrumenter.class);
    File instrumentationUtil = new File(instrumentationUtilPath);
    if (instrumentationUtil.isDirectory()) {
      //running from sources: load classes from .../out/production/instrumentation-util-8
      return Arrays.asList(instrumentationUtilPath, new File(instrumentationUtil.getParentFile(), "instrumentation-util-8").getAbsolutePath());
    }
    else {
      //running from jars: instrumentation-util-8 is located in the same jar
      return Collections.singletonList(instrumentationUtilPath);
    }
  }

  private static List<String> getJavac8RefScannerClasspath() {
    String instrumentationPath = getResourcePath(NotNullVerifyingInstrumenter.class);
    File instrumentationUtil = new File(instrumentationPath);
    if (instrumentationUtil.isDirectory()) {
      //running from sources: load classes from .../out/production/javac-ref-scanner-8
      return Collections.singletonList(new File(instrumentationUtil.getParentFile(), "javac-ref-scanner-8").getAbsolutePath());
    }
    else {
      return Collections.singletonList(instrumentationPath);
    }
  }
}
