package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactRootsIndex {
  private MultiMap<File, ArtifactRootDescriptor> myRootToDescriptorMap;
  private Map<JpsArtifact, ArtifactInstructionsBuilder> myInstructions;
  private Map<String, JpsArtifact> myArtifactByName;

  public ArtifactRootsIndex(JpsModel model, ModuleRootsIndex rootsIndex) {
    myRootToDescriptorMap = new MultiMap<File, ArtifactRootDescriptor>();
    myInstructions = new HashMap<JpsArtifact, ArtifactInstructionsBuilder>();
    myArtifactByName = new HashMap<String, JpsArtifact>();
    for (JpsArtifact artifact : JpsBuilderArtifactService.getInstance().getArtifacts(model, true)) {
      myArtifactByName.put(artifact.getName(), artifact);
      ArtifactInstructionsBuilderImpl builder = new ArtifactInstructionsBuilderImpl(rootsIndex, new ArtifactBuildTarget(artifact));
      final JpsCompositePackagingElement rootElement = artifact.getRootElement();
      ArtifactInstructionsBuilderContext context =
        new ArtifactInstructionsBuilderContextImpl(model, rootsIndex, new ProjectPaths(model.getProject()));
      String outputPath =
        StringUtil.notNullize(artifact.getOutputPath());//todo[nik] implement simplified instructions generation which only collect roots
      final CopyToDirectoryInstructionCreator instructionCreator = new CopyToDirectoryInstructionCreator(builder, outputPath);
      LayoutElementBuildersRegistry.getInstance().generateInstructions(rootElement, instructionCreator, context);
      myInstructions.put(artifact, builder);
      for (Pair<ArtifactRootDescriptor, DestinationInfo> pair : builder.getInstructions()) {
        ArtifactRootDescriptor descriptor = pair.getFirst();
        myRootToDescriptorMap.putValue(descriptor.getRootFile(), descriptor);
      }
    }
  }

  @NotNull
  public Collection<ArtifactRootDescriptor> getDescriptors(@NotNull File file) {
    File current = file;
    Collection<ArtifactRootDescriptor> result = null;
    while (current != null) {
      Collection<ArtifactRootDescriptor> descriptors = myRootToDescriptorMap.get(current);
      if (!descriptors.isEmpty()) {
        if (result == null) {
          result = descriptors;
        }
        else {
          result = new ArrayList<ArtifactRootDescriptor>(result);
          result.addAll(descriptors);
        }
      }
      current = FileUtil.getParentFile(current);
    }
    return result != null ? result : Collections.<ArtifactRootDescriptor>emptyList();
  }

  @NotNull
  public ArtifactInstructionsBuilder getInstructionsBuilder(@NotNull JpsArtifact artifact) {
    return myInstructions.get(artifact);
  }

  @Nullable
  public JpsArtifact getArtifact(String artifactName) {
    return myArtifactByName.get(artifactName);
  }
}
