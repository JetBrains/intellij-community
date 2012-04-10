package org.jetbrains.jps.incremental.artifacts.builders;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ClassMap;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.artifacts.*;
import org.jetbrains.jps.idea.OwnServiceLoader;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;

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

  public void generateInstructions(LayoutElement layoutElement, ArtifactCompilerInstructionCreator instructionCreator,
                                   ArtifactInstructionsBuilderContext builderContext) {
    final LayoutElementBuilderService builder = myBuilders.get(layoutElement.getClass());
    if (builder == null) {
      LOG.error("Builder not found for artifact output layout element of class " + layoutElement.getClass());
    }
    builder.generateInstructions(layoutElement, instructionCreator, builderContext);
  }

  private void generateChildrenInstructions(CompositeLayoutElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                            ArtifactInstructionsBuilderContext builderContext) {
    generateInstructions(element.getChildren(), instructionCreator, builderContext);
  }

  private void generateSubstitutionInstructions(ComplexLayoutElement element,
                                                ArtifactCompilerInstructionCreator instructionCreator,
                                                ArtifactInstructionsBuilderContext builderContext) {
    final List<LayoutElement> substitution = element.getSubstitution(builderContext.getProject());
    if (substitution != null) {
      generateInstructions(substitution, instructionCreator, builderContext);
    }
  }

  private void generateInstructions(final List<LayoutElement> elements, ArtifactCompilerInstructionCreator instructionCreator,
                                    ArtifactInstructionsBuilderContext builderContext) {
    for (LayoutElement child : elements) {
      generateInstructions(child, instructionCreator, builderContext);
    }
  }

  private static void generateModuleOutputInstructions(String moduleName,
                                                       boolean tests,
                                                       ArtifactCompilerInstructionCreator creator,
                                                       ArtifactInstructionsBuilderContext context) {
    final Module module = context.getProject().getModules().get(moduleName);
    if (module != null) {
      final File outputDir = context.getProjectPaths().getModuleOutputDir(module, tests);
      if (outputDir != null) {
        creator.addDirectoryCopyInstructions(outputDir);
      }
    }
  }

  private class RootElementBuilder extends LayoutElementBuilderService<RootElement> {
    public RootElementBuilder() {
      super(RootElement.class);
    }

    @Override
    public void generateInstructions(RootElement element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator, builderContext);
    }
  }

  private class DirectoryElementBuilder extends LayoutElementBuilderService<DirectoryElement> {
    public DirectoryElementBuilder() {
      super(DirectoryElement.class);
    }

    @Override
    public void generateInstructions(DirectoryElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator.subFolder(element.getName()), builderContext);
    }
  }

  private class ArchiveElementBuilder extends LayoutElementBuilderService<ArchiveElement> {
    public ArchiveElementBuilder() {
      super(ArchiveElement.class);
    }

    @Override
    public void generateInstructions(ArchiveElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator.archive(element.getName()), builderContext);
    }
  }

  private static class DirectoryCopyElementBuilder extends LayoutElementBuilderService<DirectoryCopyElement> {
    public DirectoryCopyElementBuilder() {
      super(DirectoryCopyElement.class);
    }

    @Override
    public void generateInstructions(DirectoryCopyElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final String dirPath = element.getDirPath();
      if (dirPath != null) {
        final File directory = new File(FileUtil.toSystemDependentName(dirPath));
        if (directory.isDirectory()) {
          instructionCreator.addDirectoryCopyInstructions(directory);
        }
      }
    }
  }

  private static class FileCopyElementBuilder extends LayoutElementBuilderService<FileCopyElement> {
    public FileCopyElementBuilder() {
      super(FileCopyElement.class);
    }

    @Override
    public void generateInstructions(FileCopyElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final String filePath = element.getFilePath();
      if (filePath != null) {
        final File file = new File(FileUtil.toSystemDependentName(filePath));
        if (file.isFile()) {
          final String fileName = element.getOutputFileName();
          instructionCreator.addFileCopyInstruction(file, fileName != null ? fileName : file.getName());
        }
      }
    }
  }

  private static class ExtractedDirectoryElementBuilder extends LayoutElementBuilderService<ExtractedDirectoryElement> {
    public ExtractedDirectoryElementBuilder() {
      super(ExtractedDirectoryElement.class);
    }

    @Override
    public void generateInstructions(ExtractedDirectoryElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final String jarPath = element.getJarPath();
      final String pathInJar = element.getPathInJar();
      File jarFile = new File(FileUtil.toSystemDependentName(jarPath));
      if (jarFile.isFile()) {
        instructionCreator.addExtractDirectoryInstruction(jarFile, pathInJar);
      }
    }
  }

  private static class ModuleOutputElementBuilder extends LayoutElementBuilderService<ModuleOutputElement> {
    public ModuleOutputElementBuilder() {
      super(ModuleOutputElement.class);
    }

    @Override
    public void generateInstructions(ModuleOutputElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateModuleOutputInstructions(element.getModuleName(), false, instructionCreator, builderContext);
    }
  }

  private static class ModuleTestOutputElementBuilder extends LayoutElementBuilderService<ModuleTestOutputElement> {
    public ModuleTestOutputElementBuilder() {
      super(ModuleTestOutputElement.class);
    }

    @Override
    public void generateInstructions(ModuleTestOutputElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateModuleOutputInstructions(element.getModuleName(), true, instructionCreator, builderContext);
    }
  }

  private class ComplexElementBuilder extends LayoutElementBuilderService<ComplexLayoutElement> {
    public ComplexElementBuilder() {
      super(ComplexLayoutElement.class);
    }

    @Override
    public void generateInstructions(ComplexLayoutElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateSubstitutionInstructions(element, instructionCreator, builderContext);
    }
  }

  private class ArtifactOutputElementBuilder extends LayoutElementBuilderService<ArtifactLayoutElement> {
    public ArtifactOutputElementBuilder() {
      super(ArtifactLayoutElement.class);
    }

    @Override
    public void generateInstructions(ArtifactLayoutElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      final Artifact artifact = element.findArtifact(builderContext.getProject());
      if (artifact == null) return;

      final String outputPath = artifact.getOutputPath();
      if (StringUtil.isEmpty(outputPath)) {
        generateSubstitutionInstructions(element, instructionCreator, builderContext);
        return;
      }

      final LayoutElement rootElement = artifact.getRootElement();
      final File outputDir = new File(FileUtil.toSystemDependentName(outputPath));
      if (rootElement instanceof ArchiveElement) {
        final String fileName = ((ArchiveElement)rootElement).getName();
        instructionCreator.addFileCopyInstruction(new File(outputDir, fileName), fileName);
      }
      else {
        instructionCreator.addDirectoryCopyInstructions(outputDir);
      }
    }
  }
}
