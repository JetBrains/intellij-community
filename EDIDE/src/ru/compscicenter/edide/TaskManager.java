package ru.compscicenter.edide;

import java.util.ArrayList;

/**
 * User: lia
 * Date: 26.12.13
 * Time: 20:37
 */
public class TaskManager {
    static private TaskManager instance = null;
    private ArrayList<Task> tasks;
    private int currentTask = -1;
    private TaskManager() {
        tasks = new ArrayList<Task>(10);
    }
    static public TaskManager getInstance() {
        if (instance == null) {
            instance = new TaskManager();
        }
        return instance;
    }

    public int getTasksNum() {
        return tasks.size();
    }

    public void addTask(int n) {
        tasks.add(new Task(n));
    }
    public void addTaskTextLine(int index, String line){
        tasks.get(index).addTaskTextLine(line);
    }

    public void setFileName(int index, String filename) {
        tasks.get(index).setFileName(filename);
    }

    public int getTaskFileNum(int index) {
        return tasks.get(index).getFileNum();
    }

    public String getFileName(int taskIndex, int fileIndex) {
        return tasks.get(taskIndex).fileNames.get(fileIndex);
    }

    public String getTaskText(int index) {
        return tasks.get(index).getTaskText();
    }

    public int getCurrentTask() {
        return currentTask;
    }

    public void incrementTask() {
        currentTask++;
    }

}
