/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class BuildTargetConfiguration {
  public static final Key<Set<JpsModule>> MODULES_WITH_TARGET_CONFIG_CHANGED_KEY = GlobalContextKey.create("_modules_with_target_config_changed_");

  private static final Logger LOG = Logger.getInstance(BuildTargetConfiguration.class);
  private static final GlobalContextKey<Set<File>> ALL_DELETED_ROOTS_KEY = GlobalContextKey.create("_all_deleted_output_roots_");

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

  public boolean isTargetDirty(CompileContext context) {
    final String currentState = getCurrentState(context);
    if (!currentState.equals(myConfiguration)) {
      LOG.debug(myTarget + " configuration was changed:");
      LOG.debug("Old:");
      LOG.debug(myConfiguration);
      LOG.debug("New:");
      LOG.debug(currentState);
      LOG.debug(myTarget + " will be recompiled");
      if (myTarget instanceof ModuleBuildTarget) {
        final JpsModule module = ((ModuleBuildTarget)myTarget).getModule();
        synchronized (MODULES_WITH_TARGET_CONFIG_CHANGED_KEY) {
          Set<JpsModule> modules = MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.get(context);
          if (modules == null) {
            MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.set(context, modules = new THashSet<>());
          }
          modules.add(module);
        }
      }
      return true;
    }
    return false;
  }

  public void save(CompileContext context) {
    persist(getCurrentState(context));
  }

  public void invalidate() {
    persist("$dirty_mark$");
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

  private String getCurrentState(CompileContext context) {
    String state = myCurrentState;
    if (state == null) {
      myCurrentState = state = saveToString(context);
    }
    return state;
  }

  private String saveToString(CompileContext context) {
    StringWriter out = new StringWriter();
    myTarget.writeConfiguration(context.getProjectDescriptor(), new PrintWriter(out));
    return out.toString();
  }

  public void storeNonexistentOutputRoots(CompileContext context) throws IOException {
    Collection<File> outputRoots = myTarget.getOutputRoots(context);
    List<String> nonexistentOutputRoots = new SmartList<>();
    for (File root : outputRoots) {
      if (!root.exists()) {
        nonexistentOutputRoots.add(root.getAbsolutePath());
      }
    }
    File file = getNonexistentOutputsFile();
    if (nonexistentOutputRoots.isEmpty()) {
      FileUtil.delete(file);
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
              allDeletedRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
              ALL_DELETED_ROOTS_KEY.set(context, allDeletedRoots);
            }
            allDeletedRoots.add(outputRoot);
          }
        }
        if (wasDeleted) {
          nonexistentOutputRoots.add(outputRoot.getAbsolutePath());
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
      List<String> lines = StringUtil.split(FileUtil.loadFile(file), "\n");
      storedNonExistentOutputs = new THashSet<>(lines, FileUtil.PATH_HASHING_STRATEGY);
    }
    return !storedNonExistentOutputs.containsAll(nonexistentOutputRoots);
  }
}