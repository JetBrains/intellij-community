/**
 * @author cdr
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.module.Module;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;

public abstract class BuildExplodedTarget extends Target {
  
  protected abstract String getExplodedBuildPathProperty(String name);
  protected abstract String getExplodedBuildPathProperty();

  public BuildExplodedTarget(final ModuleChunk chunk,
                             final GenerationOptions genOptions,
                             final Module moduleToBuild,
                             @NotNull final BuildConfiguration buildConfiguration,
                             final Function<String, String> explodedBuildTarget,
                             final String description) {
    super(explodedBuildTarget.fun(chunk.getName()), null, description, null);
    final Module module = chunk.getModules()[0];
    final File moduleBaseDir = chunk.getBaseDir();

    final ArrayList<Tag> tags = new ArrayList<Tag>();

    BuildRecipe buildRecipe = DeploymentUtil.getInstance().getModuleItems(moduleToBuild);
    // reverse order to get overwriting instructions later
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final String outputRelativePath = "/"+instruction.getOutputRelativePath();

        final String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, BuildProperties.getModuleBasedirProperty(instruction.getModule()), genOptions, !module.isSavePathsRelative());

        final Copy copy;
        if (instruction.isDirectory()) {
          copy = new Copy(BuildProperties.propertyRef(getExplodedBuildPathProperty())+outputRelativePath);
          final FileSet fileSet = new FileSet(sourceLocation);
          copy.add(fileSet);
        }
        else {
          copy = new Copy(sourceLocation, BuildProperties.propertyRef(getExplodedBuildPathProperty())+outputRelativePath);
        }
        tags.add(copy);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = instruction.getOutputRelativePath();
        final String destFile = BuildProperties.propertyRef(getExplodedBuildPathProperty())+outputRelativePath;
        final @NonNls String jarDirProperty = "jar.dir";
        tags.add(new Dirname(jarDirProperty, destFile));
        tags.add(new Mkdir(BuildProperties.propertyRef(jarDirProperty)));
        tags.add(generateJarTag(instruction, destFile, moduleBaseDir, genOptions));
        return true;
      }

      public boolean visitJ2EEModuleBuildInstruction(JavaeeModuleBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = "/"+instruction.getOutputRelativePath();
        final String outputPath = BuildProperties.propertyRef(getExplodedBuildPathProperty())+outputRelativePath;
        final String moduleName = instruction.getModule().getName();

        final Tag tag;
        if (instruction.getBuildProperties().isExplodedEnabled()) {
          tag = new Copy(outputPath);
          tag.add(new FileSet(BuildProperties.propertyRef(getExplodedBuildPathProperty(moduleName))));
        }
        else {
          tag = new AntCall(explodedBuildTarget.fun(moduleName));
          tag.add(new Param(getExplodedBuildPathProperty(), outputPath));
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