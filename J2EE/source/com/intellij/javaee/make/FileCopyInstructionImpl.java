package com.intellij.javaee.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.ZipUtil;
import com.intellij.javaee.J2EEBundle;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;

public class FileCopyInstructionImpl extends BuildInstructionBase implements FileCopyInstruction {
  private File myFile;
  private boolean myIsDirectory;
  // for a directory keep the subset of changed files that need to be copied
  private List<FileCopyInstructionImpl> myChangedSet;
  private @Nullable final FileFilter myFileFilter;

  public FileCopyInstructionImpl(File source,
                                 boolean isDirectory,
                                 Module module,
                                 String outputRelativePath,
                                 @Nullable final FileFilter fileFilter) {
    super(outputRelativePath, module);
    myFileFilter = fileFilter;
    setFile(source, isDirectory);
  }

  public void addFilesToExploded(@NotNull CompileContext context,
                                 @NotNull File outputDir,
                                 @Nullable Set<String> writtenPaths,
                                 @Nullable FileFilter fileFilter) throws IOException {
    if (myChangedSet == null) {
      final File to = MakeUtil.canonicalRelativePath(outputDir, getOutputRelativePath());
      // todo check for recursive copying
      if (!MakeUtil.checkFileExists(getFile(), context)) return;
      MakeUtil.getInstance().copyFile(getFile(), to, context, writtenPaths, fileFilter);
    }
    else {
      final ProgressIndicator progressIndicator = context.getProgressIndicator();
      for (FileCopyInstructionImpl singleFileCopyInstruction : myChangedSet) {
        progressIndicator.checkCanceled();
        singleFileCopyInstruction.addFilesToExploded(context, outputDir, writtenPaths, fileFilter);
      }
    }
  }

  public void addFilesToRefresh(File outputDir, Set<File> filesToRefresh) {
    if (myChangedSet == null) {
      final File to = MakeUtil.canonicalRelativePath(outputDir, getOutputRelativePath());
      filesToRefresh.add(to);
    }
    else {
      for (FileCopyInstructionImpl singleFileCopyInstruction : myChangedSet) {
        singleFileCopyInstruction.addFilesToRefresh(outputDir, filesToRefresh);
      }
    }
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitFileCopyInstruction(this);
  }

  public File findFileByRelativePath(String relativePath) {
    if (!relativePath.startsWith(getOutputRelativePath())) return null;
    final String pathFromFile = relativePath.substring(getOutputRelativePath().length());
    if (!myIsDirectory) {
      return "".equals(pathFromFile) ? myFile : null;
    }
    final File file = MakeUtil.canonicalRelativePath(myFile, pathFromFile);

    return file.exists() ? file : null;
  }

  public void addFilesToJar(@NotNull CompileContext context,
                            @NotNull File jarFile,
                            @NotNull JarOutputStream outputStream,
                            BuildRecipe dependencies,
                            @Nullable Set<String> writtenRelativePaths,
                            @Nullable FileFilter fileFilter) throws IOException {
    final String outputRelativePath = getOutputRelativePath();

    File file = getFile();
    if (isExternalDependencyInstruction()) {
      // copy dependent file along with jar file
      final File toFile = MakeUtil.canonicalRelativePath(jarFile, outputRelativePath);
      MakeUtil.getInstance().copyFile(file, toFile, context, null, fileFilter);
      dependencies.addInstruction(this);
    }
    else {
      boolean ok = ZipUtil.addFileOrDirRecursively(outputStream, jarFile, file, outputRelativePath, fileFilter, writtenRelativePaths);
      if (!ok) {
        MakeUtil.reportRecursiveCopying(context, file.getPath(), jarFile.getPath(), "",
                                        J2EEBundle.message("message.text.setup.jar.outside.directory.path", file.getPath()));
      }
    }
  }

  public String toString() {
    if (myChangedSet == null) {
      if (getModule() != null) {
        return J2EEBundle.message("file.copy.instraction.file.from.module.to.file.message.text", getFile(),
                                  ModuleUtil.getModuleNameInReadAction(getModule()), getOutputRelativePath());
      } else {
        return J2EEBundle.message("file.copy.instruction.file.to.file.message.text", getFile(), getOutputRelativePath());
      }
    }
    else {
      StringBuilder builder = new StringBuilder(J2EEBundle.message("file.copy.instruction.message.text", myFile));
      for (FileCopyInstruction fileCopyInstruction : myChangedSet) {
        builder.append(fileCopyInstruction).append(", ");
      }
      return builder.toString();
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileCopyInstruction)) return false;

