// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task;

import com.intellij.openapi.roots.ProjectModelBuildableElement;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface ProjectModelBuildTask<T extends ProjectModelBuildableElement> extends BuildTask {
  T getBuildableElement();
}
