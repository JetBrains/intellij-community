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
package com.intellij.application.options.codeStyle.arrangement.node;

import com.intellij.application.options.codeStyle.arrangement.ArrangementTreeNode;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 9/27/12 3:10 PM
 */
public class ArrangementCheckBoxNode extends ArrangementTreeNode implements  ArrangementRepresentationAwareNode {

  @NotNull private final JPanel myRenderer = new JPanel(new GridBagLayout());

  public ArrangementCheckBoxNode(@NotNull String text) {
    super(null);
    JBCheckBox checkBox = new JBCheckBox(text);
    checkBox.setBackground(UIUtil.getTreeBackground());
    myRenderer.add(checkBox, new GridBag().anchor(GridBagConstraints.WEST));
  }

  @NotNull
  @Override
  public JComponent getRenderer() {
    return myRenderer;
  }
}
