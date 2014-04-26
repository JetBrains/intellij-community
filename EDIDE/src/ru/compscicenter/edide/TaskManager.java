package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
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
        InputStream metaIS = StudyDirectoryProjectGenerator.class.getResourceAsStream("tasks.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
        com.google.gson.stream.JsonReader r = new com.google.gson.stream.JsonReader(reader);
        JsonParser parser = new JsonParser();
        com.google.gson.JsonElement el = parser.parse(r);
        try {
            JsonArray tasks_list = el.getAsJsonObject().get("tasks_list").getAsJsonArray();
            int taskIndex = 0;
            for (com.google.gson.JsonElement e: tasks_list) {
                int n = e.getAsJsonObject().get("file_num").getAsInt();
                this.addTask(n);
                JsonArray files_in_task = e.getAsJsonObject().get("file_names").getAsJsonArray();
                for (com.google.gson.JsonElement fileName:files_in_task){
                    this.setFileName(taskIndex, fileName.getAsString());
                }
                String testFileName = e.getAsJsonObject().get("test").getAsString();
                this.setTest(taskIndex, testFileName);
                String taskTextFileName = Integer.toString(taskIndex + 1) + ".meta";
                InputStream taskTextIS = StudyDirectoryProjectGenerator.class.getResourceAsStream(taskTextFileName);
                BufferedReader taskTextReader = new BufferedReader(new InputStreamReader(taskTextIS));
                while (taskTextReader.ready()) {
                    this.addTaskTextLine(taskIndex, taskTextReader.readLine());
                }
                taskIndex++;
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

    public void setTest(int index, String filename) {
        tasks.get(index).setTest(filename);
    }

    public String getTest(int index) {
        return tasks.get(index).getTest();
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
