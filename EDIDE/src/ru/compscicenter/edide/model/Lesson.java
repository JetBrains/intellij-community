package ru.compscicenter.edide.model;

import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:40
 */
public class Lesson {
  private String name;
  private List<Task> taskList;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Task> getTaskList() {
    return taskList;
  }

  public void setTaskList(List<Task> taskList) {
    this.taskList = taskList;
  }
}
