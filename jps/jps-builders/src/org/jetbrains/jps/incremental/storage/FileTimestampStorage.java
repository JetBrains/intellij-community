// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.FSOperations;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.jetbrains.jps.incremental.storage.FileTimestampStorage.FileTimestamp;
import static org.jetbrains.jps.incremental.storage.FileTimestampStorage.TimestampPerTarget;

final class FileTimestampStorage extends AbstractStateStorage<File, TimestampPerTarget[]> implements StampsStorage<FileTimestamp> {
  private final BuildTargetStateManager targetStateManager;
  private final Path timestampRoot;

  FileTimestampStorage(@NotNull Path dataStorageRoot, @NotNull BuildTargetStateManager targetStateManager) throws IOException {
    super(calcStorageRoot(dataStorageRoot).resolve("data").toFile(), new FileKeyDescriptor(), new StateExternalizer());
    timestampRoot = calcStorageRoot(dataStorageRoot);
    this.targetStateManager = targetStateManager;
  }

  private static @NotNull Path calcStorageRoot(Path dataStorageRoot) {
    return dataStorageRoot.resolve("timestamps");
  }

  @Override
  public Path getStorageRoot() {
    return timestampRoot;
  }

  @Override
  public FileTimestamp getCurrentStampIfUpToDate(@NotNull Path file, @NotNull BuildTarget<?> target, @Nullable BasicFileAttributes attrs) throws IOException {
    TimestampPerTarget[] state = getState(file.toFile());
    if (state == null) {
      return null;
    }

    int targetId = targetStateManager.getBuildTargetId(target);
    for (TimestampPerTarget timestampPerTarget : state) {
      if (timestampPerTarget.targetId == targetId) {
        long current = timestampPerTarget.timestamp;
        long timestamp = (attrs == null || !attrs.isRegularFile()) ? FSOperations.lastModified(file) : attrs.lastModifiedTime().toMillis();
        return current == timestamp ? FileTimestamp.fromLong(current) : null;
      }
    }
    return null;
  }

  @Override
  public void updateStamp(@NotNull Path file, BuildTarget<?> buildTarget, long currentFileTimestamp) throws IOException {
    int targetId = targetStateManager.getBuildTargetId(buildTarget);
    File ioFile = file.toFile();
    update(ioFile, updateTimestamp(getState(ioFile), targetId, currentFileTimestamp));
  }

  private static TimestampPerTarget @NotNull [] updateTimestamp(TimestampPerTarget[] oldState, final int targetId, long timestamp) {
    final TimestampPerTarget newItem = new TimestampPerTarget(targetId, timestamp);
    if (oldState == null) {
      return new TimestampPerTarget[]{newItem};
    }
    for (int i = 0, length = oldState.length; i < length; i++) {
      if (oldState[i].targetId == targetId) {
        oldState[i] = newItem;
        return oldState;
      }
    }
    return ArrayUtil.append(oldState, newItem);
  }

  @Override
  public void removeStamp(@NotNull Path file, BuildTarget<?> buildTarget) throws IOException {
    File ioFile = file.toFile();
    TimestampPerTarget[] state = getState(ioFile);
    if (state != null) {
      int targetId = targetStateManager.getBuildTargetId(buildTarget);
      for (int i = 0; i < state.length; i++) {
        TimestampPerTarget timestampPerTarget = state[i];
        if (timestampPerTarget.targetId == targetId) {
          if (state.length == 1) {
            remove(ioFile);
          }
          else {
            TimestampPerTarget[] newState = ArrayUtil.remove(state, i);
            update(ioFile, newState);
            break;
          }
        }
      }
    }
  }

  static final class TimestampPerTarget {
    public final int targetId;
    public final long timestamp;

    private TimestampPerTarget(int targetId, long timestamp) {
      this.targetId = targetId;
      this.timestamp = timestamp;
    }
  }

  private static final class StateExternalizer implements DataExternalizer<TimestampPerTarget[]> {
    @Override
    public void save(@NotNull DataOutput out, TimestampPerTarget[] value) throws IOException {
      out.writeInt(value.length);
      for (TimestampPerTarget target : value) {
        out.writeInt(target.targetId);
        out.writeLong(target.timestamp);
      }
    }

    @Override
    public TimestampPerTarget[] read(@NotNull DataInput in) throws IOException {
      int size = in.readInt();
      TimestampPerTarget[] targets = new TimestampPerTarget[size];
      for (int i = 0; i < size; i++) {
        int id = in.readInt();
        long timestamp = in.readLong();
        targets[i] = new TimestampPerTarget(id, timestamp);
      }
      return targets;
    }
  }

  static final class FileTimestamp {
    private final long myTimestamp;

    FileTimestamp(long timestamp) {
      myTimestamp = timestamp;
    }

    static @NotNull FileTimestamp fromLong(long l) {
      return new FileTimestamp(l);
    }

    @Override
    public String toString() {
      return "Timestamp{myTimestamp=" + myTimestamp + '}';
    }
  }
}
