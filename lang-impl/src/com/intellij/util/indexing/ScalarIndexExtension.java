package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A specialization of FileBasedIndexExtension allowing to create a mapping [DataObject -> List of files containing this object]
 *
 */
public abstract class ScalarIndexExtension<K> implements FileBasedIndexExtension<K, Void>{

  public static final DataExternalizer<Void> VOID_DATA_EXTERNALIZER = new VoidDataExternalizer();

  public final DataExternalizer<Void> getValueExternalizer() {
    return VOID_DATA_EXTERNALIZER;
  }

  private static class VoidDataExternalizer implements DataExternalizer<Void> {

    public void save(final DataOutput out, final Void value) throws IOException {
    }

    @Nullable
    public Void read(final DataInput in) throws IOException {
      return null;
    }
  }

  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }
}