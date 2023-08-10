// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface RunnablesListener {
  @Topic.AppLevel
  Topic<RunnablesListener> TOPIC = new Topic<>(RunnablesListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  DecimalFormat DEFAULT_DURATION_FORMAT = new DecimalFormat("0.00");

  default void eventsProcessed(@NotNull Class<? extends AWTEvent> eventClass, @NotNull Collection<InvocationDescription> descriptions) { }

  default void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                  @NotNull Collection<InvocationInfo> infos,
                                  @NotNull Collection<WrapperDescription> wrappers) { }

  final class InvocationDescription implements Comparable<InvocationDescription> {
    private final @NotNull String myProcessId;
    private final long myStartedAt;
    private final long myFinishedAt;

    InvocationDescription(@NotNull String processId, long startedAt, long finishedAt) {
      myProcessId = processId;
      myStartedAt = startedAt;
      myFinishedAt = finishedAt;
    }

    public @NotNull String getProcessId() {
      return myProcessId;
    }

    public long getStartedAt() {
      return myStartedAt;
    }

    public @NotNull Date getStartDateTime() {
      return new Date(getStartedAt());
    }

    public long getFinishedAt() {
      return myFinishedAt;
    }

    public long getDuration() {
      return myFinishedAt - myStartedAt;
    }

    @Override
    public int compareTo(@NotNull InvocationDescription description) {
      int result = Long.compare(myStartedAt, description.myStartedAt);

      return result != 0 ?
             result :
             Long.compare(myFinishedAt, description.myFinishedAt);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InvocationDescription description = (InvocationDescription)o;
      return myStartedAt == description.myStartedAt &&
             myFinishedAt == description.myFinishedAt &&
             myProcessId.equals(description.myProcessId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myProcessId, myStartedAt, myFinishedAt);
    }

    @Override
    public @NotNull String toString() {
      return "InvocationDescription{" +
             "processId='" + myProcessId + '\'' +
             ", duration=" + DEFAULT_DURATION_FORMAT.format(getDuration()) + " ms" +
             '}';
    }
  }

  final class InvocationInfo implements Comparable<InvocationInfo> {
    static @NotNull RunnablesListener.InvocationInfo computeNext(@NotNull String fqn,
                                                                 long duration,
                                                                 @Nullable RunnablesListener.InvocationInfo info) {
      return new InvocationInfo(fqn,
                                 info != null ? info.myCount : 0,
                                 (info != null ? info.myDuration : 0) + duration);
    }

    private final @NotNull String myFQN;
    private final int myCount;
    private final long myDuration;

    private InvocationInfo(@NotNull String fqn, int count, long duration) {
      myFQN = fqn;
      myCount = 1 + count;
      myDuration = duration;
    }

    public @NotNull String getFQN() {
      return myFQN;
    }

    public int getCount() {
      return myCount;
    }

    public double getAverageDuration() {
      return (double)myDuration / myCount;
    }

    @Override
    public int compareTo(@NotNull RunnablesListener.InvocationInfo info) {
      int result = Integer.compare(info.myCount, myCount);

      return result != 0 ?
             result :
             Double.compare(info.myDuration, myDuration);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InvocationInfo info = (InvocationInfo)o;
      return myCount == info.myCount &&
             myDuration == info.myDuration &&
             myFQN.equals(info.myFQN);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFQN, myCount, myDuration);
    }

    @Override
    public @NotNull String toString() {
      return "InvocationsInfo{" +
             "FQN='" + myFQN + '\'' +
             ", count=" + myCount +
             ", averageDuration=" + DEFAULT_DURATION_FORMAT.format(getAverageDuration()) + " ms" +
             '}';
    }
  }

  final class WrapperDescription implements Comparable<WrapperDescription> {

    static @NotNull WrapperDescription computeNext(@NotNull String fqn,
                                                   @Nullable WrapperDescription description) {
      return new WrapperDescription(fqn,
                                    description != null ? description.myUsagesCount : 0);
    }

    private final @NotNull String myFQN;
    private final int myUsagesCount;

    private WrapperDescription(@NotNull String fqn, int count) {
      myFQN = fqn;
      myUsagesCount = 1 + count;
    }

    public @NotNull String getFQN() {
      return myFQN;
    }

    public int getUsagesCount() {
      return myUsagesCount;
    }

    @Override
    public int compareTo(@NotNull WrapperDescription description) {
      return Integer.compare(description.myUsagesCount, myUsagesCount);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      WrapperDescription description = (WrapperDescription)o;
      return myUsagesCount == description.myUsagesCount &&
             myFQN.equals(description.myFQN);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFQN, myUsagesCount);
    }

    @Override
    public @NotNull String toString() {
      return "WrapperDescription{" +
             "FQN='" + myFQN + '\'' +
             ", usagesCount=" + myUsagesCount +
             '}';
    }
  }
}
