/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JetBrainsAnnotationsExternalLibraryResolver extends ExternalLibraryResolver {
  private static final ExternalLibraryDescriptor JAVA5 = new JetBrainsAnnotationsLibraryDescriptor("annotations-java5") {
    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      File annotationsJar = new File(PathManager.getLibPath(), "annotations.jar");
      if (annotationsJar.exists()) {
        return Collections.singletonList(FileUtil.toSystemIndependentName(annotationsJar.getAbsolutePath()));
      }
      return ContainerUtil.createMaybeSingletonList(getPathToJava5AnnotationsJarInDevelopmentMode());
    }
  };

  private static final ExternalLibraryDescriptor JAVA8 = new JetBrainsAnnotationsLibraryDescriptor("annotations") {
    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      File annotationsJar = new File(PathManager.getHomePath(), "redist/annotations-java8.jar");
      if (annotationsJar.exists()) {
        return Collections.singletonList(FileUtil.toSystemIndependentName(annotationsJar.getAbsolutePath()));
      }
      String annotationJava5JarPath = getPathToJava5AnnotationsJarInDevelopmentMode();
      if (annotationJava5JarPath == null) return Collections.emptyList();
      String annotationsJava8JarPath = StringUtil.replace(annotationJava5JarPath, "annotations-java5", "annotations");
      if (!new File(annotationsJava8JarPath).exists()) {
        return Collections.emptyList();
      }
      return Collections.singletonList(annotationsJava8JarPath);
    }
  };

  @Nullable
  private static String getPathToJava5AnnotationsJarInDevelopmentMode() {
    String annotationsRoot = PathManager.getJarPathForClass(Flow.class);
    if (annotationsRoot == null) return null;
    File annotationsJar = new File(annotationsRoot);
    if (annotationsJar.isFile()) return FileUtil.toSystemIndependentName(annotationsJar.getAbsolutePath());
    return null;
  }

  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (AnnotationUtil.isJetbrainsAnnotation(shortClassName)) {
      ExternalLibraryDescriptor libraryDescriptor = getAnnotationsLibraryDescriptor(contextModule);
      return new ExternalClassResolveResult("org.jetbrains.annotations." + shortClassName, libraryDescriptor);
    }
    return null;
  }

  @NotNull
  public static ExternalLibraryDescriptor getAnnotationsLibraryDescriptor(@NotNull Module contextModule) {
    boolean java8 = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(contextModule).isAtLeast(LanguageLevel.JDK_1_8);
    return java8 ? JAVA8 : JAVA5;
  }

  private static abstract class JetBrainsAnnotationsLibraryDescriptor extends ExternalLibraryDescriptor {
    public JetBrainsAnnotationsLibraryDescriptor(final String artifactId) {
      super("org.jetbrains", artifactId);
    }
  }
}
