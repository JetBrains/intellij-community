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

class myCondition implements Condition {

    @Override
    public boolean value(Object o) {
        return true;
    }
}

public class TaskTextToolWindowFactory implements ToolWindowFactory{
    JButton nextTask = new JButton("next");
    public TaskTextToolWindowFactory() {
        nextTask.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TaskManager.getInstance().incrementTask();
            }
        });
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        JPanel myPanel = new JPanel();
        //JLabel task =  new JLabel("write your first program in python");
        //int curTask = TaskManager.getInstance().getCurrentTask();
        int curTask = 0;
        JLabel task =  new JLabel(TaskManager.getInstance().getTaskText(curTask));
        Font testFont = new Font("Courier", Font.BOLD, 16);
        task.setFont(testFont);
        //myPanel.setBackground(Color.BLACK);
        task.setForeground(Color.CYAN);
        myPanel.add(task);
        myPanel.add(nextTask);
        task.setVerticalAlignment(0);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(myPanel, "", true);
        toolWindow.getContentManager().addContent(content);
    }
}
