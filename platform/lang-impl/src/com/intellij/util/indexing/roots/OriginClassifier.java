// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.util.SmartList;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

final class OriginClassifier {
  private static final Logger LOG = Logger.getInstance(OriginClassifier.class);
  public final EntityStorage entityStorage;
  public final Collection<EntityPointer<?>> myEntityPointers = new HashSet<>();
  public final Collection<Sdk> sdks = new HashSet<>();
  public final Collection<LibraryId> libraryIds = new HashSet<>();
  public final Collection<VirtualFile> filesFromAdditionalLibraryRootsProviders = new SmartList<>();
  public final Collection<VirtualFile> filesFromIndexableSetContributors = new SmartList<>();

  private OriginClassifier(Project project) {
    entityStorage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
  }

  static OriginClassifier classify(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return ReadAction.nonBlocking(() -> {
      OriginClassifier classifier = new OriginClassifier(project);
      WorkspaceFileIndexEx index = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
      for (VirtualFile file : files) {
        classifier.doClassify(index, file);
      }
      return classifier;
    }).executeSynchronously();
  }

  private void doClassify(@NotNull WorkspaceFileIndexEx workspaceFileIndex, @NotNull VirtualFile file) {
    WorkspaceFileInternalInfo fileInfo =
      workspaceFileIndex.getFileInfo(file, true, true, true, true, true);
    if (fileInfo == WorkspaceFileInternalInfo.NonWorkspace.IGNORED || fileInfo == WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED) {
      //excluded files should be ignored by indexableSetContributors
      return;
    }
    WorkspaceFileSet fileSet = fileInfo.findFileSet((data -> true));
    if (fileSet == null) {
      filesFromIndexableSetContributors.add(file);
      return;
    }

    EntityPointer<?> entityPointer = WorkspaceFileSetRecognizer.INSTANCE.getEntityPointer(fileSet);
    if (entityPointer != null) {
      myEntityPointers.add(entityPointer);
      return;
    }

    Sdk sdk = WorkspaceFileSetRecognizer.INSTANCE.getSdk(fileSet);
    if (sdk != null) {
      sdks.add(sdk);
      return;
    }

    if (WorkspaceFileSetRecognizer.INSTANCE.isFromAdditionalLibraryRootsProvider(fileSet)) {
      filesFromAdditionalLibraryRootsProviders.add(file);
      return;
    }

    LibraryId libraryId = WorkspaceFileSetRecognizer.INSTANCE.getLibraryId(fileSet, entityStorage);
    if (libraryId != null) {
      libraryIds.add(libraryId);
      return;
    }

    LOG.error("File " + file + "is unclassifiable with fileSet " + fileSet);
  }
}
