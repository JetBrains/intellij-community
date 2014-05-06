package ru.compscicenter.edide;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;

/**
 * User: lia
 */


public class TaskToolWindowFactory implements ToolWindowFactory{
    JButton nextTask = new JButton("next");
    JLabel task;
    JPanel panel = new JPanel(new BorderLayout());

    @Override
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {

        nextTask.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                FileDocumentManager.getInstance().saveAllDocuments();
                TaskManager tm = TaskManager.getInstance();
                if (!project.isOpen()) {
                    return;
                }
                String basePath = project.getBasePath();

                if (basePath == null) return;
                String testFile =  basePath +
                        "/.idea/" + tm.getTest(tm.getCurrentTask());
                String test_runner = basePath +
                        "/.idea/" + "study_utrunner.py";
                GeneralCommandLine cmd = new GeneralCommandLine();
                cmd.setWorkDirectory(basePath + "/.idea");
                cmd.setExePath("python");
                cmd.addParameter(testFile);
                try {
                    Process p = cmd.createProcess();
                    InputStream is_err =  p.getErrorStream();
                    InputStream is = p.getInputStream();
                    BufferedReader bf = new BufferedReader(new InputStreamReader(is));
                    BufferedReader bf_err = new BufferedReader(new InputStreamReader(is_err));
                    String line;
                    String testResult = "test failed";
                    while ((line = bf.readLine())!=null) {
                        if (line.equals("OK")) {
                            testResult = "test passed";
                        }
                        System.out.println(line);
                    }
                    while ((line = bf_err.readLine())!=null) {
                        if (line == "OK") {
                            testResult = "test passed";
                        }
                        System.out.println(line);
                    }
                    JOptionPane.showMessageDialog(panel, testResult, "", JOptionPane.DEFAULT_OPTION);
                    if (testResult == "test passed") {
                        int nextTaskNum = TaskManager.getInstance().getCurrentTask() + 1;
                        if (nextTaskNum < TaskManager.getInstance().getTasksNum()) {
                            TaskManager.getInstance().incrementTask();
                            int curTask = TaskManager.getInstance().getCurrentTask();
                            task.setText(TaskManager.getInstance().getTaskText(curTask));
                            panel.updateUI();
                        }
                    }



                } catch (ExecutionException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

            }
        });
        /*
        int curTask = 0;
        TaskManager tm = TaskManager.getInstance();
        String taskText;
        if (tm.getTasksNum() != 0){
            System.out.println(curTask);
            taskText = tm.getTaskText(curTask);
        } else {
            taskText = "no tasks yet";
        }
        if (project.isOpen()) {
            task =  new JLabel();
        }
        */
        String taskText;
        if (project.isOpen()) {
            taskText = TaskManager.getInstance().getTaskText(TaskManager.getInstance().getCurrentTask());
        } else {
            taskText = "";
        }
        task = new JLabel(taskText);
        Font testFont = new Font("Courier", Font.BOLD, 16);
        task.setFont(testFont);
        task.setForeground(new JBColor(Color.DARK_GRAY, Color.CYAN));
        panel.add(task, BorderLayout.NORTH);
        panel.add(nextTask, BorderLayout.SOUTH);
        panel.updateUI();
        task.setVerticalAlignment(0);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(panel, "", true);
        toolWindow.getContentManager().addContent(content);
    }
}
