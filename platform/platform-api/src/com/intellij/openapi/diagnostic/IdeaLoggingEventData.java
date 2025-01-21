// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

@ApiStatus.Internal
public interface IdeaLoggingEventData {
  @Unmodifiable @NotNull List<Attachment> getIncludedAttachments();
}
