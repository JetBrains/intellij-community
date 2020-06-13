// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.FSOperations;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;

import static org.jetbrains.jps.incremental.storage.FileTimestampStorage.Timestamp;
import static org.jetbrains.jps.incremental.storage.FileTimestampStorage.TimestampPerTarget;

/**
 * @author Eugene Zhuravlev
 */
public class FileTimestampStorage extends AbstractStateStorage<File, TimestampPerTarget[]> implements StampsStorage<Timestamp> {
  private final BuildTargetsState myTargetsState;
  private final File myTimestampsRoot;

  public FileTimestampStorage(File dataStorageRoot, BuildTargetsState targetsState) throws IOException {
    super(new File(calcStorageRoot(dataStorageRoot), "data"), new FileKeyDescriptor(), new StateExternalizer());
    myTimestampsRoot = calcStorageRoot(dataStorageRoot);
    myTargetsState = targetsState;
  }

  @NotNull
  private static File calcStorageRoot(File dataStorageRoot) {
    return new File(dataStorageRoot, "timestamps");
  }

  @Override
  public File getStorageRoot() {
    return myTimestampsRoot;
  }

  @Override
  public Timestamp getPreviousStamp(File file, BuildTarget<?> target) throws IOException {
    final TimestampPerTarget[] state = getState(file);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (TimestampPerTarget timestampPerTarget : state) {
        if (timestampPerTarget.targetId == targetId) {
          return Timestamp.fromLong(timestampPerTarget.timestamp);
        }
      }
    }
    return Timestamp.MINUS_ONE;
  }

  @Override
  public Timestamp getCurrentStamp(File file) {
    return Timestamp.fromLong(FSOperations.lastModified(file));
  }

  @Override
  public boolean isDirtyStamp(@NotNull Stamp stamp, File file) {
    if (!(stamp instanceof Timestamp)) return true;
    return ((Timestamp) stamp).myTimestamp != FSOperations.lastModified(file);
  }

  @Override
  public boolean isDirtyStamp(Stamp stamp, File file, @NotNull BasicFileAttributes attrs) {
    if (!(stamp instanceof Timestamp)) return true;
    Timestamp timestamp = (Timestamp) stamp;
    // for symlinks the attr structure reflects the symlink's timestamp and not symlink's target timestamp
    return attrs.isRegularFile() ? attrs.lastModifiedTime().toMillis() != timestamp.myTimestamp : isDirtyStamp(timestamp, file);
  }

  @Override
  public void saveStamp(File file, BuildTarget<?> buildTarget, Timestamp stamp) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(buildTarget);
    update(file, updateTimestamp(getState(file), targetId, stamp.asLong()));
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
  public void removeStamp(File file, BuildTarget<?> buildTarget) throws IOException {
    TimestampPerTarget[] state = getState(file);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(buildTarget);
      for (int i = 0; i < state.length; i++) {
        TimestampPerTarget timestampPerTarget = state[i];
        if (timestampPerTarget.targetId == targetId) {
          if (state.length == 1) {
            remove(file);
          }
          else {
            TimestampPerTarget[] newState = ArrayUtil.remove(state, i);
            update(file, newState);
            break;
          }
        }
      }
    }
  }

  static class TimestampPerTarget {
    public final int targetId;
    public final long timestamp;

    private TimestampPerTarget(int targetId, long timestamp) {
      this.targetId = targetId;
      this.timestamp = timestamp;
    }
  }

  private static class StateExternalizer implements DataExternalizer<TimestampPerTarget[]> {
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

  static class Timestamp implements StampsStorage.Stamp {
    static final Timestamp MINUS_ONE = new Timestamp(-1L);
    private final long myTimestamp;

    Timestamp(long timestamp) {
      myTimestamp = timestamp;
    }

    long asLong() {
      return myTimestamp;
    }

    static Timestamp fromLong(long l) {
      return new Timestamp(l);
    }

    @Override
    public String toString() {
      return "Timestamp{" +
             "myTimestamp=" + myTimestamp +
             '}';
    }
  }
}
