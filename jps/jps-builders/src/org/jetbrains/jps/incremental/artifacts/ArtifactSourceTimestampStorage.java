package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * Stores timestamps for files included in artifacts. If an included file is located in a jar file timestamp of the jar file is stored.
 *
 * @author nik
 */
public class ArtifactSourceTimestampStorage extends AbstractStateStorage<String, ArtifactSourceTimestampStorage.PerArtifactTimestamp[]> {
  private static final DataExternalizer<PerArtifactTimestamp[]> TIMESTAMP_EXTERNALIZER = new DataExternalizer<PerArtifactTimestamp[]>() {
    @Override
    public void save(DataOutput out, PerArtifactTimestamp[] value) throws IOException {
      out.writeInt(value.length);
      for (PerArtifactTimestamp timestamp : value) {
        out.writeInt(timestamp.myArtifactId);
        out.writeLong(timestamp.myTimestamp);
      }
    }

    @Override
    public PerArtifactTimestamp[] read(DataInput in) throws IOException {
      final int size = in.readInt();
      final PerArtifactTimestamp[] value = new PerArtifactTimestamp[size];
      for (int i = 0; i < size; i++) {
        final int artifactId = in.readInt();
        final long timestamp = in.readLong();
        value[i] = new PerArtifactTimestamp(artifactId, timestamp);
      }
      return value;
    }
  };

  public ArtifactSourceTimestampStorage(@NonNls File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), TIMESTAMP_EXTERNALIZER);
  }

  public void markDirty(String filePath) throws IOException {
    update(filePath, null);
  }

  public void removeTimestamp(String filePath, final int artifactId) throws IOException {
    final PerArtifactTimestamp[] state = getState(filePath);
    if (state == null) return;
    for (int i = 0, length = state.length; i < length; i++) {
      if (state[i].myArtifactId == artifactId) {
        final PerArtifactTimestamp[] newState = ArrayUtil.remove(state, i);
        update(filePath, newState.length > 0 ? newState : null);
        break;
      }
    }
  }

  public void update(final int artifactId, String filePath, long timestamp) throws IOException {
    PerArtifactTimestamp[] oldState = getState(filePath);
    update(filePath, updateTimestamp(oldState, timestamp, artifactId));
  }

  @NotNull
  private static PerArtifactTimestamp[] updateTimestamp(PerArtifactTimestamp[] oldState,
                                                long timestamp, final int artifactId) {
    final PerArtifactTimestamp newItem = new PerArtifactTimestamp(
      artifactId, timestamp);
    if (oldState == null) {
      return new PerArtifactTimestamp[]{newItem};
    }
    for (int i = 0, length = oldState.length; i < length; i++) {
      if (oldState[i].myArtifactId == artifactId) {
        oldState[i] = newItem;
        return oldState;
      }
    }
    return ArrayUtil.append(oldState, newItem);
  }

  public static class PerArtifactTimestamp {
    public final int myArtifactId;
    public final long myTimestamp;

    public PerArtifactTimestamp(int artifactId, long timestamp) {
      myArtifactId = artifactId;
      myTimestamp = timestamp;
    }
  }
}
