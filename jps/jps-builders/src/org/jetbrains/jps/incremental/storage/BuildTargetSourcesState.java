// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BuildListener;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompileContextImpl;
import org.jetbrains.jps.incremental.IncProjectBuilder;
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
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.jetbrains.jps.incremental.IncProjectBuilder.MAX_BUILDER_THREADS;
import static org.jetbrains.jps.incremental.storage.MurmurHashingService.HASH_SIZE;
import static org.jetbrains.jps.incremental.storage.MurmurHashingService.getStringHash;
import static org.jetbrains.jps.incremental.storage.ProjectStamps.PORTABLE_CACHES;

/**
 * Report the state of module sources from which this build was created. <b>This class created as experimental for
 * now.</b> It should help to solve the problem of detecting from which sources existing compilation outputs were
 * produced (it's the problem of usage portable caches and compilation outputs, produced by JPS).
 * E.g, a user has built project four commits ago and this report will help us to detect that compilation outputs
 * not belong to the current commit.
 *
 * <p>The output of the work is the file "sources_state" in the data storage root folder. To avoid problems with
 * handling by other plugins or systems the output is in JSON format. This report produces only if
 * {@link ProjectStamps#PORTABLE_CACHES} flag enabled and try to reuse the data calculated by {@link FileStampStorage}</p>
 *
 * <b>This is class can be changed or removed in future</b>
 */
