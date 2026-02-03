// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 *
 * @deprecated No need for this enum since {@link #WT} and {@link #EDT_WITH_IW} are deprecated.
 */
@Deprecated
public enum EdtReplacementThread {
  /**
   * Mark code which works with UI as "to run on EDT"
   */
  EDT,
  /**
   * Mark code which works with the IDE model as "to run on Write Thread"
   * @deprecated Any thread can be a write thread. Use {@link WriteIntentReadAction#run} instead.
   */
  @Deprecated
  WT,
  /**
   * Mark code which works both with UI and with the IDE model as "to run on EDT with IW lock acquired"
   * @deprecated Any
   */
  @Deprecated
  EDT_WITH_IW
}
