package com.intellij.platform;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;

/**
 * @author yole
 */
public class NewDirectoryProjectDialog extends DialogWrapper {
  private JTextField myProjectNameTextField;
  private TextFieldWithBrowseButton myLocationField;
  private JPanel myRootPane;
  private JComboBox myProjectTypeComboBox;
  private JPanel myProjectTypePanel;
  private String myBaseDir;

  protected NewDirectoryProjectDialog(Project project) {
    super(project, true);
    setTitle("Create New Project");
    init();

    myBaseDir = getBaseDir();
    File projectName = FileUtil.findSequentNonexistentFile(new File(myBaseDir), "untitled", "");
    myLocationField.setText(projectName.toString());
    myProjectNameTextField.setText(projectName.getName());

    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
        new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Select Location for Project Directory", "", myLocationField, project,
                                                                             descriptor,
                                                                             TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {

          protected void onFileChoosen(VirtualFile chosenFile) {
            super.onFileChoosen(chosenFile);
            myBaseDir = chosenFile.getPath();
            myLocationField.setText(new File(chosenFile.getPath(), myProjectNameTextField.getText()).toString());
          }
        };
    myLocationField.addActionListener(listener);

    myProjectNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        myLocationField.setText(new File(myBaseDir, myProjectNameTextField.getText()).getPath());
      }
    });
    myProjectNameTextField.selectAll();

    final DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    if (generators.length == 0) {
      myProjectTypePanel.setVisible(false);
    }
    else {
      DefaultComboBoxModel model = new DefaultComboBoxModel();
      model.addElement(null);
      for (DirectoryProjectGenerator generator : generators) {
        model.addElement(generator);
      }
      myProjectTypeComboBox.setModel(model);
      myProjectTypeComboBox.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(final JList list,
                                             final Object value,
                                             final int index,
                                             final boolean selected,
                                             final boolean hasFocus) {
          if (value == null) {
            append("Empty project", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            DirectoryProjectGenerator generator = (DirectoryProjectGenerator) value;
            append(generator.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });
    }
  }

  private static String getBaseDir() {
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    //noinspection HardCodedStringLiteral
    return userHome.replace('/', File.separatorChar) + File.separator + ApplicationNamesInfo.getInstance().getLowercaseProductName() +
           "Projects";
  }

  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  public String getNewProjectLocation() {
    return myLocationField.getText();
  }

  public String getNewProjectName() {
    return myProjectNameTextField.getText();
  }

  @Nullable
  public DirectoryProjectGenerator getProjectGenerator() {
    return (DirectoryProjectGenerator) myProjectTypeComboBox.getSelectedItem();
  }

  public JComponent getPreferredFocusedComponent() {
    return myProjectNameTextField;
  }
}
