package com.intellij.ui.border;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.String;

/**
 * User: Evgeny.Zakrevsky (evgeny.zakrevsky@jetbrains.com)
 */

public class IdeaBorderFactory {
  private IdeaBorderFactory() {
  }

  public static TitledBorder createTitledBorder(Border border,
                                String title,
                                int titleJustification,
                                int titlePosition,
                                Font titleFont,
                                Color titleColor) {
    return BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, -4, 0, 0, Color.BLACK), title);
  }
}
