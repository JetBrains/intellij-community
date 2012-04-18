package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactsBuildData extends CompositeStorageOwner {
  private Map<Artifact, ArtifactSourceFilesState> myArtifactState;
  private final ArtifactSourceTimestampStorage myTimestampStorage;
  private ArtifactCompilerPersistentData myPersistentData;
  private final File myMappingsDir;

  public ArtifactsBuildData(File artifactsDataDir) throws IOException {
    myTimestampStorage = new ArtifactSourceTimestampStorage(new File(artifactsDataDir, "timestamps"));
    myArtifactState = new HashMap<Artifact, ArtifactSourceFilesState>();
    myPersistentData = new ArtifactCompilerPersistentData(artifactsDataDir);
    myMappingsDir = new File(artifactsDataDir, "mappings");
    if (myPersistentData.isVersionChanged()) {
      myTimestampStorage.wipe();
      FileUtil.delete(myMappingsDir);
      //todo[nik] clear artifacts outputs
    }
  }

  public ArtifactSourceFilesState getOrCreateState(Artifact artifact, Project project, ModuleRootsIndex index) {
    ArtifactSourceFilesState state = myArtifactState.get(artifact);
    if (state == null) {
      final int artifactId = myPersistentData.getId(artifact.getName());
      state = new ArtifactSourceFilesState(artifact, artifactId, project, index, myTimestampStorage, myMappingsDir);
      myArtifactState.put(artifact, state);
    }
    return state;
  }

  public void clean() throws IOException {
    myTimestampStorage.wipe();
    myPersistentData.clean();
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
    FileUtil.delete(myMappingsDir);
    if (exc != null) {
      throw exc;
    }
  }

  @Override
  protected Iterable<? extends StorageOwner> getChildStorages() {
    return ContainerUtil.concat(myArtifactState.values(), Arrays.asList(myTimestampStorage, myPersistentData));
  }
}
