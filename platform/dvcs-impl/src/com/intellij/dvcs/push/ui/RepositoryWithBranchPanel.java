/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;

public class RepositoryWithBranchPanel extends NonOpaquePanel implements TreeCellRenderer {

  private final JBCheckBox myRepositoryCheckbox;
  private final TextFieldWithAutoCompletion myDestBranchTextField;
  private final JBLabel myLocalBranch;
  private final JLabel myArrowLabel;
  private final JLabel myRepositoryLabel;
  private final ColoredTreeCellRenderer myTextRenderer;

  public RepositoryWithBranchPanel(Project project, @NotNull String repoName,
                                   @NotNull String sourceName, String targetName, @NotNull Collection<String> targetVariants) {
    super();
    setLayout(new BorderLayout());
    myRepositoryCheckbox = new JBCheckBox();
    myRepositoryCheckbox.setOpaque(false);
    myRepositoryCheckbox.setEnabled(false);    //may be  return editor depends on focus
    myRepositoryLabel = new JLabel(repoName);
    myLocalBranch = new JBLabel(sourceName);
    myArrowLabel = new JLabel(" -> ");
    TextFieldWithAutoCompletionListProvider<String> provider =
      new TextFieldWithAutoCompletion.StringsCompletionProvider(targetVariants, null);
    myDestBranchTextField = new TextFieldWithAutoCompletion<String>(project, provider, true, targetName) {

      @Override
      public boolean shouldHaveBorder() {
        return false;
      }

      @Override
      protected void updateBorder(@NotNull final EditorEx editor) {
      }
    };
    myDestBranchTextField.setBorder(UIUtil.getTableFocusCellHighlightBorder());//getTextFieldBorder());
    myDestBranchTextField.setOneLineMode(true);
    myDestBranchTextField.setOpaque(true);
    myDestBranchTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myDestBranchTextField.selectAll();
      }
    });

    myTextRenderer = new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {

      }
    };
    myTextRenderer.setOpaque(false);
    layoutComponents();
  }

  private void layoutComponents() {
    add(myRepositoryCheckbox, BorderLayout.WEST);
    JPanel panel = new NonOpaquePanel(new BorderLayout());
    panel.add(myTextRenderer, BorderLayout.WEST);
    panel.add(myDestBranchTextField, BorderLayout.CENTER);
    add(panel, BorderLayout.CENTER);
  }

  @NotNull
  public String getRepositoryName() {
    return myRepositoryLabel.getText();
  }

  public String getSourceName() {
    return myLocalBranch.getText();
  }

  public String getArrow() {
    return myArrowLabel.getText();
  }

  public TextFieldWithAutoCompletion<String> getRemoteTextFiled() {
    return myDestBranchTextField;
  }

  @NotNull
  public String getRemoteTargetName() {
    return myDestBranchTextField.getText();
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    Rectangle bounds = tree.getPathBounds(tree.getPathForRow(row));
    invalidate();
    if (!(value instanceof SingleRepositoryNode)) {
      RepositoryNode node = (RepositoryNode)value;
      myRepositoryCheckbox.setSelected(node.isChecked());
      myRepositoryCheckbox.setVisible(true);
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      myTextRenderer.append(getRepositoryName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      myTextRenderer.appendFixedTextFragmentWidth(120);
    }
    else {
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      myRepositoryCheckbox.setVisible(false);
    }
    myTextRenderer.append(getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myTextRenderer.append(getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    if (bounds != null) {
      setPreferredSize(new Dimension(tree.getWidth() - bounds.x, bounds.height));
    }
    revalidate();
    return this;
  }
}


