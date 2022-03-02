// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public abstract class AbstractFieldPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(AbstractFieldPanel.class);
  private final JComponent myComponent;
  private Runnable myChangeListener;
  protected ArrayList<JButton> myButtons = new ArrayList<>(1);
  protected JLabel myLabel;
  private ActionListener myBrowseButtonActionListener;
  private final @NlsContexts.DialogTitle String myViewerDialogTitle;
  private @NlsContexts.Label String myLabelText;
  private TextFieldWithBrowseButton.MyDoClickAction myDoClickAction;

  public AbstractFieldPanel(JComponent component) {
    this(component, null, null, null, null);
  }

  public AbstractFieldPanel(JComponent component,
                            @NlsContexts.Label String labelText,
                            @NlsContexts.DialogTitle String viewerDialogTitle,
                            ActionListener browseButtonActionListener,
                            Runnable changeListener) {
    myComponent = component;
    setChangeListener(changeListener);
    setLabelText(labelText);
    setBrowseButtonActionListener(browseButtonActionListener);
    myViewerDialogTitle = viewerDialogTitle;
  }


  public abstract @Nls String getText();

  public abstract void setText(@Nls String text);

  @Override
  public void setEnabled(boolean enabled) {
    getComponent().setEnabled(enabled);
    if (myLabel != null) {
      myLabel.setEnabled(enabled);
    }
    for (JButton button: myButtons) {
      button.setEnabled(enabled);
    }
  }

  @Override
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
      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsBottom(5), 0, 0));
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
      this.add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsBottom(5), 0, 0));
      myLabel.setLabelFor(myComponent);
    }

    this.add(myComponent, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                 JBInsets.emptyInsets(), 0, 0));

    if (myBrowseButtonActionListener != null) {
      if (myComponent instanceof ExtendableTextComponent) {
        ((ExtendableTextComponent)myComponent).addExtension(ExtendableTextComponent.Extension.create(
          getDefaultIcon(), getHoveredIcon(), getIconTooltip(), this::notifyActionListener));
        new DumbAwareAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            notifyActionListener();
          }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myComponent);

      } else {
        FixedSizeButton browseButton = new FixedSizeButton(getComponent());
        myDoClickAction = new TextFieldWithBrowseButton.MyDoClickAction(browseButton);
        browseButton.setFocusable(false);
        browseButton.addActionListener(myBrowseButtonActionListener);
        myButtons.add(browseButton);
        this.add(browseButton, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsLeft(2), 0, 0));
      }
    }
    if (myViewerDialogTitle != null) {
      final FixedSizeButton showViewerButton = new FixedSizeButton(getComponent());
      if (myBrowseButtonActionListener == null) {
        LOG.assertTrue(myDoClickAction == null);
        myDoClickAction = new TextFieldWithBrowseButton.MyDoClickAction(showViewerButton);
      }
      showViewerButton.setFocusable(false);
      showViewerButton.setIcon(AllIcons.Actions.ShowViewer);
      showViewerButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Viewer viewer = new Viewer();
          viewer.setTitle(myViewerDialogTitle);
          viewer.show();
        }
      });
      myButtons.add(showViewerButton);
      this.add(showViewerButton, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                        JBInsets.emptyInsets(), 0, 0));
    }
  }

  @NotNull
  protected Icon getDefaultIcon() {
    return AllIcons.General.OpenDisk;
  }

  @NotNull
  protected Icon getHoveredIcon() {
    return AllIcons.General.OpenDiskHover;
  }

  @NotNull
  protected @NlsContexts.Tooltip String getIconTooltip() {
    return UIBundle.message("component.with.browse.button.browse.button.tooltip.text") + " (" +
           KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)) + ")";
  }

  private void notifyActionListener() {
    ActionEvent event = new ActionEvent(myComponent, ActionEvent.ACTION_PERFORMED, "action");
    if (myBrowseButtonActionListener != null) myBrowseButtonActionListener.actionPerformed(event);
  }

  public void setBrowseButtonActionListener(ActionListener browseButtonActionListener) {
    myBrowseButtonActionListener = browseButtonActionListener;
  }

  public void setLabelText(@NlsContexts.Label String labelText) {
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

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myTextArea;
    }

    @Override
    protected void doOKAction() {
      setText(myTextArea.getText());
      super.doOKAction();
    }

    @Override
    protected JComponent createCenterPanel() {
      myTextArea = new JTextArea(10, 50);
      myTextArea.setText(getText());
      myTextArea.setWrapStyleWord(true);
      myTextArea.setLineWrap(true);
      myTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(@NotNull DocumentEvent event) {
          if (myChangeListener != null) {
            myChangeListener.run();
          }
        }
      });

      DumbAwareAction.create(e -> doOKAction()).registerCustomShortcutSet(CommonShortcuts.ENTER, myTextArea);
      return ScrollPaneFactory.createScrollPane(myTextArea);
    }
  }
}
