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
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.impl.CompilerCacheManager;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.FileProcessingCompilerStateCache;
import com.intellij.compiler.impl.packagingCompiler.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactValidationUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class IncrementalArtifactsCompiler implements PackagingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.compiler.IncrementalArtifactsCompiler");
  private static final Key<Set<String>> WRITTEN_PATHS_KEY = Key.create("artifacts_written_paths");
  private static final Key<List<String>> FILES_TO_DELETE_KEY = Key.create("artifacts_files_to_delete");
  private static final Key<Set<Artifact>> AFFECTED_ARTIFACTS = Key.create("affected_artifacts");
  private static final Key<ArtifactsProcessingItemsBuilderContext> BUILDER_CONTEXT_KEY = Key.create("artifacts_builder_context");
  @Nullable private PackagingCompilerCache myOutputItemsCache;

  @Nullable
  public static IncrementalArtifactsCompiler getInstance(@NotNull Project project) {
    final IncrementalArtifactsCompiler[] compilers = CompilerManager.getInstance(project).getCompilers(IncrementalArtifactsCompiler.class);
    return compilers.length == 1 ? compilers[0] : null;
  }

  private static ArtifactPackagingProcessingItem[] collectItems(ArtifactsProcessingItemsBuilderContext builderContext, final Project project) {
    final CompileContext context = builderContext.getCompileContext();

    final Set<Artifact> artifactsToBuild = ArtifactCompileScope.getArtifactsToBuild(project, context.getCompileScope());
    if (LOG.isDebugEnabled()) {
      LOG.debug("artifacts to build: " + artifactsToBuild);
    }
    List<Artifact> additionalArtifacts = new ArrayList<Artifact>();
    for (BuildParticipantProvider provider : BuildParticipantProvider.EXTENSION_POINT_NAME.getExtensions()) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        final Collection<? extends BuildParticipant> participants = provider.getParticipants(module);
        for (BuildParticipant participant : participants) {
          ContainerUtil.addIfNotNull(participant.createArtifact(context), additionalArtifacts);
        }
      }
    }
    if (LOG.isDebugEnabled() && !additionalArtifacts.isEmpty()) {
      LOG.debug("additional artifacts to build: " + additionalArtifacts);
    }
    artifactsToBuild.addAll(additionalArtifacts);

    final List<Artifact> allArtifacts = new ArrayList<Artifact>(Arrays.asList(ArtifactManager.getInstance(project).getArtifacts()));
    allArtifacts.addAll(additionalArtifacts);
    for (Artifact artifact : allArtifacts) {
      final String outputPath = artifact.getOutputPath();
      if (outputPath != null && outputPath.length() != 0) {
        collectItems(builderContext, artifact, outputPath, project, artifactsToBuild.contains(artifact));
      }
      else if (artifactsToBuild.contains(artifact)) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot build '" + artifact.getName() + "' artifact: output path is not specified",
                        null, -1, -1);
      }
    }
    context.putUserData(AFFECTED_ARTIFACTS, artifactsToBuild);
    return builderContext.getProcessingItems();
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    DumbService.getInstance(context.getProject()).waitForSmartMode();
    return new ReadAction<ProcessingItem[]>() {
      protected void run(final Result<ProcessingItem[]> result) {
        final Project project = context.getProject();
        final Set<Artifact> selfIncludingArtifacts = ArtifactValidationUtil.getInstance(project).getSelfIncludingArtifacts();
        if (!selfIncludingArtifacts.isEmpty()) {
          LOG.info("Self including artifacts: " + selfIncludingArtifacts);
          if (!ArtifactCompileScope.getArtifactsToBuild(project, context.getCompileScope()).isEmpty()) {
            for (Artifact artifact : selfIncludingArtifacts) {
              context.addMessage(CompilerMessageCategory.ERROR, "Artifact '" + artifact.getName() + "' includes itself in the output layout", null, -1, -1);
            }
          }
          result.setResult(ProcessingItem.EMPTY_ARRAY);
          return;
        }

        ArtifactsProcessingItemsBuilderContext builderContext = new ArtifactsProcessingItemsBuilderContext(context);
        context.putUserData(BUILDER_CONTEXT_KEY, builderContext);
        ArtifactPackagingProcessingItem[] allProcessingItems = collectItems(builderContext, project);

        if (LOG.isDebugEnabled()) {
          int num = Math.min(5000, allProcessingItems.length);
          LOG.debug("All files (" + num + " of " + allProcessingItems.length + "):");
          for (int i = 0; i < num; i++) {
            LOG.debug(allProcessingItems[i].getFile().getPath());
          }
        }

        try {
          final FileProcessingCompilerStateCache cache =
              CompilerCacheManager.getInstance(project).getFileProcessingCompilerCache(IncrementalArtifactsCompiler.this);
          for (ArtifactPackagingProcessingItem item : allProcessingItems) {
            item.init(cache);
          }
        }
        catch (IOException e) {
          context.requestRebuildNextTime(e.getMessage());
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
          result.setResult(ProcessingItem.EMPTY_ARRAY);
          LOG.info(e);
          return;
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

  private static void collectItems(@NotNull ArtifactsProcessingItemsBuilderContext builderContext,
                                   @NotNull Artifact artifact,
                                   @NotNull String outputPath,
                                   final Project project, boolean enable) {
    final CompositePackagingElement<?> rootElement = artifact.getRootElement();
    final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
    final CopyToDirectoryInstructionCreator instructionCreator =
      new CopyToDirectoryInstructionCreator(builderContext, outputPath, outputFile);
    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(project).getResolvingContext();
    builderContext.setCollectingEnabledItems(enable);
    rootElement.computeIncrementalCompilerInstructions(instructionCreator, resolvingContext, builderContext, artifact.getArtifactType());
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final Set<String> deletedJars = deleteOutdatedFiles(context);

    final List<ArtifactPackagingProcessingItem> processedItems = new ArrayList<ArtifactPackagingProcessingItem>();
    final Set<String> writtenPaths = createPathsHashSet();
    final Ref<Boolean> built = Ref.create(false);
    CompilerUtil.runInContext(context, "Copying files", new ThrowableRunnable<RuntimeException>() {
      public void run() throws RuntimeException {
        built.set(doBuild(context, items, processedItems, writtenPaths, deletedJars));
      }
    });
    if (!built.get()) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.updating.caches"));
    context.getProgressIndicator().setText2("");
    refreshOutputFiles(writtenPaths);
    new ReadAction() {
      protected void run(final Result result) {
        processDestinations(processedItems);
      }
    }.execute();
    removeInvalidItems(processedItems);
    updateOutputCache(context.getProject(), processedItems);
    context.putUserData(WRITTEN_PATHS_KEY, writtenPaths);
    return processedItems.toArray(new ProcessingItem[processedItems.size()]);
  }

  private static boolean doBuild(final CompileContext context,
                                 final ProcessingItem[] items,
                                 final List<ArtifactPackagingProcessingItem> processedItems,
                                 final Set<String> writtenPaths,
                                 final Set<String> deletedJars) {
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();

    if (LOG.isDebugEnabled()) {
      int num = Math.min(200, items.length);
      LOG.debug("Files to process (" + num + " of " + items.length + "):");
      for (int i = 0; i < num; i++) {
        LOG.debug(items[i].getFile().getPath());
      }
    }
    final DeploymentUtil deploymentUtil = DeploymentUtil.getInstance();
    final FileFilter fileFilter = new IgnoredFileFilter();
    final ArtifactsProcessingItemsBuilderContext builderContext = context.getUserData(BUILDER_CONTEXT_KEY);
    final Set<JarInfo> changedJars = new THashSet<JarInfo>();
    for (String deletedJar : deletedJars) {
      ContainerUtil.addIfNotNull(builderContext.getJarInfo(deletedJar), changedJars);
    }

    try {
      onBuildStartedOrFinished(builderContext, false);
      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return false;
      }

      int i = 0;
      for (final ProcessingItem item0 : items) {
        if (item0 instanceof MockProcessingItem) continue;
        final ArtifactPackagingProcessingItem item = (ArtifactPackagingProcessingItem)item0;
        context.getProgressIndicator().checkCanceled();

        final Ref<IOException> exception = Ref.create(null);
        new ReadAction() {
          protected void run(final Result result) {
            final File fromFile = VfsUtil.virtualToIoFile(item.getFile());
            for (DestinationInfo destination : item.getEnabledDestinations()) {
              if (destination instanceof ExplodedDestinationInfo) {
                final ExplodedDestinationInfo explodedDestination = (ExplodedDestinationInfo)destination;
                File toFile = new File(FileUtil.toSystemDependentName(explodedDestination.getOutputPath()));
                if (fromFile.exists()) {
                  try {
                    deploymentUtil.copyFile(fromFile, toFile, context, writtenPaths, fileFilter);
                  }
                  catch (IOException e) {
                    exception.set(e);
                    return;
                  }
                }
              }
              else {
                changedJars.add(((JarDestinationInfo)destination).getJarInfo());
              }
            }
          }
        }.execute();
        if (exception.get() != null) {
          throw exception.get();
        }

        context.getProgressIndicator().setFraction(++i * 1.0 / items.length);
        processedItems.add(item);
        if (testMode) {
          CompilerManagerImpl.addRecompiledPath(FileUtil.toSystemDependentName(item.getFile().getPath()));
        }
      }

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
      for (ArtifactPackagingProcessingItem processedItem : processedItems) {
        recompiledSources.remove(processedItem.getFile());
      }
      for (VirtualFile source : recompiledSources) {
        ArtifactPackagingProcessingItem item = builderContext.getItemBySource(source);
        LOG.assertTrue(item != null, source);
        processedItems.add(item);
        if (testMode) {
          CompilerManagerImpl.addRecompiledPath(FileUtil.toSystemDependentName(item.getFile().getPath()));
        }
      }

      onBuildStartedOrFinished(builderContext, true);
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

  public static Set<Artifact> getAffectedArtifacts(final CompileContext compileContext) {
    return compileContext.getUserData(AFFECTED_ARTIFACTS);
  }

  @Nullable
  public static Set<String> getWrittenPaths(@NotNull CompileContext context) {
    return context.getUserData(WRITTEN_PATHS_KEY);
  }
  
  @NotNull
  public String getDescription() {
    return "Artifacts Packaging Compiler";
  }

  @NotNull
  private PackagingCompilerCache getOutputItemsCache(final Project project) {
    if (myOutputItemsCache == null) {
      myOutputItemsCache = new PackagingCompilerCache(
          CompilerPaths.getCompilerSystemDirectory(project).getPath() + File.separator + "incremental_artifacts_timestamp.dat");
    }
    return myOutputItemsCache;
  }

  public void processOutdatedItem(final CompileContext context, final String url, @Nullable final ValidityState state) {
  }

  private boolean collectFilesToDelete(final CompileContext context, final ArtifactPackagingProcessingItem[] allProcessingItems) {
    List<String> filesToDelete = new ArrayList<String>();
    Set<String> outputPaths = createPathsHashSet();
    for (ArtifactPackagingProcessingItem item : allProcessingItems) {
      for (Pair<DestinationInfo, Boolean> destinationInfo : item.getDestinations()) {
        String outputPath = getOutputPathWithJarSeparator(destinationInfo.getFirst());
        outputPaths.add(outputPath);
      }
    }

    final Iterator<String> pathIterator = getOutputItemsCache(context.getProject()).getUrlsIterator();
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

  private static void onBuildStartedOrFinished(ArtifactsProcessingItemsBuilderContext context, final boolean finished) throws Exception {
    final CompileContext compileContext = context.getCompileContext();
    final Set<Artifact> artifacts = getAffectedArtifacts(compileContext);
    for (Artifact artifact : artifacts) {
      for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
        final ArtifactProperties<?> properties = artifact.getProperties(provider);
        if (finished) {
          properties.onBuildFinished(artifact, compileContext);
        }
        else {
          properties.onBuildStarted(artifact, compileContext);
        }
      }
    }
  }

  private static THashSet<String> createPathsHashSet() {
    return SystemInfo.isFileSystemCaseSensitive
           ? new THashSet<String>()
           : new THashSet<String>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  private static void removeInvalidItems(List<ArtifactPackagingProcessingItem> processedItems) {
    Set<VirtualFile> files = new THashSet<VirtualFile>(processedItems.size());
    for (ArtifactPackagingProcessingItem item : processedItems) {
      files.add(item.getFile());
    }
    RefreshQueue.getInstance().refresh(false, false, null, VfsUtil.toVirtualFileArray(files));

    final Iterator<ArtifactPackagingProcessingItem> iterator = processedItems.iterator();
    while (iterator.hasNext()) {
      ArtifactPackagingProcessingItem item = iterator.next();
      final VirtualFile file = item.getFile();
      if (!file.isValid()) {
        iterator.remove();
      }
    }
  }

  private static void processDestinations(final List<ArtifactPackagingProcessingItem> items) {
    for (ArtifactPackagingProcessingItem item : items) {
      item.setProcessed();
    }
  }

  private Set<String> deleteOutdatedFiles(final CompileContext context) {
    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.deleting.outdated.files"));
    final List<String> filesToDelete = context.getUserData(FILES_TO_DELETE_KEY);
    final Set<String> deletedJars;
    if (filesToDelete != null) {
      deletedJars = deleteFiles(filesToDelete, context);
    }
    else {
      deletedJars = Collections.emptySet();
    }
    context.getProgressIndicator().checkCanceled();
    return deletedJars;
  }

  private Set<String> deleteFiles(final List<String> paths, CompileContext context) {
    final Set<Artifact> artifactsToBuild = getAffectedArtifacts(context);

    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    final THashSet<String> deletedJars = new THashSet<String>();
    final THashSet<String> notDeletedJars = new THashSet<String>();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Deleting outdated files...");
    }

    int notDeletedFilesCount = 0;
    final Artifact[] allArtifacts = ArtifactManager.getInstance(context.getProject()).getArtifacts();
    List<File> filesToRefresh = new ArrayList<File>();
    for (String fullPath : paths) {
      boolean isUnderOutput = false;
      boolean isInArtifactsToBuild = false;
      for (Artifact artifact : allArtifacts) {
        final String path = artifact.getOutputPath();
        if (!StringUtil.isEmpty(path) && FileUtil.startsWith(fullPath, path)) {
          isUnderOutput = true;
          if (artifactsToBuild.contains(artifact)) {
            isInArtifactsToBuild = true;
            break;
          }
        }
      }
      if (isUnderOutput && !isInArtifactsToBuild) continue;

      int end = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      boolean isJar = end != -1;
      String filePath = isJar ? fullPath.substring(0, end) : fullPath;
      boolean deleted = false;
      if (isJar) {
        if (notDeletedJars.contains(filePath)) {
          continue;
        }
        deleted = deletedJars.contains(filePath);
      }

      File file = new File(FileUtil.toSystemDependentName(filePath));
      if (!deleted) {
        filesToRefresh.add(file);
        deleted = FileUtil.delete(file);
      }

      if (deleted) {
        if (isJar) {
          deletedJars.add(filePath);
        }
        if (testMode) {
          CompilerManagerImpl.addDeletedPath(file.getAbsolutePath());
        }
        getOutputItemsCache(context.getProject()).remove(fullPath);
      }
      else {
        if (isJar) {
          notDeletedJars.add(filePath);
        }
        if (notDeletedFilesCount++ > 50) {
          context.addMessage(CompilerMessageCategory.WARNING, "Deletion of outdated files stopped because too many files cannot be deleted", null, -1, -1);
          break;
        }
        context.addMessage(CompilerMessageCategory.WARNING, "Cannot delete file '" + filePath + "'", null, -1, -1);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Cannot delete file " + file);
        }
      }
    }

    CompilerUtil.refreshIOFiles(filesToRefresh);
    return deletedJars;
  }

  private void updateOutputCache(final Project project, final List<ArtifactPackagingProcessingItem> processedItems) {
    for (ArtifactPackagingProcessingItem processedItem : processedItems) {
      for (DestinationInfo destinationInfo : processedItem.getEnabledDestinations()) {
        final VirtualFile virtualFile = destinationInfo.getOutputFile();
        if (virtualFile != null) {
          final String path = getOutputPathWithJarSeparator(destinationInfo);
          if (LOG.isDebugEnabled()) {
            LOG.debug("update output cache: file " + path);
          }
          getOutputItemsCache(project).update(path, virtualFile.getTimeStamp());
        }
      }
    }
    saveCacheIfDirty(project);
  }

  private static void refreshOutputFiles(Set<String> writtenPaths) {
    final ArrayList<File> filesToRefresh = new ArrayList<File>();
    for (String path : writtenPaths) {
      filesToRefresh.add(new File(path));
    }
    CompilerUtil.refreshIOFiles(filesToRefresh);
  }

  private void saveCacheIfDirty(final Project project) {
    if (getOutputItemsCache(project).isDirty()) {
      getOutputItemsCache(project).save();
    }
  }

  public ValidityState createValidityState(final DataInput is) throws IOException {
    return new ArtifactPackagingItemValidityState(is);
  }

  public boolean validateConfiguration(final CompileScope scope) {
    return true;
  }

  private static class MockProcessingItem implements ProcessingItem {
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
