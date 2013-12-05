package ru.compscicenter.edide;

import java.util.ArrayList;

/**
 * User: lia
 * Date: 02.12.13
 * Time: 18:16
 */
public class Task {
    ArrayList<String> fileNames;
    public Task(int n) {
        fileNames =  new ArrayList<String>(n);
    }
    public void setFileName(String name) {
        fileNames.add(name);
    }
    public int getFileNum() {
        return fileNames.size();
    }
}
