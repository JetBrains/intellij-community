/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;

/**
 * Describes a single compiler message that is shown in compiler message view
 */
public interface CompilerMessage {
  CompilerMessage[] EMPTY_ARRAY = new CompilerMessage[0];

  /**
   * @return a category this message belongs to (error, warning, information)
   */
  CompilerMessageCategory getCategory();

  /**
   * @return message text
   */
  String getMessage();

  /**
   *
   * @return Navigatable object allowing to navigate to the message source
   */
  Navigatable getNavigatable();

  /**
   * @return the file to which the message applies
   */
  VirtualFile getVirtualFile();

  /**
   * @return location prefix prepended to message while exporting compilation results to text
   */
  String getExportTextPrefix();

  /**
   * @return location prefix prepended to message while rendering compilation results in UI
   */
  String getRenderTextPrefix();
}
