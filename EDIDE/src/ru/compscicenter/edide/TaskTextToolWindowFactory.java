package ru.compscicenter.edide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: lia
 * Date: 26.12.13
 * Time: 14:45
 */

class TaskToolWindowCondition implements Condition {

    @Override
    public boolean value(Object o) {
        return true;
    }
}

public class TaskTextToolWindowFactory implements ToolWindowFactory{
    JButton nextTask = new JButton("next");
    JLabel task;
    JPanel panel = new JPanel(new BorderLayout());
    public TaskTextToolWindowFactory() {
        nextTask.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TaskManager.getInstance().incrementTask();
                int curTask = TaskManager.getInstance().getCurrentTask();
                task.setText(TaskManager.getInstance().getTaskText(curTask));
                panel.updateUI();
            }
        });
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        //JLabel task =  new JLabel("write your first program in python");
        //int curTask = TaskManager.getInstance().getCurrentTask();
        int curTask = 0;
        task =  new JLabel(TaskManager.getInstance().getTaskText(curTask));
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
