// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.api.GlobalOptions;

@ApiStatus.Internal
public final class FallbackJdkSetupNotification extends CustomBuilderMessage{
  public FallbackJdkSetupNotification(@NlsSafe String notificationMessage) {
    super(
      GlobalOptions.JPS_SYSTEM_BUILDER_ID,
      GlobalOptions.JPS_FALLBACK_SDK_SETUP_MESSAGE_ID,
      notificationMessage
    );
  }
}
