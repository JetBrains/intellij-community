// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Objects;

@ApiStatus.Experimental
public interface RunnablesListener {

  Topic<RunnablesListener> TOPIC = Topic.create(
    "RunnableListener",
    RunnablesListener.class
  );

  default void eventsProcessed(@NotNull Class<? extends AWTEvent> eventClass,
                               @NotNull Collection<InvocationDescription> descriptions) {}

  default void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                  @NotNull Collection<InvocationsInfo> infos,
                                  @NotNull Collection<WrapperDescription> wrappers) {}

  final class InvocationDescription implements Comparable<InvocationDescription> {

    @NotNull
    private final String myProcessId;
    private final long myStartedAt;
    private final long myFinishedAt;

    InvocationDescription(@NotNull String processId,
                          long startedAt,
                          long finishedAt) {
      myProcessId = processId;
      myStartedAt = startedAt;
      myFinishedAt = finishedAt;
    }

    InvocationDescription(@NotNull String processId,
                          long startedAt) {
      this(processId, startedAt, System.currentTimeMillis());
    }

    @NotNull
    public String getProcessId() {
      return myProcessId;
    }

    public long getStartedAt() {
      return myStartedAt;
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
    public String toString() {
      return String.format(
        "%dms to process %s; started at: %tc; finished at: %tc",
        getDuration(),
        myProcessId,
        myStartedAt,
        myFinishedAt
      );
    }
  }

  final class InvocationsInfo implements Comparable<InvocationsInfo> {

    @NotNull
    static InvocationsInfo computeNext(@NotNull String fqn,
                                       long duration,
                                       @Nullable InvocationsInfo info) {
      return new InvocationsInfo(
        fqn,
        info != null ? info.myCount : 0,
        (info != null ? info.myDuration : 0) + duration
      );
    }

    @NotNull
    private final String myFQN;
    private final int myCount;
    private final long myDuration;

    private InvocationsInfo(@NotNull String fqn,
                            int count,
                            long duration) {
      myFQN = fqn;
      myCount = 1 + count;
      myDuration = duration;
    }

    @NotNull
    public String getFQN() {
      return myFQN;
    }

    public int getCount() {
      return myCount;
    }

    public double getAverageDuration() {
      return (double)myDuration / myCount;
    }

    @Override
    public int compareTo(@NotNull InvocationsInfo info) {
      int result = Integer.compare(info.myCount, myCount);

      return result != 0 ?
             result :
             Double.compare(info.myDuration, myDuration);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InvocationsInfo info = (InvocationsInfo)o;
      return myCount == info.myCount &&
             myDuration == info.myDuration &&
             myFQN.equals(info.myFQN);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFQN, myCount, myDuration);
    }

    @NotNull
    @Override
    public String toString() {
      return String.format(
        "%s=[average: %.2f; count: %d]",
        myFQN,
        getAverageDuration(),
        myCount
      );
    }
  }

  final class WrapperDescription implements Comparable<WrapperDescription> {

    @NotNull
    static WrapperDescription computeNext(@NotNull String fqn,
                                          @Nullable WrapperDescription description) {
      return new WrapperDescription(
        fqn,
        description != null ? description.myUsagesCount : 0
      );
    }

    @NotNull
    private final String myFQN;
    private final int myUsagesCount;

    private WrapperDescription(@NotNull String fqn, int count) {
      myFQN = fqn;
      myUsagesCount = 1 + count;
    }

    @NotNull
    public String getFQN() {
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
    public String toString() {
      return String.format(
        "%s; usages: %d",
        myFQN,
        myUsagesCount
      );
    }
  }
}
