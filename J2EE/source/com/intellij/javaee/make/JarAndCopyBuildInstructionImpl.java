package com.intellij.javaee.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.javaee.J2EEBundle;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.jar.JarFile;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 21, 2004
 * Time: 4:08:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class JarAndCopyBuildInstructionImpl extends FileCopyInstructionImpl implements JarAndCopyBuildInstruction {
  private File myJarFile;
  private List<File> myTempJars = new ArrayList<File>(1);
  @NonNls protected static final String TMP_FILE_SUFFIX = ".tmp";

  public JarAndCopyBuildInstructionImpl(Module module,
                                        File directoryToJar,
                                        String outputRelativePath, final FileFilter fileFilter) {
    super(directoryToJar, false, module, outputRelativePath, fileFilter);
  }

  public void addFilesToExploded(CompileContext context,
                                 File outputDir,
                                 Set<String> writtenPaths,
                                 FileFilter fileFilter) throws IOException {
    //todo optmization: cache created jar and issue single FileCopy on it
    final File jarFile = MakeUtil.canonicalRelativePath(outputDir, getOutputRelativePath());

    makeJar(context, jarFile, fileFilter);
    writtenPaths.add(myJarFile.getPath());
  }

  public void addFilesToJar(@NotNull CompileContext context,
                            @NotNull File jarFile,
                            @NotNull JarOutputStream outputStream,
                            BuildRecipe dependencies,
                            @Nullable Set<String> writtenRelativePaths,
                            @Nullable FileFilter fileFilter) throws IOException {
    // create temp jars, and add these into upper level jar
    // todo optimization: cache created jars
    @NonNls final String moduleName = getModule() == null ? "jar" : ModuleUtil.getModuleNameInReadAction(getModule());
    final File tempFile = File.createTempFile(moduleName+"___",TMP_FILE_SUFFIX);
    myTempJars.add(tempFile);
    makeJar(context, tempFile, fileFilter);

    final String outputRelativePath = getOutputRelativePath();

    File file = getJarFile();
    if (isExternalDependencyInstruction()) {
      // copy dependent file along with jar file
      final File toFile = MakeUtil.canonicalRelativePath(jarFile, outputRelativePath);
      MakeUtil.getInstance().copyFile(file, toFile, context, null, fileFilter);
      dependencies.addInstruction(this);
    }
    else {
      ZipUtil.addFileToZip(outputStream, file, outputRelativePath, writtenRelativePaths, fileFilter);
    }
  }

  public void makeJar(@NotNull CompileContext context, @NotNull File jarFile, @Nullable FileFilter fileFilter) throws IOException {
    if (jarFile.equals(myJarFile)) return;
    if (myJarFile != null && myJarFile.exists()) {
      // optimization: file already jarred, copy it over
      MakeUtil.getInstance().copyFile(myJarFile, jarFile, context, null, fileFilter);
    }
    else {
      FileUtil.createParentDirs(jarFile);
      Manifest manifest = new Manifest();
      ManifestBuilder.setGlobalAttributes(manifest.getMainAttributes());

      final JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)), manifest);

      try {
        final THashSet<String> writtenPaths = new THashSet<String>();
        writtenPaths.add(JarFile.MANIFEST_NAME);
        boolean ok = ZipUtil.addDirToZipRecursively(jarOutputStream, jarFile, getFile(), "", fileFilter, writtenPaths);
        if (!ok) {
          String dirPath = getFile().getPath();
          MakeUtil.reportRecursiveCopying(context, dirPath, jarFile.getPath(), "",
                                          J2EEBundle.message("message.text.setup.jar.outside.directory.path", dirPath));
        }
      }
      finally {
        jarOutputStream.close();
      }
    }
    myJarFile = jarFile;
  }

  public void clearCaches() {
    myJarFile = null;
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitJarAndCopyBuildInstruction(this);
  }

  @NonNls public String toString() {
    return "JAR and copy: " + getFile() + "->"+getOutputRelativePath();
  }

  public File getJarFile() {
    return myJarFile;
  }

  public void deleteTemporaryJars() {
    FileUtil.asyncDelete(myTempJars);
    myTempJars.clear();
  }
}
