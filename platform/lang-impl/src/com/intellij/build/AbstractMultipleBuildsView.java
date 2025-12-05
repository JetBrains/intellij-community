// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

@ApiStatus.Internal
public abstract class AbstractMultipleBuildsView implements BuildProgressListener, Disposable {
  abstract Map<BuildDescriptor, BuildView> getBuildsMap();
  abstract BuildView getBuildView(Object buildId);
  abstract boolean shouldConsume(Object buildId);
  abstract boolean isPinned();
  abstract void lockContent();
}