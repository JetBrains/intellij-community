/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.TimestampCache;
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
import com.intellij.openapi.vfs.LocalFileSystem;
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
  private static final Key<List<ManifestFileInfo>> MANIFEST_FILES_KEY = Key.create("manifest_files");
  private static final Key<Map<ExplodedDestinationInfo, BuildParticipant>> DESTINATION_OWNERS_KEY = Key.create("exploded_destination_owners");
  private static final Key<Map<VirtualFile, PackagingProcessingItem>> ITEMS_BY_SOURCE_KEY = Key.create("items_by_source");
  @Nullable private TimestampCache myOutputItemsCache;
  private final Project myProject;

  public IncrementalPackagingCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  private TimestampCache getOutputItemsCache() {
    if (myOutputItemsCache == null) {
      myOutputItemsCache = new TimestampCache(CompilerPaths.getCompilerSystemDirectory(myProject).getPath(), INCREMENTAL_PACKAGING_CACHE_ID);
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
        context.putUserData(DESTINATION_OWNERS_KEY, builderContext.getDestinationOwners());
        context.putUserData(MANIFEST_FILES_KEY, builderContext.getManifestFiles());
        context.putUserData(ITEMS_BY_SOURCE_KEY, builderContext.getItemsBySourceMap());
        PackagingProcessingItem[] allProcessingItems = builderContext.getProcessingItems(affectedModules);

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
        outputPaths.add(destinationInfo.getOutputFilePath());
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
    new ProcessingItemsBuilder(participant, builderContext).build();
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    deleteOutdatedFiles(context);


    final List<PackagingProcessingItem> processedItems = new ArrayList<PackagingProcessingItem>();
    final Set<String> writtenPaths = createPathsHashSet();
    Boolean built = new ReadAction<Boolean>() {
      protected void run(final Result<Boolean> result) {
        boolean built = doBuild(context, items, processedItems, writtenPaths);
        result.setResult(built);
      }
    }.execute().getResultObject();
    if (!built) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.updating.caches"));
    processOutputFiles(writtenPaths);
    new ReadAction() {
      protected void run(final Result result) {
        processDestinations(processedItems);
      }
    }.execute();
    return processedItems.toArray(new ProcessingItem[processedItems.size()]);
  }

  private static boolean doBuild(final CompileContext context, final ProcessingItem[] items, final List<PackagingProcessingItem> processedItems,
                          final Set<String> writtenPaths) {
    final DeploymentUtil deploymentUtil = DeploymentUtil.getInstance();
    final FileFilter fileFilter = new IgnoredFileFilter();
    Map<ExplodedDestinationInfo, BuildParticipant> destinationOwners = context.getUserData(DESTINATION_OWNERS_KEY);
    Set<BuildParticipant> affectedParticipants = new HashSet<BuildParticipant>();

    try {
      Set<JarInfo> changedJars = new HashSet<JarInfo>();

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
            if (DeploymentUtil.checkFileExists(fromFile, context)) {
              deploymentUtil.copyFile(fromFile, toFile, context, writtenPaths, fileFilter);
            }
            affectedParticipants.add(destinationOwners.get(explodedDestination));
          } else {
            changedJars.add(((JarDestinationInfo)destination).getJarInfo());
          }
        }
        processedItems.add(item);
      }

      createManifestFiles(context.getUserData(MANIFEST_FILES_KEY));

      JarsBuilder builder = new JarsBuilder(changedJars, fileFilter, context);
      final boolean processed = builder.buildJars(writtenPaths);
      if (!processed) {
        return false;
      }

      Map<VirtualFile, PackagingProcessingItem> itemsBySource = context.getUserData(ITEMS_BY_SOURCE_KEY);
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
        PackagingProcessingItem item = itemsBySource.get(source);
        LOG.assertTrue(item != null, source);
        processedItems.add(item);
      }

      for (ExplodedDestinationInfo destination : builder.getJarsDestinations()) {
        affectedParticipants.add(destinationOwners.get(destination));
      }

      context.putUserData(AFFECTED_PARTICIPANTS_KEY, affectedParticipants.toArray(new BuildParticipant[affectedParticipants.size()]));
      for (BuildParticipant participant : affectedParticipants) {
        BuildConfiguration buildConfiguration = participant.getBuildConfiguration();
        if (buildConfiguration.willBuildExploded()) {
          participant.afterExplodedCreated(new File(FileUtil.toSystemDependentName(DeploymentUtilImpl.getOrCreateExplodedDir(participant))), context);
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

  private void deleteOutdatedFiles(final CompileContext context) {
    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.deleting.outdated.files"));
    final List<String> filesToDelete = context.getUserData(FILES_TO_DELETE_KEY);
    if (filesToDelete != null) {
      deleteFiles(filesToDelete);
    }
    context.getProgressIndicator().checkCanceled();
  }

  protected void deleteFiles(final List<String> files) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Deleting outdated files...");
    }
    for (String path : files) {
      File file = new File(FileUtil.toSystemDependentName(path));
      boolean deleted = FileUtil.delete(file);
      if (LOG.isDebugEnabled() && !deleted) {
        LOG.debug("Cannot delete file " + file);
      }

      if (deleted) {
        getOutputItemsCache().remove(path);
      }
    }
  }

  private void processOutputFiles(final Set<String> writtenPaths) {
    final ArrayList<File> filesToRefresh = new ArrayList<File>();
    for (String path : writtenPaths) {
      filesToRefresh.add(new File(path));
    }
    CompilerUtil.refreshIOFiles(filesToRefresh);

    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    for (File file : filesToRefresh) {
      final VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
      if (virtualFile != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("update output cache: file " + virtualFile.getPath());
        }
        getOutputItemsCache().update(virtualFile.getPath(), virtualFile.getTimeStamp());
      }
    }
    saveCacheIfDirty();
  }

  private void saveCacheIfDirty() {
    if (getOutputItemsCache().isDirty()) {
      getOutputItemsCache().save();
    }
  }


  public ValidityState createValidityState(final DataInputStream is) throws IOException {
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
    private VirtualFile myFile;

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
