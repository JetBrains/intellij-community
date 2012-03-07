package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.artifacts.LayoutElement;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.instructions.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class ArtifactSourceFilesState {
  private final Project myProject;
  private final Artifact myArtifact;
  private final int myArtifactId;
  private final ModuleRootsIndex myRootsIndex;
  private final ArtifactSourceTimestampStorage myTimestampStorage;
  private Set<String> myChangedFiles = new HashSet<String>();
  private Set<String> myDeletedFiles = new HashSet<String>();
  private ArtifactInstructionsBuilder myInstructionsBuilder;
  private ArtifactSourceToOutputMapping myMapping;
  private final AtomicBoolean myInitialized = new AtomicBoolean();
  private final File myMappingsFile;

  public ArtifactSourceFilesState(Artifact artifact, int artifactId, Project project,
                                  ModuleRootsIndex rootsIndex,
                                  ArtifactSourceTimestampStorage timestampStorage,
                                  File mappingsDir) {
    myProject = project;
    myArtifact = artifact;
    myRootsIndex = rootsIndex;
    myTimestampStorage = timestampStorage;
    myArtifactId = artifactId;
    myMappingsFile = new File(new File(mappingsDir, String.valueOf(artifactId)), "src-out");
  }

  public ArtifactSourceToOutputMapping getOrCreateMapping() throws IOException {
    if (myMapping == null) {
      myMapping = new ArtifactSourceToOutputMapping(myMappingsFile);
    }
    return myMapping;
  }

  public void clean() {
    if (myMapping != null) {
      myMapping.wipe();
    }
  }

  public Set<String> getChangedFiles() {
    return myChangedFiles;
  }

  public Set<String> getDeletedFiles() {
    return myDeletedFiles;
  }

  public void initState() throws IOException {
    /*
    if (!myInitialized.compareAndSet(false, true)) {
      return;
    }
    */

    final Set<String> currentPaths = new HashSet<String>();
    myChangedFiles.clear();
    myDeletedFiles.clear();
    getOrCreateInstructions().processRoots(new ArtifactRootProcessor() {
      @Override
      public void process(ArtifactSourceRoot root, Collection<DestinationInfo> destinations) throws IOException {
        final File rootFile = root.getRootFile();
        if (rootFile.exists()) {
          processRecursively(rootFile, root.getFilter(), currentPaths);
        }
      }
    });
    final ArtifactSourceToOutputMapping mapping = getOrCreateMapping();
    final Iterator<String> iterator = mapping.getKeysIterator();
    while (iterator.hasNext()) {
      String path = iterator.next();
      if (!currentPaths.contains(path)) {
        myDeletedFiles.add(path);
      }
    }
  }

  private void processRecursively(File file, SourceFileFilter filter, Set<String> currentPaths) throws IOException {
    final String filePath = FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(file.getPath()));
    if (!filter.accept(filePath)) return;

    if (file.isDirectory()) {
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          processRecursively(child, filter, currentPaths);
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
        myChangedFiles.add(filePath);
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
    for (String filePath : myChangedFiles) {
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

  public void close() throws IOException {
    if (myMapping != null) {
      myMapping.close();
    }
  }

  public void flush(boolean memoryCachesOnly) {
    if (myMapping != null) {
      myMapping.flush(memoryCachesOnly);
    }
  }
}
