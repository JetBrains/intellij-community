/**
 * @author cdr
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.make.FileCopyInstruction;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.compiler.make.JarAndCopyBuildInstruction;
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.JavaeeModuleBuildInstruction;
import com.intellij.openapi.compiler.make.ModuleBuildProperties;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.deployment.DeploymentUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;

public class BuildExplodedTarget extends Target {
  public BuildExplodedTarget(final ModuleChunk chunk, final File moduleBaseDir,
                                 final GenerationOptions genOptions, @NotNull final ModuleBuildProperties moduleBuildProperties) {
    super(BuildProperties.getJ2EEExplodedBuildTargetName(chunk.getName()), null,
          CompilerBundle.message("generated.ant.build.build.exploded.dir.for.module.description", chunk.getName()), null);
    final Module module = chunk.getModules()[0];

    final ArrayList<Tag> tags = new ArrayList<Tag>();

    BuildRecipe buildRecipe = DeploymentUtil.getInstance().getModuleItems(moduleBuildProperties.getModule());
    // reverse order to get overwriting instructions later
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final String outputRelativePath = "/"+instruction.getOutputRelativePath();
        final String explodedPathProperty = BuildProperties.getJ2EEExplodedPathProperty();

        final String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, BuildProperties.getModuleBasedirProperty(instruction.getModule()), genOptions, !module.isSavePathsRelative());

        final Copy copy;
        if (instruction.isDirectory()) {
          copy = new Copy(BuildProperties.propertyRef(explodedPathProperty)+outputRelativePath);
          final FileSet fileSet = new FileSet(sourceLocation);
          copy.add(fileSet);
        }
        else {
          copy = new Copy(sourceLocation, BuildProperties.propertyRef(explodedPathProperty)+outputRelativePath);
        }
        tags.add(copy);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = instruction.getOutputRelativePath();
        final String explodedPathProperty = BuildProperties.getJ2EEExplodedPathProperty();
        final String destFile = BuildProperties.propertyRef(explodedPathProperty)+outputRelativePath;
        final @NonNls String jarDirProperty = "jar.dir";
        tags.add(new Dirname(jarDirProperty, destFile));
        tags.add(new Mkdir(BuildProperties.propertyRef(jarDirProperty)));
        tags.add(generateJarTag(instruction, destFile, moduleBaseDir, genOptions));
        return true;
      }

      public boolean visitJ2EEModuleBuildInstruction(JavaeeModuleBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = "/"+instruction.getOutputRelativePath();
        final String explodedPathProperty = BuildProperties.getJ2EEExplodedPathProperty();
        final String outputPath = BuildProperties.propertyRef(explodedPathProperty)+outputRelativePath;
        final String moduleName = instruction.getBuildProperties().getModule().getName();

        final Tag tag;
        if (instruction.getBuildProperties().isExplodedEnabled()) {
          tag = new Copy(outputPath);
          tag.add(new FileSet(BuildProperties.propertyRef(BuildProperties.getJ2EEExplodedPathProperty(moduleName))));
        }
        else {
          tag = new AntCall(BuildProperties.getJ2EEExplodedBuildTargetName(moduleName));
          tag.add(new Param(BuildProperties.getJ2EEExplodedPathProperty(), outputPath));
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
    String sourceLocation = GenerationUtils.toRelativePath(moduleOutputDirPath, moduleBaseDir, BuildProperties.getModuleBasedirProperty(module), genOptions, !module.isSavePathsRelative());
    final FileSet fileSet = new FileSet(sourceLocation);
    tag.add(fileSet);
    return tag;
  }

}