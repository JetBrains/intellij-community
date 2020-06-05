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
package org.jetbrains.jps.cmdline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.incremental.CompilerEncodingConfiguration;
import org.jetbrains.jps.incremental.FSCache;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*/
public final class ProjectDescriptor {
  private final JpsProject myProject;
  private final JpsModel myModel;
  public final BuildFSState fsState;
  private final ProjectStamps myProjectStamps;
  public final BuildDataManager dataManager;
  private final BuildLoggingManager myLoggingManager;
  private final BuildTargetsState myTargetsState;
  private final ModuleExcludeIndex myModuleExcludeIndex;
  private int myUseCounter = 1;
  private final Set<JpsSdk<?>> myProjectJavaSdks;
  private final CompilerEncodingConfiguration myEncodingConfiguration;
  private final BuildRootIndex myBuildRootIndex;
  private final BuildTargetIndex myBuildTargetIndex;
  private final IgnoredFileIndex myIgnoredFileIndex;
  private FSCache myFSCache = FSCache.NO_CACHE;

  public ProjectDescriptor(JpsModel model,
                           BuildFSState fsState,
                           ProjectStamps projectStamps,
                           BuildDataManager dataManager,
                           BuildLoggingManager loggingManager,
                           final ModuleExcludeIndex moduleExcludeIndex,
                           final BuildTargetsState targetsState,
                           final BuildTargetIndex buildTargetIndex, final BuildRootIndex buildRootIndex, IgnoredFileIndex ignoredFileIndex) {
    myModel = model;
    myIgnoredFileIndex = ignoredFileIndex;
    myProject = model.getProject();
    this.fsState = fsState;
    myProjectStamps = projectStamps;
    this.dataManager = dataManager;
    myBuildTargetIndex = buildTargetIndex;
    myBuildRootIndex = buildRootIndex;
    myLoggingManager = loggingManager;
    myModuleExcludeIndex = moduleExcludeIndex;
    myProjectJavaSdks = new HashSet<>();
    myEncodingConfiguration = new CompilerEncodingConfiguration(model, buildRootIndex);
    for (JpsModule module : myProject.getModules()) {
      final JpsSdk<?> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
      if (sdk != null && !myProjectJavaSdks.contains(sdk) && sdk.getVersionString() != null && sdk.getHomePath() != null) {
        myProjectJavaSdks.add(sdk);
      }
    }
    myTargetsState = targetsState;
  }

  /**
   * @deprecated not used after file traversal rewrite to NIO
   */
  @NotNull
  @Deprecated
  public FSCache getFSCache() {
    return FSCache.NO_CACHE;
  }

  /**
   * @deprecated not used after file traversal rewrite to NIO
   */
  @Deprecated
  public void setFSCache(FSCache cache) {
    myFSCache = cache == null? FSCache.NO_CACHE : cache;
  }

  public BuildRootIndex getBuildRootIndex() {
    return myBuildRootIndex;
  }

  public BuildTargetIndex getBuildTargetIndex() {
    return myBuildTargetIndex;
  }

  public IgnoredFileIndex getIgnoredFileIndex() {
    return myIgnoredFileIndex;
  }

  public BuildTargetsState getTargetsState() {
    return myTargetsState;
  }

  public CompilerEncodingConfiguration getEncodingConfiguration() {
    return myEncodingConfiguration;
  }

  public Set<JpsSdk<?>> getProjectJavaSdks() {
    return myProjectJavaSdks;
  }

  public BuildLoggingManager getLoggingManager() {
    return myLoggingManager;
  }

  public synchronized void incUsageCounter() {
    myUseCounter++;
  }

  public void release() {
    boolean shouldClose;
    synchronized (this) {
      --myUseCounter;
      shouldClose = myUseCounter == 0;
    }
    if (shouldClose) {
      try {
        myProjectStamps.close();
      }
      finally {
        try {
          dataManager.close();
        }
        catch (IOException e) {
          e.printStackTrace(System.err);
        }
      }
    }
  }

  public ModuleExcludeIndex getModuleExcludeIndex() {
    return myModuleExcludeIndex;
  }

  public JpsModel getModel() {
    return myModel;
  }

  public JpsProject getProject() {
    return myProject;
  }

  public ProjectStamps getProjectStamps() {
    return myProjectStamps;
  }
}
