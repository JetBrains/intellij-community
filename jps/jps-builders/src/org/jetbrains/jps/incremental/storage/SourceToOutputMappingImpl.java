package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;

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
public final class SourceToOutputMappingImpl extends AbstractStateStorage<String, Collection<String>> implements SourceToOutputMapping {

  public SourceToOutputMappingImpl(File storePath) throws IOException {
    super(storePath, new PathStringDescriptor(), new StringCollectionExternalizer());
  }

  @Override
  public void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
    super.update(FileUtil.toSystemIndependentName(srcPath), normalizePaths(outputs));
  }

  @Override
  public void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    super.update(FileUtil.toSystemIndependentName(srcPath), Collections.singleton(FileUtil.toSystemIndependentName(outputPath)));
  }

  @Override
  public void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    super.appendData(FileUtil.toSystemIndependentName(srcPath), Collections.singleton(FileUtil.toSystemIndependentName(outputPath)));
  }

  @Override
  public void appendData(@NotNull String srcPath, @NotNull Collection<String> data) throws IOException {
    super.appendData(FileUtil.toSystemIndependentName(srcPath), normalizePaths(data));
  }

  @Override
  public void remove(@NotNull String srcPath) throws IOException {
    super.remove(FileUtil.toSystemIndependentName(srcPath));
  }

  @Nullable
  @Override
  public Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
    return super.getState(FileUtil.toSystemIndependentName(srcPath));
  }

  @NotNull
  @Override
  public Collection<String> getSources() throws IOException {
    return getKeys();
  }

  private static Collection<String> normalizePaths(Collection<String> outputs) {
    Collection<String> normalized = new ArrayList<String>(outputs.size());
    for (String out : outputs) {
      normalized.add(FileUtil.toSystemIndependentName(out));
    }
    return normalized;
  }

  @Override
  public void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException {
    final Collection<String> outputPaths = getOutputs(FileUtil.toSystemIndependentName(sourcePath));
    if (outputPaths != null) {
      outputPaths.remove(FileUtil.toSystemIndependentName(outputPath));
      if (outputPaths.isEmpty()) {
        remove(sourcePath);
      }
      else {
        setOutputs(sourcePath, outputPaths);
      }
    }
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
