/**
 * @author cdr
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;

public class BuildJarTarget extends Target {

  public BuildJarTarget(final ModuleChunk chunk, final File moduleBaseDir, final GenerationOptions genOptions,
                            @NotNull final ModuleBuildProperties moduleBuildProperties) {
    super(J2EEBuildProperties.getJ2EEJarBuildTargetName(chunk.getName()), null,
          CompilerBundle.message("generated.ant.build.build.j2ee.archive.for.module.description", chunk.getName()), null);
    final Module module = chunk.getModules()[0];
    final String moduleName = module.getName();

    //noinspection HardCodedStringLiteral
    final File jarDir = new File(moduleBuildProperties.isJarEnabled() ? new File(moduleBuildProperties.getJarPath()).getParentFile() : moduleBaseDir, "/temp");
    String tempDir = GenerationUtils.toRelativePath(jarDir.getPath(), moduleBaseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !chunk.isSavePathsRelative());
    final String tempDirProperty = J2EEBuildProperties.getTempDirForModuleProperty(moduleName);
    final boolean[] tempDirUsed = new boolean[] { false };

    BuildRecipe buildRecipe = DeploymentUtil.getInstance().getModuleItems(moduleBuildProperties.getModule());
    final List<ZipFileSet> zipFileSetTags = new ArrayList<ZipFileSet>();
    final List<Tag> prepareTags = new ArrayList<Tag>();
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final File sourceFile = instruction.getFile();
        final Module instructionModule = instruction.getModule();
        final String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, BuildProperties.getModuleBasedirProperty(instructionModule), genOptions, !instructionModule.isSavePathsRelative());
        final ZipFileSet fileSet = new ZipFileSet(sourceLocation, instruction.getOutputRelativePath(), instruction.isDirectory());

        zipFileSetTags.add(fileSet);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        tempDirUsed[0] = true;
        final String jarName = new File(instruction.getOutputRelativePath()).getName();
        final String destJarPath = BuildProperties.propertyRef(tempDirProperty)+"/"+jarName;
        prepareTags.add(generateJarTag(instruction, destJarPath, moduleBaseDir, genOptions));

        zipFileSetTags.add(new ZipFileSet(destJarPath, instruction.getOutputRelativePath(), false));
        return true;
      }

      public boolean visitJ2EEModuleBuildInstruction(JavaeeModuleBuildInstruction instruction) throws RuntimeException {
        if (instruction.isExternalDependencyInstruction()) return true;
        final String moduleName = ModuleUtil.getModuleNameInReadAction(instruction.getModule());
        // gather child module dependencies
        final BuildRecipe childModuleRecipe = instruction.getChildInstructions(DummyCompileContext.getInstance());
        childModuleRecipe.visitInstructions(new BuildInstructionVisitor() {
          public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws RuntimeException {
            if (!instruction.isExternalDependencyInstruction()) return true;
            final File file = instruction.getFile();
            final Module instructionModule = instruction.getModule();
            String sourceLocation = GenerationUtils.toRelativePath(file.getPath(), moduleBaseDir, BuildProperties.getModuleBasedirProperty(instructionModule), genOptions, !instructionModule.isSavePathsRelative());
            final String relPath = PathUtil.getCanonicalPath("/tmp/"+instruction.getOutputRelativePath()).substring(1);
            final ZipFileSet zipFileSet = new ZipFileSet(sourceLocation, relPath, false);
            zipFileSetTags.add(zipFileSet);
            return true;
          }

          public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws RuntimeException {
            if (!instruction.isExternalDependencyInstruction()) return true;
            final String relPath = PathUtil.getCanonicalPath("/tmp/"+instruction.getOutputRelativePath()).substring(1);
            final ZipFileSet zipFileSet = new ZipFileSet(BuildProperties.propertyRef(tempDirProperty) + "/" + relPath, relPath, false);
            zipFileSetTags.add(zipFileSet);
            return true;
          }
        }, false);

        if (instruction.getBuildProperties().isJarEnabled()) {
          final ZipFileSet zipFileSet = new ZipFileSet(BuildProperties.propertyRef(J2EEBuildProperties.getJ2EEJarPathProperty(moduleName)), instruction.getOutputRelativePath(), false);
          zipFileSetTags.add(zipFileSet);
        }
        else {
          final String jarName = new File(instruction.getOutputRelativePath()).getName();
          final String destJarPath = BuildProperties.propertyRef(tempDirProperty)+"/"+jarName;
          tempDirUsed[0] = true;
          final AntCall makeJar = new AntCall(J2EEBuildProperties.getJ2EEJarBuildTargetName(moduleName));
          prepareTags.add(makeJar);
          makeJar.add(new Param(J2EEBuildProperties.getJ2EEJarPathProperty(), destJarPath));
          zipFileSetTags.add(new ZipFileSet(destJarPath, instruction.getOutputRelativePath(), false));
        }
        return true;
      }
    }, false);
    if (tempDirUsed[0]) {
      add(new Property(tempDirProperty, tempDir));
      add(new Mkdir(BuildProperties.propertyRef(tempDirProperty)));
    }
    for (Tag tag : prepareTags) {
      add(tag);
    }
    final String destFile = BuildProperties.propertyRef(J2EEBuildProperties.getJ2EEJarPathProperty());
    final @NonNls String jarDirProperty = "jar.dir";
    add(new Dirname(jarDirProperty, destFile));
    add(new Mkdir(BuildProperties.propertyRef(jarDirProperty)));
    final Jar jarTag = new Jar(destFile, "preserve");
    add(jarTag);
    final java.util.jar.Manifest manifest = DeploymentUtil.getInstance().createManifest(buildRecipe);

    if (manifest != null) {
      final Manifest manifestTag = new Manifest();
      jarTag.add(manifestTag);
      applyManifestAttributesToTag(manifest, manifestTag);
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
        String sourceLocation = GenerationUtils.toRelativePath(sourceFile.getPath(), moduleBaseDir, BuildProperties.getModuleBasedirProperty(instructionModule), genOptions, !instructionModule.isSavePathsRelative());
        String jarPathPropertyRef = BuildProperties.propertyRef(J2EEBuildProperties.getJ2EEJarPathProperty());

        final Copy copy;
        if (instruction.isDirectory()) {
          copy = new Copy(DeploymentUtil.appendToPath(jarPathPropertyRef,outputRelativePath));
          final FileSet fileSet = new FileSet(sourceLocation);
          copy.add(fileSet);
        }
        else {
          copy = new Copy(sourceLocation, DeploymentUtil.appendToPath(jarPathPropertyRef,outputRelativePath));
        }

        add(copy);
        return true;
      }

      public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws Exception {
        if (!instruction.isExternalDependencyInstruction()) return true;
        String jarPathPropertyRef = BuildProperties.propertyRef(J2EEBuildProperties.getJ2EEJarPathProperty());
        String pathToCreateJar = jarPathPropertyRef + DeploymentUtil.appendToPath("/",instruction.getOutputRelativePath());
        @NonNls final String jarDir = "jar.dir" + myJarDirCount++;
        add(new Dirname(jarDir, pathToCreateJar));
        add(new Mkdir(BuildProperties.propertyRef(jarDir)));
        add(generateJarTag(instruction, pathToCreateJar, moduleBaseDir, genOptions));
        return true;
      }
    }, false);
  }

  private static void applyManifestAttributesToTag(final java.util.jar.Manifest manifest, final Manifest manifestTag) {
    final Attributes mainAttributes = manifest.getMainAttributes();
    for (final Object o : mainAttributes.keySet()) {
      Attributes.Name name = (Attributes.Name)o;
      String value = (String)mainAttributes.get(name);
      manifestTag.add(new Attribute(name.toString(), value));
    }
  }

  private static Tag generateJarTag(BuildInstruction instruction, final String destFile, File moduleBaseDir, final GenerationOptions genOptions) {
    final Tag jarTag = new Jar(destFile, "preserve");

    final Manifest manifestTag = new Manifest();
    jarTag.add(manifestTag);
    final java.util.jar.Manifest manifest = new java.util.jar.Manifest();
    ManifestBuilder.setGlobalAttributes(manifest.getMainAttributes());
    applyManifestAttributesToTag(manifest, manifestTag);

    final Module module = instruction.getModule();
    final String moduleOutputDirPath = MakeUtil.getModuleOutputDirPath(module);
    String sourceLocation = GenerationUtils.toRelativePath(moduleOutputDirPath, moduleBaseDir, BuildProperties.getModuleBasedirProperty(module), genOptions, !module.isSavePathsRelative());
    final FileSet fileSet = new FileSet(sourceLocation);
    jarTag.add(fileSet);
    return jarTag;
  }

}