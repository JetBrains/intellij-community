package com.intellij.application.options.pathMacros;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.awt.*;

/**
 *  @author dsl
 */
public class PathMacroEditor extends DialogWrapper {
  private JTextField myNameField;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myValueField;
  private JPanel myDescriptionPanel;
  private JTextPane myDescriptionPane;
  private final Validator myValidator;
  private final DocumentListener myDocumentListener;

  public interface Validator {
    boolean checkName(String name);
    boolean isOK(String name, String value);
  }

  public PathMacroEditor(String title, String macroName, String value, String description, Validator validator, boolean editPathsOnly) {
    super(true);
    setTitle(title);
    myValidator = validator;
    myNameField.setText(macroName);
    myDocumentListener = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateControls();
      }
    };
    myNameField.getDocument().addDocumentListener(myDocumentListener);
    myValueField.setText(value);
    myValueField.addBrowseFolderListener(null, null, null, new FileChooserDescriptor(false, true, true, false, true, false), new TextComponentAccessor<JTextField>() {
      public String getText(JTextField component) {
        return component.getText();
      }

      public void setText(JTextField component, String text) {
        final int len = text.length();
        if (len > 0 && text.charAt(len - 1) == File.separatorChar) {
          text = text.substring(0, len - 1);
        }
        component.setText(text);
      }
    });
    myValueField.getTextField().getDocument().addDocumentListener(myDocumentListener);

    if (!editPathsOnly) {
      myDescriptionPane = new JTextPane();
      myDescriptionPanel = new JPanel();
      myDescriptionPanel.setLayout(new BorderLayout(5, 5));
      myDescriptionPanel.add(new JLabel(ProjectBundle.message("project.configure.path.variables.description.title")), BorderLayout.NORTH);
      final JScrollPane scrollPane = new JScrollPane(myDescriptionPane);
      scrollPane.setPreferredSize(new Dimension(300, 100));
      myDescriptionPanel.add(scrollPane, BorderLayout.CENTER);
      myDescriptionPane.setText(description);
    }

    init();
    updateControls();
  }

  public void setMacroNameEditable(boolean isEditable) {
    myNameField.setEditable(isEditable);
  }

  private void updateControls() {
    final boolean isNameOK = myValidator.checkName(myNameField.getText());
    getOKAction().setEnabled(isNameOK);
    if (isNameOK) {
      final String text = myValueField.getText();
      getOKAction().setEnabled(text.length() > 0);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(PathMacroConfigurable.HELP_ID);
  }

  protected void doOKAction() {
    if (!myValidator.isOK(getName(), getValue())) return;
    super.doOKAction();
  }

  public String getName() {
    return myNameField.getText();
  }

  public String getValue() {
    return myValueField.getText();
  }

  @Nullable
  public String getDescription() {
    if (myDescriptionPane == null) {
      return null;
    }

    final String s = myDescriptionPane.getText();
    return s.trim().length() == 0 ? null : s;
  }

  protected JComponent createNorthPanel() {
    return myPanel;
  }

  protected JComponent createCenterPanel() {
    return myDescriptionPanel;
  }
}
