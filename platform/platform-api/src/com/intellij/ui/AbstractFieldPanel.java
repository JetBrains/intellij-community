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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author Alexey Kudravtsev
 */
public abstract class AbstractFieldPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.AbstractFieldPanel");
  private final JComponent myComponent;
  private Runnable myChangeListener;
  protected ArrayList<JButton> myButtons = new ArrayList<>(1);
  protected JLabel myLabel;
  private ActionListener myBrowseButtonActionListener;
  private String myViewerDialogTitle;
  private String myLabelText;
  private TextFieldWithBrowseButton.MyDoClickAction myDoClickAction;

  public AbstractFieldPanel(JComponent component) {
    this(component, null, null, null, null);
  }

  public AbstractFieldPanel(JComponent component,
                            String labelText,
                            final String viewerDialogTitle,
                            ActionListener browseButtonActionListener,
                            Runnable changeListener) {
    myComponent = component;
    setChangeListener(changeListener);
    setLabelText(labelText);
    setBrowseButtonActionListener(browseButtonActionListener);
    myViewerDialogTitle = viewerDialogTitle;
  }


  public abstract String getText();

  public abstract void setText(String text);

  public void setEnabled(boolean enabled) {
    getComponent().setEnabled(enabled);
    if (myLabel != null) {
      myLabel.setEnabled(enabled);
    }
    for (JButton button: myButtons) {
      button.setEnabled(enabled);
    }
  }

  public boolean isEnabled() {
    return myComponent != null && myComponent.isEnabled();
  }

  protected TextFieldWithBrowseButton.MyDoClickAction getDoClickAction() { return myDoClickAction; }

  public final JComponent getComponent() {
    return myComponent;
  }

  public final JLabel getFieldLabel() {
    if (myLabel == null){
      myLabel = new JLabel(myLabelText);
      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 5, 0), 0, 0));
      myLabel.setLabelFor(getComponent());      
    }
    return myLabel;
  }

  public final Runnable getChangeListener() {
    return myChangeListener;
  }

  public final void setChangeListener(Runnable runnable) {
    myChangeListener = runnable;
  }

  public void createComponent() {
    removeAll();
    setLayout(new GridBagLayout());

    if (myLabelText != null) {
      myLabel = new JLabel(myLabelText);
      this.add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 5, 0), 0, 0));
      myLabel.setLabelFor(myComponent);
    }

    this.add(myComponent, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    if (myBrowseButtonActionListener != null) {
      FixedSizeButton browseButton = new FixedSizeButton(getComponent());
      myDoClickAction = new TextFieldWithBrowseButton.MyDoClickAction(browseButton);
      browseButton.setFocusable(false);
      browseButton.addActionListener(myBrowseButtonActionListener);
      myButtons.add(browseButton);
      this.add(browseButton, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0));
    }
    if (myViewerDialogTitle != null) {
      final FixedSizeButton showViewerButton = new FixedSizeButton(getComponent());
      if (myBrowseButtonActionListener == null) {
        LOG.assertTrue(myDoClickAction == null);
        myDoClickAction = new TextFieldWithBrowseButton.MyDoClickAction(showViewerButton);
      }
      showViewerButton.setFocusable(false);
      showViewerButton.setIcon(PlatformIcons.OPEN_EDIT_DIALOG_ICON);
      showViewerButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Viewer viewer = new Viewer();
          viewer.setTitle(myViewerDialogTitle);
          viewer.show();
        }
      });
      myButtons.add(showViewerButton);
      this.add(showViewerButton, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    }
  }

  public void setBrowseButtonActionListener(ActionListener browseButtonActionListener) {
    myBrowseButtonActionListener = browseButtonActionListener;
  }

  public void setViewerDialogTitle(String viewerDialogTitle) {
    myViewerDialogTitle = viewerDialogTitle;
  }

  public void setLabelText(String labelText) {
    myLabelText = labelText;
  }

  public void setDisplayedMnemonic(char c) {
    getFieldLabel().setDisplayedMnemonic(c);
  }

  public void setDisplayedMnemonicIndex(int i) {
    getFieldLabel().setDisplayedMnemonicIndex(i);
  }

  protected class Viewer extends DialogWrapper {
    protected JTextArea myTextArea;

    public Viewer() {
      super(getComponent(), true);
      init();
    }

    @NotNull
    protected Action[] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    public JComponent getPreferredFocusedComponent() {
      return myTextArea;
    }

    protected void doOKAction() {
      setText(myTextArea.getText());
      super.doOKAction();
    }

    protected JComponent createCenterPanel() {
      myTextArea = new JTextArea(10, 50);
      myTextArea.setText(getText());
      myTextArea.setWrapStyleWord(true);
      myTextArea.setLineWrap(true);
      myTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
        public void textChanged(DocumentEvent event) {
          if (myChangeListener != null) {
            myChangeListener.run();
          }
        }
      });

      new AnAction() {
        public void actionPerformed(AnActionEvent e) {
          doOKAction();
        }
      }.registerCustomShortcutSet(CommonShortcuts.ENTER, myTextArea);

      return ScrollPaneFactory.createScrollPane(myTextArea);
    }
  }
}
