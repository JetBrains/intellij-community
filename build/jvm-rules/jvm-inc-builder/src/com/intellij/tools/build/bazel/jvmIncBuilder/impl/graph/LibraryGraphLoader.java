package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ClassDataZipEntry;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.SourceSnapshotImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Delta;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.impl.DeltaImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;
import org.jetbrains.jps.util.Pair;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class LibraryGraphLoader {
  private static final int CACHE_SIZE = 512; // todo: make configurable

  private static final LoadingCache<LibDescriptor, Pair<NodeSourceSnapshot, Graph>> ourCache = Caffeine
    .newBuilder()
    .softValues()
    .maximumSize(CACHE_SIZE)
    .build(desc -> loadReadonlyLibraryGraph(desc.loadPath));

  public static Pair<NodeSourceSnapshot, Graph> getLibraryGraph(NodeSource library, String digest, Path loadPath) {
    return ourCache.get(new LibDescriptor(library, digest, loadPath));
  }

  public static void clearSharedCache() {  // for tests
    ourCache.invalidateAll();
  }

  private static Pair<NodeSourceSnapshot, Graph> loadReadonlyLibraryGraph(Path jarPath) throws IOException {
    try (var is = new BufferedInputStream(Files.newInputStream(jarPath))) {
      return loadReadonlyLibraryGraph(new DeltaImpl(Set.of(), Set.of()) /*depGraph.createDelta(Set.of(), Set.of(), false)*/, ClassDataZipEntry.fromSteam(is));
    }
  }

  private static Pair<NodeSourceSnapshot, Graph> loadReadonlyLibraryGraph(Delta delta, Iterator<ClassDataZipEntry> entries) {
    // for this presentation, we use packages as 'node sources', and class files in the corresponding package as 'nodes'
    Map<String, Iterable<NodeSource>> sourcesMap = new HashMap<>();
    Map<NodeSource, List<Pair<String, Long>>> packagesMap = new HashMap<>();
    while (entries.hasNext()) {
      ClassDataZipEntry entry = entries.next();
      String parent = entry.getParent();
      if (parent != null) {
        var libNode = JvmClassNodeBuilder.createForLibrary(entry.getPath(), entry.getClassReader()).getResult();
        if (JvmClassNodeBuilder.isAbiNode(libNode)) { // with this check, the code is applicable for non-abi jars too
          Iterable<NodeSource> libSrc = sourcesMap.computeIfAbsent(parent, n -> Set.of(new PathSource(n)));
          delta.associate(libNode, libSrc);
          packagesMap.computeIfAbsent(libSrc.iterator().next(), s -> new ArrayList<>()).add(Pair.create(entry.getPath(), Hashing.xxh3_64().hashBytesToLong(entry.getContent())));
        }
      }
    }
    Map<NodeSource, String> snapshotMap = new HashMap<>(); // map of [packageName -> digest] where digest reflects the state of all classes currently present in the package
    HashStream64 stream = Hashing.xxh3_64().hashStream();
    for (Map.Entry<NodeSource, List<Pair<String, Long>>> entry : packagesMap.entrySet()) {
      List<Pair<String, Long>> nodes = entry.getValue();
      Collections.sort(nodes, Comparator.comparing(nameWithDigest -> nameWithDigest.first));
      stream.reset();
      for (Pair<String, Long> node : nodes) {
        stream.putString(node.first);
        stream.putLong(node.second);
      }
      snapshotMap.put(entry.getKey(), Long.toHexString(stream.getAsLong()));
    }
    return Pair.create(new SourceSnapshotImpl(snapshotMap), delta);
  }

  private static final class LibDescriptor {
    final @NotNull NodeSource library;
    final @NotNull String digest;
    final @NotNull Path loadPath;

    LibDescriptor(@NotNull NodeSource library, @NotNull String digest, @NotNull Path loadPath) {
      this.library = library;
      this.digest = digest;
      this.loadPath = loadPath;
    }

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
