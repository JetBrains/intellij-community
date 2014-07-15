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
  public static final String DIALOG_TITLE = "Select The Course";
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
      myGenerator.setSelectedCourse(availableCourses.iterator().next());
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

  class LocalCourseChosenListener implements ActionListener {
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

  class CourseSelectedListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComboBox cb = (JComboBox)e.getSource();
      String selectedCourseName = (String)cb.getSelectedItem();
      myGenerator.setSelectedCourse(selectedCourseName);
    }
  }

  class RefreshActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      myGenerator.downloadAndUnzip(true);
      Map<String, File> downloadedCourses = myGenerator.loadCourses();
      if (downloadedCourses.isEmpty()) {
        setError(CONNECTION_ERROR);
        return;
      }
      Map<String, File> oldCourses = myGenerator.getMyDefaultCourseFiles();
      Map<String, File> newCourses = new HashMap<String, File>();
      if (!downloadedCourses.equals(oldCourses)) {
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
      }
    }
  }

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
