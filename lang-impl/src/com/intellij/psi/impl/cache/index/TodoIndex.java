package com.intellij.psi.impl.cache.index;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public class TodoIndex implements FileBasedIndexExtension<TodoIndexEntry, Integer> {

  private final PersistentEnumerator.DataDescriptor<TodoIndexEntry> myKeyDescriptor = new PersistentEnumerator.DataDescriptor<TodoIndexEntry>() {
    public int getHashCode(final TodoIndexEntry value) {
      return value.hashCode();
    }

    public boolean isEqual(final TodoIndexEntry val1, final TodoIndexEntry val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final TodoIndexEntry value) throws IOException {
      out.writeUTF(value.pattern);
      out.writeBoolean(value.caseSensitive);
    }

    public TodoIndexEntry read(final DataInput in) throws IOException {
      final String pattern = in.readUTF();
      final boolean caseSensitive = in.readBoolean();
      return new TodoIndexEntry(pattern, caseSensitive);
    }
  };
  
  private DataExternalizer<Integer> myValueExternalizer = new DataExternalizer<Integer>() {
    public void save(final DataOutput out, final Integer value) throws IOException {
      out.writeInt(value.intValue());
    }

    public Integer read(final DataInput in) throws IOException {
      return new Integer(in.readInt());
    }
  };
  
  public int getVersion() {
    return 1;
  }

  public String getName() {
    return "TodoIndex";
  }

  public DataIndexer<TodoIndexEntry, Integer, FileBasedIndex.FileContent> getIndexer() {
    return null;
  }

  public PersistentEnumerator.DataDescriptor<TodoIndexEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public DataExternalizer<Integer> getValueExternalizer() {
    return myValueExternalizer;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return null;
  }

}
