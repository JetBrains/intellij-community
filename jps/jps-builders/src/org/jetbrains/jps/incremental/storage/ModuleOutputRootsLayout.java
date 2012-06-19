package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.Pair;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/29/11
 */
public class ModuleOutputRootsLayout extends AbstractStateStorage<String, Pair<String, String>>{

  public ModuleOutputRootsLayout(File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), new PairDataExternalizer());
  }

  public void appendData(String s, Pair<String, String> data) throws IOException {
    update(s, data);
  }

  private static class PairDataExternalizer implements DataExternalizer<Pair<String, String>> {
    public void save(DataOutput out, Pair<String, String> value) throws IOException {
      IOUtil.writeString(value.getFirst(), out);
      IOUtil.writeString(value.getSecond(), out);
    }

    public Pair<String, String> read(DataInput in) throws IOException {
      final String first = IOUtil.readString(in);
      final String second = IOUtil.readString(in);
      return new Pair<String, String>(first, second);
    }
  }
}
