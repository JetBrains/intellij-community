package org.jetbrains.jps.incremental.artifacts.builders;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ClassMap;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.artifacts.*;
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

  private ClassMap<LayoutElementBuilder> myBuilders;

  private LayoutElementBuildersRegistry() {
    myBuilders = new ClassMap<LayoutElementBuilder>();
    myBuilders.put(RootElement.class, new RootElementBuilder());
    myBuilders.put(DirectoryElement.class, new DirectoryElementBuilder());
    myBuilders.put(ArchiveElement.class, new ArchiveElementBuilder());
    myBuilders.put(DirectoryCopyElement.class, new DirectoryCopyElementBuilder());
    myBuilders.put(FileCopyElement.class, new FileCopyElementBuilder());
    myBuilders.put(ExtractedDirectoryElement.class, new ExtractedDirectoryElementBuilder());
    myBuilders.put(ModuleOutputElement.class, new ModuleOutputElementBuilder());
    myBuilders.put(ModuleTestOutputElement.class, new ModuleTestOutputElementBuilder());
    myBuilders.put(ComplexLayoutElement.class, new ComplexElementBuilder());
  }

  public void generateInstructions(LayoutElement layoutElement, ArtifactCompilerInstructionCreator instructionCreator,
                                   ArtifactInstructionsBuilderContext builderContext) {
    final LayoutElementBuilder builder = myBuilders.get(layoutElement.getClass());
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

  private class RootElementBuilder extends LayoutElementBuilder<RootElement> {
    @Override
    public void generateInstructions(RootElement element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator, builderContext);
    }
  }

  private class DirectoryElementBuilder extends LayoutElementBuilder<DirectoryElement> {
    @Override
    public void generateInstructions(DirectoryElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator.subFolder(element.getName()), builderContext);
    }
  }

  private class ArchiveElementBuilder extends LayoutElementBuilder<ArchiveElement> {
    @Override
    public void generateInstructions(ArchiveElement element, ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateChildrenInstructions(element, instructionCreator.archive(element.getName()), builderContext);
    }
  }

  private static class DirectoryCopyElementBuilder extends LayoutElementBuilder<DirectoryCopyElement> {
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

  private static class FileCopyElementBuilder extends LayoutElementBuilder<FileCopyElement> {
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

  private static class ExtractedDirectoryElementBuilder extends LayoutElementBuilder<ExtractedDirectoryElement> {
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

  private static class ModuleOutputElementBuilder extends LayoutElementBuilder<ModuleOutputElement> {
    @Override
    public void generateInstructions(ModuleOutputElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateModuleOutputInstructions(element.getModuleName(), false, instructionCreator, builderContext);
    }
  }

  private static class ModuleTestOutputElementBuilder extends LayoutElementBuilder<ModuleTestOutputElement> {
    @Override
    public void generateInstructions(ModuleTestOutputElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateModuleOutputInstructions(element.getModuleName(), true, instructionCreator, builderContext);
    }
  }

  private class ComplexElementBuilder extends LayoutElementBuilder<ComplexLayoutElement> {
    @Override
    public void generateInstructions(ComplexLayoutElement element,
                                     ArtifactCompilerInstructionCreator instructionCreator,
                                     ArtifactInstructionsBuilderContext builderContext) {
      generateSubstitutionInstructions(element, instructionCreator, builderContext);
    }
  }

  private class ArtifactOutputElementBuilder extends LayoutElementBuilder<ArtifactLayoutElement> {
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
