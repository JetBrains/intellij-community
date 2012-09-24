package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactBuildTarget extends BuildTarget<ArtifactRootDescriptor> {
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
  public void writeConfiguration(PrintWriter out, BuildRootIndex buildRootIndex) {
    out.println(StringUtil.notNullize(myArtifact.getOutputPath()));
    for (ArtifactRootDescriptor descriptor : buildRootIndex.getTargetRoots(this, null)) {
      descriptor.writeConfiguration(out);
    }
  }

  @NotNull
  @Override
  public List<ArtifactRootDescriptor> computeRootDescriptors(JpsModel model, ModuleRootsIndex index) {
    ArtifactInstructionsBuilderImpl builder = new ArtifactInstructionsBuilderImpl(index, this);
    final JpsCompositePackagingElement rootElement = myArtifact.getRootElement();
    ArtifactInstructionsBuilderContext context = new ArtifactInstructionsBuilderContextImpl(model, new ProjectPaths(model.getProject()));
    String outputPath = StringUtil.notNullize(myArtifact.getOutputPath());//todo[nik] implement simplified instructions generation which only collect roots
    final CopyToDirectoryInstructionCreator instructionCreator = new CopyToDirectoryInstructionCreator(builder, outputPath);
    LayoutElementBuildersRegistry.getInstance().generateInstructions(rootElement, instructionCreator, context);
    return builder.getDescriptors();
  }

  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId,
                                                BuildRootIndex rootIndex) {
    return rootIndex.getTargetRoots(this, null).get(Integer.valueOf(rootId));
  }
}
