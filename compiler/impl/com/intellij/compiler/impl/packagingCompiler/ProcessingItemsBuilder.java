/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.jar.JarFile;

/**
 * @author nik
*/
public class ProcessingItemsBuilder extends BuildInstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.ProcessingItemsBuilder");
  private final Stack<String> myOutputPaths;
  private Stack<NestedJarInfo> myNestedJars;
  private final BuildConfiguration myBuildConfiguration;
  private final ProcessingItemsBuilderContext myContext;
  private final BuildParticipant myBuildParticipant;
  private final LocalFileSystem myLocalFileSystem;

  public ProcessingItemsBuilder(final BuildParticipant buildParticipant, ProcessingItemsBuilderContext context) {
    myContext = context;
    myBuildParticipant = buildParticipant;
    myBuildConfiguration = myBuildParticipant.getBuildConfiguration();
    myOutputPaths = new Stack<String>();
    myOutputPaths.push("");
    myLocalFileSystem = LocalFileSystem.getInstance();

    final String jarPath = myBuildConfiguration.getJarPath();
    if (myBuildConfiguration.isJarEnabled() && jarPath != null) {
      myNestedJars = new Stack<NestedJarInfo>();
      final DestinationInfo destinationInfo = createExplodedDestination(jarPath);
      myNestedJars.push(myContext.createNestedJarInfo(destinationInfo, myBuildConfiguration, myBuildParticipant.getBuildInstructions(
        myContext.getCompileContext())));
    }
  }

  private ExplodedDestinationInfo createExplodedDestination(final String path) {
    VirtualFile file = myLocalFileSystem.findFileByPath(path);
    return createExplodedDestination(path, file);
  }

  private ExplodedDestinationInfo createExplodedDestination(final String path, final VirtualFile file) {
    ExplodedDestinationInfo destinationInfo = new ExplodedDestinationInfo(path, file);
    myContext.registerDestination(myBuildParticipant, destinationInfo);
    return destinationInfo;
  }

  public void build() {
    buildItems(myBuildParticipant.getBuildInstructions(myContext.getCompileContext()));
  }

  private void buildItems(final BuildRecipe instructions) {
    instructions.visitInstructions(this, false);

    if (myBuildConfiguration.willBuildExploded()) {
      List<String> classpath = DeploymentUtilImpl.getExternalDependenciesClasspath(instructions);
      if (!classpath.isEmpty()) {
        String outputRoot = DeploymentUtilImpl.getOrCreateExplodedDir(myBuildParticipant);
        String fullOutputPath = getCanonicalConcat(outputRoot, myOutputPaths.peek(), JarFile.MANIFEST_NAME);
        myContext.addManifestFile(new ManifestFileInfo(fullOutputPath, classpath));
      }
    }
  }

  private static String getCanonicalConcat(final String... paths) {
    return getCanonicalPath(DeploymentUtil.concatPaths(paths));
  }

  public boolean visitFileCopyInstruction(final FileCopyInstruction instruction) throws Exception {
    final String output = myOutputPaths.peek();
    final VirtualFile sourceFile = myLocalFileSystem.findFileByIoFile(instruction.getFile());
    if (sourceFile == null) return true;

    PackagingFileFilter fileFilter = instruction.getFileFilter();

    String outputRelativePath = instruction.getOutputRelativePath();
    if (myBuildConfiguration.willBuildExploded()) {
      String outputRoot = DeploymentUtilImpl.getOrCreateExplodedDir(myBuildParticipant);
      String fullOutputPath = getCanonicalConcat(outputRoot, output, outputRelativePath);

      checkRecursiveCopying(sourceFile, fullOutputPath);
      addItemsToExplodedRecursively(sourceFile, fullOutputPath, fileFilter);
    }

    if (myNestedJars != null) {
      NestedJarInfo nestedJar = getNestedJar(myNestedJars, instruction.isExternalDependencyInstruction());
      if (nestedJar != null) {
        checkRecursiveCopying(sourceFile, nestedJar.myDestination.getOutputFilePath());
        addItemsToJarRecursively(sourceFile, DeploymentUtil.trimForwardSlashes(trimParentPrefix(outputRelativePath)),
                                 nestedJar.myDestination, nestedJar.myJarInfo, nestedJar.myAddJarContent, fileFilter);
      }
      else {
        String fullOutputPath = getCanonicalConcat(myBuildConfiguration.getJarPath(), outputRelativePath);
        checkRecursiveCopying(sourceFile, fullOutputPath);
        addItemsToExplodedRecursively(sourceFile, fullOutputPath, fileFilter);
      }
    }

    return true;
  }

  @NotNull
  private static String getCanonicalPath(final String fullOutputPath) {
    String path = PathUtil.getCanonicalPath(fullOutputPath);
    if (path == null) {
      LOG.error("invalid path: " + fullOutputPath);
    }
    return path;
  }

  private void checkRecursiveCopying(final @NotNull VirtualFile sourceFile, @NotNull final String outputPath) {
    File fromFile = VfsUtil.virtualToIoFile(sourceFile);
    File toFile = new File(FileUtil.toSystemDependentName(outputPath));
    try {
      if (FileUtil.isAncestor(fromFile, toFile, true)) {
        DeploymentUtil.reportRecursiveCopying(myContext.getCompileContext(), fromFile.getAbsolutePath(), toFile.getAbsolutePath(), "", "");
      }
    }
    catch (IOException e) {
    }
  }

  private static String trimParentPrefix(String outputRelativePath) {
    if (outputRelativePath.startsWith("..")) {
      return outputRelativePath.substring(2);
    }
    return outputRelativePath;
  }

  @Nullable
  private static NestedJarInfo getNestedJar(final @NotNull Stack<NestedJarInfo> nestedJarInfos,
                                                           final boolean externalDependencyInstruction) {
    if (!externalDependencyInstruction) {
      return nestedJarInfos.peek();
    }
    if (nestedJarInfos.size() > 1) {
      return nestedJarInfos.get(nestedJarInfos.size() - 2);
    }
    return null;
  }

  private void addItemsToExplodedRecursively(final VirtualFile sourceFile, final String fullOutputPath, @Nullable PackagingFileFilter fileFilter) {
    VirtualFile outputFile = myLocalFileSystem.findFileByPath(fullOutputPath);
    addItemsToExplodedRecursively(sourceFile, fullOutputPath, outputFile, fileFilter);
  }

  public boolean visitJarAndCopyBuildInstruction(final JarAndCopyBuildInstruction instruction) throws Exception {
    final VirtualFile sourceFile = myLocalFileSystem.findFileByIoFile(instruction.getFile());
    if (sourceFile == null) return true;

    PackagingFileFilter fileFilter = instruction.getFileFilter();

    if (myBuildConfiguration.willBuildExploded()) {
      String outputRoot = DeploymentUtilImpl.getOrCreateExplodedDir(myBuildParticipant);
      String jarPath = getCanonicalConcat(outputRoot, myOutputPaths.peek(), instruction.getOutputRelativePath());

      checkRecursiveCopying(sourceFile, jarPath);
      addItemsToJar(sourceFile, createExplodedDestination(jarPath), fileFilter);
    }

    if (myNestedJars != null) {
      final NestedJarInfo nestedJar = getNestedJar(myNestedJars, instruction.isExternalDependencyInstruction());
      if (nestedJar != null) {
        final String outputRelativePath = trimParentPrefix(instruction.getOutputRelativePath());
        JarDestinationInfo destination = new JarDestinationInfo(outputRelativePath, nestedJar.myJarInfo, nestedJar.myDestination);
        checkRecursiveCopying(sourceFile, destination.getOutputFilePath());
        addItemsToJar(sourceFile, destination, fileFilter);
      }
      else {
        String jarPath = getCanonicalConcat(myBuildConfiguration.getJarPath(), instruction.getOutputRelativePath());
        checkRecursiveCopying(sourceFile, jarPath);
        addItemsToJar(sourceFile, createExplodedDestination(jarPath), fileFilter);
      }
    }

    return true;
  }

  private void addItemsToJar(final VirtualFile sourceFile, final DestinationInfo destination, final @Nullable PackagingFileFilter fileFilter) {
    JarInfo jarInfo = myContext.getCachedJar(sourceFile);
    boolean addToJarInfo = jarInfo == null;
    if (jarInfo == null) {
      jarInfo = new JarInfo();
      myContext.putCachedJar(sourceFile, jarInfo);
    }

    jarInfo.addDestination(destination);
    addItemsToJarRecursively(sourceFile, "", destination, jarInfo, addToJarInfo, fileFilter);
  }

  private void addItemsToJarRecursively(final VirtualFile sourceFile, String pathInJar, DestinationInfo jarDestination,
                                        final JarInfo jarInfo, boolean addToJarContent, @Nullable PackagingFileFilter fileFilter) {
    if (fileFilter != null && !fileFilter.accept(sourceFile, myContext.getCompileContext())) {
      return;
    }

    if (sourceFile.isDirectory()) {
      final VirtualFile[] children = sourceFile.getChildren();
      for (VirtualFile child : children) {
        addItemsToJarRecursively(child, DeploymentUtil.appendToPath(pathInJar, child.getName()), jarDestination, jarInfo, addToJarContent, fileFilter);
      }
    }
    else {
      String fullOutputPath = DeploymentUtil.appendToPath(jarDestination.getOutputPath(), pathInJar);
      if (myContext.checkOutputPath(fullOutputPath, sourceFile)) {
        final PackagingProcessingItem item = myContext.getOrCreateProcessingItem(sourceFile, myBuildParticipant);
        item.addDestination(new JarDestinationInfo(pathInJar, jarInfo, jarDestination));
        if (addToJarContent) {
          jarInfo.addContent(pathInJar, sourceFile);
        }
      }
    }
  }

  private void addItemsToExplodedRecursively(final VirtualFile sourceFile, final String outputPath, final @Nullable VirtualFile outputFile,
                                             @Nullable PackagingFileFilter fileFilter) {
    if (fileFilter != null && !fileFilter.accept(sourceFile, myContext.getCompileContext())) {
      return;
    }

    if (sourceFile.isDirectory()) {
      final VirtualFile[] children = sourceFile.getChildren();
      THashMap<String, VirtualFile> outputChildren = null;
      if (outputFile != null) {
        outputChildren = new THashMap<String, VirtualFile>();
        VirtualFile[] files = outputFile.getChildren();
        if (files != null) {
          for (VirtualFile file : files) {
            outputChildren.put(file.getName(), file);
          }
        }
      }
      for (VirtualFile child : children) {
        final VirtualFile outputChild = outputChildren != null ? outputChildren.get(child.getName()) : null;
        addItemsToExplodedRecursively(child, DeploymentUtil.appendToPath(outputPath, child.getName()), outputChild, fileFilter);
      }
    }
    else if (myContext.checkOutputPath(outputPath, sourceFile)) {
      PackagingProcessingItem item = myContext.getOrCreateProcessingItem(sourceFile, myBuildParticipant);
      item.addDestination(createExplodedDestination(outputPath, outputFile));
    }
  }

  public boolean visitCompoundBuildInstruction(final CompoundBuildInstruction instruction) throws Exception {
    String outputPath = DeploymentUtil.appendToPath(myOutputPaths.peek(), instruction.getOutputRelativePath());
    myOutputPaths.push(outputPath);
    BuildRecipe childInstructions = instruction.getChildInstructions(myContext.getCompileContext());
    NestedJarInfo oldNestedJar = null;
    if (myNestedJars != null) {
      DestinationInfo destinationInfo;
      if (instruction.isExternalDependencyInstruction()) {
        oldNestedJar = myNestedJars.pop();
      }
      if (!myNestedJars.isEmpty()) {
        NestedJarInfo nestedJar = myNestedJars.peek();
        destinationInfo = new JarDestinationInfo(trimParentPrefix(instruction.getOutputRelativePath()), nestedJar.myJarInfo, nestedJar.myDestination);
      }
      else {
        String jarPath = getCanonicalConcat(myBuildConfiguration.getJarPath(), instruction.getOutputRelativePath());
        destinationInfo = createExplodedDestination(jarPath);
      }
      myNestedJars.push(myContext.createNestedJarInfo(destinationInfo, instruction.getBuildProperties(), childInstructions));
    }

    buildItems(childInstructions);

    if (myNestedJars != null) {
      myNestedJars.pop();
      if (oldNestedJar != null) {
        myNestedJars.push(oldNestedJar);
      }
    }
    myOutputPaths.pop();

    return true;
  }

  public static class NestedJarInfo {
    private final JarInfo myJarInfo;
    private final DestinationInfo myDestination;
    private final boolean myAddJarContent;

    public NestedJarInfo(final JarInfo jarInfo, final DestinationInfo destination, final boolean addJarContent) {
      myJarInfo = jarInfo;
      myDestination = destination;
      myAddJarContent = addJarContent;
    }
  }
}
