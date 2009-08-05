package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

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

  @NotNull
  public Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.emptyList();
  }

  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }
}