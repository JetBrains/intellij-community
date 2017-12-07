/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui.panel;

import javax.swing.*;
import java.awt.*;

public interface GridBagPanelBuilder extends PanelBuilder {
  int gridWidth();
  void addToPanel(JPanel panel, GridBagConstraints gc);
}
