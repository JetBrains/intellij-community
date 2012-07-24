package org.jetbrains.jps.incremental.artifacts.builders;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ClassMap;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.idea.OwnServiceLoader;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement;
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement;

import java.io.File;
import java.util.List;

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
    myBuilders = new ClassMap<LayoutElementBuilderService>();
    LayoutElementBuilderService<?>[] standardBuilders = {
      new RootElementBuilder(), new DirectoryElementBuilder(), new ArchiveElementBuilder(), new DirectoryCopyElementBuilder(),
      new FileCopyElementBuilder(), new ExtractedDirectoryElementBuilder(), new ModuleOutputElementBuilder(),
      new ModuleTestOutputElementBuilder(), new ComplexElementBuilder(), new ArtifactOutputElementBuilder()
    };
    for (LayoutElementBuilderService<?> builder : standardBuilders) {
      myBuilders.put(builder.getElementClass(), builder);
    }
    for (LayoutElementBuilderService builder : OwnServiceLoader.load(LayoutElementBuilderService.class)) {
      myBuilders.put(builder.getElementClass(), builder);
    }
  }

  public void generateInstructions(JpsPackagingElement layoutElement, ArtifactCompilerInstructionCreator instructionCreator,
                                   ArtifactInstructionsBuilderContext builderContext) {
    final LayoutElementBuilderService builder = myBuilders.get(layoutElement.getClass());
    if (builder != null) {
      //noinspection unchecked
      builder.generateInstructions(layoutElement, instructionCreator, builderContext);
    }
    else {
      LOG.error("Builder not found for artifact output layout element of class " + layoutElement.getClass());
    }
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

  private static void generateModuleOutputInstructions(String outputUrl, ArtifactCompilerInstructionCreator creator) {
    if (outputUrl != null) {
      creator.addDirectoryCopyInstructions(JpsPathUtil.urlToFile(outputUrl));
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
        final File directory = new File(FileUtil.toSystemDependentName(dirPath));
        if (directory.isDirectory()) {
          instructionCreator.addDirectoryCopyInstructions(directory);
        }
      }
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
        final File file = new File(FileUtil.toSystemDependentName(filePath));
        if (file.isFile()) {
          final String fileName = element.getRenamedOutputFileName();
          instructionCreator.addFileCopyInstruction(file, fileName != null ? fileName : file.getName());
        }
      }
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
      File jarFile = new File(FileUtil.toSystemDependentName(jarPath));
      if (jarFile.isFile()) {
        instructionCreator.addExtractDirectoryInstruction(jarFile, pathInJar);
      }
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
      generateModuleOutputInstructions(element.getOutputUrl(), instructionCreator);
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
      generateModuleOutputInstructions(element.getOutputUrl(), instructionCreator);
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

      final String outputPath = artifact.getOutputPath();
      if (StringUtil.isEmpty(outputPath)) {
        generateSubstitutionInstructions(element, instructionCreator, builderContext);
        return;
      }

      final JpsPackagingElement rootElement = artifact.getRootElement();
      final File outputDir = new File(FileUtil.toSystemDependentName(outputPath));
      if (rootElement instanceof JpsArchivePackagingElement) {
        final String fileName = ((JpsArchivePackagingElement)rootElement).getArchiveName();
        instructionCreator.addFileCopyInstruction(new File(outputDir, fileName), fileName);
      }
      else {
        instructionCreator.addDirectoryCopyInstructions(outputDir);
      }
    }
  }
}
