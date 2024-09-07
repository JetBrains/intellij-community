// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final BuildTargetsState myTargetsState;
  private final Path timestampRoot;

  FileTimestampStorage(Path dataStorageRoot, BuildTargetsState targetsState) throws IOException {
    super(calcStorageRoot(dataStorageRoot).resolve("data").toFile(), new FileKeyDescriptor(), new StateExternalizer());
    timestampRoot = calcStorageRoot(dataStorageRoot);
    myTargetsState = targetsState;
  }

  private static Path calcStorageRoot(Path dataStorageRoot) {
    return dataStorageRoot.resolve("timestamps");
  }

  @Override
  public Path getStorageRoot() {
    return timestampRoot;
  }

  @Override
  public @Nullable FileTimestamp getPreviousStamp(@NotNull Path file, BuildTarget<?> target) throws IOException {
    TimestampPerTarget[] state = getState(file.toFile());
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (TimestampPerTarget timestampPerTarget : state) {
        if (timestampPerTarget.targetId == targetId) {
          return FileTimestamp.fromLong(timestampPerTarget.timestamp);
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull FileTimestamp getCurrentStamp(@NotNull Path file) {
    return FileTimestamp.fromLong(FSOperations.lastModified(file));
  }

  @Override
  public boolean isDirtyStamp(@NotNull Stamp stamp, @NotNull Path file) {
    return !(stamp instanceof FileTimestamp) || ((FileTimestamp)stamp).myTimestamp != FSOperations.lastModified(file);
  }

  @Override
  public boolean isDirtyStamp(@Nullable Stamp stamp, @NotNull Path file, @NotNull BasicFileAttributes attrs) {
    if (!(stamp instanceof FileTimestamp)) return true;
    FileTimestamp timestamp = (FileTimestamp) stamp;
    // for symlinks, the attr structure reflects the symlink's timestamp and not symlink's target timestamp
    return attrs.isRegularFile() ? attrs.lastModifiedTime().toMillis() != timestamp.myTimestamp : isDirtyStamp(timestamp, file);
  }

  @Override
  public void saveStamp(@NotNull Path file, BuildTarget<?> buildTarget, @NotNull FileTimestamp stamp) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(buildTarget);
    File ioFile = file.toFile();
    update(ioFile, updateTimestamp(getState(ioFile), targetId, stamp.asLong()));
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
      int targetId = myTargetsState.getBuildTargetId(buildTarget);
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

  static final class FileTimestamp implements StampsStorage.Stamp {
    private final long myTimestamp;

    FileTimestamp(long timestamp) {
      myTimestamp = timestamp;
    }

    long asLong() {
      return myTimestamp;
    }

    static @NotNull FileTimestamp fromLong(long l) {
      return new FileTimestamp(l);
    }

    @Override
    public String toString() {
      return "Timestamp{" +
             "myTimestamp=" + myTimestamp +
             '}';
    }
  }
}
