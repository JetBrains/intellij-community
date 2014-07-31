package ru.compscicenter.edide.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.StudyDirectoryProjectGenerator;
import ru.compscicenter.edide.StudyUtils;
import ru.compscicenter.edide.course.CourseInfo;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
  private JLabel myAuthorLabel;
  private JLabel myDescriptionLabel;
  private final StudyDirectoryProjectGenerator myGenerator;
  private static final String CONNECTION_ERROR = "<html>Failed to download courses.<br>Check your Internet connection.</html>";
  private static final String INVALID_COURSE = "The course you chosen is invalid";

  public StudyNewCourseDialog(final Project project, StudyDirectoryProjectGenerator generator) {
    super(project, true);
    setTitle(DIALOG_TITLE);
    init();
    myGenerator = generator;
    Map<CourseInfo, File> courses = myGenerator.getCourses();
    if (courses.isEmpty()) {
      setError(CONNECTION_ERROR);
    }
    else {
      Set<CourseInfo> availableCourses = courses.keySet();
      for (CourseInfo courseInfo : availableCourses) {
        myDefaultCoursesComboBox.addItem(courseInfo);
      }
      myAuthorLabel.setText("Author: " + StudyUtils.getFirst(availableCourses).getAuthor());
      myDescriptionLabel.setText(StudyUtils.getFirst(availableCourses).getDescription());
      //setting the first course in list as selected
      myGenerator.setSelectedCourse(StudyUtils.getFirst(availableCourses));
      setOK();
    }
    initListeners(project);
    myRefreshButton.setVisible(true);
  }

  private void initListeners(Project project) {
    FileChooserDescriptor fileChooser = new FileChooserDescriptor(true, false, false, true, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) || StudyUtils.isZip(file.getName());
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
       return super.isFileSelectable(file)|| file.getName().contains(".zip");
      }
    };
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
      CourseInfo courseInfo = myGenerator.addLocalCourse(fileName);
      if (courseInfo != null) {
        myDefaultCoursesComboBox.addItem(courseInfo);
        myDefaultCoursesComboBox.setSelectedItem(courseInfo);
        setOK();
      }
      else {
        setError(INVALID_COURSE);
        if (myGenerator.getSelectedCourseFile() != null) {
          myOKAction.setEnabled(true);
        }
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
      CourseInfo selectedCourse = (CourseInfo)cb.getSelectedItem();
      if (selectedCourse == null) {
        myAuthorLabel.setText("");
        myDescriptionLabel.setText("");
        return;
      }
      myAuthorLabel.setText("Author: " + selectedCourse.getAuthor());
      myDescriptionLabel.setText(selectedCourse.getDescription());
      myGenerator.setSelectedCourse(selectedCourse);
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
      Map<CourseInfo, File> downloadedCourses = myGenerator.loadCourses();
      if (downloadedCourses.isEmpty()) {
        setError(CONNECTION_ERROR);
        return;
      }
      Map<CourseInfo, File> oldCourses = myGenerator.getLoadedCourses();
      Map<CourseInfo, File> newCourses = new HashMap<CourseInfo, File>();
      for (Map.Entry<CourseInfo, File> course : oldCourses.entrySet()) {
        File courseFile = course.getValue();
        if (courseFile.exists()) {
          newCourses.put(course.getKey(), courseFile);
        }
      }
      for (Map.Entry<CourseInfo, File> course : downloadedCourses.entrySet()) {
        CourseInfo courseName = course.getKey();
        if (newCourses.get(courseName) == null) {
          newCourses.put(courseName, course.getValue());
        }
      }
      myDefaultCoursesComboBox.removeAllItems();

      for (CourseInfo courseInfo:newCourses.keySet()) {
        myDefaultCoursesComboBox.addItem(courseInfo);
      }
      myGenerator.setSelectedCourse(StudyUtils.getFirst(newCourses.keySet()));

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
