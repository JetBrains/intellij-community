package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.artifacts.instructions.DestinationInfo;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactBuildTarget extends BuildTarget {
  private final JpsArtifact myArtifact;

  public ArtifactBuildTarget(@NotNull JpsArtifact artifact) {
    super(ArtifactBuildTargetType.INSTANCE);
    myArtifact = artifact;
  }

  @Override
  public String getId() {
    return myArtifact.getName();
  }

  public JpsArtifact getArtifact() {
    return myArtifact;
  }

  @Override
  public Collection<? extends BuildTarget> computeDependencies() {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myArtifact.equals(((ArtifactBuildTarget)o).myArtifact);
  }

  @Override
  public int hashCode() {
    return myArtifact.hashCode();
  }

  @Override
  public void writeConfiguration(PrintWriter out, ModuleRootsIndex index, ArtifactRootsIndex rootsIndex) {
    out.println(StringUtil.notNullize(myArtifact.getOutputPath()));
    for (Pair<ArtifactRootDescriptor, DestinationInfo> pair : rootsIndex.getInstructionsBuilder(myArtifact).getInstructions()) {
      pair.getFirst().writeConfiguration(out);
      out.println("->" + pair.getSecond().getOutputPath());
    }
  }

  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, ModuleRootsIndex index, ArtifactRootsIndex artifactRootsIndex) {
    return artifactRootsIndex.getInstructionsBuilder(myArtifact).getInstructions().get(Integer.valueOf(rootId)).getFirst();
  }
}
