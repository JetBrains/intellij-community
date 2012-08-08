/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.codeStyle.arrangement.renderer;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:06 AM
 */
public class ArrangementAtomRenderer extends JPanel implements ArrangementNodeRenderer<ArrangementSettingsAtomNode> {

  private static final int PADDING = 2;

  private final JPanel myRenderer = new JPanel(new BorderLayout());
  private final JLabel myLabel    = new JLabel();
  
  public ArrangementAtomRenderer() {
    JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setOpaque(false);
    labelPanel.add(myLabel, BorderLayout.CENTER);
    labelPanel.setBorder(IdeBorderFactory.createEmptyBorder(PADDING));

    JPanel borderPanel = new JPanel(new BorderLayout());
    borderPanel.add(labelPanel, BorderLayout.CENTER);
    borderPanel.setBorder(IdeBorderFactory.createRoundedBorder(myLabel.getFont().getSize()));
    borderPanel.setOpaque(false);

    myRenderer.setBorder(IdeBorderFactory.createEmptyBorder(PADDING));
    myRenderer.add(borderPanel, BorderLayout.CENTER);
  }

  @Override
  public JComponent getRendererComponent(@NotNull ArrangementSettingsAtomNode node) {
    // TODO den delegate to the dedicated method
    myLabel.setText(node.getValue().toString());
    return myRenderer;
  }
}
