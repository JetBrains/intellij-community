package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.jdom.DataConversionException;
import org.jdom.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * author: liana
 * Date: 02.12.13
 * Time: 18:16
 */

class Task {
    private final ArrayList<TaskFile> files;
    private final StringBuilder taskText;
    private String taskTextFileName;
    private String test;

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public String getTaskTextFileName() {
        return taskTextFileName;
    }

    public Task(int n) {
        files = new ArrayList<TaskFile>(n);
        taskText = new StringBuilder();
    }

    public int getFileNum() {
        return files.size();
    }

    public TaskFile getTaskFile(int index) {
        return files.get(index);
    }

    public boolean contains(String filename) {
        for (TaskFile f : files) {
            if (f.getMyName().equals(filename)) {
                return true;
            }
        }
        return false;
    }

    private void generateTaskText(String taskTextFile) {
        InputStream taskTextIS = Task.class.getResourceAsStream(taskTextFile);
        BufferedReader taskTextBf = new BufferedReader(new InputStreamReader(taskTextIS));
        try {
            while (taskTextBf.ready()) {
                String line = taskTextBf.readLine();
                taskText.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public TaskFile getTaskFileByName(String name) {
        for (TaskFile f : files) {
            if (f.getMyName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public void addNewTaskFile(String fileName) {
        int of = fileName.indexOf(".");
        String metaFileName = fileName.substring(0, of + 1) + "json";
        InputStream metaIS = StudyDirectoryProjectGenerator.class.getResourceAsStream(metaFileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
        JsonReader r = new com.google.gson.stream.JsonReader(reader);
        JsonParser parser = new JsonParser();
        JsonElement el = parser.parse(r);
        int windNum = el.getAsJsonObject().get("windows_num").getAsInt();
        TaskFile taskFile = new TaskFile(fileName, windNum);
        JsonArray windows = el.getAsJsonObject().get("windows_description").getAsJsonArray();
        for (JsonElement e : windows) {
            int line = e.getAsJsonObject().get("line").getAsInt();
            int startOffset = e.getAsJsonObject().get("start").getAsInt();
            String text = e.getAsJsonObject().get("text").getAsString();
            String docs = e.getAsJsonObject().get("docs").getAsString();
            TaskWindow window = new TaskWindow(line, startOffset, text, docs);
            taskFile.addTaskWindow(window);
        }
        files.add(taskFile);
    }

    public String getTaskText() {
        return taskText.toString();
    }
    public void setTaskTextFile(String taskTextFileName) {
        this.taskTextFileName = taskTextFileName;
        generateTaskText(taskTextFileName);
    }

    public Element saveState() {
       Element task = new Element("Task");
       task.setAttribute("taskText", getTaskTextFileName());
       task.setAttribute("testFile", test);
       for (TaskFile taskFile:files) {
           task.addContent(taskFile.saveState());
       }
        return task;
    }

    public void loadState(List<Element> taskFileElements) throws DataConversionException {
        for (Element taskFileElement: taskFileElements) {
            List<Element> taskWindowElements = taskFileElement.getChildren();
            String name = taskFileElement.getName();
            TaskFile taskFile = new TaskFile(name, taskWindowElements.size());
            taskFile.setMyLastLineNum(taskFileElement.getAttribute("lineNum").getIntValue());
            taskFile.loadState(taskWindowElements);
            files.add(taskFile);
        }
    }
}
