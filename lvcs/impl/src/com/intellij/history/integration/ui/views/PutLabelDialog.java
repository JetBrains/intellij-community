package com.intellij.history.integration.ui.views;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class PutLabelDialog extends DialogWrapper {
  private IdeaGateway myGateway;
  private VirtualFile myFile;

  private JTextField myNameField;
  private JRadioButton myProjectButton;
  private JRadioButton myFileButton;

  public PutLabelDialog(IdeaGateway gw, VirtualFile f) {
    super(gw.getProject(), false);
    setTitle("Put Label");

    myGateway = gw;
    myFile = f;
    init();

    updateOkStatus();
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    initNameField();
    panel.add(new JLabel("Label name"), atCell(0, 0));
    panel.add(myNameField, atCell(1, 0));

    if (canPutLabelOnSelectedFile()) {
      initGroupButtons();
      panel.add(new JLabel("Put on"), atCell(0, 1));
      panel.add(myProjectButton, atCell(1, 1));
      panel.add(myFileButton, atCell(1, 2));
    }

    panel.setPreferredSize(new Dimension(300, 50));
    return panel;
  }

  private void initNameField() {
    myNameField = new JTextField();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateOkStatus();
      }
    });
  }

  private void initGroupButtons() {
    myProjectButton = new JRadioButton("Whole project");
    myFileButton = new JRadioButton(myFile.getPath());

    ButtonGroup group = new ButtonGroup();
    group.add(myProjectButton);
    group.add(myFileButton);

    myProjectButton.setSelected(true);
  }

  private GridBagConstraints atCell(int x, int y) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4, 4, 4, 4);
    c.anchor = GridBagConstraints.EAST;
    c.fill = GridBagConstraints.BOTH;
    c.gridx = x;
    c.gridy = y;
    c.weightx = x;
    return c;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void updateOkStatus() {
    setOKActionEnabled(getLabelName().trim().length() > 0);
  }

  @Override
  public void doOKAction() {
    ILocalVcs vcs = LocalHistoryComponent.getLocalVcsFor(myGateway.getProject());
    if (canPutLabelOnSelectedFile() && myFileButton.isSelected()) {
      vcs.putUserLabel(myFile.getPath(), getLabelName());
    }
    else {
      vcs.putUserLabel(getLabelName());
    }
    close(0);
  }

  private String getLabelName() {
    return myNameField.getText();
  }

  // test-support
  public void selectFileLabel() {
    myFileButton.setSelected(true);
  }

  public boolean canPutLabelOnSelectedFile() {
    return myFile != null && myGateway.getFileFilter().isAllowedAndUnderContentRoot(myFile);
  }
}