package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 *
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public final class SourceToOutputMapping extends AbstractStateStorage<String, Collection<String>> {

  public SourceToOutputMapping(File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), new StringCollectionExternalizer());
  }

  @Override
  public void update(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
    super.update(FileUtil.toSystemIndependentName(srcPath), normalizePaths(outputs));
  }

  public void update(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    super.update(FileUtil.toSystemIndependentName(srcPath), Collections.singleton(FileUtil.toSystemIndependentName(outputPath)));
  }

  public void appendData(String srcPath, String outputPath) throws IOException {
    super.appendData(FileUtil.toSystemIndependentName(srcPath), Collections.singleton(FileUtil.toSystemIndependentName(outputPath)));
  }

  public void appendData(String srcPath, Collection<String> data) throws IOException {
    super.appendData(FileUtil.toSystemIndependentName(srcPath), normalizePaths(data));
  }

  @Override
  public void remove(@NotNull String srcPath) throws IOException {
    super.remove(FileUtil.toSystemIndependentName(srcPath));
  }

  @Nullable
  @Override
  public Collection<String> getState(@NotNull String srcPath) throws IOException {
    return super.getState(FileUtil.toSystemIndependentName(srcPath));
  }

  protected static Collection<String> normalizePaths(Collection<String> outputs) {
    Collection<String> normalized = new ArrayList<String>(outputs.size());
    for (String out : outputs) {
      normalized.add(FileUtil.toSystemIndependentName(out));
    }
    return normalized;
  }

  protected static class StringCollectionExternalizer implements DataExternalizer<Collection<String>> {

    public void save(DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeString(str, out);
      }
    }

    public Collection<String> read(DataInput in) throws IOException {
      final List<String> result = new ArrayList<String>();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String str = IOUtil.readString(stream);
        result.add(str);
      }
      return result;
    }
  }
}
