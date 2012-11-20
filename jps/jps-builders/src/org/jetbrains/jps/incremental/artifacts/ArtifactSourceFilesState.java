package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.artifacts.instructions.SourceFileFilter;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.storage.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactSourceFilesState extends CompositeStorageOwner {
  private final ArtifactBuildTarget myTarget;
  private ArtifactOutputToSourceMapping myOutSrcMapping;
  private File myOutSrcMappingsFile;
  private final ProjectDescriptor myProjectDescriptor;

  public ArtifactSourceFilesState(ArtifactBuildTarget target, ProjectDescriptor projectDescriptor) {
    myProjectDescriptor = projectDescriptor;
    myTarget = target;
    myOutSrcMappingsFile = new File(projectDescriptor.dataManager.getDataPaths().getTargetDataRoot(target), "out-src" + File.separator + "data");
  }

  public ArtifactOutputToSourceMapping getOrCreateOutSrcMapping() throws IOException {
    if (myOutSrcMapping == null) {
      myOutSrcMapping = new ArtifactOutputToSourceMapping(myOutSrcMappingsFile);
    }
    return myOutSrcMapping;
  }

  @Override
  protected Collection<? extends StorageOwner> getChildStorages() {
    return Collections.singletonList(myOutSrcMapping);
  }

  public void ensureFsStateInitialized(final BuildDataManager dataManager, final CompileContext context) throws IOException {
    BuildFSState fsState = myProjectDescriptor.fsState;
    BuildTargetConfiguration configuration = myProjectDescriptor.getTargetsState().getTargetConfiguration(myTarget);
    String outputFilePath = myTarget.getArtifact().getOutputFilePath();
    boolean outputDirWasDeleted = !StringUtil.isEmpty(outputFilePath) && !new File(FileUtil.toSystemDependentName(outputFilePath)).exists();

    if (context.isProjectRebuild() || configuration.isTargetDirty() || context.getScope().isRecompilationForced(myTarget) || outputDirWasDeleted) {
      IncProjectBuilder.clearOutputFiles(context, myTarget);
      ((SourceToOutputMappingImpl)myProjectDescriptor.dataManager.getSourceToOutputMap(myTarget)).clean();
      getOrCreateOutSrcMapping().clean();
      markDirtyFiles(null, true, context);
      configuration.save();
    }
    else if (fsState.markInitialScanPerformed(myTarget)) {
      final Set<File> currentPaths = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      fsState.clearDeletedPaths(myTarget);
      markDirtyFiles(currentPaths, false, context);
      final SourceToOutputMapping mapping = dataManager.getSourceToOutputMap(myTarget);
      final Iterator<String> iterator = mapping.getSourcesIterator();
      while (iterator.hasNext()) {
        String path = iterator.next();
        File file = new File(FileUtil.toSystemDependentName(path));
        if (!currentPaths.contains(file)) {
          fsState.registerDeleted(myTarget, file, myProjectDescriptor.timestamps.getStorage());
        }
      }
    }
  }

  private void markDirtyFiles(@Nullable Set<File> currentPaths,
                              final boolean forceMarkDirty, CompileContext context) throws IOException {
    for (ArtifactRootDescriptor descriptor : myProjectDescriptor.getBuildRootIndex().getTargetRoots(myTarget, context)) {
      myProjectDescriptor.fsState.clearRecompile(descriptor);
      final File rootFile = descriptor.getRootFile();
      if (rootFile.exists()) {
        processRecursively(rootFile, descriptor, descriptor.getFilter(), currentPaths, forceMarkDirty);
      }
    }
  }

  private void processRecursively(File file, ArtifactRootDescriptor descriptor, SourceFileFilter filter,
                                  @Nullable Set<File> currentPaths, final boolean forceMarkDirty) throws IOException {
    final String filePath = FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(file.getPath()));
    if (!filter.accept(filePath, myProjectDescriptor)) return;

    if (file.isDirectory()) {
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          processRecursively(child, descriptor, filter, currentPaths, forceMarkDirty);
        }
      }
    }
    else {
      if (currentPaths != null) {
        currentPaths.add(file);
      }
      if (forceMarkDirty || myProjectDescriptor.timestamps.getStorage().getStamp(file, myTarget) != FileSystemUtil.lastModified(file)) {
        myProjectDescriptor.fsState.markDirty(null, file, descriptor, myProjectDescriptor.timestamps.getStorage(), false);
      }
    }
  }
}
