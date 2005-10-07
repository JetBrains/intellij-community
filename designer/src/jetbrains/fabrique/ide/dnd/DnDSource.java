/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ide.dnd;

import jetbrains.fabrique.openapi.ide.dnd.DnDAction;

import java.awt.*;

public interface DnDSource {

  boolean canStartDragging(DnDAction action, Point dragOrigin);

  DnDEventImpl createEventForNewDragging(DnDAction action, Point dragOrigin);

}
