package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildProcessLogger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.jps.util.Iterators.flat;

public final class BatchBuildProcessLogger implements BuildProcessLogger {
  private final BuildProcessLogger myDelegate;
  private boolean myBatchActive;
  private final List<PostponedEvent> myEvents = new ArrayList<>();

  public BatchBuildProcessLogger(BuildProcessLogger delegate) {
    myDelegate = delegate;
  }

  public void startBatch() {
    myBatchActive = true;
  }

  public void stopBatch() {
    myBatchActive = false;
    try {
      for (PostponedEvent event : myEvents) {
        event.process(myDelegate);
      }
    }
    finally {
      myEvents.clear();
    }
  }

  @Override
  public boolean isEnabled() {
    return myDelegate.isEnabled();
  }

  @Override
  public void logDeletedPaths(Iterable<String> paths) {
    if (myBatchActive && isEnabled()) {
      if (myEvents.isEmpty() || !myEvents.getLast().mergeDeleted(paths)) {
        myEvents.add(PostponedEvent.pathsDeleted(paths));
      }
    }
    else {
      myDelegate.logDeletedPaths(paths);
    }
  }

  @Override
  public void logCompiledPaths(Iterable<Path> files, String builderId, String description) {
    if (myBatchActive && isEnabled()) {
      if (myEvents.isEmpty() || !myEvents.getLast().mergeCompiled(files, builderId, description)) {
        myEvents.add(PostponedEvent.pathsCompiled(files, builderId, description));
      }
    }
    else {
      myDelegate.logCompiledPaths(files, builderId, description);
    }
  }

  @Override
  public String getCollectedData() {
    stopBatch(); // ensure all postponed data is logged
    return myDelegate.getCollectedData();
  }

  private interface PostponedEvent {
    void process(BuildProcessLogger target);
    boolean mergeDeleted(Iterable<String> paths);
    boolean mergeCompiled(Iterable<Path> files, String builderId, String description);

    static PostponedEvent pathsDeleted(Iterable<String> paths) {
      return new PostponedEvent() {
        Iterable<String> myData = paths;
        @Override
        public void process(BuildProcessLogger target) {
          target.logDeletedPaths(myData);
        }

        @Override
        public boolean mergeDeleted(Iterable<String> paths) {
          myData = flat(myData, paths);
          return true;
        }

        @Override
        public boolean mergeCompiled(Iterable<Path> files, String builderId, String description) {
          return false;
        }
      };
    }

    static PostponedEvent pathsCompiled(Iterable<Path> files, String builderId, String description) {
      return new PostponedEvent() {
        private Iterable<Path> myData = files;
        private String myDescription = description;
        @Override
        public void process(BuildProcessLogger target) {
          target.logCompiledPaths(myData, builderId, myDescription);
        }

        @Override
        public boolean mergeDeleted(Iterable<String> paths) {
          return false;
        }

        @Override
        public boolean mergeCompiled(Iterable<Path> otherFiles, String otherBuilderId, String otherDescription) {
          if (!builderId.equals(otherBuilderId)) {
            return false;
          }
          myData = flat(myData, otherFiles);
          if (!myDescription.endsWith(otherDescription)) {
            myDescription = myDescription + "\n" + otherDescription;
          }
          return true;
        }
      };
    }
  }

}
