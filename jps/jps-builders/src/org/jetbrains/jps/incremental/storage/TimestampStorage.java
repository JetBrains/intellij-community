// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class TimestampStorage extends AbstractStateStorage<File, TimestampStorage.TimestampPerTarget[]> implements Timestamps {
  private final BuildTargetsState myTargetsState;

  public TimestampStorage(File storePath, BuildTargetsState targetsState) throws IOException {
    super(storePath, new FileKeyDescriptor(), new StateExternalizer());
    myTargetsState = targetsState;
  }

  @Override
  public void force() {
    super.force();
  }

  @Override
  public void clean() throws IOException {
    super.clean();
  }

  @Override
  public long getStamp(File file, BuildTarget<?> target) throws IOException {
    final TimestampPerTarget[] state = getState(file);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (TimestampPerTarget timestampPerTarget : state) {
        if (timestampPerTarget.targetId == targetId) {
          return timestampPerTarget.timestamp;
        }
      }
    }
    return -1L;
  }

  @Override
  public void saveStamp(File file, BuildTarget<?> buildTarget, long timestamp) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(buildTarget);
    update(file, updateTimestamp(getState(file), targetId, timestamp));
  }

  @NotNull
  private static TimestampPerTarget[] updateTimestamp(TimestampPerTarget[] oldState, final int targetId, long timestamp) {
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

  public static class TimestampPerTarget {
    public final int targetId;
    public final long timestamp;

    public TimestampPerTarget(int targetId, long timestamp) {
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
}
