package ru.compscicenter.edide.model;

import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 19:00
 */
public class Course {
  private List<Lesson> lessons;

  public List<Lesson> getLessons() {
    return lessons;
  }

  public void setLessons(List<Lesson> lessons) {
    this.lessons = lessons;
  }
}
