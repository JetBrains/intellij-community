package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactsBuildData extends CompositeStorageOwner {
  private Map<ArtifactBuildTarget, ArtifactSourceFilesState> myArtifactState;

  public ArtifactsBuildData() throws IOException {
    myArtifactState = new HashMap<ArtifactBuildTarget, ArtifactSourceFilesState>();
  }

  public ArtifactSourceFilesState getOrCreateState(ArtifactBuildTarget target, ProjectDescriptor projectDescriptor) {
    ArtifactSourceFilesState state = myArtifactState.get(target);
    if (state == null) {
      state = new ArtifactSourceFilesState(target, projectDescriptor);
      myArtifactState.put(target, state);
    }
    return state;
  }

  public void clean() throws IOException {
    IOException exc = null;
    for (ArtifactSourceFilesState state : myArtifactState.values()) {
      try {
        state.close();
      }
      catch (IOException e) {
        if (exc == null) {
          exc = e;
        }
      }
    }
    myArtifactState.clear();
    if (exc != null) {
      throw exc;
    }
  }

  @Override
  protected Iterable<? extends StorageOwner> getChildStorages() {
    return myArtifactState.values();
  }
}
