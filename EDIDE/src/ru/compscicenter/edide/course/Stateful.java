package ru.compscicenter.edide.course;

public interface Stateful {
  StudyStatus getStatus();
  void setStatus(StudyStatus status);
}
