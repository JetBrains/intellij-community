/**
 * @author cdr
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;

public class BuildExplodedTarget extends Target {
  
  public BuildExplodedTarget(final ExplodedAndJarTargetParameters parameters,
                             final BuildRecipe buildRecipe,
                             final String description) {
    super(parameters.getBuildExplodedTargetName(parameters.getConfigurationName()), null, description, null);
    final Module containingModule = parameters.getContainingModule();
    final File moduleBaseDir = parameters.getChunk().getBaseDir();

    final ArrayList<Tag> tags = new ArrayList<Tag>();

    // reverse order to get overwriting instructions later
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final String outputRelativePath = "/" + instruction.getOutputRelativePath();

        final String baseDirProperty = BuildProperties.getModuleBasedirProperty(instruction.getModule());
        final String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, baseDirProperty,
                                                                     parameters.getGenerationOptions(), !containingModule.isSavePathsRelative());

        final Copy copy;
        if (instruction.isDirectory()) {
          copy = new Copy(BuildProperties.propertyRef(parameters.getExplodedPathProperty()) + outputRelativePath);
          final FileSet fileSet = new FileSet(sourceLocation);
          copy.add(fileSet);
        }
        else {
          copy = new Copy(sourceLocation, BuildProperties.propertyRef(parameters.getExplodedPathProperty()) + outputRelativePath);
        }
        tags.add(copy);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = instruction.getOutputRelativePath();
        final String destFile = BuildProperties.propertyRef(parameters.getExplodedPathProperty()) + outputRelativePath;
        final @NonNls String jarDirProperty = "jar.dir";
        tags.add(new Dirname(jarDirProperty, destFile));
        tags.add(new Mkdir(BuildProperties.propertyRef(jarDirProperty)));
        tags.add(generateJarTag(instruction, destFile, moduleBaseDir, parameters.getGenerationOptions()));
        return true;
      }

      public boolean visitCompoundBuildInstruction(CompoundBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = "/" + instruction.getOutputRelativePath();
        final String outputPath = BuildProperties.propertyRef(parameters.getExplodedPathProperty()) + outputRelativePath;
        final String configurationName = parameters.getConfigurationName(instruction);

        final Tag tag;
        if (instruction.getBuildProperties().isExplodedEnabled()) {
          tag = new Copy(outputPath);
          tag.add(new FileSet(BuildProperties.propertyRef(parameters.getExplodedPathProperty(configurationName))));
        }
        else {
          tag = new AntCall(parameters.getBuildExplodedTargetName(configurationName));
          tag.add(new Param(parameters.getExplodedPathProperty(), outputPath));
        }
        tags.add(tag);
        return true;
      }
    }, true);
    for (Tag tag : tags) {
      add(tag);
    }
  }

  private static Tag generateJarTag(BuildInstruction instruction, final String destFile, File moduleBaseDir, final GenerationOptions genOptions) {
    final Tag tag = new Jar(destFile, "preserve");

    final Module module = instruction.getModule();
    final String moduleOutputDirPath = MakeUtil.getModuleOutputDirPath(module);
    String sourceLocation = GenerationUtils.toRelativePath(moduleOutputDirPath, moduleBaseDir, module, genOptions);
    final FileSet fileSet = new FileSet(sourceLocation);
    tag.add(fileSet);
    return tag;
  }

}