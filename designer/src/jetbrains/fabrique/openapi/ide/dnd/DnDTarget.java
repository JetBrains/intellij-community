/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.openapi.ide.dnd;

import com.intellij.openapi.project.Project;

public interface DnDTarget extends DropTargetHighlightingType {

  /**
   * @return
   *    <code>true</code> - if this target is unable to handle the event and parent component should be asked to process it.
   *    <code>false</code> - if this target is unable to handle the event and parent component should NOT be asked to process it.
   */
  boolean update(DnDEvent aEvent);

  void drop(DnDEvent aEvent);

  void cleanUpOnLeave();

}
