package org.jetbrains.jps.incremental.artifacts;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author nik
 */
public class ArtifactRootId {
  private final int myArtifactId;
  private final int myRootIndex;

  public ArtifactRootId(int artifactId, int rootIndex) {
    myArtifactId = artifactId;
    myRootIndex = rootIndex;
  }

  public ArtifactRootId(DataInput input) throws IOException {
    myArtifactId = input.readInt();
    myRootIndex = input.readInt();
  }

  public int getArtifactId() {
    return myArtifactId;
  }

  public int getRootIndex() {
    return myRootIndex;
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myArtifactId);
    out.writeInt(myRootIndex);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArtifactRootId id = (ArtifactRootId)o;
    return myArtifactId == id.myArtifactId && myRootIndex == id.myRootIndex;
  }

  @Override
  public int hashCode() {
    return 31 * myArtifactId + myRootIndex;
  }
}
