package org.jetbrains.jps.cmdline;

import org.jetbrains.jps.Project;
import org.jetbrains.jps.incremental.BuildLoggingManager;
import org.jetbrains.jps.incremental.CompilerEncodingConfiguration;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootsIndex;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
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
*         Date: 1/8/12
*/
public final class ProjectDescriptor {
  public final Project project;
  public final JpsProject jpsProject;
  public final JpsModel jpsModel;
  public final BuildFSState fsState;
  public final ProjectTimestamps timestamps;
  public final BuildDataManager dataManager;
  private final BuildLoggingManager myLoggingManager;
  public ModuleRootsIndex rootsIndex;
  private final ArtifactRootsIndex myArtifactRootsIndex;
  private int myUseCounter = 1;
  private Set<JpsSdk<?>> myProjectJavaSdks;
  private CompilerEncodingConfiguration myEncodingConfiguration;

  public ProjectDescriptor(Project project,
                           JpsModel jpsModel,
                           BuildFSState fsState,
                           ProjectTimestamps timestamps,
                           BuildDataManager dataManager,
                           BuildLoggingManager loggingManager) {
    this.project = project;
    this.jpsModel = jpsModel;
    this.jpsProject = jpsModel.getProject();
    this.fsState = fsState;
    this.timestamps = timestamps;
    this.dataManager = dataManager;
    myLoggingManager = loggingManager;
    rootsIndex = new ModuleRootsIndex(jpsProject, dataManager);
    myArtifactRootsIndex = new ArtifactRootsIndex(jpsModel, project, dataManager, rootsIndex);
    myProjectJavaSdks = new HashSet<JpsSdk<?>>();
    myEncodingConfiguration = new CompilerEncodingConfiguration(jpsModel, rootsIndex);
    for (JpsModule module : jpsProject.getModules()) {
      final JpsSdk<?> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
      if (sdk != null && !myProjectJavaSdks.contains(sdk) && sdk.getVersionString() != null && sdk.getHomePath() != null) {
        myProjectJavaSdks.add(sdk);
      }
    }
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

  public ArtifactRootsIndex getArtifactRootsIndex() {
    return myArtifactRootsIndex;
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
        timestamps.close();
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
}