@ApiStatus.Experimental
public class BuildTargetSourcesState implements BuildListener {
  private static final Logger LOG = Logger.getInstance(BuildTargetSourcesState.class);
  private static final String TARGET_SOURCES_STATE_FILE_NAME = "target_sources_state.json";
  private final ExecutorService myParallelBuildExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "TargetSourcesState Executor Pool", SharedThreadPool.getInstance(), MAX_BUILDER_THREADS);
  private final Map<String, BuildTarget<?>> myChangedBuildTargets;
  // Some modules can have same out folder for different BuildTarget's to avoid extra hash calculation collection will be used
  // There are no pre-calculated hashes for entries from this collection in FileStampStorage
  private final Map<String, byte[]> myCalculatedHashes;
  private final PathRelativizerService myRelativizer;
  private final BuildTargetIndex myBuildTargetIndex;
  private final BuildRootIndex myBuildRootIndex;
  private final ProjectStamps myProjectStamps;
  private final CompileContextImpl myContext;
  private final String myOutputFolderPath;
  private final File myTargetStateStorage;
  private final Type myTokenType;
  private final Gson gson;

  public BuildTargetSourcesState(@NotNull CompileContextImpl context) {
    gson = new Gson();
    myContext = context;
    myCalculatedHashes = new ConcurrentHashMap<>();
    myChangedBuildTargets = new ConcurrentHashMap<>();

    ProjectDescriptor pd = myContext.getProjectDescriptor();
    myProjectStamps = pd.getProjectStamps();
    myBuildRootIndex = pd.getBuildRootIndex();
    myBuildTargetIndex = pd.getBuildTargetIndex();
    myRelativizer = pd.dataManager.getRelativizer();
    myOutputFolderPath = getOutputFolderPath(pd.getProject());

    BuildDataPaths dataPaths = pd.getTargetsState().getDataPaths();
    myTargetStateStorage = new File(dataPaths.getDataStorageRoot(), TARGET_SOURCES_STATE_FILE_NAME);
    myTokenType = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType();

    // Subscribe to events for reporting only changed build targets
    myContext.addBuildListener(this);
  }

  public void reportSourcesState() {
    if (reportStateUnavailable()) return;

    long start = System.nanoTime();
    Map<String, Map<String, BuildTargetState>> targetTypeHashMap = loadCurrentTargetState();

    List<BuildTarget<?>> buildTargets;
    if (targetTypeHashMap.isEmpty()) {
      buildTargets = myBuildTargetIndex.getAllTargets();
    } else {
      List<BuildTarget<?>> changedBuildTargets = new ArrayList<>(myChangedBuildTargets.values());
      LOG.info("List of changed build targets: " + changedBuildTargets);
      buildTargets = changedBuildTargets;
    }

    ContainerUtil.map(buildTargets, target -> {
      return myParallelBuildExecutor.submit(() -> {
        BuildTargetType<?> buildTargetType = target.getTargetType();
        String typeTypeId = buildTargetType.getTypeId();

        getBuildTargetHash(target, myContext).ifPresent(buildTargetHash -> {
          String hexString = StringUtil.toHexString(buildTargetHash);

          // Now in project each build target has single output root
          String relativePath = target.getOutputRoots(myContext).stream()
                                                                .map(file -> myRelativizer.toRelative(file.getAbsolutePath()))
                                                                .findFirst().orElse("");
          synchronized (targetTypeHashMap) {
            targetTypeHashMap.computeIfAbsent(typeTypeId, key -> new HashMap<>()).put(target.getId(), new BuildTargetState(hexString, relativePath));
          }
        });
      });
    }).forEach(future -> {
      try {
        future.get();
      }
      catch (InterruptedException | ExecutionException e) {
        LOG.warn("Unable to get the result from future", e);
      }
    });

    try {
      FileUtil.writeToFile(myTargetStateStorage, gson.toJson(targetTypeHashMap));
    }
    catch (IOException e) {
      LOG.warn("Unable to save sources state", e);
    }
    LOG.info("Build target sources report took: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
  }

  public void clearSourcesState() {
    if (reportStateUnavailable()) return;
    if (myTargetStateStorage.exists()) {
      LOG.info("Clear build target sources report");
      FileUtil.delete(myTargetStateStorage);
    }
  }

  @Override
  public void filesGenerated(@NotNull FileGeneratedEvent event) {
    if (reportStateUnavailable()) return;
    BuildTarget<?> sourceTarget = event.getSourceTarget();
    String key = sourceTarget.getTargetType().getTypeId() + " " +sourceTarget.getId();
    myChangedBuildTargets.put(key, sourceTarget);
  }

  @Override
  public void filesDeleted(@NotNull FileDeletedEvent event) {
    if (reportStateUnavailable()) return;
    event.getFilePaths().stream().map(path -> new File(FileUtil.toSystemDependentName(path)))
      .map(file -> myBuildRootIndex.findAllParentDescriptors(file, myContext))
      .flatMap(collection -> collection.stream())
      .forEach(buildRootDesc -> {
        BuildTarget<?> target = buildRootDesc.getTarget();
        String key = target.getTargetType().getTypeId() + target.getId();
        myChangedBuildTargets.put(key, target);
      });
  }

  @NotNull
  private Optional<byte[]> getBuildTargetHash(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    Function<File, List<byte[]>> compilationOutputHashCalculationFunction = rootFile -> {
      try {
        if (!rootFile.exists()) return null;

        List<byte[]> targetRootHashes = new ArrayList<>();
        Files.walkFileTree(rootFile.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            String filePathString = path.toString();
            if (filePathString.endsWith(".class")) {
              byte[] calculatedHash = myCalculatedHashes.get(filePathString);
              if (calculatedHash != null) {
                targetRootHashes.add(calculatedHash);
              }
              else {
                File file = path.toFile();
                getOutputFileHash(file, rootFile).ifPresent(hash -> {
                  targetRootHashes.add(hash);
                  myCalculatedHashes.put(filePathString, hash);
                });
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
        return targetRootHashes;
      }
      catch (IOException e) {
        LOG.warn("Couldn't calculate build target hash for : " + target.getPresentableName(), e);
        return null;
      }
    };

    Function<BuildRootDescriptor, List<byte[]>> sourceRootHashCalculationFunction = rootDescriptor -> {
      try {
        File rootFile = rootDescriptor.getRootFile();
        if (!rootFile.exists() || rootFile.getAbsolutePath().startsWith(myOutputFolderPath)) return null;

        List<byte[]> targetRootHashes = new ArrayList<>();
        Files.walkFileTree(rootFile.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return myBuildRootIndex.isDirectoryAccepted(dir.toFile(), rootDescriptor)
                   ? FileVisitResult.CONTINUE
                   : FileVisitResult.SKIP_SUBTREE;
          }

          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            final File file = path.toFile();
            if (!myBuildRootIndex.isFileAccepted(file, rootDescriptor)) return FileVisitResult.CONTINUE;
            getFileHash(target, file, rootFile).ifPresent(targetRootHashes::add);
            return FileVisitResult.CONTINUE;
          }
        });
        return targetRootHashes;
      }
      catch (IOException e) {
        LOG.warn("Couldn't calculate build target hash for : " + target.getPresentableName(), e);
        return null;
      }
    };
    return Stream.concat(target.getOutputRoots(context).stream().map(compilationOutputHashCalculationFunction),
                         myBuildRootIndex.getTargetRoots(target, context).stream().map(sourceRootHashCalculationFunction))
      .filter(it -> !ContainerUtil.isEmpty(it))
      .flatMap(List::stream)
      .reduce(BuildTargetSourcesState::sum);
  }

  @NotNull
  private Optional<byte[]> getFileHash(@NotNull BuildTarget<?> target, @NotNull File file, @NotNull File rootPath) throws IOException {
    StampsStorage<? extends StampsStorage.Stamp> storage = myProjectStamps.getStampStorage();
    assert storage instanceof FileStampStorage;
    FileStampStorage fileStampStorage = (FileStampStorage)storage;
    byte[] fileHash = fileStampStorage.getStoredFileHash(file, target);
    if (fileHash == null) {
      return Optional.empty();
    }

    byte[] stringHash = getStringHash(toRelative(file, rootPath));
    return Optional.of(sum(stringHash, fileHash));
  }

  @NotNull
  private static Optional<byte[]> getOutputFileHash(@NotNull File file, @NotNull File rootPath) throws IOException {
    byte[] fileHash = MurmurHashingService.getFileHash(file);
    if (fileHash == null) {
      return Optional.empty();
    }

    byte[] stringHash = getStringHash(toRelative(file, rootPath));
    return Optional.of(sum(stringHash, fileHash));
  }

  @NotNull
  private Map<String, Map<String, BuildTargetState>> loadCurrentTargetState() {
    if (!myTargetStateStorage.exists()) return new HashMap<>();
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(myTargetStateStorage))) {
      Map<String, Map<String, BuildTargetState>> result = gson.fromJson(bufferedReader, myTokenType);
      if (result != null) return result;
    }
    catch (IOException e) {
      LOG.warn("Couldn't parse current build target state", e);
    }
    return new HashMap<>();
  }

  private boolean reportStateUnavailable() {
    return !PORTABLE_CACHES || myProjectStamps == null;
  }

  @NotNull
  private static String toRelative(@NotNull File target, @NotNull File rootPath) {
    return FileUtilRt.toSystemIndependentName(Paths.get(rootPath.getPath()).relativize(Paths.get(target.getPath())).toString());
  }

  @NotNull
  private static String getOutputFolderPath(JpsProject project) {
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(project);
    if (projectExtension == null) return "";
    String url = projectExtension.getOutputUrl();
    if (StringUtil.isEmpty(url)) return "";
    return JpsPathUtil.urlToFile(url).getAbsolutePath();
  }

  private static byte @NotNull [] sum(byte[] firstHash, byte[] secondHash) {
    byte[] result = firstHash != null ? firstHash : new byte[HASH_SIZE];
    for (int i = 0; i < result.length; i++) {
      result[i] += secondHash[i];
    }
    return result;
  }

  private static final class BuildTargetState {
    private final String hash;
    private final String relativePath;

    private BuildTargetState(String hash, String relativePath) {
      this.hash = hash;
      this.relativePath = relativePath;
    }
  }
}