package com.intellij.openapi.externalSystem.model.task;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents id of the task enqueued to Gradle API for execution. 
 *
 * @author Denis Zhdanov
 * @since 11/10/11 9:09 AM
 */
public class ExternalSystemTaskId implements Serializable {

  private static final long       serialVersionUID = 1L;
  private static final AtomicLong COUNTER          = new AtomicLong();

  private final ExternalSystemTaskType myType;
  private final long                   myId;

  private ExternalSystemTaskId(@NotNull ExternalSystemTaskType type, long id) {
    myType = type;
    myId = id;
  }

  /**
   * Allows to retrieve distinct task id object of the given type.
   *
   * @param type  target task type
   * @return distinct task id object of the given type
   */
  @NotNull
  public static ExternalSystemTaskId create(@NotNull ExternalSystemTaskType type) {
    return new ExternalSystemTaskId(type, COUNTER.getAndIncrement());
  }

  @NotNull
  public ExternalSystemTaskType getType() {
    return myType;
  }

  @Override
  public int hashCode() {
    return 31 * myType.hashCode() + (int)(myId ^ (myId >>> 32));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemTaskId that = (ExternalSystemTaskId)o;
    return myId == that.myId && myType == that.myType;
  }

  @Override
  public String toString() {
    return myType + ":" + myId;
  }
}
