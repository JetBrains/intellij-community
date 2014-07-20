package ru.compscicenter.edide.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.StudyDirectoryProjectGenerator;
import ru.compscicenter.edide.StudyUtils;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StudyNewCourseDialog extends DialogWrapper {
  private static final String DIALOG_TITLE = "Select The Course";
  private JPanel myContentPane;
  private JButton myRefreshButton;
  private JComboBox myDefaultCoursesComboBox;
  private TextFieldWithBrowseButton myCourseLocationField;
  private JLabel myErrorLabel;
  private JLabel myErrorIconLabel;
  private final StudyDirectoryProjectGenerator myGenerator;
  private static final String CONNECTION_ERROR = "<html>Failed to download courses.<br>Check your Internet connection.</html>";
  private static final String INVALID_COURSE = "The course you chosen is invalid";

  public StudyNewCourseDialog(final Project project, StudyDirectoryProjectGenerator generator) {
    super(project, true);
    setTitle(DIALOG_TITLE);
    init();
    myGenerator = generator;
    Map<String, File> courses = myGenerator.getCourses();
    if (courses.isEmpty()) {
      setError(CONNECTION_ERROR);
    }
    else {
      Set<String> availableCourses = courses.keySet();
      for (String courseName : availableCourses) {
        myDefaultCoursesComboBox.addItem(courseName);
      }
      //setting the first course in list as selected
      myGenerator.setSelectedCourse(StudyUtils.getFirst(availableCourses));
      setOK();
    }
    initListeners(project);
    myRefreshButton.setVisible(true);
  }

  private void initListeners(Project project) {
    final FileChooserDescriptor fileChooser = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    myCourseLocationField.addBrowseFolderListener("Select course archive", null, project, fileChooser);
    myCourseLocationField.addActionListener(new LocalCourseChosenListener());
    myDefaultCoursesComboBox.addActionListener(new CourseSelectedListener());
    myRefreshButton.addActionListener(new RefreshActionListener());
  }

  /**
   * Handles choosing course zip archive from local file system
   * Automatically sets course chosen as selected course if it is valid
   */
  private class LocalCourseChosenListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String fileName = myCourseLocationField.getText();
      if (StudyUtils.isZip(fileName)) {
        setError(INVALID_COURSE);
        return;
      }
      String courseName = myGenerator.addLocalCourse(fileName);
      if (courseName !=null) {
        myDefaultCoursesComboBox.addItem(courseName);
        myDefaultCoursesComboBox.setSelectedItem(courseName);
        setOK();
      } else {
        setError(INVALID_COURSE);
      }
    }
  }

  /**
   * Handles selecting course in combo box
   * Sets selected course in combo box as selected in
   * {@link ru.compscicenter.edide.ui.StudyNewCourseDialog#myGenerator}
   */
  private class CourseSelectedListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComboBox cb = (JComboBox)e.getSource();
      String selectedCourseName = (String)cb.getSelectedItem();
      myGenerator.setSelectedCourse(selectedCourseName);
    }
  }

  /**
   * Handles refreshing courses
   * Old courses added to new courses only if their
   * meta file still exists in local file system
   */
  private class RefreshActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      myGenerator.downloadAndUnzip(true);
      Map<String, File> downloadedCourses = myGenerator.loadCourses();
      if (downloadedCourses.isEmpty()) {
        setError(CONNECTION_ERROR);
        return;
      }
      Map<String, File> oldCourses = myGenerator.getLoadedCourses();
      Map<String, File> newCourses = new HashMap<String, File>();
        for (Map.Entry<String, File> course : oldCourses.entrySet()) {
          File courseFile = course.getValue();
          if (courseFile.exists()) {
            newCourses.put(course.getKey(), courseFile);
          }
        }
        for (Map.Entry<String, File> course : downloadedCourses.entrySet()) {
          String courseName = course.getKey();
          if (newCourses.get(courseName) == null) {
            newCourses.put(courseName, course.getValue());
            myDefaultCoursesComboBox.addItem(courseName);
          }
        }
        myGenerator.setCourses(newCourses);
        myGenerator.flushCache();
    }
  }

  /**
   * Sets normal state of dialog
   */
  private void setOK() {
    myErrorLabel.setVisible(false);
    myErrorIconLabel.setVisible(false);
    myOKAction.setEnabled(true);
  }

  private void setError(String errorText) {
    myErrorLabel.setText(errorText);
    myErrorLabel.setVisible(true);
    myErrorIconLabel.setVisible(true);
    myOKAction.setEnabled(false);
  }


  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }
}
