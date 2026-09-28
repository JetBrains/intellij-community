// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

@ApiStatus.Internal
public interface MultipleBuildsView extends BuildProgressListener, Disposable {
  Map<BuildDescriptor, BuildView> getBuildsMap();
  BuildView getBuildView(Object buildId);
  boolean shouldConsume(Object buildId);
  boolean isPinned();
  void lockContent();
}