package ru.compscicenter.edide;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.util.ArrayList;

public class StudyNewCourseDialog extends DialogWrapper {
    private JPanel contentPane;
    private JComboBox comboBox1;
  private JLabel locationLabel;
  private TextFieldWithBrowseButton button;

    public StudyNewCourseDialog(final Project project) {
        super(project, true);
        setTitle("Select the Course");
        init();
        ArrayList<String> availableCouseNames = StudyDirectoryProjectGenerator.getCourseFiles();
        for (String courseName: availableCouseNames) {
          comboBox1.addItem(courseName);
        }
      button =  new TextFieldWithBrowseButton();
      button.getButton().setFocusable(true);
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final JFileChooser fc = new JFileChooser();
          int returnVal = fc.showOpenDialog(button);
        }
      });
      FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      fcd.setShowFileSystemRoots(true);
      fcd.setTitle(DvcsBundle.getString("clone.destination.directory.title"));
      fcd.setDescription(DvcsBundle.getString("clone.destination.directory.description"));
      fcd.setHideIgnored(false);
      button.addActionListener(
        new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(fcd.getTitle(), fcd.getDescription(), button,
                                                                             project, fcd, TextComponentAccessor.TEXT_FIELD_SELECTED_TEXT) {
          @Override
          protected VirtualFile getInitialFile() {
            // suggest project base directory only if nothing is typed in the component.
            String text = getComponentText();
            if (text.length() == 0) {
              VirtualFile file = project.getBaseDir();
              if (file != null) {
                return file;
              }
            }
            return super.getInitialFile();
          }
        }
      );
        comboBox1.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            JComboBox cb = (JComboBox)e.getSource();
            String testString = (String)cb.getSelectedItem();
            StudyDirectoryProjectGenerator.setMyBaseCouseFile(testString);
          }
        });


    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

}
