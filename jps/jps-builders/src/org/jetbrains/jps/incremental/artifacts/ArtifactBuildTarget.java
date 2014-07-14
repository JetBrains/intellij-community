/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, final TargetOutputIndex outputIndex) {
    final LinkedHashSet<BuildTarget<?>> dependencies = new LinkedHashSet<BuildTarget<?>>();
    JpsArtifactUtil.processPackagingElements(myArtifact.getRootElement(), new Processor<JpsPackagingElement>() {
      @Override
      public boolean process(JpsPackagingElement element) {
        if (element instanceof JpsArtifactOutputPackagingElement) {
          JpsArtifact included = ((JpsArtifactOutputPackagingElement)element).getArtifactReference().resolve();
          if (included != null && !included.equals(myArtifact)) {
            if (!StringUtil.isEmpty(included.getOutputPath())) {
              dependencies.add(new ArtifactBuildTarget(included));
              return false;
            }
          }
        }
        dependencies.addAll(LayoutElementBuildersRegistry.getInstance().getDependencies(element, outputIndex));
        return true;
      }
    });
    if (!dependencies.isEmpty()) {
      final List<BuildTarget<?>> additional = new SmartList<BuildTarget<?>>();
      for (BuildTarget<?> dependency : dependencies) {
        if (dependency instanceof ModuleBasedTarget<?>) {
          final ModuleBasedTarget target = (ModuleBasedTarget)dependency;
          additional.addAll(targetRegistry.getModuleBasedTargets(target.getModule(), target.isTests()? BuildTargetRegistry.ModuleTargetSelector.TEST : BuildTargetRegistry.ModuleTargetSelector.PRODUCTION));
        }
      }
      dependencies.addAll(additional);
    }
    return dependencies;
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
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
    out.println(StringUtil.notNullize(myArtifact.getOutputPath()));
    final BuildRootIndex rootIndex = pd.getBuildRootIndex();
    for (ArtifactRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      descriptor.writeConfiguration(out);
    }
  }

  @NotNull
  @Override
  public List<ArtifactRootDescriptor> computeRootDescriptors(JpsModel model,
                                                             ModuleExcludeIndex index,
                                                             IgnoredFileIndex ignoredFileIndex,
                                                             BuildDataPaths dataPaths) {
    ArtifactInstructionsBuilderImpl builder = new ArtifactInstructionsBuilderImpl(index, ignoredFileIndex, this, model, dataPaths);
    ArtifactInstructionsBuilderContext context = new ArtifactInstructionsBuilderContextImpl(model, dataPaths);
    String outputPath = StringUtil.notNullize(myArtifact.getOutputPath());
    final CopyToDirectoryInstructionCreator instructionCreator = new CopyToDirectoryInstructionCreator(builder, outputPath);
    LayoutElementBuildersRegistry.getInstance().generateInstructions(myArtifact, instructionCreator, context);
    return builder.getDescriptors();
  }

  @Override
  public ArtifactRootDescriptor findRootDescriptor(String rootId,
                                                BuildRootIndex rootIndex) {
    return rootIndex.getTargetRoots(this, null).get(Integer.valueOf(rootId));
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Artifact '" + myArtifact.getName() + "'";
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    String outputFilePath = myArtifact.getOutputFilePath();
    return outputFilePath != null && !StringUtil.isEmpty(outputFilePath) ? Collections.singleton(new File(FileUtil.toSystemDependentName(outputFilePath))) : Collections.<File>emptyList();
  }
}
