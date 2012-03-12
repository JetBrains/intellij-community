package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * Stores output paths for each source file path. If a source file is located inside a jar the path to the jar file is stored.
 * If an output file is located in a jar file the path to the jar file with '!/' suffix is stored.
 *
 * @author nik
 */
public class ArtifactSourceToOutputMapping extends AbstractStateStorage<String, String[]> {
  private static DataExternalizer<String[]> STRING_ARRAY_EXTERNALIZER = new DataExternalizer<String[]>() {
    private final byte[] myBuffer = IOUtil.allocReadWriteUTFBuffer();

    @Override
    public void save(DataOutput out, String[] value) throws IOException {
      out.writeInt(value.length);
      for (String path : value) {
        IOUtil.writeUTFFast(myBuffer, out, path);
      }
    }

    @Override
    public String[] read(DataInput in) throws IOException {
      final int size = in.readInt();
      String[] result = new String[size];
      for (int i = 0; i < size; i++) {
        final String path = IOUtil.readUTFFast(myBuffer, in);
        result[i] = path;
      }
      return result;
    }
  };

  public ArtifactSourceToOutputMapping(@NonNls File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), STRING_ARRAY_EXTERNALIZER);
  }
}
