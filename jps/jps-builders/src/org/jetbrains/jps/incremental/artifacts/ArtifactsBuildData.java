package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactsBuildData extends CompositeStorageOwner {
  private Map<JpsArtifact, ArtifactSourceFilesState> myArtifactState;
  private final ArtifactSourceTimestampStorage myTimestampStorage;
  private ArtifactCompilerPersistentData myPersistentData;
  private final File myMappingsDir;

  public ArtifactsBuildData(File artifactsDataDir) throws IOException {
    myTimestampStorage = new ArtifactSourceTimestampStorage(new File(artifactsDataDir, "timestamps"));
    myArtifactState = new HashMap<JpsArtifact, ArtifactSourceFilesState>();
    myPersistentData = new ArtifactCompilerPersistentData(artifactsDataDir);
    myMappingsDir = new File(artifactsDataDir, "mappings");
    if (myPersistentData.isVersionChanged()) {
      myTimestampStorage.wipe();
      FileUtil.delete(myMappingsDir);
      //todo[nik] clear artifacts outputs
    }
  }

  public ArtifactSourceFilesState getOrCreateState(JpsArtifact artifact, ProjectDescriptor projectDescriptor) {
    ArtifactSourceFilesState state = myArtifactState.get(artifact);
    if (state == null) {
      final int artifactId = getArtifactId(artifact);
      state = new ArtifactSourceFilesState(artifact, artifactId, projectDescriptor, myTimestampStorage, myMappingsDir);
      myArtifactState.put(artifact, state);
    }
    return state;
  }

  public int getArtifactId(JpsArtifact artifact) {
    return myPersistentData.getId(artifact.getName());
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

  public ArtifactSourceTimestampStorage getTimestampStorage() {
    return myTimestampStorage;
  }
}
