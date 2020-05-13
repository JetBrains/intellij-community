// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion="2020.2")
public abstract class RefResolveService {
  public static final boolean ENABLED = false;

  public static RefResolveService getInstance(Project project) {
    return null;
  }
  public boolean isUpToDate() { return true; }
}
