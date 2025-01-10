// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BuildListener;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FileHashUtil;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.service.SharedThreadPool;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

import static org.jetbrains.jps.incremental.IncProjectBuilder.MAX_BUILDER_THREADS;
import static org.jetbrains.jps.incremental.storage.ProjectStamps.PORTABLE_CACHES;

/**
 * Report the state of module sources from which this build was created. <b>This class created as experimental for
 * now.</b> It should help to solve the problem of detecting from which source existing compilation outputs were
 * produced (it's the problem of usage of portable caches and compilation outputs, produced by JPS).
 * E.g., a user built project four commits ago, and this report will help us to detect that compilation outputs
 * not belong to the current commit.
 *
 * <p>The output of the work is the file "sources_state" in the data storage root folder. To avoid problems with
 * handling by other plugins or systems, the output is in JSON format. This report produces only if
 * {@link ProjectStamps#PORTABLE_CACHES} flag enabled and try to reuse the data calculated by {@link HashStampStorage}</p>
 *
 * <b>This is class can be changed or removed in future</b>
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class BuildTargetSourcesState implements BuildListener {
  private static final Logger LOG = Logger.getInstance(BuildTargetSourcesState.class);
  public static final String TARGET_SOURCES_STATE_FILE_NAME = "target_sources_state.json";
  private final ExecutorService parallelBuildExecutor = SharedThreadPool.getInstance().createBoundedExecutor(
    "TargetSourcesState Executor Pool", MAX_BUILDER_THREADS);
  private final Map<String, BuildTarget<?>> changedBuildTargets = new ConcurrentHashMap<>();
  // Some modules can have same out folder for different BuildTarget's to avoid an extra hash calculation collection will be used
  // There are no pre-calculated hashes for entries from this collection in FileStampStorage
  private final Map<String, Long> calculatedHashes = new ConcurrentHashMap<>();
  private final BuildTargetIndex buildTargetIndex;
  private final BuildRootIndex buildRootIndex;
  private final BuildDataManager dataManager;
  private final CompileContext context;
  private final String outputFolderPath;
  private final Path targetStateStorage;

  public BuildTargetSourcesState(@NotNull CompileContext context) {
    this.context = context;

    ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    dataManager = projectDescriptor.dataManager;
    buildRootIndex = projectDescriptor.getBuildRootIndex();
    buildTargetIndex = projectDescriptor.getBuildTargetIndex();
    outputFolderPath = getOutputFolderPath(projectDescriptor.getProject());

    targetStateStorage = dataManager.getDataPaths().getDataStorageDir().resolve(TARGET_SOURCES_STATE_FILE_NAME);

    // subscribe to events for reporting only changed build targets
    context.addBuildListener(this);
  }

  public void reportSourcesState() {
    if (reportStateUnavailable()) {
      return;
    }

    long start = System.nanoTime();
    Map<String, Map<String, BuildTargetState>> targetTypeHashMap = loadCurrentTargetState();

    List<BuildTarget<?>> buildTargets;
    if (targetTypeHashMap.isEmpty()) {
      buildTargets = buildTargetIndex.getAllTargets();
    }
    else {
      List<BuildTarget<?>> changedBuildTargets = new ArrayList<>(this.changedBuildTargets.values());
      LOG.info("List of changed build targets: " + changedBuildTargets);
      buildTargets = changedBuildTargets;
    }

    @Unmodifiable @NotNull List<? extends Future<?>> result;
    if (buildTargets.isEmpty()) {
      result = Collections.emptyList();
    }
    else {
      PathRelativizerService relativizer = dataManager.getRelativizer();
      List<Future<?>> list = new ArrayList<>(buildTargets.size());
      for (BuildTarget<?> t : buildTargets) {
        list.add(parallelBuildExecutor.submit(() -> {
          String targetTypeId = t.getTargetType().getTypeId();
          long buildTargetHash = getBuildTargetHash(t, context);

          // now in a project, each build target has a single output root
          String relativePath = "";
          for (File file : t.getOutputRoots(context)) {
            relativePath = relativizer.toRelative(file.getAbsolutePath());
            break;
          }

          synchronized (targetTypeHashMap) {
            targetTypeHashMap.computeIfAbsent(targetTypeId, key -> new HashMap<>())
              .put(t.getId(), new BuildTargetState(buildTargetHash, relativePath));
          }
        }));
      }
      result = list;
    }
    // now in a project, each build target has a single output root
    for (Future<?> future : result) {
      try {
        future.get();
      }
      catch (InterruptedException | ExecutionException e) {
        LOG.warn("Unable to get the result from future", e);
      }
    }
    clearRemovedBuildTargets(targetTypeHashMap);
    try {
      Files.createDirectories(targetStateStorage.getParent());
      try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(targetStateStorage))) {
        writeJson(writer, targetTypeHashMap);
      }
    }
    catch (IOException e) {
      LOG.warn("Unable to save sources state", e);
    }
    LOG.info("Build target sources report took: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
  }

  private void clearRemovedBuildTargets(Map<String, Map<String, BuildTargetState>> targetsMap) {
    Map<String, List<String>> allTargets = new HashMap<>();
    for (BuildTarget<?> it : buildTargetIndex.getAllTargets()) {
      String id = it.getId();
      allTargets.computeIfAbsent(it.getTargetType().getTypeId(), k -> new ArrayList<>()).add(id);
    }
    targetsMap.keySet().removeIf(targetTypeId -> !allTargets.containsKey(targetTypeId));
    for (Map.Entry<String, Map<String, BuildTargetState>> entry : targetsMap.entrySet()) {
      String targetTypeId = entry.getKey();
      Map<String, BuildTargetState> targetStates = entry.getValue();
      targetStates.keySet().removeIf(targetId -> !allTargets.get(targetTypeId).contains(targetId));
    }
  }

  public void clearSourcesState() {
    if (reportStateUnavailable()) {
      return;
    }
    if (Files.exists(targetStateStorage)) {
      try {
        if (Files.deleteIfExists(targetStateStorage)) {
          LOG.info("Clear build target sources report");
        }
      }
      catch (IOException ignore) {
      }
    }
  }

  @Override
  public void filesGenerated(@NotNull FileGeneratedEvent event) {
    if (reportStateUnavailable()) {
      return;
    }

    BuildTarget<?> sourceTarget = event.getSourceTarget();
    String key = sourceTarget.getTargetType().getTypeId() + " " +sourceTarget.getId();
    changedBuildTargets.put(key, sourceTarget);
  }

  @Override
  public void filesDeleted(@NotNull FileDeletedEvent event) {
    if (reportStateUnavailable()) {
      return;
    }

    for (String path : event.getFilePaths()) {
      File file = new File(FileUtilRt.toSystemDependentName(path));
      Collection<BuildRootDescriptor> collection = buildRootIndex.findAllParentDescriptors(file, context);
      for (BuildRootDescriptor buildRootDesc : collection) {
        BuildTarget<?> target = buildRootDesc.getTarget();
        String key = target.getTargetType().getTypeId() + target.getId();
        changedBuildTargets.put(key, target);
      }
    }
  }

  private void compilationOutputHash(@NotNull Path rootFile,
                                     @NotNull BuildTarget<?> target,
                                     @NotNull LongArrayList hash,
                                     @NotNull HashStream64 hashToReuse) {
    try {
      if (Files.notExists(rootFile)) {
        return;
      }

      Files.walkFileTree(rootFile, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          String filePathString = file.toString();
          if (filePathString.endsWith(".class")) {
            Long calculatedHash = calculatedHashes.get(filePathString);
            long outputFileHash;
            if (calculatedHash == null) {
              outputFileHash = getOutputFileHash(file, rootFile, hashToReuse);
              calculatedHashes.put(filePathString, outputFileHash);
            }
            else {
              outputFileHash = calculatedHash;
            }
            hash.add(outputFileHash);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
    catch (IOException e) {
      LOG.warn("Couldn't calculate build target hash for : " + target.getPresentableName(), e);
    }
  }

  private void sourceRootHash(@NotNull BuildRootDescriptor rootDescriptor,
                              @NotNull BuildTarget<?> target,
                              @NotNull LongArrayList hash,
                              @NotNull HashStream64 hashToReuse) {
    try {
      Path rootFile = rootDescriptor.getFile();
      if (Files.notExists(rootFile) || rootFile.toAbsolutePath().startsWith(outputFolderPath)) {
        return;
      }

      StampsStorage<?> stStorage = dataManager.getFileStampStorage(target);
      if (stStorage instanceof HashStampStorage) {
        HashStampStorage stampStorage = (HashStampStorage)stStorage;
        Files.walkFileTree(rootFile, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return buildRootIndex.isDirectoryAccepted(dir, rootDescriptor) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
          }

          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
            if (!buildRootIndex.isFileAccepted(path, rootDescriptor)) {
              return FileVisitResult.CONTINUE;
            }
            getFileHash(path, rootFile, hash, hashToReuse, stampStorage);
            return FileVisitResult.CONTINUE;
          }
        });
      }
    }
    catch (IOException e) {
      LOG.warn("Couldn't calculate build target hash for : " + target.getPresentableName(), e);
    }
  }

  private long getBuildTargetHash(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    LongArrayList hash = new LongArrayList();
    HashStream64 hashToReuse = Hashing.komihash5_0().hashStream();
    for (File root : target.getOutputRoots(context)) {
      compilationOutputHash(root.toPath(), target, hash, hashToReuse);
    }
    for (BuildRootDescriptor root : buildRootIndex.getTargetRoots(target, context)) {
      sourceRootHash(root, target, hash, hashToReuse);
    }

    hash.sort(null);

    return hashToReuse
      .reset()
      .putLongs(hash.elements(), 0, hash.size())
      .putInt(hash.size())
      .getAsLong();
  }

  private static void getFileHash(@NotNull Path path,
                                  @NotNull Path rootFile,
                                  @NotNull LongArrayList hash,
                                  @NotNull HashStream64 hashToReuse,
                                  @NotNull HashStampStorage stampStorage) {
    HashStamp stamp = stampStorage.getStoredFileStamp(path);
    if (stamp == null) {
      return;
    }

    String relativePath = toRelative(path, rootFile);
    if (relativePath.isEmpty()) {
      return;
    }

    hash.add(hashToReuse
               .reset()
               .putLong(stamp.hash)
               .putString(relativePath)
               .getAsLong());
  }

  private static long getOutputFileHash(@NotNull Path file, @NotNull Path rootPath, @NotNull HashStream64 hashToReuse) throws IOException {
    // reduce GC - reuse hashToReuse - do not inline fileHash variable
    FileHashUtil.getFileHash(file, hashToReuse.reset());
    long fileHash = hashToReuse.getAsLong();
    return hashToReuse
      .reset()
      .putLong(fileHash)
      .putString(toRelative(file, rootPath))
      .getAsLong();
  }

  private @NotNull Map<String, Map<String, BuildTargetState>> loadCurrentTargetState() {
    try (BufferedReader reader = Files.newBufferedReader(targetStateStorage)) {
      return readJson(new JsonReader(reader));
    }
    catch (NoSuchFileException ignore) {
    }
    catch (Throwable e) {
      LOG.warn("Couldn't parse current build target state", e);
    }
    return new HashMap<>();
  }

  private static boolean reportStateUnavailable() {
    return !PORTABLE_CACHES;
  }

  private static @NotNull String toRelative(@NotNull Path target, @NotNull Path rootPath) {
    return FileUtilRt.toSystemIndependentName(rootPath.relativize(target).toString());
  }

  private static @NotNull String getOutputFolderPath(JpsProject project) {
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(project);
    if (projectExtension == null) {
      return "";
    }

    String url = projectExtension.getOutputUrl();
    if (url == null || url.isEmpty()) {
      return "";
    }
    return JpsPathUtil.urlToFile(url).getAbsolutePath();
  }

  public static @NotNull Map<String, Map<String, BuildTargetState>> readJson(JsonReader reader) throws IOException {
    reader.beginObject();
    Map<String, Map<String, BuildTargetState>> result = new HashMap<>();
    while (reader.hasNext()) {
      String category = reader.nextName();

      reader.beginObject();
      Map<String, BuildTargetState> moduleNameToDescriptor = new HashMap<>();
      while (reader.hasNext()) {
        String moduleName = reader.nextName();
        readModule(reader, moduleNameToDescriptor, moduleName);
        result.put(category, moduleNameToDescriptor);
      }
      reader.endObject();
    }
    reader.endObject();
    return result;
  }

  private static void readModule(JsonReader reader,
                                 Map<String, BuildTargetState> moduleNameToDescriptor,
                                 String moduleName) throws IOException {
    reader.beginObject();
    long hash = -1;
    boolean hasHash = false;
    String relativePath = null;
    while (reader.hasNext()) {
      String propertyName = reader.nextName();
      switch (propertyName) {
        case "relativePath":
          relativePath = reader.nextString();
          break;
        case "h":
          hash = reader.nextLong();
          hasHash = true;
          break;
        case "hash":
          reader.skipValue();
          break;
        default:
          LOG.warn("Unknown property: " + propertyName);
          reader.skipValue();
          break;
      }
    }
    reader.endObject();

    if (hasHash && relativePath != null) {
      moduleNameToDescriptor.put(moduleName, new BuildTargetState(hash, relativePath));
    }
  }

  @VisibleForTesting
  public static void writeJson(JsonWriter writer, Map<String, Map<String, BuildTargetState>> map) throws IOException {
    String[] keys = ArrayUtilRt.toStringArray(map.keySet());
    Arrays.sort(keys);

    writer.beginObject();
    for (String category : keys) {
      writer.name(category);

      Map<String, BuildTargetState> subMap = map.get(category);
      String[] modules = ArrayUtilRt.toStringArray(subMap.keySet());
      writer.beginObject();
      for (String module : modules) {
        writer.name(module);

        BuildTargetState state = subMap.get(module);
        writer.beginObject();
        writer.name("h").value(state.hash);
        writer.name("relativePath").value(state.relativePath);
        writer.endObject();
      }
      writer.endObject();
    }
    writer.endObject();
  }
}