package ru.compscicenter.edide;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.util.Set;

public class StudyNewCourseDialog extends DialogWrapper {
  private JPanel nyContentPane;
  private JButton myRefreshButton;
  private JComboBox myDefaultCoursesComboBox;
  private TextFieldWithBrowseButton myCourseLocationField;
  private JLabel myDefaultLabel;
  private JLabel myLocationLabel;
  private JLabel myErrorLabel;


  public StudyNewCourseDialog(final Project project, Set<String> availableDefaultCourses) {
    super(project, true);
    setTitle("Select the Course");
    init();
    myErrorLabel.setVisible(false);
    if (availableDefaultCourses.size() == 0) {
        myErrorLabel.setVisible(true);
    }
    for (String courseName: availableDefaultCourses) {
      myDefaultCoursesComboBox.addItem(courseName);
    }

    final FileChooserDescriptor fileChooser = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    myCourseLocationField.addBrowseFolderListener("Select course file", null, project, fileChooser);



    myDefaultCoursesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        String testString = (String)cb.getSelectedItem();
        StudyDirectoryProjectGenerator.setMyBaseCourseFile(testString);
      }
    });
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
       myDefaultCoursesComboBox.removeAllItems();
       Set<String> newCourses = StudyDirectoryProjectGenerator.updateDefaultCourseList();
       if (newCourses.size() == 0) {
         myErrorLabel.setVisible(true);
         return;
       }
        myErrorLabel.setVisible(false);
        for (String course:newCourses) {
         myDefaultCoursesComboBox.addItem(course);
       }
      }
    });
    myRefreshButton.setVisible(true);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return nyContentPane;
  }

}
