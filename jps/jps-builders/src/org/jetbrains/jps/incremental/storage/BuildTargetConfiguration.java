// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetHashSupplier;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public final class BuildTargetConfiguration {
  public static final Key<Set<JpsModule>> MODULES_WITH_TARGET_CONFIG_CHANGED_KEY = GlobalContextKey.create("_modules_with_target_config_changed_");

  private static final Logger LOG = Logger.getInstance(BuildTargetConfiguration.class);
  private static final GlobalContextKey<Set<File>> ALL_DELETED_ROOTS_KEY = GlobalContextKey.create("_all_deleted_output_roots_");
  private static final String DIRTY_MARK = "$dirty_mark$";

  private final BuildTarget<?> target;
  @NotNull private final BuildDataPaths dataPaths;
  private @NotNull String configuration;
  private volatile String currentState;

  @ApiStatus.Internal
  public BuildTargetConfiguration(@NotNull BuildTarget<?> target, @NotNull BuildDataPaths dataPaths) {
    this.target = target;
    this.dataPaths = dataPaths;
    configuration = load();
  }

  private @NotNull String load() {
    try {
      return Files.readString(getConfigFile());
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      LOG.warn("Cannot load configuration of " + target, e);
    }
    return "";
  }

  public boolean isTargetDirty(@NotNull ProjectDescriptor projectDescriptor) {
    return DIRTY_MARK.equals(configuration) || !getCurrentState(projectDescriptor).equals(configuration);
  }

  public void logDiagnostics(CompileContext context) {
    if (DIRTY_MARK.equals(configuration)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(target + " has been marked dirty in the previous compilation session");
      }
    }
    else {
      String currentState = getCurrentState(context.getProjectDescriptor());
      if (currentState.equals(configuration)) {
        return;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug(target + " configuration was changed:");
        LOG.debug("Old: " + configuration);
        LOG.debug("New: " + currentState);
        LOG.debug(target + " will be recompiled");
      }

      if (target instanceof ModuleBuildTarget) {
        final JpsModule module = ((ModuleBuildTarget)target).getModule();
        synchronized (MODULES_WITH_TARGET_CONFIG_CHANGED_KEY) {
          Set<JpsModule> modules = MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.get(context);
          if (modules == null) {
            MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.set(context, modules = new HashSet<>());
          }
          modules.add(module);
        }
      }
    }
  }

  public void save(@NotNull CompileContext context) {
    persist(getCurrentState(context.getProjectDescriptor()));
  }

  void invalidate() {
    persist(DIRTY_MARK);
  }

  private void persist(@NotNull String data) {
    try {
      Path configFile = getConfigFile();
      Files.createDirectories(configFile.getParent());
      Files.writeString(configFile, data);
      configuration = data;
    }
    catch (IOException e) {
      LOG.info("Cannot save configuration of " + configuration, e);
    }
  }

  private @NotNull Path getConfigFile() {
    return dataPaths.getTargetDataRootDir(target).resolve("config.dat");
  }

  private @NotNull Path getNonexistentOutputsFile() {
    return dataPaths.getTargetDataRootDir(target).resolve("nonexistent-outputs.dat");
  }

  private @NotNull String getCurrentState(@NotNull ProjectDescriptor projectDescriptor) {
    String state = currentState;
    if (state != null) {
      return state;
    }

    if (target instanceof BuildTargetHashSupplier) {
      HashStream64 hash = Hashing.komihash5_0().hashStream();
      ((BuildTargetHashSupplier)target).computeConfigurationDigest(projectDescriptor, hash);
      state = Long.toUnsignedString(hash.getAsLong(), Character.MAX_RADIX);
    }
    else {
      StringWriter out = new StringWriter();
      target.writeConfiguration(projectDescriptor, new PrintWriter(out));
      state = out.toString();
    }
    currentState = state;
    return state;
  }

  void storeNonExistentOutputRoots(@NotNull CompileContext context) throws IOException {
    PathRelativizerService relativizer = context.getProjectDescriptor().dataManager.getRelativizer();
    Collection<File> outputRoots = target.getOutputRoots(context);
    List<String> nonexistentOutputRoots = new ArrayList<>();
    for (File root : outputRoots) {
      if (!root.exists()) {
        nonexistentOutputRoots.add(relativizer.toRelative(root.getAbsolutePath()));
      }
    }

    Path file = getNonexistentOutputsFile();
    if (nonexistentOutputRoots.isEmpty()) {
      Files.deleteIfExists(file);
    }
    else {
      Files.createDirectories(file.getParent());
      Files.writeString(file, String.join("\n", nonexistentOutputRoots));
    }
  }

  public boolean outputRootWasDeleted(CompileContext context) throws IOException {
    List<String> nonexistentOutputRoots = new ArrayList<>();

    Collection<File> targetRoots = target.getOutputRoots(context);
    synchronized (ALL_DELETED_ROOTS_KEY) {
      Set<File> allDeletedRoots = ALL_DELETED_ROOTS_KEY.get(context);
      for (File outputRoot : targetRoots) {
        boolean wasDeleted = allDeletedRoots != null && allDeletedRoots.contains(outputRoot);
        if (!wasDeleted) {
          wasDeleted = !outputRoot.exists();
          if (wasDeleted) {
            if (allDeletedRoots == null) { // lazy init
              allDeletedRoots = FileCollectionFactory.createCanonicalFileSet();
              ALL_DELETED_ROOTS_KEY.set(context, allDeletedRoots);
            }
            allDeletedRoots.add(outputRoot);
          }
        }
        if (wasDeleted) {
          nonexistentOutputRoots.add(FileUtilRt.toSystemIndependentName(outputRoot.getAbsolutePath()));
        }
      }
    }

    if (nonexistentOutputRoots.isEmpty()) {
      return false;
    }

    Set<String> storedNonExistentOutputs;
    Path file = getNonexistentOutputsFile();
    if (Files.notExists(file)) {
      storedNonExistentOutputs = Set.of();
    }
    else {
      PathRelativizerService relativizer = context.getProjectDescriptor().dataManager.getRelativizer();
      List<String> lines = ContainerUtil.map(StringUtil.split(Files.readString(file), "\n"), s -> relativizer.toFull(s));
      storedNonExistentOutputs = CollectionFactory.createFilePathSet(lines);
    }
    return !storedNonExistentOutputs.containsAll(nonexistentOutputRoots);
  }
}