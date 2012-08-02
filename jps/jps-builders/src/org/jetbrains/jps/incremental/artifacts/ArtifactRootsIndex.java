package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
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

  public ArtifactRootsIndex(JpsModel model, Project project, BuildDataManager manager, ModuleRootsIndex rootsIndex) {
    myRootToDescriptorMap = new MultiMap<File, ArtifactRootDescriptor>();
    myInstructions = new HashMap<JpsArtifact, ArtifactInstructionsBuilder>();
    ArtifactsBuildData data = manager.getArtifactsBuildData();
    for (JpsArtifact artifact : JpsBuilderArtifactService.getInstance().getArtifacts(model, true)) {
      int artifactId = data.getArtifactId(artifact);
      ArtifactInstructionsBuilderImpl builder = new ArtifactInstructionsBuilderImpl(rootsIndex, project.getIgnoredFilePatterns(), artifactId, artifact.getName());
      final JpsCompositePackagingElement rootElement = artifact.getRootElement();
      ArtifactInstructionsBuilderContext context = new ArtifactInstructionsBuilderContextImpl(model, rootsIndex, new ProjectPaths(model.getProject()));
      String outputPath = StringUtil.notNullize(artifact.getOutputPath());//todo[nik] implement simplified instructions generation which only collect roots
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
}
