// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * A {@code VirtualFile} that needs to be included in a project scope.
 *
 * @author gregsh
 */
public interface ProjectAwareVirtualFile {
  boolean isInProject(@NotNull Project project);
}
