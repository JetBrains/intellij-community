// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus.Internal;

public interface ExternalChangeAction extends Runnable, IgnorePsiEventsMarker {

  @Internal
  interface ExternalDocumentChange extends ExternalChangeAction {
  }
}
