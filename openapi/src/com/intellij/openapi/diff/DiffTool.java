/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;


public interface DiffTool {
  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  Object HINT_SHOW_MODAL_DIALOG = "showModalDialog";

  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  Object HINT_SHOW_FRAME = "showNotModalWindow";

  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  Object HINT_SHOW_NOT_MODAL_DIALOG = "showNotModalDialog";

  /**
   * Opens window to compare contents. Clients should call {@link #canShow(com.intellij.openapi.diff.DiffRequest)} first.
   */
  void show(DiffRequest request);

  /**
   * @return true if this tool can comare given contents
   */
  boolean canShow(DiffRequest request);
}
