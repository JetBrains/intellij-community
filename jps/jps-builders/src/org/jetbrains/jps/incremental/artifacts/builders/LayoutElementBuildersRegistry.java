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
package org.jetbrains.jps.incremental.artifacts.builders;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ClassMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement;
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class LayoutElementBuildersRegistry {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry");

  private static class InstanceHolder {

    static final LayoutElementBuildersRegistry ourInstance = new LayoutElementBuildersRegistry();
  }
  public static LayoutElementBuildersRegistry getInstance() {
    return InstanceHolder.ourInstance;
  }

  private ClassMap<LayoutElementBuilderService> myBuilders;

  private LayoutElementBuildersRegistry() {
    myBuilders = new ClassMap<>();
    LayoutElementBuilderService<?>[] standardBuilders = {
      new RootElementBuilder(), new DirectoryElementBuilder(), new ArchiveElementBuilder(), new DirectoryCopyElementBuilder(),
      new FileCopyElementBuilder(), new ExtractedDirectoryElementBuilder(), new ModuleOutputElementBuilder(),
      new ModuleTestOutputElementBuilder(), new ComplexElementBuilder(), new ArtifactOutputElementBuilder()
    };
    for (LayoutElementBuilderService<?> builder : standardBuilders) {
      myBuilders.put(builder.getElementClass(), builder);
    }
    for (LayoutElementBuilderService builder : JpsServiceManager.getInstance().getExtensions(LayoutElementBuilderService.class)) {
      myBuilders.put(builder.getElementClass(), builder);
    }
  }

  public void generateInstructions(JpsArtifact artifact, CopyToDirectoryInstructionCreator creator, ArtifactInstructionsBuilderContext context) {
    context.enterArtifact(artifact);
    generateInstructions(artifact.getRootElement(), creator, context);
  }

  public Collection<BuildTarget<?>> getDependencies(JpsPackagingElement element, TargetOutputIndex outputIndex) {
    LayoutElementBuilderService builder = getElementBuilder(element);
    if (builder != null) {
      //noinspection unchecked
      return builder.getDependencies(element, outputIndex);
    }
    return Collections.emptyList();
  }

  private void generateInstructions(JpsPackagingElement layoutElement, ArtifactCompilerInstructionCreator instructionCreator,
                                   ArtifactInstructionsBuilderContext builderContext) {
    final LayoutElementBuilderService builder = getElementBuilder(layoutElement);
    if (builder != null) {
      //noinspection unchecked
      builder.generateInstructions(layoutElement, instructionCreator, builderContext);
    }
  }

  private LayoutElementBuilderService<?> getElementBuilder(JpsPackagingElement layoutElement) {
    final LayoutElementBuilderService<?> builder = myBuilders.get(layoutElement.getClass());
    if (builder == null) {
      LOG.error("Builder not found for artifact output layout element of class " + layoutElement.getClass());
    }
    return builder;
  }

  private void generateChildrenInstructions(JpsCompositePackagingElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                            ArtifactInstructionsBuilderContext builderContext) {
    generateInstructions(element.getChildren(), instructionCreator, builderContext);
  }

  private void generateSubstitutionInstructions(JpsComplexPackagingElement element,
                                                ArtifactCompilerInstructionCreator instructionCreator,
                                                ArtifactInstructionsBuilderContext builderContext) {
    final List<JpsPackagingElement> substitution = element.getSubstitution();
    if (substitution != null) {
      generateInstructions(substitution, instructionCreator, builderContext);
    }
  }

  private void generateInstructions(final List<JpsPackagingElement> elements, ArtifactCompilerInstructionCreator instructionCreator,
                                    ArtifactInstructionsBuilderContext builderContext) {
    for (JpsPackagingElement child : elements) {
      generateInstructions(child, instructionCreator, builderContext);
    }
  }

  private static void generateModuleOutputInstructions(@Nullable String outputUrl,
                                                       @NotNull ArtifactCompilerInstructionCreator creator,
                                                       @NotNull JpsPackagingElement contextElement) {
    if (outputUrl != null) {
      File directory = JpsPathUtil.urlToFile(outputUrl);
      creator.addDirectoryCopyInstructions(directory, null, creator.getInstructionsBuilder().createCopyingHandler(directory, contextElement, creator));
    }
  }

  private class RootElementBuilder extends LayoutElementBuilderService<JpsArtifactRootElement> {
    public RootElementBuilder() {
      super(JpsArtifactRootElement.class);
    }

    @Override
    public void generateInstructions(JpsArtifactRootElement element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator, builderContext);
    }
  }

  private class DirectoryElementBuilder extends LayoutElementBuilderService<JpsDirectoryPackagingElement> {
    public DirectoryElementBuilder() {
      super(JpsDirectoryPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsDirectoryPackagingElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator.subFolder(element.getDirectoryName()), builderContext);
    }
  }

  private class ArchiveElementBuilder extends LayoutElementBuilderService<JpsArchivePackagingElement> {
    public ArchiveElementBuilder() {
      super(JpsArchivePackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsArchivePackagingElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator.archive(element.getArchiveName()), builderContext);
    }
  }

  private static class DirectoryCopyElementBuilder extends LayoutElementBuilderService<JpsDirectoryCopyPackagingElement> {
    public DirectoryCopyElementBuilder() {
      super(JpsDirectoryCopyPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsDirectoryCopyPackagingElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final String dirPath = element.getDirectoryPath();
      if (dirPath != null) {
        final File directory = new File(dirPath);
        instructionCreator.addDirectoryCopyInstructions(directory, null, instructionCreator.getInstructionsBuilder().createCopyingHandler(directory, element, instructionCreator));
      }
    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(@NotNull JpsDirectoryCopyPackagingElement element,
                                                                TargetOutputIndex outputIndex) {
      String dirPath = element.getDirectoryPath();
      if (dirPath != null) {
        return outputIndex.getTargetsByOutputFile(new File(dirPath));
      }
      return Collections.emptyList();
    }
  }

  private static class FileCopyElementBuilder extends LayoutElementBuilderService<JpsFileCopyPackagingElement> {
    public FileCopyElementBuilder() {
      super(JpsFileCopyPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsFileCopyPackagingElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final String filePath = element.getFilePath();
      if (filePath != null) {
        final File file = new File(filePath);
        final String fileName = element.getRenamedOutputFileName();
        String outputFileName = fileName != null ? fileName : file.getName();
        instructionCreator.addFileCopyInstruction(file, outputFileName,
                                                  instructionCreator.getInstructionsBuilder().createCopyingHandler(file, element, instructionCreator));
      }
    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(@NotNull JpsFileCopyPackagingElement element,
                                                                TargetOutputIndex outputIndex) {
      String filePath = element.getFilePath();
      if (filePath != null) {
        return outputIndex.getTargetsByOutputFile(new File(filePath));
      }
      return Collections.emptyList();
    }
  }

  private static class ExtractedDirectoryElementBuilder extends LayoutElementBuilderService<JpsExtractedDirectoryPackagingElement> {
    public ExtractedDirectoryElementBuilder() {
      super(JpsExtractedDirectoryPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsExtractedDirectoryPackagingElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final String jarPath = element.getFilePath();
      final String pathInJar = element.getPathInJar();
      instructionCreator.addExtractDirectoryInstruction(new File(jarPath), pathInJar);
    }
  }

  private static class ModuleOutputElementBuilder extends LayoutElementBuilderService<JpsProductionModuleOutputPackagingElement> {
    public ModuleOutputElementBuilder() {
      super(JpsProductionModuleOutputPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsProductionModuleOutputPackagingElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateModuleOutputInstructions(element.getOutputUrl(), instructionCreator, element);
    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(@NotNull JpsProductionModuleOutputPackagingElement element,
                                                                TargetOutputIndex outputIndex) {
      JpsModule module = element.getModuleReference().resolve();
      if (module != null) {
        return Collections.singletonList(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
      }
      return Collections.emptyList();
    }
  }

  private static class ModuleTestOutputElementBuilder extends LayoutElementBuilderService<JpsTestModuleOutputPackagingElement> {
    public ModuleTestOutputElementBuilder() {
      super(JpsTestModuleOutputPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsTestModuleOutputPackagingElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateModuleOutputInstructions(element.getOutputUrl(), instructionCreator, element);
    }

    @Override
    public Collection<? extends BuildTarget<?>> getDependencies(@NotNull JpsTestModuleOutputPackagingElement element,
                                                                TargetOutputIndex outputIndex) {
      JpsModule module = element.getModuleReference().resolve();
      if (module != null) {
        return Collections.singletonList(new ModuleBuildTarget(module, JavaModuleBuildTargetType.TEST));
      }
      return Collections.emptyList();
    }
  }

  private class ComplexElementBuilder extends LayoutElementBuilderService<JpsComplexPackagingElement> {
    public ComplexElementBuilder() {
      super(JpsComplexPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsComplexPackagingElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateSubstitutionInstructions(element, instructionCreator, builderContext);
    }
  }

  private class ArtifactOutputElementBuilder extends LayoutElementBuilderService<JpsArtifactOutputPackagingElement> {
    public ArtifactOutputElementBuilder() {
      super(JpsArtifactOutputPackagingElement.class);
    }

    @Override
    public void generateInstructions(JpsArtifactOutputPackagingElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final JpsArtifact artifact = element.getArtifactReference().resolve();
      if (artifact == null) return;

      Set<JpsArtifact> parentArtifacts = builderContext.getParentArtifacts();
      List<JpsPackagingElement> customLayout = getCustomArtifactLayout(artifact, parentArtifacts);

      final String outputPath = artifact.getOutputPath();
      if (StringUtil.isEmpty(outputPath) || customLayout != null) {
        try {
          if (builderContext.enterArtifact(artifact)) {
            if (customLayout != null) {
              LayoutElementBuildersRegistry.this.generateInstructions(customLayout, instructionCreator, builderContext);
            }
            else {
              generateSubstitutionInstructions(element, instructionCreator, builderContext);
            }
          }
        }
        finally {
          builderContext.leaveArtifact(artifact);
        }
        return;
      }

      final JpsPackagingElement rootElement = artifact.getRootElement();
      final File outputDir = new File(outputPath);
      if (rootElement instanceof JpsArchivePackagingElement) {
        final String fileName = ((JpsArchivePackagingElement)rootElement).getArchiveName();
        instructionCreator.addFileCopyInstruction(new File(outputDir, fileName), fileName);
      }
      else {
        instructionCreator.addDirectoryCopyInstructions(outputDir);
      }
    }

    @Nullable
    private List<JpsPackagingElement> getCustomArtifactLayout(@NotNull JpsArtifact artifact, @NotNull Set<JpsArtifact> parentArtifacts) {
      for (ArtifactLayoutCustomizationService service : JpsServiceManager.getInstance().getExtensions(ArtifactLayoutCustomizationService.class)) {
        List<JpsPackagingElement> elements = service.getCustomizedLayout(artifact, parentArtifacts);
        if (elements != null) {
          return elements;
        }
      }
      return null;
    }
  }
}
