/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import java.awt.*;

public interface ErrorStripeRenderer {
  String getTooltipMessage();
  void paint(Component c, Graphics g, Rectangle r);
}