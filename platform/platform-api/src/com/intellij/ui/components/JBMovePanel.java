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
package com.intellij.ui.components;

import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public class JBMovePanel extends JBPanel {
  private final JButton myMoveLeftButton;
  private final JButton myMoveAllLeftButton;
  private final JButton myMoveRightButton;
  private final JButton myMoveAllRightButton;
  @NotNull protected final JList myLeft;
  @NotNull protected final JList myRight;

  protected enum ButtonType {MOVE_LEFT, MOVE_RIGHT, MOVE_ALL_LEFT, MOVE_ALL_RIGHT}

  public JBMovePanel(@NotNull JList left, @NotNull JList right) {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    assertModelIsEditable(left);
    assertModelIsEditable(right);
    myLeft = left;
    myRight = right;
    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    buttonsPanel.add(myMoveLeftButton = createButton(ButtonType.MOVE_RIGHT));
    buttonsPanel.add(myMoveRightButton = createButton(ButtonType.MOVE_LEFT));
    buttonsPanel.add(myMoveAllLeftButton = createButton(ButtonType.MOVE_ALL_RIGHT));
    buttonsPanel.add(myMoveAllRightButton = createButton(ButtonType.MOVE_ALL_LEFT));
    final Dimension size = new Dimension(getButtonsWidth(), buttonsPanel.getPreferredSize().height);
    buttonsPanel.setPreferredSize(size);
    buttonsPanel.setMaximumSize(size);

    add(new JBScrollPane(left));
    add(buttonsPanel);
    add(new JBScrollPane(right));
  }

  private static void assertModelIsEditable(JList list) {
    assert list.getModel() instanceof DefaultListModel: String.format("List model should extends %s interface", DefaultListModel.class.getName());
  }

  protected JButton createButton(@NotNull final ButtonType type) {
    final int size = 20;
    final JButton button = new JButton(getButtonText(type)) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(getButtonsWidth(), size);
      }

      @Override
      public Dimension getMinimumSize() {
        return getPreferredSize();
      }

      @Override
      public Dimension getMaximumSize() {
        return getPreferredSize();
      }
    };

    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        switch (type) {
          case MOVE_LEFT:
            doMoveLeft();
            break;
          case MOVE_RIGHT:
            doMoveRight();
            break;
          case MOVE_ALL_LEFT:
            doMoveAllLeft();
            break;
          case MOVE_ALL_RIGHT:
            doMoveAllRight();
            break;
        }
      }
    });
    return button;
  }

  protected int getButtonsWidth() {
    return 150;
  }

  protected void doMoveRight() {
    move(myRight, myLeft);
  }

  protected void doMoveLeft() {
    move(myLeft, myRight);
  }

  protected void doMoveAllLeft() {
    moveAll(myLeft, myRight);
  }

  protected void doMoveAllRight() {
    moveAll(myRight, myLeft);
  }

  private static void move(JList to, JList from) {
    final Object[] values = from.getSelectedValues();
    final int[] indices = from.getSelectedIndices();
    for (int i = indices.length - 1; i >=0; i--) {
      ((DefaultListModel)from.getModel()).remove(i);
    }
    for (Object value : values) {
      ((DefaultListModel)to.getModel()).addElement(value);
    }
  }

  private static void moveAll(JList to, JList from) {
    final DefaultListModel fromModel = (DefaultListModel)from.getModel();
    final DefaultListModel toModel = (DefaultListModel)to.getModel();
    while (fromModel.getSize() > 0) {
      toModel.addElement(fromModel.remove(0));
    }
  }

  protected String getButtonText(@NotNull ButtonType type) {
    switch (type) {
      case MOVE_LEFT: return "Move <<";
      case MOVE_RIGHT: return "Move >>";
      case MOVE_ALL_LEFT: return "Move All <<";
      case MOVE_ALL_RIGHT: return "Move All >>";
    }
    throw new IllegalArgumentException("Unknown button type: " + type);
  }

  public static void main(String[] args) {
    final JBMovePanel panel = new JBMovePanel(new JBList("asdas", "weqrwe", "ads12312", "aZSD23"),
                                              new JBList("123412", "as2341", "aaaaaaaaaaa", "ZZZZZZZZZZ", "12"));
    final JFrame test = new JFrame("Test");
    test.setContentPane(panel);
    test.setSize(500, 500);
    test.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    test.setVisible(true);
  }
}
