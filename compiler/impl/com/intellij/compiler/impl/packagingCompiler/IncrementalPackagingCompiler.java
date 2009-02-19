/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * @author nik
 */
public class IncrementalPackagingCompiler implements PackagingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.IncrementalPackagingCompiler");
  public static final Key<BuildParticipant[]> AFFECTED_PARTICIPANTS_KEY = Key.create("AFFECTED_PARTICIPANTS");
  @NonNls private static final String INCREMENTAL_PACKAGING_CACHE_ID = "incremental_packaging";
  private static final Key<List<String>> FILES_TO_DELETE_KEY = Key.create("files_to_delete");
  private static final Key<ProcessingItemsBuilderContext> BUILDER_CONTEXT_KEY = Key.create("processing_items_builder");
  @Nullable private PackagingCompilerCache myOutputItemsCache;
  private final Project myProject;

  public IncrementalPackagingCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  private PackagingCompilerCache getOutputItemsCache() {
    if (myOutputItemsCache == null) {
      myOutputItemsCache = new PackagingCompilerCache(CompilerPaths.getCompilerSystemDirectory(myProject).getPath() + File.separator + INCREMENTAL_PACKAGING_CACHE_ID + "_timestamp.dat");
    }
    return myOutputItemsCache;
  }

  public void processOutdatedItem(final CompileContext context, final String url, @Nullable final ValidityState state) {
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    return new ReadAction<ProcessingItem[]>() {
      protected void run(final Result<ProcessingItem[]> result) {
        Module[] affectedModules = context.getCompileScope().getAffectedModules();
        if (affectedModules.length == 0) {
          result.setResult(ProcessingItem.EMPTY_ARRAY);
          return;
        }

        Module[] allModules = ModuleManager.getInstance(myProject).getSortedModules();
        ProcessingItemsBuilderContext builderContext = new ProcessingItemsBuilderContext(context);
        final BuildParticipantProvider<?>[] providers = DeploymentUtilImpl.getBuildParticipantProviders();
        for (BuildParticipantProvider<?> provider : providers) {
          addItemsForProvider(provider, allModules, builderContext);
        }
        context.putUserData(BUILDER_CONTEXT_KEY, builderContext);
        PackagingProcessingItem[] allProcessingItems = builderContext.getProcessingItems(affectedModules);

        if (LOG.isDebugEnabled()) {
          int num = Math.min(100, allProcessingItems.length);
          LOG.debug("All files (" + num + " of " + allProcessingItems.length + "):");
          for (int i = 0; i < num; i++) {
              LOG.debug(allProcessingItems[i].getFile().getPath());
          }
        }

        boolean hasFilesToDelete = collectFilesToDelete(context, builderContext.getProcessingItems());
        if (hasFilesToDelete) {
          MockProcessingItem mockItem = new MockProcessingItem(new LightVirtualFile("239239293"));
          result.setResult(ArrayUtil.append(allProcessingItems, mockItem, ProcessingItem.class));
        }
        else {
          result.setResult(allProcessingItems);
        }
      }
    }.execute().getResultObject();
  }

  private boolean collectFilesToDelete(final CompileContext context, final PackagingProcessingItem[] allProcessingItems) {
    List<String> filesToDelete = new ArrayList<String>();
    Set<String> outputPaths = createPathsHashSet();
    for (PackagingProcessingItem item : allProcessingItems) {
      for (DestinationInfo destinationInfo : item.getDestinations()) {
        String outputPath = getOutputPathWithJarSeparator(destinationInfo);
        outputPaths.add(outputPath);
      }
    }

    final Iterator<String> pathIterator = getOutputItemsCache().getUrlsIterator();
    while (pathIterator.hasNext()) {
      String path = pathIterator.next();
      if (!outputPaths.contains(path)) {
        filesToDelete.add(path);
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Files to delete (" + filesToDelete.size() + "):");
      for (String path : filesToDelete) {
        LOG.debug(path);
      }
    }

    if (filesToDelete.isEmpty()) {
      return false;
    }
    context.putUserData(FILES_TO_DELETE_KEY, filesToDelete);
    return true;
  }

  private static String getOutputPathWithJarSeparator(DestinationInfo destinationInfo) {
    String outputPath = destinationInfo.getOutputFilePath();
    if (destinationInfo instanceof JarDestinationInfo) {
      final String fullOutputPath = destinationInfo.getOutputPath();
      final String fileOutputPath = destinationInfo.getOutputFilePath();
      if (fullOutputPath.startsWith(fileOutputPath)) {
        outputPath += JarFileSystem.JAR_SEPARATOR + DeploymentUtil.trimForwardSlashes(fullOutputPath.substring(fileOutputPath.length()));
      }
    }
    return outputPath;
  }

  private static THashSet<String> createPathsHashSet() {
    return SystemInfo.isFileSystemCaseSensitive ? new THashSet<String>() : new THashSet<String>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  private static <P extends BuildParticipant> void addItemsForProvider(final BuildParticipantProvider<P> provider,
                                                                       final Module[] modulesToCompile,
                                                                       ProcessingItemsBuilderContext builderContext) {
    for (Module module : modulesToCompile) {
      final Collection<P> participants = provider.getParticipants(module);
      for (P participant : participants) {
        addItemsForParticipant(participant, builderContext);
      }
    }
  }

  private static void addItemsForParticipant(final BuildParticipant participant, ProcessingItemsBuilderContext builderContext) {
    participant.buildStarted(builderContext.getCompileContext());
    new ProcessingItemsBuilder(participant, builderContext).build();
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final Set<String> deletedJars = deleteOutdatedFiles(context);

    final List<PackagingProcessingItem> processedItems = new ArrayList<PackagingProcessingItem>();
    final Set<String> writtenPaths = createPathsHashSet();
    Boolean built = new ReadAction<Boolean>() {
      protected void run(final Result<Boolean> result) {
        boolean built = doBuild(context, items, processedItems, writtenPaths, deletedJars);
        result.setResult(built);
      }
    }.execute().getResultObject();
    if (!built) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.updating.caches"));
    refreshOutputFiles(writtenPaths);
    new ReadAction() {
      protected void run(final Result result) {
        processDestinations(processedItems);
      }
    }.execute();
    removeInvalidItems(processedItems);
    updateOutputCache(processedItems);
    return processedItems.toArray(new ProcessingItem[processedItems.size()]);
  }

  private static void removeInvalidItems(List<PackagingProcessingItem> processedItems) {
    final Iterator<PackagingProcessingItem> iterator = processedItems.iterator();
    while (iterator.hasNext()) {
      PackagingProcessingItem item = iterator.next();
      final VirtualFile file = item.getFile();
      file.refresh(false, false);
      if (!file.isValid()) {
        iterator.remove();
      }
    }
  }

  private static boolean doBuild(final CompileContext context, final ProcessingItem[] items, final List<PackagingProcessingItem> processedItems,
                                 final Set<String> writtenPaths, final Set<String> deletedJars) {
    if (LOG.isDebugEnabled()) {
      int num = Math.min(100, items.length);
      LOG.debug("Files to process (" + num + " of " + items.length + "):");
      for (int i = 0; i < num; i++) {
        LOG.debug(items[i].getFile().getPath());
      }
    }
    final DeploymentUtil deploymentUtil = DeploymentUtil.getInstance();
    final FileFilter fileFilter = new IgnoredFileFilter();
    final ProcessingItemsBuilderContext builderContext = context.getUserData(BUILDER_CONTEXT_KEY);
    Set<JarInfo> changedJars = new THashSet<JarInfo>();
    for (String deletedJar : deletedJars) {
      final Collection<JarInfo> infos = builderContext.getJarInfos(deletedJar);
      if (infos != null) {
        changedJars.addAll(infos);
      }
    }
    Set<BuildParticipant> affectedParticipants = new HashSet<BuildParticipant>();

    try {

      for (ProcessingItem item0 : items) {
        if (item0 instanceof MockProcessingItem) continue;
        context.getProgressIndicator().checkCanceled();
        PackagingProcessingItem item = (PackagingProcessingItem)item0;
        final List<DestinationInfo> destinations = item.getDestinations();
        final File fromFile = VfsUtil.virtualToIoFile(item.getFile());
        for (DestinationInfo destination : destinations) {
          if (destination instanceof ExplodedDestinationInfo) {
            final ExplodedDestinationInfo explodedDestination = (ExplodedDestinationInfo)destination;
            File toFile = new File(FileUtil.toSystemDependentName(explodedDestination.getOutputPath()));
            if (fromFile.exists()) {
              deploymentUtil.copyFile(fromFile, toFile, context, writtenPaths, fileFilter);
            }
            affectedParticipants.add(builderContext.getDestinationOwner(explodedDestination));
          }
          else {
            changedJars.add(((JarDestinationInfo)destination).getJarInfo());
          }
        }
        processedItems.add(item);
      }

      createManifestFiles(builderContext.getManifestFiles());

      JarsBuilder builder = new JarsBuilder(changedJars, fileFilter, context);
      final boolean processed = builder.buildJars(writtenPaths);
      if (!processed) {
        return false;
      }

      Set<VirtualFile> recompiledSources = new HashSet<VirtualFile>();
      for (JarInfo info : builder.getJarsToBuild()) {
        for (Pair<String, VirtualFile> pair : info.getPackedFiles()) {
          recompiledSources.add(pair.getSecond());
        }
      }
      for (PackagingProcessingItem processedItem : processedItems) {
        recompiledSources.remove(processedItem.getFile());
      }
      for (VirtualFile source : recompiledSources) {
        PackagingProcessingItem item = builderContext.getItemBySource(source);
        LOG.assertTrue(item != null, source);
        processedItems.add(item);
      }

      for (ExplodedDestinationInfo destination : builder.getJarsDestinations()) {
        affectedParticipants.add(builderContext.getDestinationOwner(destination));
      }

      context.putUserData(AFFECTED_PARTICIPANTS_KEY, affectedParticipants.toArray(new BuildParticipant[affectedParticipants.size()]));
      for (BuildParticipant participant : affectedParticipants) {
        BuildConfiguration buildConfiguration = participant.getBuildConfiguration();
        if (participant.willBuildExploded()) {
          participant.afterExplodedCreated(new File(FileUtil.toSystemDependentName(DeploymentUtilImpl.getOrCreateExplodedDir(participant))),
                                           context);
        }
        String jarPath = buildConfiguration.getJarPath();
        if (buildConfiguration.isJarEnabled() && jarPath != null) {
          participant.afterJarCreated(new File(FileUtil.toSystemDependentName(jarPath)), context);
        }
        participant.buildFinished(context);
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      context.addMessage(CompilerMessageCategory.ERROR, e.getLocalizedMessage(), null, -1, -1);
      return false;
    }
    return true;
  }

  private static void createManifestFiles(final List<ManifestFileInfo> manifestFileInfos) throws IOException {
    for (ManifestFileInfo manifestFileInfo : manifestFileInfos) {
      File outputFile = new File(FileUtil.toSystemDependentName(manifestFileInfo.getOutputPath()));
      Manifest manifest;
      if (outputFile.exists()) {
        FileInputStream stream = new FileInputStream(outputFile);
        try {
          manifest = new Manifest(stream);
        }
        finally {
          stream.close();
        }
      }
      else {
        manifest = new Manifest();
      }

      DeploymentUtilImpl.setManifestAttributes(manifest.getMainAttributes(), manifestFileInfo.getClasspath());

      FileUtil.createParentDirs(outputFile);
      FileOutputStream out = null;
      try {
        out = new FileOutputStream(outputFile);
        manifest.write(out);
      }
      finally {
        if (out != null) {
          out.close();
        }
      }
    }
  }

  private static void processDestinations(final List<PackagingProcessingItem> items) {
    for (PackagingProcessingItem item : items) {
      for (DestinationInfo destination : item.getDestinations()) {
        destination.update();
      }
    }
  }

  private Set<String> deleteOutdatedFiles(final CompileContext context) {
    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.deleting.outdated.files"));
    final List<String> filesToDelete = context.getUserData(FILES_TO_DELETE_KEY);
    final Set<String> deletedJars;
    if (filesToDelete != null) {
      deletedJars = deleteFiles(filesToDelete);
    }
    else {
      deletedJars = Collections.emptySet();
    }
    context.getProgressIndicator().checkCanceled();
    return deletedJars;
  }

  protected Set<String> deleteFiles(final List<String> paths) {
    final THashSet<String> deletedJars = new THashSet<String>();
    LOG.debug("Deleting outdated files...");
    List<File> filesToRefresh = new ArrayList<File>();
    for (String fullPath : paths) {
      int end = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      String filePath = end != -1 ? fullPath.substring(0, end) : fullPath;
      if (end != -1) {
        deletedJars.add(filePath);
      }
      File file = new File(FileUtil.toSystemDependentName(filePath));
      filesToRefresh.add(file);
      boolean deleted = FileUtil.delete(file);
      if (!deleted && LOG.isDebugEnabled()) {
        LOG.debug("Cannot delete file " + file);
      }

      if (deleted) {
        getOutputItemsCache().remove(fullPath);
      }
    }

    CompilerUtil.refreshIOFiles(filesToRefresh);
    return deletedJars;
  }

  private void updateOutputCache(final List<PackagingProcessingItem> processedItems) {
    for (PackagingProcessingItem processedItem : processedItems) {
      for (DestinationInfo destinationInfo : processedItem.getDestinations()) {
        final VirtualFile virtualFile = destinationInfo.getOutputFile();
        if (virtualFile != null) {
          final String path = getOutputPathWithJarSeparator(destinationInfo);
          if (LOG.isDebugEnabled()) {
            LOG.debug("update output cache: file " + path);
          }
          getOutputItemsCache().update(path, virtualFile.getTimeStamp());
        }
      }
    }
    saveCacheIfDirty();
  }

  private static void refreshOutputFiles(Set<String> writtenPaths) {
    final ArrayList<File> filesToRefresh = new ArrayList<File>();
    for (String path : writtenPaths) {
      filesToRefresh.add(new File(path));
    }
    CompilerUtil.refreshIOFiles(filesToRefresh);
  }

  private void saveCacheIfDirty() {
    if (getOutputItemsCache().isDirty()) {
      getOutputItemsCache().save();
    }
  }


  public ValidityState createValidityState(final DataInput is) throws IOException {
    return new PackagingItemValidityState(is);
  }

  public boolean validateConfiguration(final CompileScope scope) {
    return true;
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("incremental.packaging.compiler.description");
  }

  public static class MockProcessingItem implements ProcessingItem {
    private final VirtualFile myFile;

    public MockProcessingItem(final VirtualFile file) {
      myFile = file;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    public ValidityState getValidityState() {
      return null;
    }
  }

}
