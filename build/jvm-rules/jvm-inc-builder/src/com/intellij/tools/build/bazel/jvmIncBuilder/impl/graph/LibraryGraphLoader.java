package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import com.dynatrace.hash4j.hashing.Hashing;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.VMFlags;
import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ClassDataZipEntry;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.SourceSnapshotImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Delta;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.impl.DeltaImpl;
import org.jetbrains.jps.dependency.impl.GraphImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;
import org.jetbrains.jps.util.Pair;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class LibraryGraphLoader {
  private static final int CACHE_SIZE = VMFlags.getLibraryGraphCacheSize();

  private static final LoadingCache<@NotNull LibDescriptor, Pair<NodeSourceSnapshot, Graph>> ourCache = Caffeine
    .newBuilder()
    .softValues()
    .maximumSize(CACHE_SIZE)
    .build(desc -> loadReadonlyLibraryGraph(desc.library, desc.loadPath));

  public static Pair<NodeSourceSnapshot, Graph> getLibraryGraph(NodeSource library, String digest, Path loadPath) {
    return ourCache.get(new LibDescriptor(library, digest, loadPath));
  }

  private static Pair<NodeSourceSnapshot, Graph> loadReadonlyLibraryGraph(NodeSource lib, Path jarPath) throws IOException {
    try (var is = new BufferedInputStream(Files.newInputStream(jarPath))) {
      return loadReadonlyLibraryGraph(lib, new DeltaImpl(Set.of(), Set.of(), GraphImpl.IndexFactory.mandatoryIndices()) /*depGraph.createDelta(Set.of(), Set.of(), false)*/, ClassDataZipEntry.fromSteam(is));
    }
  }

  private static Pair<NodeSourceSnapshot, Graph> loadReadonlyLibraryGraph(NodeSource lib, Delta delta, Iterator<ClassDataZipEntry> entries) {
    // for this presentation, we use packages within the given library as 'node sources', and class files in the corresponding package as 'nodes'
    Map<NodeSource, String> snapshotMap = new HashMap<>(); // map of [nodePath -> digest] where digest reflects the content state of the class Node
    String prefix = getLibraryPathPrefix(lib);
    while (entries.hasNext()) {
      ClassDataZipEntry entry = entries.next();
      String entryPath = entry.getPath();
      if (!ZipOutputBuilder.isDirectoryName(entryPath)) {
        var libNode = JvmClassNodeBuilder.createForLibrary(entryPath, entry.getClassReader()).getResult();
        if (JvmClassNodeBuilder.isAbiNode(libNode)) { // with this check, the code is applicable for non-abi jars too
          NodeSource nodeSource = new PathSource(prefix + entryPath);
          delta.associate(libNode, Set.of(nodeSource));
          snapshotMap.put(nodeSource, Long.toHexString(Hashing.xxh3_64().hashBytesToLong(entry.getContent())));
        }
      }
    }
    return Pair.create(new SourceSnapshotImpl(snapshotMap), delta);
  }

  private static @NotNull String getLibraryPathPrefix(NodeSource lib) {
    String path = lib.toString();
    int idx = path.lastIndexOf('/');
    if (idx <= 0) {
      return path + "!/";
    }
    // hash parent path to make the prefix shorter, yet distinct
    return Long.toHexString(Utils.digest(path.substring(0, idx))) + path.substring(idx) + "!/";
  }

  private record LibDescriptor(@NotNull NodeSource library, @NotNull String digest, @NotNull Path loadPath) {

    @Override
      public boolean equals(Object o) {
        if (!(o instanceof final LibDescriptor that)) {
          return false;
        }
        return Objects.equals(library, that.library) && Objects.equals(digest, that.digest);
      }

      @Override
      public int hashCode() {
        return Objects.hash(library, digest);
      }
    }
}
