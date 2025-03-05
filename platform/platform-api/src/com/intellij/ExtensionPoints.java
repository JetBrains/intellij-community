// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

/** @deprecated don't add new constants to this class and use the replacement for the existing one */
@Deprecated(forRemoval = true)
public interface ExtensionPoints {
  /** @deprecated plugins should register their own {@link ErrorReportSubmitter} and/or use services */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  ExtensionPointName<ErrorReportSubmitter> ERROR_HANDLER_EP = ExtensionPointName.create("com.intellij.errorHandler");
}
