// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentMapBuilder;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class OutputToTargetRegistry extends AbstractStateStorage<Integer, IntSet> implements OutputToTargetMapping {
  private static final DataExternalizer<IntSet> DATA_EXTERNALIZER = new DataExternalizer<>() {
    @Override
    public void save(@NotNull DataOutput out, IntSet value) throws IOException {
      IntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        out.writeInt(iterator.nextInt());
      }
    }

    @Override
    public IntSet read(@NotNull DataInput in) throws IOException {
      IntSet result = new IntOpenHashSet();
      DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(in.readInt());
      }
      return result;
    }
  };
  private final PathRelativizerService relativizer;

  OutputToTargetRegistry(@NotNull Path storePath, PathRelativizerService relativizer) throws IOException {
    super(PersistentMapBuilder.newBuilder(storePath, EnumeratorIntegerDescriptor.INSTANCE, DATA_EXTERNALIZER));

    this.relativizer = relativizer;
  }

  void addMapping(@NotNull String outputPath, int buildTargetId) throws IOException {
    appendData(pathHashCode(outputPath), IntSets.singleton(buildTargetId));
  }

  void addMappings(int buildTargetId, @NotNull Collection<Path> outputPaths) throws IOException {
    IntSet set = IntSets.singleton(buildTargetId);
    for (Path outputPath : outputPaths) {
      appendData(pathHashCode(outputPath), set);
    }
  }

  @Override
  public void removeMappings(@NotNull Collection<String> outputPaths, int buildTargetId, @NotNull SourceToOutputMapping srcToOut) throws IOException {
    if (outputPaths.isEmpty()) {
      return;
    }

    for (String outputPath : outputPaths) {
      int key = pathHashCode(outputPath);
      synchronized (dataLock) {
        IntSet state = getState(key);
        if (state != null) {
          boolean removed = state.remove(buildTargetId);
          if (state.isEmpty()) {
            remove(key);
          }
          else if (removed) {
            update(key, state);
          }
        }
      }
    }
  }

  @Override
  public @NotNull Collection<String> removeTargetAndGetSafeToDeleteOutputs(Collection<String> outputPaths,
                                                                           int currentTargetId,
                                                                           @NotNull SourceToOutputMapping srcToOut) throws IOException {
    int size = outputPaths.size();
    if (size == 0) {
      return outputPaths;
    }

    List<String> result = new ArrayList<>(size);
    for (String outputPath : outputPaths) {
      int key = pathHashCode(outputPath);
      synchronized (dataLock) {
        IntSet state = getState(key);
        boolean isSafeToDelete = false;
        if (state == null) {
          isSafeToDelete = true;
        }
        else {
          boolean removed = state.remove(currentTargetId);
          if (state.isEmpty()) {
            remove(key);
            isSafeToDelete = true;
          }
          else if (removed) {
            update(key, state);
          }
        }
        if (isSafeToDelete) {
          result.add(outputPath);
        }
      }
    }
    return result;
  }

  private int pathHashCode(@NotNull String path) {
    String relativePath = relativizer.toRelative(path);
    if (ProjectStamps.PORTABLE_CACHES) {
      return Hashing.xxh3_64().hashBytesToInt(relativePath.getBytes(StandardCharsets.UTF_8));
    }
    else {
      return FileUtilRt.pathHashCode(relativePath);
    }
  }

  private int pathHashCode(@NotNull Path path) {
    String relativePath = relativizer.toRelative(path);
    if (ProjectStamps.PORTABLE_CACHES) {
      return Hashing.xxh3_64().hashBytesToInt(relativePath.getBytes(StandardCharsets.UTF_8));
    }
    else {
      return FileUtilRt.pathHashCode(relativePath);
    }
  }
}
