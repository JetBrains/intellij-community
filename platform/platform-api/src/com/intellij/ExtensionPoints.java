// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

public interface ExtensionPoints {
  /**
   * This extension point allows a plugin vendor to provide users with ability to report exceptions that happened in
   * their plugin code.
   * Extension declaration sample is as follows:
   * <pre>{@code
   *   <extensions xmlns="com.intellij">
   *     <errorHandler implementation="my.plugin.package.MyErrorHandler"/>
   *   </extensions>
   * }</pre>
   * The {@code my.plugin.package.MyErrorHandler} class must implement {@link ErrorReportSubmitter}.
   */
  @ApiStatus.Internal
  ExtensionPointName<ErrorReportSubmitter> ERROR_HANDLER_EP = ExtensionPointName.create("com.intellij.errorHandler");
}