// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

class OriginClassifier {
  private static final Logger LOG = Logger.getInstance(OriginClassifier.class);
  public final EntityStorage entityStorage;
  public final Collection<EntityReference<?>> entityReferences = new HashSet<>();
  public final Collection<Sdk> sdks = new HashSet<>();
  public final Collection<LibraryId> libraryIds = new HashSet<>();
  public final Collection<VirtualFile> filesFromAdditionalLibraryRootsProviders = new SmartList<>();
  public final Collection<VirtualFile> filesFromIndexableSetContributors = new SmartList<>();

  private OriginClassifier(Project project) {
    entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
  }

  static OriginClassifier classify(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return ReadAction.nonBlocking(() -> {
      OriginClassifier classifier = new OriginClassifier(project);
      WorkspaceFileIndex index = WorkspaceFileIndex.getInstance(project);
      for (VirtualFile file : files) {
        classifier.doClassify(index, file);
      }
      return classifier;
    }).executeSynchronously();
  }

  private void doClassify(@NotNull WorkspaceFileIndex workspaceFileIndex, @NotNull VirtualFile file) {
    WorkspaceFileSet fileSet = workspaceFileIndex.findFileSet(file, true, true, true, true);
    if (fileSet == null) {
      filesFromIndexableSetContributors.add(file);
      return;
    }

    EntityReference<?> entityReference = WorkspaceFileSetRecognizer.INSTANCE.getEntityReference(fileSet);
    if (entityReference != null) {
      entityReferences.add(entityReference);
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
