package ru.compscicenter.edide;

import com.intellij.openapi.diagnostic.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            instance.load();
        }
        return instance;
    }

    private void load() {
        InputStream metaIS = StudyDirectoryProjectGenerator.class.getResourceAsStream("tasks.meta");
        BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
        try {

            final int tasksNumber = Integer.parseInt(reader.readLine());
            for (int task = 0; task < tasksNumber; task++) {

                int n = Integer.parseInt(reader.readLine());
                this.addTask(n);
                for (int h = 0; h < n; h++) {
                    this.setFileName(task, reader.readLine());
                }
                String taskTextFileName = Integer.toString(task + 1) + ".meta";
                System.out.println(taskTextFileName);
                InputStream taskTextIS = StudyDirectoryProjectGenerator.class.getResourceAsStream(taskTextFileName);
                System.out.println((taskTextIS == null));
                BufferedReader taskTextReader = new BufferedReader(new InputStreamReader(taskTextIS));
                System.out.println((taskTextReader == null));
                while (taskTextReader.ready()) {
                    this.addTaskTextLine(task, taskTextReader.readLine());
                }

            }


        } catch (IOException e) {
            Log.print("Something wrong with meta file: " + e.getCause());
            Log.flush();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // close silently
            }
        }
    }



    public int getTasksNum() {
        return tasks.size();
    }

    public void addTask(int n) {
        tasks.add(new Task(n));
    }

    public void addTaskTextLine(int index, String line) {
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

    public void setCurrentTask(int currentTask) {
        this.currentTask = currentTask;
    }

    public void incrementTask() {
        currentTask++;
    }

    public int getTaskNumForFile(String filename) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).contains(filename)) {
                return i;
            }
        }
        return -1;
    }

}
