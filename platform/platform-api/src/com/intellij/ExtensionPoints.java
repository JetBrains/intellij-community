// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated don't add new constants to this class and use the replacement for the existing one.
 */
@Deprecated
public interface ExtensionPoints {
  /**
   * @deprecated the constant was moved to IdeErrorsDialog 
   */
  @ApiStatus.Internal
  @Deprecated  
  ExtensionPointName<ErrorReportSubmitter> ERROR_HANDLER_EP = ExtensionPointName.create("com.intellij.errorHandler");
}