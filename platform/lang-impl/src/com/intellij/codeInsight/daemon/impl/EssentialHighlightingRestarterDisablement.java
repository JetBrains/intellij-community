// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/**
 * Disables essential highlighting restart on save on a project basis
 */
public interface EssentialHighlightingRestarterDisablement {

  ExtensionPointName<EssentialHighlightingRestarterDisablement> EP_NAME = ExtensionPointName.create("com.intellij.daemon.essentialHighlightingRestarterDisablement");

  boolean shouldBeDisabledForProject(Project project);

  static boolean isEssentialHighlightingRestarterDisabledForProject(Project project) {
    return EP_NAME.getExtensionList().stream()
      .map(extension -> extension.shouldBeDisabledForProject(project))
      .reduce(Boolean::logicalOr)
      .orElse(false);
  }
}
