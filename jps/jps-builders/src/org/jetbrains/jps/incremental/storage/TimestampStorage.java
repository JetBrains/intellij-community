package org.jetbrains.jps.incremental.storage;

import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class TimestampStorage extends AbstractStateStorage<File, TimestampValidityState> implements Timestamps {

  public TimestampStorage(File storePath) throws IOException {
    super(storePath, new FileKeyDescriptor(), new StateExternalizer());
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
  public long getStamp(File file) throws IOException {
    final TimestampValidityState state = getState(file);
    return state != null? state.getTimestamp() : -1L;
  }

  @Override
  public void saveStamp(File file, long timestamp) throws IOException {
    update(file, new TimestampValidityState(timestamp));
  }

  public void removeStamp(File file) throws IOException {
    remove(file);
  }

  private static class StateExternalizer implements DataExternalizer<TimestampValidityState> {

    public void save(DataOutput out, TimestampValidityState value) throws IOException {
      value.save(out);
    }

    public TimestampValidityState read(DataInput in) throws IOException {
      return new TimestampValidityState(in);
    }
  }
}
