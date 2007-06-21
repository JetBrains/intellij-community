package com.intellij.compiler.impl.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collection;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class CompoundBuildInstructionImpl extends BuildInstructionBase implements CompoundBuildInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.make.J2EEModuleBuildInstructionImpl");

  private final BuildConfiguration myBuildConfiguration;
  private BuildParticipant myBuildParticipant;
  private final BuildRecipe myBuildRecipe;
  @NonNls protected static final String TMP_FILE_SUFFIX = ".tmp";

  public CompoundBuildInstructionImpl(@Nullable BuildRecipe buildRecipe, BuildParticipant buildParticipant, String outputRelativePath) {
    super(outputRelativePath, buildParticipant.getModule());
    myBuildRecipe = buildRecipe;
    myBuildConfiguration = buildParticipant.getBuildConfiguration();
    myBuildParticipant = buildParticipant;
    LOG.assertTrue(!isExternalDependencyInstruction());
  }

  public BuildParticipant getBuildParticipant() {
    return myBuildParticipant;
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitCompoundBuildInstruction(this);
  }

  public void addFilesToJar(@NotNull final CompileContext context,
                            @NotNull final File jarFile,
                            @NotNull final JarOutputStream outputStream,
                            BuildRecipe dependencies,
                            @Nullable final Set<String> writtenRelativePaths,
                            @Nullable final FileFilter fileFilter) throws IOException {
    // create temp jars, and add these into upper level jar
    // todo optimization: cache created jars
    final File tempFile;
    final BuildRecipe childDependencies = new BuildRecipeImpl();
    if (myBuildConfiguration.isJarEnabled()) {
      tempFile = new File(myBuildConfiguration.getJarPath());
      final BuildRecipe childModuleRecipe = getChildInstructions(context);
      childModuleRecipe.visitInstructions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
          if (instruction.isExternalDependencyInstruction()) {
            childDependencies.addInstruction(instruction);
          }
          return true;
        }
      }, false);
    }
    else {
      tempFile = File.createTempFile("jar___", TMP_FILE_SUFFIX);
      tempFile.deleteOnExit();
      makeJar(context, tempFile, childDependencies, fileFilter, true);
      childDependencies.visitInstructions(new BuildInstructionVisitor() {
        public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
          File file = new File(PathUtil.getCanonicalPath(DeploymentUtil.appendToPath(tempFile.getPath(), instruction.getOutputRelativePath())));
          addFileToDelete(file);
          return true;
        }
      }, false);
    }
    ZipUtil.addFileToZip(outputStream, tempFile, getOutputRelativePath(), writtenRelativePaths, fileFilter);
    try {
      childDependencies.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
          File file = instruction.getFile();
          String dependencyRelativePath = PathUtil.getCanonicalPath(DeploymentUtil.appendToPath(getOutputRelativePath(), instruction.getOutputRelativePath()));

          ZipUtil.addFileOrDirRecursively(outputStream, jarFile, file, dependencyRelativePath, fileFilter, writtenRelativePaths);
          return true;
        }

        public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws Exception {
          if (instruction.getJarFile() == null) {
            File tempJar = File.createTempFile("___",TMP_FILE_SUFFIX);
            addFileToDelete(tempJar);
            instruction.makeJar(context, tempJar, fileFilter);
          }
          File jarFile = instruction.getJarFile();
          String dependencyRelativePath = PathUtil.getCanonicalPath(DeploymentUtil.appendToPath(getOutputRelativePath(), instruction.getOutputRelativePath()));

          ZipUtil.addFileToZip(outputStream, jarFile, dependencyRelativePath, writtenRelativePaths, fileFilter);
          return true;
        }
      }, false);
    }
    catch (IOException e) {
      throw e;
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
    }
  }

  // return jarFile and possible jars linked via manifest
  public void makeJar(final CompileContext context,
                      final File jarFile,
                      final BuildRecipe dependencies,
                      final FileFilter fileFilter,
                      final boolean processExternalDependencies) throws IOException {
    final BuildRecipe buildRecipe = getChildInstructions(context);
    final Manifest manifest = DeploymentUtil.getInstance().createManifest(buildRecipe);
    if (manifest == null) {
      File file = DeploymentUtil.getInstance().findUserSuppliedManifestFile(buildRecipe);
      LOG.assertTrue(file != null);
      context.addMessage(CompilerMessageCategory.WARNING, CompilerBundle.message("message.text.using.user.supplied.manifest", file.getAbsolutePath()), null, -1, -1);
    }
    FileUtil.createParentDirs(jarFile);
    final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(jarFile));
    final JarOutputStream jarOutputStream = manifest == null ? new JarOutputStream(out) : new JarOutputStream(out, manifest);

    final Set<String> tempWrittenRelativePaths = new THashSet<String>();
    if (manifest != null) {
      tempWrittenRelativePaths.add(JarFile.MANIFEST_NAME);
    }
    try {
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws IOException {
          if (processExternalDependencies || !instruction.isExternalDependencyInstruction()) {
            instruction.addFilesToJar(context, jarFile, jarOutputStream, dependencies, tempWrittenRelativePaths, fileFilter);
          }
          return true;
        }
      }, false);
    }
    catch (IOException e) {
      throw e;
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
    }
    finally {
      jarOutputStream.close();
    }
  }

  public BuildRecipe getChildInstructions(CompileContext context) {
    if (myBuildRecipe != null) {
      return myBuildRecipe;
    }
    return myBuildParticipant.getBuildInstructions(context);
  }

  public BuildConfiguration getBuildProperties() {
    return myBuildConfiguration;
  }

  public String toString() {
    return "Java EE build instruction: " +  myBuildParticipant + " -> " + getOutputRelativePath();
  }

  public File findFileByRelativePath(String relativePath) {
    if (!relativePath.startsWith(getOutputRelativePath())) return null;
    final String pathFromFile = relativePath.substring(getOutputRelativePath().length());
    final Ref<File> file = new Ref<File>();
    final BuildRecipe buildRecipe = getChildInstructions(null);
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
        final File found = instruction.findFileByRelativePath(pathFromFile);
        if (found != null) {
          file.set(found);
          return false;
        }
        return true;
      }
    }, false);
    return file.get();
  }

  public void collectFilesToDelete(final Collection<File> filesToDelete) {
    super.collectFilesToDelete(filesToDelete);
    BuildRecipe childInstructions = getChildInstructions(null);
    childInstructions.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws Exception {
        ((BuildInstructionBase)instruction).collectFilesToDelete(filesToDelete);
        return true;
      }
    }, false);
  }
}