    final FileCopyInstruction item = (FileCopyInstruction) o;

    if (getFile() != null ? !getFile().equals(item.getFile()) : item.getFile() != null) return false;

    if (getOutputRelativePath() != null) {
      if (!getOutputRelativePath().equals( item.getOutputRelativePath() )) return false;
    } else if ( item.getOutputRelativePath() != null ) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return (getFile() != null ? getFile().hashCode() : 0) +
           (getOutputRelativePath() != null ? getOutputRelativePath().hashCode():0);
  }

  public File getFile() {
    return myFile;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  public void setFile(File file, boolean isDirectory) {
    myFile = file;
    myIsDirectory = isDirectory;
  }

  // incremental compiler integration support
  // instruction implementation should only process the intersection of files it owns and files passed in this method
  public void addFileToChangedSet(FileCopyInstructionImpl item) {
    if (myChangedSet == null) {
      clearChangedSet();
    }
    myChangedSet.add(item);
  }

  public void clearChangedSet() {
    myChangedSet = new ArrayList<FileCopyInstructionImpl>();
  }

  public void addFlattenDirectoryItems(Map<VirtualFile, InstructionProcessingItem> items,
                                       Module targetModule,
                                       final boolean isExplodedEnabled,
                                       final VirtualFile fileInExplodedPath,
                                       final boolean jarEnabled,
                                       final VirtualFile jarFile,
                                       String relativePathToModuleOutputRoot) {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile virtualFile = localFileSystem.findFileByPath(FileUtil.toSystemIndependentName(getFile().getPath()));
    if (virtualFile == null) return;

    final String outputRelativePath = MakeUtil.trimForwardSlashes(MakeUtil.appendToPath(relativePathToModuleOutputRoot, getOutputRelativePath()));
    addFileItemsRecursively(virtualFile, outputRelativePath, fileInExplodedPath, items, targetModule, isExplodedEnabled, jarEnabled, jarFile);
  }

  private void addFileItemsRecursively(@NotNull final VirtualFile virtualFile,
                                       final String outputRelativePath,
                                       final VirtualFile fileInExplodedPath,
                                       final Map<VirtualFile, InstructionProcessingItem> items,
                                       final Module targetModule,
                                       final boolean isExplodedEnabled,
                                       final boolean jarEnabled,
                                       final VirtualFile jarFile) {
    if (myFileFilter != null && !myFileFilter.accept(new File(virtualFile.getPath()))) return;
    if (virtualFile.isDirectory()) {
      VirtualFile[] children = virtualFile.getChildren();
      VirtualFile[] explodedChildren = fileInExplodedPath == null ? null : fileInExplodedPath.getChildren();
      if (explodedChildren == null) explodedChildren = VirtualFile.EMPTY_ARRAY;
      Map<String, VirtualFile> explodedFilesMap = new THashMap<String, VirtualFile>();
      for (VirtualFile file : explodedChildren) {
        explodedFilesMap.put(file.getName(), file);
      }

      for (final VirtualFile child : children) {
        VirtualFile childFileInExploded = fileInExplodedPath == null ? null : explodedFilesMap.get(child.getName());
        addFileItemsRecursively(child,
                                MakeUtil.trimForwardSlashes(MakeUtil.appendToPath(outputRelativePath, child.getName())),
                                childFileInExploded,
                                items,
                                targetModule,
                                isExplodedEnabled,
                                jarEnabled,
                                jarFile
        );
      }
    }
    else {
      InstructionProcessingItem processingItem = items.get(virtualFile);
      if (processingItem == null) {
        processingItem = new InstructionProcessingItem(virtualFile);
        items.put(virtualFile, processingItem);
      }
      boolean targetFileExists = (!isExplodedEnabled || fileInExplodedPath != null) && (!jarEnabled || jarFile != null);
      processingItem.addInstructionInfo(new InstructionProcessingItem.InstructionInfo(this, outputRelativePath, targetModule, targetFileExists));
    }
  }

  public void clearCaches() {
  }
}
