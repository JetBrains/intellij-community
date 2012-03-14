package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.SmartList;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.*;
import java.util.List;

/**
 * Stores output paths for each source file path. If a source file or an output file is located inside a jar the path to the jar file is stored.
 * //If an output file is located in a jar file the full path is stored using '!/' to separate path to the jar file from path to file inside the jar.
 *
 * @author nik
 */
public class ArtifactSourceToOutputMapping extends AbstractStateStorage<String, List<String>> {
  private static DataExternalizer<List<String>> STRING_LIST_EXTERNALIZER = new DataExternalizer<List<String>>() {
    private final byte[] myBuffer = IOUtil.allocReadWriteUTFBuffer();

    @Override
    public void save(DataOutput out, List<String> value) throws IOException {
      for (String path : value) {
        IOUtil.writeUTFFast(myBuffer, out, path);
      }
    }

    @Override
    public List<String> read(DataInput in) throws IOException {
      List<String> result = new SmartList<String>();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(IOUtil.readUTFFast(myBuffer, stream));
      }
      return result;
    }
  };

  public ArtifactSourceToOutputMapping(@NonNls File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), STRING_LIST_EXTERNALIZER);
  }

  public void removeValue(String sourcePath, String outputPath) throws IOException {
    final List<String> outputPaths = getState(sourcePath);
    if (outputPaths != null) {
      outputPaths.remove(outputPath);
      if (outputPaths.isEmpty()) {
        remove(sourcePath);
      }
      else {
        update(sourcePath, outputPaths);
      }
    }
  }
}
