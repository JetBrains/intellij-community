// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.*;
import java.util.*;

public final class BuildTargetConfiguration {
  public static final Key<Set<JpsModule>> MODULES_WITH_TARGET_CONFIG_CHANGED_KEY = GlobalContextKey.create("_modules_with_target_config_changed_");

  private static final Logger LOG = Logger.getInstance(BuildTargetConfiguration.class);
  private static final GlobalContextKey<Set<File>> ALL_DELETED_ROOTS_KEY = GlobalContextKey.create("_all_deleted_output_roots_");
  private static final String DIRTY_MARK = "$dirty_mark$";

  private final BuildTarget<?> myTarget;
  private final BuildTargetsState myTargetsState;
  private String myConfiguration;
  private volatile String myCurrentState;

  public BuildTargetConfiguration(BuildTarget<?> target, BuildTargetsState targetsState) {
    myTarget = target;
    myTargetsState = targetsState;
    myConfiguration = load();
  }

  private String load() {
    File configFile = getConfigFile();
    if (configFile.exists()) {
      try {
        return new String(FileUtil.loadFileText(configFile));
      }
      catch (IOException e) {
        LOG.info("Cannot load configuration of " + myTarget);
      }
    }
    return "";
  }

  public boolean isTargetDirty(final ProjectDescriptor pd) {
    return DIRTY_MARK.equals(myConfiguration) || !getCurrentState(pd).equals(myConfiguration);
  }

  public void logDiagnostics(CompileContext context) {
    if (DIRTY_MARK.equals(myConfiguration)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(myTarget + " has been marked dirty in the previous compilation session");
      }
    }
    else {
      final String currentState = getCurrentState(context.getProjectDescriptor());
      if (!currentState.equals(myConfiguration)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(myTarget + " configuration was changed:");
          LOG.debug("Old:");
          LOG.debug(myConfiguration);
          LOG.debug("New:");
          LOG.debug(currentState);
          LOG.debug(myTarget + " will be recompiled");
        }
        if (myTarget instanceof ModuleBuildTarget) {
          final JpsModule module = ((ModuleBuildTarget)myTarget).getModule();
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
  }

  public void save(CompileContext context) {
    persist(getCurrentState(context.getProjectDescriptor()));
  }

  public void invalidate() {
    persist(DIRTY_MARK);
  }

  private void persist(final String data) {
    try {
      File configFile = getConfigFile();
      FileUtil.createParentDirs(configFile);
      try (Writer out = new BufferedWriter(new FileWriter(configFile))) {
        out.write(data);
        myConfiguration = data;
      }
    }
    catch (IOException e) {
      LOG.info("Cannot save configuration of " + myConfiguration, e);
    }
  }

  private File getConfigFile() {
    return new File(myTargetsState.getDataPaths().getTargetDataRoot(myTarget), "config.dat");
  }

  private File getNonexistentOutputsFile() {
    return new File(myTargetsState.getDataPaths().getTargetDataRoot(myTarget), "nonexistent-outputs.dat");
  }

  @NotNull
  private String getCurrentState(final ProjectDescriptor pd) {
    String state = myCurrentState;
    if (state == null) {
      myCurrentState = state = saveToString(pd);
    }
    return state;
  }

  private String saveToString(final ProjectDescriptor pd) {
    StringWriter out = new StringWriter();
    myTarget.writeConfiguration(pd, new PrintWriter(out));
    return out.toString();
  }

  public void storeNonexistentOutputRoots(CompileContext context) throws IOException {
    PathRelativizerService relativizer = context.getProjectDescriptor().dataManager.getRelativizer();
    Collection<File> outputRoots = myTarget.getOutputRoots(context);
    List<String> nonexistentOutputRoots = new SmartList<>();
    for (File root : outputRoots) {
      if (!root.exists()) {
        nonexistentOutputRoots.add(relativizer.toRelative(root.getAbsolutePath()));
      }
    }
    File file = getNonexistentOutputsFile();
    if (nonexistentOutputRoots.isEmpty()) {
      file.delete();
    }
    else {
      FileUtil.writeToFile(file, StringUtil.join(nonexistentOutputRoots, "\n"));
    }
  }

  public boolean outputRootWasDeleted(CompileContext context) throws IOException {
    List<String> nonexistentOutputRoots = new SmartList<>();

    final Collection<File> targetRoots = myTarget.getOutputRoots(context);
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
          nonexistentOutputRoots.add(FileUtil.toSystemIndependentName(outputRoot.getAbsolutePath()));
        }
      }
    }

    if (nonexistentOutputRoots.isEmpty()) {
      return false;
    }

    Set<String> storedNonExistentOutputs;
    File file = getNonexistentOutputsFile();
    if (!file.exists()) {
      storedNonExistentOutputs = Collections.emptySet();
    }
    else {
      PathRelativizerService relativizer = context.getProjectDescriptor().dataManager.getRelativizer();
      List<String> lines = ContainerUtil.map(StringUtil.split(FileUtil.loadFile(file), "\n"),
                                             s -> relativizer.toFull(s));
      storedNonExistentOutputs = CollectionFactory.createFilePathSet(lines);
    }
    return !storedNonExistentOutputs.containsAll(nonexistentOutputRoots);
  }
}