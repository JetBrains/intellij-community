// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public interface JobProvider extends ModalContextProjectLocator {
  @NotNull Job getJob();
}