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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.codeStyle.arrangement.node.ArrangementRepresentationAwareNode;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 10/30/12 12:10 PM
 */
public class ArrangementEditorTreeNode extends ArrangementTreeNode implements ArrangementRepresentationAwareNode {

  private static final int STEPS_NUMBER = 10;

  @NotNull private final JPanel           myRenderer;
  @NotNull private final DefaultTreeModel myTreeModel;
  
  private final int myAvailableWidth;
  
  private boolean myExpanding = true;
  private int     myStep      = 1;

  public ArrangementEditorTreeNode(@NotNull ArrangementRuleEditor editor, @NotNull DefaultTreeModel treeModel, int availableWidth) {
    super(null);
    myTreeModel = treeModel;
    myAvailableWidth = availableWidth;
    editor.applyAvailableWidth(availableWidth - ArrangementConstants.HORIZONTAL_PADDING);
    myRenderer = createRenderer(editor);
  }

  @NotNull
  private JPanel createRenderer(@NotNull ArrangementRuleEditor editor) {
    final Dimension size = editor.getPreferredSize();
    JPanel result = new JPanel() {

      @Override
      public Dimension getMinimumSize() {
        return getPreferredSize();
      }

      @Override
      public Dimension getMaximumSize() {
        return getPreferredSize();
      }

      @Override
      public Dimension getPreferredSize() {
        if ((myExpanding && myStep >= STEPS_NUMBER) || (!myExpanding && myStep <= 1)) {
          return new Dimension(Math.min(myAvailableWidth, size.width), size.height);
        }
        
        final int heightUnits = myExpanding ? myStep : STEPS_NUMBER - myStep;
        return new Dimension(Math.min(myAvailableWidth, size.width), size.height * heightUnits / STEPS_NUMBER);
      }

      @Override
      public void paint(Graphics g) {
        if (!isInFinalState()) {
          changeState();
        }
        super.paint(g);
      }
    };
    
    result.setLayout(new GridBagLayout());
    result.add(editor, new GridBag().fillCell().weightx(1).weighty(1).anchor(GridBagConstraints.NORTHWEST));
    result.setBorder(IdeBorderFactory.createBorder());
    return result;
  }
  
  public void setExpanding(boolean expanding) {
    myExpanding = expanding;
    myStep = 1;
  }

  public boolean isInFinalState() {
    return myStep >= STEPS_NUMBER;
  }
  
  public void changeState() {
    myStep++;
    myTreeModel.nodeChanged(this);
  }

  @NotNull
  @Override
  public JComponent getRenderer() {
    return myRenderer;
  }
}
