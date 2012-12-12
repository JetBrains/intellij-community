/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.*;
import java.util.*;

/**
 * @author nik
 */
public class BuildTargetConfiguration {
  private static final Logger LOG = Logger.getInstance(BuildTargetConfiguration.class);
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

  public boolean isTargetDirty() {
    final String currentState = getCurrentState();
    if (!currentState.equals(myConfiguration)) {
      LOG.debug(myTarget + " configuration was changed:");
      LOG.debug("Old:");
      LOG.debug(myConfiguration);
      LOG.debug("New:");
      LOG.debug(currentState);
      LOG.debug(myTarget + " will be recompiled");
      return true;
    }
    return false;
  }

  public void save() {
    try {
      File configFile = getConfigFile();
      FileUtil.createParentDirs(configFile);
      Writer out = new BufferedWriter(new FileWriter(configFile));
      try {
        String current = getCurrentState();
        out.write(current);
        myConfiguration = current;
      }
      finally {
        out.close();
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

  private String getCurrentState() {
    String state = myCurrentState;
    if (state == null) {
      myCurrentState = state = saveToString();
    }
    return state;
  }

  private String saveToString() {
    StringWriter out = new StringWriter();
    //noinspection IOResourceOpenedButNotSafelyClosed
    myTarget.writeConfiguration(new PrintWriter(out), myTargetsState.getDataPaths(), myTargetsState.getBuildRootIndex());
    return out.toString();
  }

  public void storeNonexistentOutputRoots(CompileContext context) throws IOException {
    Collection<File> outputRoots = myTarget.getOutputRoots(context);
    List<String> nonexistentOutputRoots = new SmartList<String>();
    for (File root : outputRoots) {
      if (!root.exists()) {
        nonexistentOutputRoots.add(root.getAbsolutePath());
      }
    }
    File file = getNonexistentOutputsFile();
    if (nonexistentOutputRoots.isEmpty()) {
      if (file.exists()) {
        FileUtil.delete(file);
      }
    }
    else {
      FileUtil.writeToFile(file, StringUtil.join(nonexistentOutputRoots, "\n"));
    }
  }

  public boolean outputRootWasDeleted(CompileContext context) throws IOException {
    List<String> nonexistentOutputRoots = new SmartList<String>();
    for (File outputRoot : myTarget.getOutputRoots(context)) {
      if (!outputRoot.exists()) {
        nonexistentOutputRoots.add(outputRoot.getAbsolutePath());
      }
    }
    if (nonexistentOutputRoots.isEmpty()) return false;
    
    Set<String> storedNonExistentOutputs;
    File file = getNonexistentOutputsFile();
    if (!file.exists()) {
      storedNonExistentOutputs = Collections.emptySet();
    }
    else {
      List<String> lines = StringUtil.split(FileUtil.loadFile(file), "\n");
      storedNonExistentOutputs = new THashSet<String>(lines, FileUtil.PATH_HASHING_STRATEGY);
    }
    return !storedNonExistentOutputs.containsAll(nonexistentOutputRoots);
  }
}
