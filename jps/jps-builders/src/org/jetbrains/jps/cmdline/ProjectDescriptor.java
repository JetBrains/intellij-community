// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.incremental.CompilerEncodingConfiguration;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
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
import java.util.Objects;
import java.util.Set;

public final class ProjectDescriptor {
  private final JpsProject myProject;
  private final JpsModel myModel;
  public final BuildFSState fsState;
  public final BuildDataManager dataManager;
  private final BuildLoggingManager myLoggingManager;
  private final ModuleExcludeIndex myModuleExcludeIndex;
  private int myUseCounter = 1;
  private final Set<JpsSdk<?>> myProjectJavaSdks;
  private final CompilerEncodingConfiguration myEncodingConfiguration;
  private final BuildRootIndex myBuildRootIndex;
  private final BuildTargetIndex myBuildTargetIndex;
  private final IgnoredFileIndex myIgnoredFileIndex;


  @ApiStatus.Internal
  @TestOnly
  // todo: to be removed later, after KotlinTests update
  public ProjectDescriptor(JpsModel model,
                           BuildFSState fsState,
                           ProjectStamps projectStamps,
                           BuildDataManager dataManager,
                           BuildLoggingManager loggingManager,
                           ModuleExcludeIndex moduleExcludeIndex,
                           BuildTargetIndex buildTargetIndex,
                           BuildRootIndex buildRootIndex,
                           IgnoredFileIndex ignoredFileIndex) {
    this(model, fsState, dataManager, loggingManager, moduleExcludeIndex, buildTargetIndex, buildRootIndex, ignoredFileIndex);
    assert dataManager.getFileStampService() == null; // should be not yet initialized
    dataManager.setFileStampService(projectStamps);
  }

  @ApiStatus.Internal
  public ProjectDescriptor(JpsModel model,
                           BuildFSState fsState,
                           BuildDataManager dataManager,
                           BuildLoggingManager loggingManager,
                           ModuleExcludeIndex moduleExcludeIndex,
                           BuildTargetIndex buildTargetIndex,
                           BuildRootIndex buildRootIndex,
                           IgnoredFileIndex ignoredFileIndex) {
    myModel = model;
    myIgnoredFileIndex = ignoredFileIndex;
    myProject = model.getProject();
    this.fsState = fsState;
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
      try {
        dataManager.close();
      }
      catch (IOException e) {
        e.printStackTrace(System.err);
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

  /**
   * @deprecated Use {@link BuildDataManager#getFileStampStorage(BuildTarget)}.
   */
  @Deprecated(forRemoval = true)
  public ProjectStamps getProjectStamps() {
    //noinspection removal
    return Objects.requireNonNull(dataManager.getFileStampService());
  }
}
