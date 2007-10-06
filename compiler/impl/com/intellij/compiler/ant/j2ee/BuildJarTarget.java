/**
 * @author cdr
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.ExplodedAndJarTargetParameters;
import com.intellij.compiler.ant.GenerationUtils;
import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.ExplodedAndJarBuildGenerator;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuildJarTarget extends Target {

  public BuildJarTarget(final ExplodedAndJarTargetParameters parameters,
                        final BuildRecipe buildRecipe,
                        final String description) {
    super(parameters.getBuildJarTargetName(), null, description, null);
    final File moduleBaseDir = parameters.getChunk().getBaseDir();

    BuildConfiguration buildConfiguration = parameters.getBuildConfiguration();
    //noinspection HardCodedStringLiteral
    final File jarDir = new File(buildConfiguration.isJarEnabled() ? new File(buildConfiguration.getJarPath()).getParentFile() : moduleBaseDir, "/temp");
    String tempDir = GenerationUtils.toRelativePath(jarDir.getPath(), parameters.getChunk(), parameters.getGenerationOptions());
    final String tempDirProperty = BuildProperties.getTempDirForModuleProperty(parameters.getContainingModule().getName());
    final boolean[] tempDirUsed = new boolean[] { false };

    final ExplodedAndJarBuildGenerator[] generators = Extensions.getExtensions(DefaultExplodedAndJarBuildGenerator.EP_NAME);
    final List<ZipFileSet> zipFileSetTags = new ArrayList<ZipFileSet>();
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(final BuildInstruction instruction) throws Exception {
        for (final ExplodedAndJarBuildGenerator generator : generators) {
          final Ref<Boolean> ref = new Ref<Boolean>(false);
          final ZipFileSet[] tags = generator.generateTagsForJarTarget(instruction, parameters, ref);
          if (tags != null) {
            if (ref.get() != null && ref.get()) {
              tempDirUsed[0] = true;
            }
            zipFileSetTags.addAll(Arrays.asList(tags));
            return true;
          }
        }
        final Ref<Boolean> ref = Ref.create(false);
        zipFileSetTags.addAll(Arrays.asList(DefaultExplodedAndJarBuildGenerator.INSTANCE.generateTagsForJarTarget(instruction, parameters, ref)));
        if (ref.get() != null && ref.get()) {
          tempDirUsed[0] = true;
        }
        return super.visitInstruction(instruction);
      }
    }, false);
    if (tempDirUsed[0]) {
      add(new Property(tempDirProperty, tempDir));
      add(new Mkdir(BuildProperties.propertyRef(tempDirProperty)));
    }
    final List<Tag> prepareTags = new ArrayList<Tag>();
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(final BuildInstruction instruction) throws Exception {
        for (final ExplodedAndJarBuildGenerator generator : generators) {
          final Tag[] tags = generator.generateJarBuildPrepareTags(instruction, parameters);
          if (tags != null) {
            prepareTags.addAll(Arrays.asList(tags));
            return true;
          }
        }
        prepareTags.addAll(Arrays.asList(DefaultExplodedAndJarBuildGenerator.INSTANCE.generateJarBuildPrepareTags(instruction, parameters)));
        return super.visitInstruction(instruction);
      }
    }, false);
    for (Tag tag : prepareTags) {
      add(tag);
    }
    final String destFile = BuildProperties.propertyRef(parameters.getJarPathParameter());
    final @NonNls String jarDirProperty = "jar.dir";
    add(new Dirname(jarDirProperty, destFile));
    add(new Mkdir(BuildProperties.propertyRef(jarDirProperty)));
    final Jar jarTag = new Jar(destFile, "preserve");
    add(jarTag);
    final java.util.jar.Manifest manifest = DeploymentUtil.getInstance().createManifest(buildRecipe);

    if (manifest != null) {
      final Manifest manifestTag = new Manifest();
      jarTag.add(manifestTag);
      manifestTag.applyAttributes(manifest);
    }

    for (ZipFileSet zipFileSet : zipFileSetTags) {
      jarTag.add(zipFileSet);
    }
    if (tempDirUsed[0]) {
      add(new Delete(BuildProperties.propertyRef(tempDirProperty)));
    }

    // emit copy commands for dependencies
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      private int myJarDirCount = 2;

      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
        if (!instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final String outputRelativePath = DeploymentUtil.appendToPath("/",instruction.getOutputRelativePath());
        final Module instructionModule = instruction.getModule();
        String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, instructionModule,
                                                               parameters.getGenerationOptions());
        String jarPathPropertyRef = BuildProperties.propertyRef(parameters.getJarPathParameter());

        final Copy copy;
        if (instruction.isDirectory()) {
          copy = new Copy(DeploymentUtil.appendToPath(jarPathPropertyRef,outputRelativePath));
          copy.add(new FileSet(sourceLocation));
        }
        else {
          copy = new Copy(sourceLocation, DeploymentUtil.appendToPath(jarPathPropertyRef,outputRelativePath));
        }

        add(copy);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws Exception {
        if (!instruction.isExternalDependencyInstruction()) return true;
        String jarPathPropertyRef = BuildProperties.propertyRef(parameters.getJarPathParameter());
        String pathToCreateJar = jarPathPropertyRef + DeploymentUtil.appendToPath("/",instruction.getOutputRelativePath());
        @NonNls final String jarDir = "jar.dir" + myJarDirCount++;
        add(new Dirname(jarDir, pathToCreateJar));
        add(new Mkdir(BuildProperties.propertyRef(jarDir)));
        add(DefaultExplodedAndJarBuildGenerator.generateJarTag(instruction, pathToCreateJar, moduleBaseDir, parameters.getGenerationOptions()));
        return true;
      }
    }, false);
  }

}