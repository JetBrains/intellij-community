package ru.compscicenter.edide;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.jetbrains.python.run.ProcessRunner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * User: lia
 * Date: 26.12.13
 * Time: 14:45
 */


public class TaskToolWindowFactory implements ToolWindowFactory{
    JButton nextTask = new JButton("next");
    JLabel task;
    JPanel panel = new JPanel(new BorderLayout());
    public TaskToolWindowFactory() {
        nextTask.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TaskManager tm = TaskManager.getInstance();
                String basePath = ProjectManager.getInstance().getOpenProjects()[0].getBasePath();
                String testFile =  basePath +
                        "/.idea/" + tm.getTest(tm.getCurrentTask());
                String test_runner = basePath +
                        "/.idea/" + "study_utrunner.py";
                //GeneralCommandLine cmd = new GeneralCommandLine();
                //cmd.setWorkDirectory(basePath);
                //cmd.addParameter("pwd\n");
                try {
                    Process p = ProcessRunner.createProcess(basePath, "python", testFile);
                    InputStream is = p.getInputStream();
                    BufferedReader bf = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = bf.readLine())!=null) {
                        System.out.println(line);
                    }


                } catch (ExecutionException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                //System.out.println(cmd.getCommandLineString());

                /*
                Project project = ProjectManager.getInstance().getOpenProjects()[0];
                List<RunnerAndConfigurationSettings> settings = RunManager.getInstance(project).getConfigurationSettingsList(new StudyConfigurationType());
                ProgramRunnerUtil.executeConfiguration(project, settings.get(0), DefaultRunExecutor.getRunExecutorInstance()) ;
                int nextTaskNum = TaskManager.getInstance().getCurrentTask() + 1;
                if (nextTaskNum < TaskManager.getInstance().getTasksNum()) {
                    TaskManager.getInstance().incrementTask();
                    int curTask = TaskManager.getInstance().getCurrentTask();
                    task.setText(TaskManager.getInstance().getTaskText(curTask));
                    panel.updateUI();
                }
                */

            }
        });
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        //JLabel task =  new JLabel("write your first program in python");
        //int curTask = TaskManager.getInstance().getCurrentTask();
        int curTask = 0;
        TaskManager tm = TaskManager.getInstance();
        String taskText;
        if (tm.getTasksNum() != 0){
            System.out.println(curTask);
            taskText = tm.getTaskText(curTask);
        } else {
            taskText = "no tasks yet";
        }
        task =  new JLabel(taskText);
        Font testFont = new Font("Courier", Font.BOLD, 16);
        task.setFont(testFont);
        //panel.setBackground(Color.BLACK);
        task.setForeground(Color.CYAN);
        panel.add(task, BorderLayout.NORTH);
        panel.add(nextTask, BorderLayout.SOUTH);
        panel.updateUI();
        task.setVerticalAlignment(0);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(panel, "", true);
        toolWindow.getContentManager().addContent(content);
    }
}
