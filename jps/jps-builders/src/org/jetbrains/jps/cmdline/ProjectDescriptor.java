// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.incremental.CompilerEncodingConfiguration;
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
  private final ModuleExcludeIndex myModuleExcludeIndex;
  private int myUseCounter = 1;
  private final Set<JpsSdk<?>> myProjectJavaSdks;
  private final CompilerEncodingConfiguration myEncodingConfiguration;
  private final BuildRootIndex myBuildRootIndex;
  private final BuildTargetIndex myBuildTargetIndex;
  private final IgnoredFileIndex myIgnoredFileIndex;

  public ProjectDescriptor(JpsModel model,
                           BuildFSState fsState,
                           ProjectStamps projectStamps,
                           BuildDataManager dataManager,
                           BuildLoggingManager loggingManager,
                           final ModuleExcludeIndex moduleExcludeIndex,
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
    return dataManager.getTargetsState();
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

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void release() {
    boolean shouldClose;
    synchronized (this) {
      --myUseCounter;
      shouldClose = myUseCounter == 0;
    }
    if (shouldClose) {
      if (dataManager.getStorageManager() == null) {
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
      else {
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
