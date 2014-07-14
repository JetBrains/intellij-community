package ru.compscicenter.edide.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.StudyDirectoryProjectGenerator;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.util.Map;
import java.util.Set;

public class StudyNewCourseDialog extends DialogWrapper {
  private JPanel nyContentPane;
  private JButton myRefreshButton;
  private JComboBox myDefaultCoursesComboBox;
  private TextFieldWithBrowseButton myCourseLocationField;
  private JLabel myDefaultLabel;
  private JLabel myLocationLabel;
  private JLabel myErrorLabel;
  private JLabel myErrorIconLabel;
  private final StudyDirectoryProjectGenerator myGenerator;
  private static final String CONNECTION_ERROR = "Check your internet connection";
  private static final String INVALID_COURSE_ERROR = "The course you chosen is invalid";


  public StudyNewCourseDialog(final Project project, StudyDirectoryProjectGenerator generator) {
    super(project, true);
    setTitle("Select The Course");
    init();
    myGenerator = generator;
    myErrorLabel.setVisible(false);
    myErrorIconLabel.setVisible(false);
    myOKAction.setEnabled(false);
    Map<String, File> downloadedDefaultCourses = myGenerator.getDefaultCourses();
    myGenerator.setMyDefaultCourseFiles(downloadedDefaultCourses);
    Set<String> availableDefaultCourses = downloadedDefaultCourses.keySet();
    if (availableDefaultCourses.size() == 0) {
      myErrorLabel.setText(CONNECTION_ERROR);
      myErrorLabel.setVisible(true);
      myErrorIconLabel.setVisible(true);
      if (myGenerator.downloadCoursesFromGithub()) {
        downloadedDefaultCourses = myGenerator.getDefaultCourses();
        myGenerator.setMyDefaultCourseFiles(downloadedDefaultCourses);
        availableDefaultCourses = myGenerator.getDefaultCourses().keySet();
        if (availableDefaultCourses.size() != 0) {
          myErrorIconLabel.setVisible(false);
          myErrorLabel.setVisible(false);
          myOKAction.setEnabled(true);
        }
      }
    }
    else {
      myOKAction.setEnabled(true);
    }
    for (String courseName : availableDefaultCourses) {
      myDefaultCoursesComboBox.addItem(courseName);
    }

    //TODO:try make filters
    final FileChooserDescriptor fileChooser = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    myCourseLocationField.addBrowseFolderListener("Select course archive", null, project, fileChooser);
    myCourseLocationField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String fileName = myCourseLocationField.getText();
        if (!fileName.contains("course.json")) {
          myErrorLabel.setText(INVALID_COURSE_ERROR);
          myErrorLabel.setVisible(true);
          myErrorIconLabel.setVisible(true);
        }
        else {
          myGenerator.setMyLocalCourseBaseFileName(fileName);
          myErrorLabel.setVisible(false);
          myErrorIconLabel.setVisible(false);
          myOKAction.setEnabled(true);
        }
      }
    });

    myDefaultCoursesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        String selectedFileName = (String)cb.getSelectedItem();
        myGenerator.setMyDefaultSelectedCourseName(selectedFileName);
        myOKAction.setEnabled(true);
      }
    });
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myGenerator.downloadCoursesFromGithub();
        Map<String, File> newCourses = myGenerator.getDefaultCourses();
        if (newCourses.size() != myGenerator.getMyDefaultCourseFiles().size() && newCourses.size() != 0) {
          myGenerator.setMyDefaultCourseFiles(newCourses);
          myErrorLabel.setVisible(false);
          myErrorIconLabel.setVisible(false);
          myDefaultCoursesComboBox.removeAllItems();
          for (String course : newCourses.keySet()) {
            myDefaultCoursesComboBox.addItem(course);
          }
          myOKAction.setEnabled(true);
        }
        else {
          if (newCourses.size() == 0) {
            myErrorLabel.setText(CONNECTION_ERROR);
            myErrorIconLabel.setVisible(true);
            myErrorLabel.setVisible(true);
            myOKAction.setEnabled(false);
          }
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
