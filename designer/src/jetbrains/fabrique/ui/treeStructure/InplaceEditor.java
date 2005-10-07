/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeRenderer;
import jetbrains.fabrique.ide.util.LayoutHelper;
import jetbrains.fabrique.util.ui.components.panels.NonOpaquePanel;
import jetbrains.fabrique.util.StringUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellEditor;
import java.awt.*;
import java.awt.event.*;

/**
 * @author kir
 */
public abstract class InplaceEditor extends AbstractCellEditor implements TreeCellEditor, FocusListener {

  protected JTree myTree;
  protected JTextField myTextField;
  protected JPanel myComponent;
  protected JLabel myIconLabel;
  protected int myIconTextGap;

  public InplaceEditor() {
    myIconLabel = new JLabel();

    myTextField = new JTextField() {
      public Dimension getPreferredSize() {
        Dimension prefSize = super.getPreferredSize();
        Dimension minSize = new Dimension(getColumnWidth() * 3, prefSize.height - 1);
        prefSize.width = prefSize.width + 15;
        return LayoutHelper.computeNotSmallerDimension(prefSize, minSize);
      }
    };

    myTextField.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_ENTER:
          case KeyEvent.VK_TAB:
            if (stopCellEditing()) {
              myTree.stopEditing();
              myTree.requestFocusInWindow();
            }
            break;
          case KeyEvent.VK_ESCAPE:
            if (myTree instanceof SimpleTree) {
              ((SimpleTree)myTree).setEscapePressed();
            }

            myTree.cancelEditing();
            myTree.requestFocusInWindow();
            break;
        }
      }
    });

    myComponent = new NonOpaquePanel() {
      public void invalidate() {
        super.invalidate();
        Dimension size = getSize();
        size.width = myTextField.getPreferredSize().width + myIconLabel.getPreferredSize().width + myIconTextGap;
        myComponent.setSize(size);
        myTree.revalidate();
      }
    };
    myComponent.addFocusListener(new FocusAdapter() {
      public void focusGained(FocusEvent e) {
        myTextField.requestFocus();
        StringUtil.selectLastFragment(myTextField);
      }

      public void focusLost(FocusEvent e) {
        if (e.getOppositeComponent() != myTextField) {
          myTree.stopEditing();
        }
      }
    });
  }

  public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
    myTree = tree;

    NodeRenderer renderer = ((NodeRenderer) tree.getCellRenderer().getTreeCellRendererComponent(tree, value, isSelected, expanded, leaf, row, false));
    rebuildUI(renderer);

    myIconLabel.setIcon(renderer.getIcon());
    myTextField.setText(getText(((DefaultMutableTreeNode) value).getUserObject()));

    myTextField.addFocusListener(this);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        StringUtil.selectLastFragment(myTextField);
      }
    });

    return myComponent;
  }

  protected abstract String getText(Object userObject);

  public abstract boolean setTextIfValid(String text);

  public JTextField getTextField() {
    return myTextField;
  }

  public boolean stopCellEditing() {
    boolean stopped = setTextIfValid(myTextField.getText());
    if (stopped) {
      myTextField.removeFocusListener(this);
      return super.stopCellEditing();
    }
    return stopped;
  }

  public void cancelCellEditing() {
    myTextField.removeFocusListener(this);
    super.cancelCellEditing();
  }

  private void rebuildUI(NodeRenderer aRenderer) {
    myComponent.removeAll();
    myIconTextGap = aRenderer.getIconTextGap() - myTextField.getBorder().getBorderInsets(myTextField).left + 1;
    myComponent.setLayout(new BorderLayout(myIconTextGap, 0));
    myComponent.add(myTextField, BorderLayout.CENTER);
    myComponent.add(myIconLabel, BorderLayout.WEST);

    Insets padding = aRenderer.getIpad();
    myComponent.setBorder(BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right));
  }

  public void focusGained(FocusEvent e) {

  }

  public void focusLost(FocusEvent e) {
    if (myTree.isEditing() && !e.isTemporary()) {
      if (stopCellEditing()) {
        myTree.stopEditing();
      }
      else {
        myTree.cancelEditing();
      }
    }
  }
}
