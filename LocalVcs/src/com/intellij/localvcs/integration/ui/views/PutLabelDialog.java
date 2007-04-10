package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.LocalVcsComponent;
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

    if (myFile != null) {
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
    ILocalVcs vcs = LocalVcsComponent.getLocalVcsFor(myGateway.getProject());
    if (myFile != null && myFileButton.isSelected()) {
      vcs.putLabel(myFile.getPath(), getLabelName());
    }
    else {
      vcs.putLabel(getLabelName());
    }
    close(0);
  }

  public String getLabelName() {
    return myNameField.getText();
  }
}