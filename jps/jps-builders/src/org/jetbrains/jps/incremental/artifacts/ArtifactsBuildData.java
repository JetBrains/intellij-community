package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.incremental.ModuleRootsIndex;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactsBuildData {
  private Map<Artifact, ArtifactSourceFilesState> myArtifactState;
  private final ArtifactSourceTimestampStorage myTimestampStorage;
  private ArtifactCompilerPersistentData myPersistentData;
  private final File myArtifactsDataDir;

  public ArtifactsBuildData(File artifactsDataDir) throws Exception {
    myArtifactsDataDir = artifactsDataDir;
    myTimestampStorage = new ArtifactSourceTimestampStorage(new File(artifactsDataDir, "timestamps"));
    myArtifactState = new HashMap<Artifact, ArtifactSourceFilesState>();
    myPersistentData = new ArtifactCompilerPersistentData(artifactsDataDir);
  }

  public ArtifactSourceFilesState getOrCreateState(Artifact artifact, Project project, ModuleRootsIndex index) {
    ArtifactSourceFilesState state = myArtifactState.get(artifact);
    if (state == null) {
      final int artifactId = myPersistentData.getId(artifact.getName());
      state = new ArtifactSourceFilesState(artifact, artifactId, project, index, myTimestampStorage, myArtifactsDataDir);
      myArtifactState.put(artifact, state);
    }
    return state;
  }

  public void clean() {
    myTimestampStorage.wipe();
    myPersistentData.clean();
    for (ArtifactSourceFilesState state : myArtifactState.values()) {
      state.clean();
    }
    FileUtil.delete(myArtifactsDataDir);
  }

  public void close() throws IOException {
    myTimestampStorage.close();
    for (ArtifactSourceFilesState state : myArtifactState.values()) {
      state.close();
    }
  }

  public void flush(boolean memoryCachesOnly) {
    myTimestampStorage.flush(memoryCachesOnly);
    for (ArtifactSourceFilesState state : myArtifactState.values()) {
      state.flush(memoryCachesOnly);
    }
  }
}
