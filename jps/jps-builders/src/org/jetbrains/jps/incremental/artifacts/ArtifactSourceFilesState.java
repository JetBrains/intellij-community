package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.artifacts.instructions.DestinationInfo;
import org.jetbrains.jps.incremental.artifacts.instructions.SourceFileFilter;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.UptoDateFilesSavedEvent;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactSourceFilesState extends CompositeStorageOwner {
  private final ArtifactBuildTarget myTarget;
  private ArtifactSourceToOutputMapping mySrcOutMapping;
  private ArtifactOutputToSourceMapping myOutSrcMapping;
  private final File mySrcOutMappingsFile;
  private File myOutSrcMappingsFile;
  private final ProjectDescriptor myProjectDescriptor;

  public ArtifactSourceFilesState(ArtifactBuildTarget target,
                                  ProjectDescriptor projectDescriptor,
                                  File mappingsDir) {
    int artifactId = projectDescriptor.getTargetsState().getBuildTargetId(target);
    myProjectDescriptor = projectDescriptor;
    myTarget = target;
    mySrcOutMappingsFile = new File(new File(mappingsDir, String.valueOf(artifactId)), "src-out");
    myOutSrcMappingsFile = new File(new File(mappingsDir, String.valueOf(artifactId)), "out-src");
  }

  public ArtifactSourceToOutputMapping getOrCreateSrcOutMapping() throws IOException {
    if (mySrcOutMapping == null) {
      mySrcOutMapping = new ArtifactSourceToOutputMapping(mySrcOutMappingsFile);
    }
    return mySrcOutMapping;
  }

  public ArtifactOutputToSourceMapping getOrCreateOutSrcMapping() throws IOException {
    if (myOutSrcMapping == null) {
      myOutSrcMapping = new ArtifactOutputToSourceMapping(myOutSrcMappingsFile);
    }
    return myOutSrcMapping;
  }

  @Override
  protected Collection<? extends StorageOwner> getChildStorages() {
    return Arrays.asList(mySrcOutMapping, myOutSrcMapping);
  }

  public void ensureFsStateInitialized(final BuildDataManager dataManager, final CompileContext context) throws IOException {
    ArtifactInstructionsBuilder builder = myProjectDescriptor.getArtifactRootsIndex().getInstructionsBuilder(myTarget.getArtifact());
    BuildFSState fsState = myProjectDescriptor.fsState;
    if (context.isProjectRebuild() || context.getScope().isRecompilationForced(myTarget)) {
      markDirtyFiles(builder, dataManager, null, true);
    }
    else if (fsState.markInitialScanPerformed(myTarget)) {
      final Set<String> currentPaths = new HashSet<String>();
      fsState.clearDeletedPaths(myTarget);
      markDirtyFiles(builder, dataManager, currentPaths, false);
      final ArtifactSourceToOutputMapping mapping = getOrCreateSrcOutMapping();
      final Iterator<String> iterator = mapping.getKeysIterator();
      while (iterator.hasNext()) {
        String path = iterator.next();
        File file = new File(path);
        if (!currentPaths.contains(path)) {
          fsState.registerDeleted(myTarget, file, myProjectDescriptor.timestamps.getStorage());
        }
      }
    }
  }

  private void markDirtyFiles(ArtifactInstructionsBuilder builder, BuildDataManager dataManager, @Nullable Set<String> currentPaths,
                              final boolean forceMarkDirty) throws IOException {
    for (Pair<ArtifactRootDescriptor, DestinationInfo> pair : builder.getInstructions()) {
      ArtifactRootDescriptor descriptor = pair.getFirst();
      myProjectDescriptor.fsState.clearRecompile(descriptor);
      final File rootFile = descriptor.getRootFile();
      if (rootFile.exists()) {
        processRecursively(rootFile, descriptor, dataManager, descriptor.getFilter(), currentPaths, forceMarkDirty);
      }
    }
  }

  private void processRecursively(File file, ArtifactRootDescriptor descriptor, BuildDataManager dataManager, SourceFileFilter filter,
                                  @Nullable Set<String> currentPaths, final boolean forceMarkDirty) throws IOException {
    final String filePath = FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(file.getPath()));
    if (!filter.accept(filePath, dataManager)) return;

    if (file.isDirectory()) {
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          processRecursively(child, descriptor, dataManager, filter, currentPaths, forceMarkDirty);
        }
      }
    }
    else {
      if (currentPaths != null) {
        currentPaths.add(filePath);
      }
      if (forceMarkDirty || myProjectDescriptor.timestamps.getStorage().getStamp(file, myTarget) != FileSystemUtil.lastModified(file)) {
        myProjectDescriptor.fsState.markDirty(null, file, descriptor, myProjectDescriptor.timestamps.getStorage());
      }
    }
  }

  public void markUpToDate(CompileContext context) throws IOException {
    BuildFSState fsState = myProjectDescriptor.fsState;
    if (context.isProjectRebuild()) {
      fsState.markInitialScanPerformed(myTarget);
    }
    ArtifactInstructionsBuilder builder = myProjectDescriptor.getArtifactRootsIndex().getInstructionsBuilder(myTarget.getArtifact());
    boolean marked = false;
    for (Pair<ArtifactRootDescriptor, DestinationInfo> pair : builder.getInstructions()) {
      ArtifactRootDescriptor descriptor = pair.getFirst();
      marked |= fsState.markAllUpToDate(descriptor, myProjectDescriptor.timestamps.getStorage(), context.getCompilationStartStamp());
    }
    if (marked) {
      context.processMessage(UptoDateFilesSavedEvent.INSTANCE);
    }
  }
}
