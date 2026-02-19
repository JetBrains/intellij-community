// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

/**
 * A service that provides recognized annotations for static analysis.
 * Methods return lists of fully-qualified annotation names from various static analysis packages with known meaning.
 */
@Service(Service.Level.APP)
public final class StaticAnalysisAnnotationManager {
  private static final String[] KNOWN_UNSTABLE_API_ANNOTATIONS = {
    "org.jetbrains.annotations.ApiStatus.ScheduledForRemoval",
    "org.jetbrains.annotations.ApiStatus.Experimental",
    "org.jetbrains.annotations.ApiStatus.Internal",
    "com.google.common.annotations.Beta",
    "io.reactivex.annotations.Beta",
    "io.reactivex.annotations.Experimental",
    "rx.annotations.Experimental",
    "rx.annotations.Beta",
    "org.apache.http.annotation.Beta",
    "org.gradle.api.Incubating"
  };

  private static final String[] KNOWN_CONTRACT_ANNOTATIONS = {
    "org.jetbrains.annotations.Contract",
    "org.springframework.lang.Contract"
  };

  public static StaticAnalysisAnnotationManager getInstance() {
    return ApplicationManager.getApplication().getService(StaticAnalysisAnnotationManager.class);
  }

  /**
   * @return array of annotations applicable to JVM methods, fields and classes that mark element as unstable, experimental,
   * or not intended for external use.
   */
  public @NotNull String @NotNull [] getKnownUnstableApiAnnotations() {
    return KNOWN_UNSTABLE_API_ANNOTATIONS;
  }

  /**
   * @return array of contract annotations that uses the contract syntax specified by {@link org.jetbrains.annotations.Contract}
   */
  public @NotNull String @NotNull [] getKnownContractAnnotations() {
    return KNOWN_CONTRACT_ANNOTATIONS;
  }
}
