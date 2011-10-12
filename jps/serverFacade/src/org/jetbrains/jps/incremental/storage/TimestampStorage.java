package org.jetbrains.jps.incremental.storage;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class TimestampStorage extends AbstractStateStorage<File, TimestampValidityState> {

  public TimestampStorage(File storePath) throws IOException {
    super(storePath, new FileKeyDescriptor(), new StateExternalizer());
  }

  public void saveStamp(File file) throws IOException {
    saveStamp(file, file.lastModified());
  }

  public long getStamp(File file) throws IOException {
    final TimestampValidityState state = getState(file);
    return state != null? state.getTimestamp() : -1L;
  }

  public void saveStamp(File file, long timestamp) throws IOException {
    update(file, new TimestampValidityState(timestamp));
  }

  private static class FileKeyDescriptor implements KeyDescriptor<File> {
    private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

    public void save(DataOutput out, File value) throws IOException {
      IOUtil.writeUTFFast(buffer, out, value.getPath());
    }

    public File read(DataInput in) throws IOException {
      return new File(IOUtil.readUTFFast(buffer, in));
    }

    public int getHashCode(File value) {
      return value.hashCode();
    }

    public boolean isEqual(File val1, File val2) {
      return val1.equals(val2);
    }
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
