package ru.compscicenter.edide.model;

import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:53
 */
public class TaskFile {
    private String name;
    private List<Window> windows;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Window> getWindows() {
        return windows;
    }

    public void setWindows(List<Window> windows) {
        this.windows = windows;
    }
}
