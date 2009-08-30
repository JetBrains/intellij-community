/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.ExplodedAndJarBuildGenerator;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author peter
 */
public class DefaultExplodedAndJarBuildGenerator extends ExplodedAndJarBuildGenerator {
  public static final DefaultExplodedAndJarBuildGenerator INSTANCE = new DefaultExplodedAndJarBuildGenerator();

  @NotNull 
  public Tag[] generateTagsForExplodedTarget(@NotNull final BuildInstruction instruction, @NotNull final ExplodedAndJarTargetParameters parameters,
                                             final int instructionCount)
    throws Exception {
    final File moduleBaseDir = parameters.getChunk().getBaseDir();
    final List<Tag> tags = new SmartList<Tag>();
    instruction.accept(new BuildInstructionVisitor() {
      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final String outputRelativePath = "/" + instruction.getOutputRelativePath();

        final String baseDirProperty = BuildProperties.getModuleBasedirProperty(instruction.getModule());
        final String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, baseDirProperty,
                                                                     parameters.getGenerationOptions(), !parameters.getContainingModule().isSavePathsRelative());

        final Copy copy;
        if (instruction.isDirectory()) {
          copy = new Copy(BuildProperties.propertyRef(parameters.getExplodedPathParameter()) + outputRelativePath);
          final FileSet fileSet = new FileSet(sourceLocation);
          copy.add(fileSet);
        }
        else {
          copy = new Copy(sourceLocation, BuildProperties.propertyRef(parameters.getExplodedPathParameter()) + outputRelativePath);
        }
        tags.add(copy);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = instruction.getOutputRelativePath();
        final String destFile = BuildProperties.propertyRef(parameters.getExplodedPathParameter()) + outputRelativePath;
        final @NonNls String jarDirProperty = parameters.getBuildExplodedTargetName() + ".jar.dir" + instructionCount;
        tags.add(new Dirname(jarDirProperty, destFile));
        tags.add(new Mkdir(BuildProperties.propertyRef(jarDirProperty)));
        tags.add(generateExplodedTag(instruction, destFile, moduleBaseDir, parameters.getGenerationOptions()));
        return true;
      }

      public boolean visitCompoundBuildInstruction(CompoundBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String outputRelativePath = "/" + instruction.getOutputRelativePath();
        final String outputPath = BuildProperties.propertyRef(parameters.getExplodedPathParameter()) + outputRelativePath;

        final Tag tag;
        if (instruction.getBuildProperties().isExplodedEnabled()) {
          tag = new Copy(outputPath);
          tag.add(new FileSet(BuildProperties.propertyRef(parameters.getCompoundBuildInstructionNaming().getExplodedPathProperty(instruction))));
        }
        else {
          tag = new AntCall(parameters.getCompoundBuildInstructionNaming().getBuildExplodedTargetName(instruction));
          tag.add(new Param(parameters.getExplodedPathParameter(), outputPath));
        }
        tags.add(tag);
        return true;
      }
    });
    return tags.toArray(new Tag[tags.size()]);
  }

  private static Tag generateExplodedTag(BuildInstruction instruction, final String destFile, File moduleBaseDir, final GenerationOptions genOptions) {
    final Tag tag = new Jar(destFile, "preserve");
    final Module module = instruction.getModule();
    final String moduleOutputDirPath = MakeUtil.getModuleOutputDirPath(module);
    String sourceLocation = GenerationUtils.toRelativePath(moduleOutputDirPath, moduleBaseDir, module, genOptions);
    final FileSet fileSet = new FileSet(sourceLocation);
    tag.add(fileSet);
    return tag;
  }

  public static Tag generateJarTag(BuildInstruction instruction, final String destFile, File moduleBaseDir, final GenerationOptions genOptions) {
    final Tag jarTag = new Jar(destFile, "preserve");

    final Manifest manifestTag = new Manifest();
    jarTag.add(manifestTag);
    manifestTag.applyAttributes(new java.util.jar.Manifest());

    final Module module = instruction.getModule();
    final String moduleOutputDirPath = MakeUtil.getModuleOutputDirPath(module);
    String sourceLocation = GenerationUtils.toRelativePath(moduleOutputDirPath, moduleBaseDir, module, genOptions);
    final FileSet fileSet = new FileSet(sourceLocation);
    jarTag.add(fileSet);
    return jarTag;
  }



  @NotNull
  public ZipFileSet[] generateTagsForJarTarget(@NotNull final BuildInstruction instruction, @NotNull final ExplodedAndJarTargetParameters parameters,
                                final Ref<Boolean> tempDirUsed)
    throws Exception {
    final List<ZipFileSet> zipFileSetTags = new SmartList<ZipFileSet>();
    final String tempDirProperty = BuildProperties.getTempDirForModuleProperty(parameters.getContainingModule().getName());
    final File moduleBaseDir = parameters.getChunk().getBaseDir();
    instruction.accept(new BuildInstructionVisitor() {
      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final Module instructionModule = instruction.getModule();
        final String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, instructionModule,
                                                                     parameters.getGenerationOptions());
        final ZipFileSet fileSet = new ZipFileSet(sourceLocation, instruction.getOutputRelativePath(), instruction.isDirectory());

        zipFileSetTags.add(fileSet);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        tempDirUsed.set(true);
        final String jarName = new File(instruction.getOutputRelativePath()).getName();
        final String destJarPath = BuildProperties.propertyRef(tempDirProperty)+"/"+jarName;

        zipFileSetTags.add(new ZipFileSet(destJarPath, instruction.getOutputRelativePath(), false));
        return true;
      }

      public boolean visitCompoundBuildInstruction(CompoundBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        // gather child module dependencies
        final BuildRecipe childModuleRecipe = instruction.getChildInstructions(DummyCompileContext.getInstance());
        childModuleRecipe.visitInstructions(new BuildInstructionVisitor() {
          public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
            if (!instruction.isExternalDependencyInstruction()) return true;
            final File file = instruction.getFile();
            final Module instructionModule = instruction.getModule();
            String sourceLocation = GenerationUtils.toRelativePath(file.getPath(), moduleBaseDir, instructionModule,
                                                                   parameters.getGenerationOptions());
            final String relPath = PathUtil.getCanonicalPath("/tmp/"+instruction.getOutputRelativePath()).substring(1);
            final ZipFileSet zipFileSet = new ZipFileSet(sourceLocation, relPath, false);
            zipFileSetTags.add(zipFileSet);
            return true;
          }

          public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
            if (!instruction.isExternalDependencyInstruction()) return true;
            final String relPath = PathUtil.getCanonicalPath("/tmp/"+instruction.getOutputRelativePath()).substring(1);
            tempDirUsed.set(true);
            final ZipFileSet zipFileSet = new ZipFileSet(BuildProperties.propertyRef(tempDirProperty) + "/" + relPath, relPath, false);
            zipFileSetTags.add(zipFileSet);
            return true;
          }
        }, false);

        if (instruction.getBuildProperties().isJarEnabled()) {
          final ZipFileSet zipFileSet = new ZipFileSet(BuildProperties.propertyRef(parameters.getCompoundBuildInstructionNaming().getJarPathProperty(instruction)), instruction.getOutputRelativePath(), false);
          zipFileSetTags.add(zipFileSet);
        }
        else {
          final String jarName = new File(instruction.getOutputRelativePath()).getName();
          final String destJarPath = BuildProperties.propertyRef(tempDirProperty)+"/"+jarName;
          tempDirUsed.set(true);
          zipFileSetTags.add(new ZipFileSet(destJarPath, instruction.getOutputRelativePath(), false));
        }
        return true;
      }
    });
    return zipFileSetTags.toArray(new ZipFileSet[zipFileSetTags.size()]);
  }

  @NotNull
  public Tag[] generateJarBuildPrepareTags(@NotNull final BuildInstruction instruction, @NotNull final ExplodedAndJarTargetParameters parameters)
    throws Exception {
    final List<Tag> prepareTags = new SmartList<Tag>();
    final String tempDirProperty = BuildProperties.getTempDirForModuleProperty(parameters.getContainingModule().getName());
    final File moduleBaseDir = parameters.getChunk().getBaseDir();
    instruction.accept(new BuildInstructionVisitor() {

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String jarName = new File(instruction.getOutputRelativePath()).getName();
        final String destJarPath = BuildProperties.propertyRef(tempDirProperty)+"/"+jarName;
        prepareTags.add(generateJarTag(instruction, destJarPath, moduleBaseDir, parameters.getGenerationOptions()));
        return true;
      }

      public boolean visitCompoundBuildInstruction(CompoundBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        if (!instruction.getBuildProperties().isJarEnabled()) {
          final String jarName = new File(instruction.getOutputRelativePath()).getName();
          final String destJarPath = BuildProperties.propertyRef(tempDirProperty)+"/"+jarName;
          final AntCall makeJar = new AntCall(parameters.getCompoundBuildInstructionNaming().getBuildJarTargetName(instruction));
          prepareTags.add(makeJar);
          makeJar.add(new Param(parameters.getJarPathParameter(), destJarPath));
        }
        return true;
      }
    });
    return prepareTags.toArray(new Tag[prepareTags.size()]);
  }
}
