package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.artifacts.LayoutElement;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactSourceFilesState extends CompositeStorageOwner {
  private final Project myProject;
  private final Artifact myArtifact;
  private final int myArtifactId;
  private final ModuleRootsIndex myRootsIndex;
  private final ArtifactSourceTimestampStorage myTimestampStorage;
  private Map<String, IntArrayList> myChangedFiles = new HashMap<String, IntArrayList>();
  private Set<String> myDeletedFiles = new HashSet<String>();
  private ArtifactInstructionsBuilder myInstructionsBuilder;
  private ArtifactSourceToOutputMapping mySrcOutMapping;
  private ArtifactOutputToSourceMapping myOutSrcMapping;
  private final File mySrcOutMappingsFile;
  private File myOutSrcMappingsFile;

  public ArtifactSourceFilesState(Artifact artifact, int artifactId, Project project,
                                  ModuleRootsIndex rootsIndex,
                                  ArtifactSourceTimestampStorage timestampStorage,
                                  File mappingsDir) {
    myProject = project;
    myArtifact = artifact;
    myRootsIndex = rootsIndex;
    myTimestampStorage = timestampStorage;
    myArtifactId = artifactId;
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

  public Map<String, IntArrayList> getChangedFiles() {
    return myChangedFiles;
  }

  public Set<String> getDeletedFiles() {
    return myDeletedFiles;
  }

  public void initState() throws IOException {
    final Set<String> currentPaths = new HashSet<String>();
    myChangedFiles.clear();
    myDeletedFiles.clear();
    getOrCreateInstructions().processRoots(new ArtifactRootProcessor() {
      @Override
      public boolean process(ArtifactSourceRoot root, int rootIndex, Collection<DestinationInfo> destinations) throws IOException {
        final File rootFile = root.getRootFile();
        if (rootFile.exists()) {
          processRecursively(rootFile, rootIndex, root.getFilter(), currentPaths);
        }
        return true;
      }
    });
    final ArtifactSourceToOutputMapping mapping = getOrCreateSrcOutMapping();
    final Iterator<String> iterator = mapping.getKeysIterator();
    while (iterator.hasNext()) {
      String path = iterator.next();
      if (!currentPaths.contains(path)) {
        myDeletedFiles.add(path);
      }
    }
  }

  private void processRecursively(File file, int rootIndex, SourceFileFilter filter, Set<String> currentPaths) throws IOException {
    final String filePath = FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(file.getPath()));
    if (!filter.accept(filePath)) return;

    if (file.isDirectory()) {
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          processRecursively(child, rootIndex, filter, currentPaths);
        }
      }
    }
    else {
      currentPaths.add(filePath);
      final ArtifactSourceTimestampStorage.PerArtifactTimestamp[] state = myTimestampStorage.getState(filePath);
      boolean upToDate = false;
      if (state != null) {
        for (ArtifactSourceTimestampStorage.PerArtifactTimestamp artifactTimestamp : state) {
          if (artifactTimestamp.myArtifactId == myArtifactId && artifactTimestamp.myTimestamp == file.lastModified()) {
            upToDate = true;
            break;
          }
        }
      }
      if (!upToDate) {
        myDeletedFiles.remove(filePath);
        IntArrayList list = myChangedFiles.get(filePath);
        if (list == null) {
          list = new IntArrayList(1);
          myChangedFiles.put(filePath, list);
        }
        list.add(rootIndex);
      }
    }
  }

  public ArtifactInstructionsBuilder getOrCreateInstructions() {
    if (myInstructionsBuilder == null) {
      myInstructionsBuilder = computeInstructions();
    }
    return myInstructionsBuilder;
  }

  private ArtifactInstructionsBuilder computeInstructions() {
    final LayoutElement rootElement = myArtifact.getRootElement();
    ArtifactInstructionsBuilderContext context = new ArtifactInstructionsBuilderContextImpl(myProject, new ProjectPaths(myProject));
    final ArtifactInstructionsBuilderImpl instructionsBuilder = new ArtifactInstructionsBuilderImpl(myRootsIndex, myProject.getIgnoredFilePatterns());
    final CopyToDirectoryInstructionCreator instructionCreator = new CopyToDirectoryInstructionCreator(instructionsBuilder, myArtifact.getOutputPath());
    LayoutElementBuildersRegistry.getInstance().generateInstructions(rootElement, instructionCreator, context);
    return instructionsBuilder;
  }

  public void updateTimestamps() throws IOException {
    for (String filePath : myDeletedFiles) {
      final ArtifactSourceTimestampStorage.PerArtifactTimestamp[] state = myTimestampStorage.getState(filePath);
      if (state == null) continue;
      for (int i = 0, length = state.length; i < length; i++) {
        if (state[i].myArtifactId == myArtifactId) {
          final ArtifactSourceTimestampStorage.PerArtifactTimestamp[] newState = ArrayUtil.remove(state, i);
          myTimestampStorage.update(filePath, newState.length > 0 ? newState : null);
          break;
        }
      }
    }
    for (String filePath : myChangedFiles.keySet()) {
      final ArtifactSourceTimestampStorage.PerArtifactTimestamp[] state = myTimestampStorage.getState(filePath);
      File file = new File(FileUtil.toSystemDependentName(filePath));
      final long timestamp = file.lastModified();
      myTimestampStorage.update(filePath, updateTimestamp(state, timestamp));
    }
  }

  public void markUpToDate() {
    myDeletedFiles.clear();
    myChangedFiles.clear();
  }

  @NotNull
  private ArtifactSourceTimestampStorage.PerArtifactTimestamp[] updateTimestamp(ArtifactSourceTimestampStorage.PerArtifactTimestamp[] oldState, long timestamp) {
    final ArtifactSourceTimestampStorage.PerArtifactTimestamp newItem = new ArtifactSourceTimestampStorage.PerArtifactTimestamp(myArtifactId, timestamp);
    if (oldState == null) {
      return new ArtifactSourceTimestampStorage.PerArtifactTimestamp[]{newItem};
    }
    for (int i = 0, length = oldState.length; i < length; i++) {
      if (oldState[i].myArtifactId == myArtifactId) {
        oldState[i] = newItem;
        return oldState;
      }
    }
    return ArrayUtil.append(oldState, newItem);
  }
}
