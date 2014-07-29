package ru.compscicenter.edide;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.course.Lesson;
import ru.compscicenter.edide.course.LessonInfo;
import ru.compscicenter.edide.ui.StudyProgressBar;

import javax.swing.*;
import java.awt.*;

/**
 * author: liana
 * data: 7/25/14.
 */

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "StudyToolWindow";
  JPanel contentPanel = new JPanel();

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {

    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
    contentPanel.add(Box.createRigidArea(new Dimension(10, 0)));
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    Course course = taskManager.getCourse();
    String courseName = UIUtil.toHtml("<h1>" + course.getName() + "</h1>", 10);
    String description = UIUtil.toHtml(course.getDescription(), 5);
    String author = "Liana";
    String authorLabel = UIUtil.toHtml("<b>Author: </b>" + author, 5);
    contentPanel.add(new JLabel(courseName));
    contentPanel.add(new JLabel(authorLabel));
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    contentPanel.add(new JLabel(description));

    int taskNum = 0;
    int taskSolved = 0;
    int taskFailed = 0;
    for (Lesson lesson : course.getLessons()) {
      LessonInfo lessonInfo = lesson.getLessonInfo();
      taskNum += lessonInfo.getTaskNum();
      taskFailed += lessonInfo.getTaskFailed();
      taskSolved += lessonInfo.getTaskSolved();
    }
    int taskUnchecked = taskNum - taskSolved - taskFailed;
    double percent = (taskSolved * 100.0)/taskNum;
    String statistics = UIUtil.toHtml(Math.floor(percent) + "% of course is passed", 5);
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    JLabel statisticLabel = new JLabel(statistics);
    contentPanel.add(statisticLabel);
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    StudyProgressBar studyProgressBar = new StudyProgressBar(percent, JBColor.GREEN, 40);
    //studyProgressBar.setFraction(percent);
    contentPanel.add(studyProgressBar);

    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(contentPanel, "", true);
    toolWindow.getContentManager().addContent(content);
  }


}
