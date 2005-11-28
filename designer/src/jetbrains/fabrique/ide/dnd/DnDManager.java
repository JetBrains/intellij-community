/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ide.dnd;

import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativeRectangle;
import jetbrains.fabrique.openapi.ide.dnd.DnDEvent;
import jetbrains.fabrique.openapi.ide.dnd.DnDTarget;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class DnDManager {
  public static DnDManager getInstance(Project project) {
    return project.getComponent(DnDManager.class);
  }

  public abstract void register(DnDSource source, JComponent component);

  public abstract void unregister(DnDSource source, JComponent component);

  public abstract void register(DnDTarget target, JComponent component);

  public abstract void unregister(DnDTarget target, JComponent component);

  abstract void showHighlighter(Component aComponent, int aType, DnDEvent aEvent);

  abstract void showHighlighter(RelativeRectangle rectangle, int aType, DnDEvent aEvent);

  abstract void showHighlighter(JLayeredPane layeredPane, RelativeRectangle rectangle, int aType, DnDEvent event);

  abstract void hideCurrentHighlighter();
}
