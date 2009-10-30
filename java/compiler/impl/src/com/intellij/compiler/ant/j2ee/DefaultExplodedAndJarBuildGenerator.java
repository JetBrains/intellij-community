/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.ExplodedAndJarBuildGenerator;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.module.Module;
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
                                                                     parameters.getGenerationOptions());

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


}
