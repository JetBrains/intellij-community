// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

/**
 * @deprecated Use {@link SaveAndSyncHandler} directly, do not use implementation class.
 */
@Deprecated(forRemoval = true)
public final class SaveAndSyncHandlerImpl {
  private SaveAndSyncHandlerImpl() {
  }

  /**
   * @deprecated Use {@link SaveAndSyncHandler} directly, do not use implementation class.
   */
  @Deprecated
  public static SaveAndSyncHandler getInstance() {
    return SaveAndSyncHandler.getInstance();
  }
}
