/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.packagingCompiler.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.generic.GenericCompilerCacheState;
import com.intellij.openapi.compiler.generic.GenericCompilerInstance;
import com.intellij.openapi.compiler.generic.GenericCompilerProcessingItem;
import com.intellij.openapi.compiler.generic.VirtualFilePersistentState;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactSortingUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactsCompilerInstance extends GenericCompilerInstance<ArtifactBuildTarget, ArtifactCompilerCompileItem,
  String, VirtualFilePersistentState, ArtifactPackagingItemOutputState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.compiler.ArtifactsCompilerInstance");
  public static final Logger FULL_LOG = Logger.getInstance("#com.intellij.full-artifacts-compiler-log");
  private ArtifactsProcessingItemsBuilderContext myBuilderContext;

  public ArtifactsCompilerInstance(CompileContext context) {
    super(context);
  }

  @NotNull
  @Override
  public List<ArtifactBuildTarget> getAllTargets() {
    return getArtifactTargets(false);
  }

  @NotNull
  @Override
  public List<ArtifactBuildTarget> getSelectedTargets() {
    return getArtifactTargets(true);
  }

  private List<ArtifactBuildTarget> getArtifactTargets(final boolean selectedOnly) {
    final List<ArtifactBuildTarget> targets = new ArrayList<ArtifactBuildTarget>();
    new ReadAction() {
      protected void run(final Result result) {
        final Set<Artifact> artifacts;
        if (selectedOnly) {
          artifacts = ArtifactCompileScope.getArtifactsToBuild(getProject(), myContext.getCompileScope(), true);
        }
        else {
          artifacts = new HashSet<Artifact>(Arrays.asList(ArtifactManager.getInstance(getProject()).getArtifacts()));
        }

        Map<String, Artifact> artifactsMap = new HashMap<String, Artifact>();
        for (Artifact artifact : artifacts) {
          artifactsMap.put(artifact.getName(), artifact);
        }
        for (String name : ArtifactSortingUtil.getInstance(getProject()).getArtifactsSortedByInclusion()) {
          Artifact artifact = artifactsMap.get(name);
          if (artifact != null) {
            targets.add(new ArtifactBuildTarget(artifact));
          }
        }

        for (BuildParticipantProvider provider : BuildParticipantProvider.EXTENSION_POINT_NAME.getExtensions()) {
          for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
            final Collection<? extends BuildParticipant> participants = provider.getParticipants(module);
            for (BuildParticipant participant : participants) {
              Artifact artifact = participant.createArtifact(myContext);
              if (artifact != null) {
                LOG.debug("additional artifact to build: " + artifact);
                targets.add(new ArtifactBuildTarget(artifact));
              }
            }
          }
        }
      }
    }.execute();
    return targets;
  }

  @Override
  public void processObsoleteTarget(@NotNull String targetId,
                                    @NotNull List<GenericCompilerCacheState<String, VirtualFilePersistentState, ArtifactPackagingItemOutputState>> obsoleteItems) {
    deleteFiles(obsoleteItems, Collections.<GenericCompilerProcessingItem<ArtifactCompilerCompileItem, VirtualFilePersistentState, ArtifactPackagingItemOutputState>>emptyList());
  }

  @NotNull
  @Override
  public List<ArtifactCompilerCompileItem> getItems(@NotNull ArtifactBuildTarget target) {
    myBuilderContext = new ArtifactsProcessingItemsBuilderContext(myContext);
    final Artifact artifact = target.getArtifact();

    final Map<String, String> selfIncludingArtifacts = new ReadAction<Map<String, String>>() {
      protected void run(final Result<Map<String, String>> result) {
        result.setResult(ArtifactSortingUtil.getInstance(getProject()).getArtifactToSelfIncludingNameMap());
      }
    }.execute().getResultObject();
    final String selfIncludingName = selfIncludingArtifacts.get(artifact.getName());
    if (selfIncludingName != null) {
      String name = selfIncludingName.equals(artifact.getName()) ? "it" : "'" + selfIncludingName + "' artifact";
      myContext.addMessage(CompilerMessageCategory.ERROR, "Cannot build '" + artifact.getName() + "' artifact: " + name + " includes itself in the output layout", null, -1, -1);
      return Collections.emptyList();
    }

    final String outputPath = artifact.getOutputPath();
    if (outputPath == null || outputPath.length() == 0) {
      myContext.addMessage(CompilerMessageCategory.ERROR, "Cannot build '" + artifact.getName() + "' artifact: output path is not specified",
                      null, -1, -1);
      return Collections.emptyList();
    }

    DumbService.getInstance(getProject()).runReadActionInSmartMode(new Runnable() {
      @Override
      public void run() {
        collectItems(artifact, outputPath);
      }
    });
    return new ArrayList<ArtifactCompilerCompileItem>(myBuilderContext.getProcessingItems());
  }

  private void collectItems(@NotNull Artifact artifact, @NotNull String outputPath) {
    final CompositePackagingElement<?> rootElement = artifact.getRootElement();
    final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
    final CopyToDirectoryInstructionCreator instructionCreator = new CopyToDirectoryInstructionCreator(myBuilderContext, outputPath, outputFile);
    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(getProject()).getResolvingContext();
    FULL_LOG.debug("Collecting items for " + artifact.getName());
    rootElement.computeIncrementalCompilerInstructions(instructionCreator, resolvingContext, myBuilderContext, artifact.getArtifactType());
  }

  private boolean doBuild(@NotNull Artifact artifact,
                          final List<GenericCompilerProcessingItem<ArtifactCompilerCompileItem, VirtualFilePersistentState, ArtifactPackagingItemOutputState>> changedItems,
                          final Set<ArtifactCompilerCompileItem> processedItems,
                          final @NotNull Set<String> writtenPaths,
                          final Set<String> deletedJars) {
    FULL_LOG.debug("Building " + artifact.getName());
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();

    final DeploymentUtil deploymentUtil = DeploymentUtil.getInstance();
    final FileFilter fileFilter = new IgnoredFileFilter();
    final Set<JarInfo> changedJars = new THashSet<JarInfo>();
    for (String deletedJar : deletedJars) {
      ContainerUtil.addIfNotNull(myBuilderContext.getJarInfo(deletedJar), changedJars);
    }

    try {
      onBuildStartedOrFinished(artifact, false);
      if (myContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        return false;
      }

      int i = 0;
      for (final GenericCompilerProcessingItem<ArtifactCompilerCompileItem, VirtualFilePersistentState, ArtifactPackagingItemOutputState> item : changedItems) {
        final ArtifactCompilerCompileItem sourceItem = item.getItem();
        myContext.getProgressIndicator().checkCanceled();

        final Ref<IOException> exception = Ref.create(null);
        new ReadAction() {
          protected void run(final Result result) {
            final VirtualFile sourceFile = sourceItem.getFile();
            for (DestinationInfo destination : sourceItem.getDestinations()) {
              if (destination instanceof ExplodedDestinationInfo) {
                final ExplodedDestinationInfo explodedDestination = (ExplodedDestinationInfo)destination;
                File toFile = new File(FileUtil.toSystemDependentName(explodedDestination.getOutputPath()));
                try {
                  if (sourceFile.isInLocalFileSystem()) {
                    final File ioFromFile = VfsUtilCore.virtualToIoFile(sourceFile);
                    if (ioFromFile.exists()) {
                      deploymentUtil.copyFile(ioFromFile, toFile, myContext, writtenPaths, fileFilter);
                    }
                    else {
                      LOG.debug("Cannot copy " + ioFromFile.getAbsolutePath() + ": file doesn't exist");
                    }
                  }
                  else {
                    extractFile(sourceFile, toFile, writtenPaths, fileFilter);
                  }
                }
                catch (IOException e) {
                  exception.set(e);
                  return;
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

        myContext.getProgressIndicator().setFraction(++i * 1.0 / changedItems.size());
        processedItems.add(sourceItem);
        if (testMode) {
          CompilerManagerImpl.addRecompiledPath(FileUtil.toSystemDependentName(sourceItem.getFile().getPath()));
        }
      }

      JarsBuilder builder = new JarsBuilder(changedJars, fileFilter, myContext);
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
      for (VirtualFile source : recompiledSources) {
        ArtifactCompilerCompileItem item = myBuilderContext.getItemBySource(source);
        LOG.assertTrue(item != null, source);
        processedItems.add(item);
        if (testMode) {
          CompilerManagerImpl.addRecompiledPath(FileUtil.toSystemDependentName(item.getFile().getPath()));
        }
      }

      onBuildStartedOrFinished(artifact, true);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      myContext.addMessage(CompilerMessageCategory.ERROR, e.getLocalizedMessage(), null, -1, -1);
      return false;
    }
    return true;
  }

  private void extractFile(VirtualFile sourceFile, File toFile, Set<String> writtenPaths, FileFilter fileFilter) throws IOException {
    if (!writtenPaths.add(toFile.getPath())) {
      return;
    }

    if (!FileUtil.createParentDirs(toFile)) {
      myContext.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory for '" + toFile.getAbsolutePath() + "' file", null, -1, -1);
      return;
    }

    final BufferedInputStream input = ArtifactCompilerUtil.getJarEntryInputStream(sourceFile, myContext);
    if (input == null) return;
    final BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(toFile));
    try {
      FileUtil.copy(input, output);
    }
    finally {
      input.close();
      output.close();
    }
  }

  private void onBuildStartedOrFinished(@NotNull Artifact artifact, final boolean finished) throws Exception {
    for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
      final ArtifactProperties<?> properties = artifact.getProperties(provider);
      if (finished) {
        properties.onBuildFinished(artifact, myContext);
      }
      else {
        properties.onBuildStarted(artifact, myContext);
      }
    }
  }

  private static THashSet<String> createPathsHashSet() {
    return new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
  }

  @Override
  public void processItems(@NotNull final ArtifactBuildTarget target,
                           @NotNull final List<GenericCompilerProcessingItem<ArtifactCompilerCompileItem,VirtualFilePersistentState,ArtifactPackagingItemOutputState>> changedItems,
                           @NotNull List<GenericCompilerCacheState<String, VirtualFilePersistentState, ArtifactPackagingItemOutputState>> obsoleteItems,
                           @NotNull OutputConsumer<ArtifactCompilerCompileItem> consumer) {

    final THashSet<String> deletedJars = deleteFiles(obsoleteItems, changedItems);

    final Set<String> writtenPaths = createPathsHashSet();
    final Ref<Boolean> built = Ref.create(false);
    final Set<ArtifactCompilerCompileItem> processedItems = new HashSet<ArtifactCompilerCompileItem>();
    CompilerUtil.runInContext(myContext, "Copying files", new ThrowableRunnable<RuntimeException>() {
      public void run() throws RuntimeException {
        built.set(doBuild(target.getArtifact(), changedItems, processedItems, writtenPaths, deletedJars));
      }
    });
    if (!built.get()) {
      return;
    }

    myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.updating.caches"));
    myContext.getProgressIndicator().setText2("");
    for (String path : writtenPaths) {
      consumer.addFileToRefresh(new File(path));
    }
    for (ArtifactCompilerCompileItem item : processedItems) {
      consumer.addProcessedItem(item);
    }
    ArtifactsCompiler.addWrittenPaths(myContext, writtenPaths);
    ArtifactsCompiler.addChangedArtifact(myContext, target.getArtifact());
  }

  private THashSet<String> deleteFiles(List<GenericCompilerCacheState<String, VirtualFilePersistentState, ArtifactPackagingItemOutputState>> obsoleteItems,
                                       List<GenericCompilerProcessingItem<ArtifactCompilerCompileItem, VirtualFilePersistentState, ArtifactPackagingItemOutputState>> changedItems) {
    myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.deleting.outdated.files"));

    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    final THashSet<String> deletedJars = new THashSet<String>();
    final THashSet<String> notDeletedJars = new THashSet<String>();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Deleting outdated files...");
    }

    Set<String> pathToDelete = new THashSet<String>();
    for (GenericCompilerProcessingItem<ArtifactCompilerCompileItem, VirtualFilePersistentState, ArtifactPackagingItemOutputState> item : changedItems) {
      final ArtifactPackagingItemOutputState cached = item.getCachedOutputState();
      if (cached != null) {
        for (Pair<String, Long> destination : cached.myDestinations) {
          pathToDelete.add(destination.getFirst());
        }
      }
    }
    for (GenericCompilerProcessingItem<ArtifactCompilerCompileItem, VirtualFilePersistentState, ArtifactPackagingItemOutputState> item : changedItems) {
      for (DestinationInfo destination : item.getItem().getDestinations()) {
        pathToDelete.remove(destination.getOutputPath());
      }
    }
    for (GenericCompilerCacheState<String, VirtualFilePersistentState, ArtifactPackagingItemOutputState> item : obsoleteItems) {
      for (Pair<String, Long> destination : item.getOutputState().myDestinations) {
        pathToDelete.add(destination.getFirst());
      }
    }

    int notDeletedFilesCount = 0;
    List<File> filesToRefresh = new ArrayList<File>();

    for (String fullPath : pathToDelete) {
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
    }
    else {
      if (isJar) {
        notDeletedJars.add(filePath);
      }
      if (notDeletedFilesCount++ > 50) {
        myContext.addMessage(CompilerMessageCategory.WARNING, "Deletion of outdated files stopped because too many files cannot be deleted", null, -1, -1);
        break;
      }
      myContext.addMessage(CompilerMessageCategory.WARNING, "Cannot delete file '" + filePath + "'", null, -1, -1);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Cannot delete file " + file);
      }
    }
    }

    CompilerUtil.refreshIOFiles(filesToRefresh);
    return deletedJars;
  }

}
