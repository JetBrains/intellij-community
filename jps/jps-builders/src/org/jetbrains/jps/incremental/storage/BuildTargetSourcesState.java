// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

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
public class BuildTargetSourcesState {
  private static final Logger LOG = Logger.getInstance(BuildTargetSourcesState.class);
  private static final String TARGET_SOURCES_STATE_FILE_NAME = "target_sources_state.json";
  private final PathRelativizerService myRelativizer;
  private final BuildTargetIndex myBuildTargetIndex;
  private final BuildRootIndex myBuildRootIndex;
  private final ProjectStamps myProjectStamps;
  private final String myOutputFolderPath;
  private final File myTargetStateStorage;
  private final Gson gson;

  public BuildTargetSourcesState(@NotNull JpsProject project, @NotNull BuildTargetIndex buildTargetIndex, @NotNull BuildRootIndex buildRootIndex,
                                 ProjectStamps projectStamps, @NotNull BuildDataPaths dataPaths, @NotNull PathRelativizerService relativizer) {
    gson = new Gson();
    myRelativizer = relativizer;
    myProjectStamps = projectStamps;
    myBuildRootIndex = buildRootIndex;
    myOutputFolderPath = getOutputFolderPath(project);
    myBuildTargetIndex = buildTargetIndex;
    myTargetStateStorage = new File(dataPaths.getDataStorageRoot(), TARGET_SOURCES_STATE_FILE_NAME);
  }

  public void reportSourcesState(@NotNull CompileContext context) {
    if (!PORTABLE_CACHES || myProjectStamps == null) return;

    long start = System.currentTimeMillis();
    Map<String, Map<String, BuildTargetState>> targetTypeHashMap = new HashMap<>();
    myBuildTargetIndex.getAllTargets().forEach(target -> {
      BuildTargetType<?> buildTargetType = target.getTargetType();
      String typeTypeId = buildTargetType.getTypeId();

      getBuildTargetHash(target, context).ifPresent(buildTargetHash -> {
        String hexString = StringUtil.toHexString(buildTargetHash);

        // Now in project each build target has single output root
        String relativePath = target.getOutputRoots(context).stream().map(file -> myRelativizer.toRelative(file.getAbsolutePath())).findFirst().orElse("");
        targetTypeHashMap.computeIfAbsent(typeTypeId, key -> new HashMap<>()).put(target.getId(), new BuildTargetState(hexString, relativePath));
      });
    });

    try {
      FileUtil.writeToFile(myTargetStateStorage, gson.toJson(targetTypeHashMap));
    }
    catch (IOException e) {
      LOG.warn("Unable to save sources state", e);
    }
    LOG.info("Build target sources report took: " + (System.currentTimeMillis() - start) + " ms");
  }

  @NotNull
  private Optional<byte[]> getBuildTargetHash(@NotNull BuildTarget<?> target, @NotNull CompileContext context) {
    return myBuildRootIndex.getTargetRoots(target, context).stream().map(rootDescriptor -> {
      try {
        File rootFile = rootDescriptor.getRootFile();
        if (!rootFile.exists() || rootFile.getAbsolutePath().startsWith(myOutputFolderPath)) {
          return null;
        }

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
    }).filter(it -> !ContainerUtil.isEmpty(it)).flatMap(List::stream).reduce((acc, value) -> sum(acc, value));
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

  @NotNull
  private static byte[] sum(byte[] firstHash, byte[] secondHash) {
    byte[] result = firstHash != null ? firstHash : new byte[HASH_SIZE];
    for (int i = 0; i < result.length; i++) {
      result[i] += secondHash[i];
    }
    return result;
  }

  private static class BuildTargetState {
    private final String hash;
    private final String relativePath;

    private BuildTargetState(String hash, String relativePath) {
      this.hash = hash;
      this.relativePath = relativePath;
    }
  }
}