// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

/**
 * The new threading model revokes the IDE model access from EDT. Hence, the code which executed previously on EDT
 * falls into one of three categories:
 * <ol>
 *   <li>Code working with Swing and other UI</li>
 *   <li>Code working with the model (read, write)</li>
 *   <li>Code doing both things during one Swing event</li>
 * </ol>
 *
 * This enum represents this classification and aids in preserving compatibility with the code designed with
 * the old threading model in mind.
 * 
 * @see com.intellij.openapi.application.ex.ApplicationUtil#invokeLaterSomewhere
 * @see com.intellij.openapi.application.ex.ApplicationUtil#invokeAndWaitSomewhere
 */
public enum EdtReplacementThread {
  /**
   * Mark code which works with UI as "to run on EDT"
   */
  EDT,
  /**
   * Mark code which works with the IDE model as "to run on Write Thread"
   */
  WT,
  /**
   * Mark code which works both with UI and with the IDE model as "to run on EDT with IW lock acquired"
   */
  EDT_WITH_IW
}
