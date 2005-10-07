/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ide.dnd;

import jetbrains.fabrique.openapi.ide.dnd.DnDEvent;

import javax.swing.*;
import java.awt.*;

interface DropTargetHighlighter {

  void show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent);

  void vanish();

  int getMask();

}
