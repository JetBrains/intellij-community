package ru.compscicenter.edide;

import java.util.ArrayList;

/**
 * User: lia
 * Date: 02.12.13
 * Time: 18:16
 */
public class Task {
    ArrayList<String> fileNames;
    private StringBuilder taskText;
    public Task(int n) {
        fileNames =  new ArrayList<String>(n);
        taskText = new StringBuilder();
    }
    public void setFileName(String name) {
        fileNames.add(name);
    }
    public int getFileNum() {
        return fileNames.size();
    }
    public void addTaskTextLine(String line) {
        taskText.append(line);
    }
    public  String getTaskText() {
        return taskText.toString();
    }
    public boolean contains(String filename) {
        return fileNames.contains(filename);
    }
}
