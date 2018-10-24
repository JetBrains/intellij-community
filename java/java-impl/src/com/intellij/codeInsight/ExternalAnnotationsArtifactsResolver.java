// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface ExternalAnnotationsArtifactsResolver {
  ExtensionPointName<ExternalAnnotationsArtifactsResolver> EP_NAME =
    new ExtensionPointName<>("com.intellij.externalAnnotationsArtifactsResolver");

  /**
   * Lookup and attach external annotations for given library synchronously.
   * @param library - a library to attach annotations roots to
   * @param mavenId - maven coordinates for annotations look-up in format "groupId:artifactId:version"
   * @return modified library, with attached annotations.
   */
  Library resolve(@NotNull Project project, @NotNull Library library, @Nullable String mavenId);

  /**
   * Lookup and attach external annotations for given library in background.
   * @param project - current project
   * @param library - a library to attach annotations roots to
   * @param mavenId - maven coordinates for annotations look-up in format "groupId:artifactId:version"
   */
  @NotNull
  Promise<Library> resolveAsync(@NotNull Project project, @NotNull Library library, @Nullable String mavenId);

}