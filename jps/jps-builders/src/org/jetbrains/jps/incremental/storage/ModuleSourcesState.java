// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static org.jetbrains.jps.incremental.storage.Md5HashingService.*;
import static org.jetbrains.jps.incremental.storage.ProjectStamps.PORTABLE_CACHES;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

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
public class ModuleSourcesState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.ModuleSourcesState");
  private static final List<JpsModuleSourceRootType<?>> PRODUCTION_SOURCE_ROOTS = Arrays.asList(SOURCE, RESOURCE);
  private static final List<JpsModuleSourceRootType<?>> TEST_SOURCE_ROOTS = Arrays.asList(TEST_SOURCE, TEST_RESOURCE);
  private static final String SOURCES_STATE_FILE_NAME = "sources_state.json";
  private final IgnoredFileIndex myIgnoredFileIndex;
  private final ProjectStamps myProjectStamps;
  private final File mySourceStateStorageRoot;
  private final JpsProject myProject;
  private final Gson gson;

  public ModuleSourcesState(@NotNull JpsProject project, ProjectStamps projectStamps, @NotNull BuildDataPaths dataPaths, @NotNull IgnoredFileIndex ignoredFileIndex) {
    gson = new Gson();
    myProject = project;
    myProjectStamps = projectStamps;
    mySourceStateStorageRoot = new File(dataPaths.getDataStorageRoot(), SOURCES_STATE_FILE_NAME);
    myIgnoredFileIndex = ignoredFileIndex;
  }

  public void reportSourcesState() {
    if (!PORTABLE_CACHES && myProjectStamps == null) return;

    try {
      Map<String, String> productionModulesHash = new HashMap<>();
      Map<String, String> testModulesHash = new HashMap<>();
      myProject.getModules().forEach(module -> {
        getModuleHash(module, PRODUCTION_SOURCE_ROOTS)
          .ifPresent(moduleHash -> productionModulesHash.put(module.getName(), convertToStringRepr(moduleHash)));
        getModuleHash(module, TEST_SOURCE_ROOTS)
          .ifPresent(moduleHash -> testModulesHash.put(module.getName(), convertToStringRepr(moduleHash)));
      });

      SourcesState state = new SourcesState(productionModulesHash, testModulesHash);
      FileUtil.writeToFile(mySourceStateStorageRoot, gson.toJson(state));
    }
    catch (IOException e) {
      LOG.warn("Unable to save sources state", e);
    }
  }

  @NotNull
  private Optional<byte[]> getModuleHash(@NotNull JpsModule module, @NotNull List<JpsModuleSourceRootType<?>> rootTypeList) {
    return rootTypeList.stream().map(rootType -> getSourceRootHash(module, rootType))
      .filter(Objects::nonNull)
      .reduce((acc, value) -> sum(acc, value));
  }

  private byte[] getSourceRootHash(@NotNull JpsModule module, @NotNull JpsModuleSourceRootType<?> rootType) {
    byte[] rootHash = null;
    Iterator<? extends JpsTypedModuleSourceRoot<?>> sourceRootIterator = module.getSourceRoots(rootType).iterator();
    try {
      while (sourceRootIterator.hasNext()) {
        File rootFile = sourceRootIterator.next().getFile();
        if (!rootFile.exists()) continue;
        Set<File> fileList = new HashSet<>();
        getFilesRecursively(rootFile, fileList);
        for (File file : fileList) {
          rootHash = sum(rootHash, getFileHash(file, rootFile));
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Couldn't calculate sources hash for module: " + module.getName(), e);
    }
    return rootHash;
  }

  private void getFilesRecursively(@NotNull File baseDir, @NotNull Set<File> result) {
    final File[] children = baseDir.listFiles();
    if (children == null) return;
    for (File child : children) {
      if (child.isDirectory()) {
        getFilesRecursively(child, result);
        return;
      }
      if (myIgnoredFileIndex.isIgnored(child.getName())) continue;
      result.add(child);
    }
  }

  @NotNull
  private byte[] getFileHash(@NotNull File file, @NotNull File rootPath) throws IOException {
    StampsStorage<? extends StampsStorage.Stamp> storage = myProjectStamps.getStampStorage();
    assert storage instanceof FileStampStorage;
    FileStampStorage fileStampStorage = (FileStampStorage)storage;
    byte[] fileHash = fileStampStorage.getStoredFileHash(file);
    fileHash = fileHash != null ? fileHash : Md5HashingService.getFileHash(file);
    byte[] stringHash = getStringHash(toRelative(file, rootPath));
    return sum(stringHash, fileHash);
  }

  private static String toRelative(File target, File rootPath) {
    return FileUtilRt.toSystemIndependentName(Paths.get(rootPath.getPath()).relativize(Paths.get(target.getPath())).toString());
  }

  private static byte[] sum(byte[] firstHash, byte[] secondHash) {
    byte[] result = firstHash != null ? firstHash : new byte[getHashSize()];
    for (int i = 0; i < result.length; i++) {
      result[i] += secondHash[i];
    }
    return result;
  }

  private static String convertToStringRepr(byte[] hash) {
    //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }

  public static class SourcesState {
    private final Map<String, String> production;
    private final Map<String, String> test;

    public SourcesState(Map<String, String> production, Map<String, String> test) {
      this.production = production;
      this.test = test;
    }

    public Map<String, String> getProduction() {
      return production;
    }

    public Map<String, String> getTest() {
      return test;
    }
  }
}