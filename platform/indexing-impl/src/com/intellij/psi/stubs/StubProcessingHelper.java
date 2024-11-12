// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public final class StubProcessingHelper extends StubProcessingHelperBase {
  private static final boolean SKIP_INDEX_REPAIR_ON_ERROR = Boolean.getBoolean("skip.index.repair");
  static final boolean REPORT_SENSITIVE_DATA_ON_ERROR =
    ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal();

  private final ThreadLocal<Set<VirtualFile>> myFilesHavingProblems = new ThreadLocal<>();

  public @Nullable <Key, Psi extends PsiElement> StubIdList retrieveStubIdList(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                               @NotNull Key key,
                                                                               @NotNull VirtualFile file,
                                                                               @NotNull Project project,
                                                                               boolean failOnMissedKeys) {
    int id = ((VirtualFileWithId)file).getId();
    try {
      Map<Integer, SerializedStubTree> data = StubIndexEx.getStubUpdatingIndex().getIndexedFileData(id);
      if (data.size() != 1) {
        if (failOnMissedKeys) {
          LOG.error("Stub index points to a file (" + getFileTypeInfo(file, project) + ") without indexed stub tree; " +
                    "indexing stamp = " + StubTreeLoader.getInstance().getIndexingStampInfo(file) + ", " +
                    "can have stubs = " + StubUpdatingIndex.canHaveStub(file) + ", " +
                    "actual stub count = " + data.size());
          onInternalError(file);
        }
        return null;
      }
      SerializedStubTree tree = data.values().iterator().next();
      StubIdList stubIdList = tree.restoreIndexedStubs(indexKey, key);
      if (stubIdList == null && failOnMissedKeys) {
        String mainMessage = "Stub ids not found for key in index = " + indexKey.getName() + ", " + getFileTypeInfo(file, project);
        String additionalMessage;
        if (REPORT_SENSITIVE_DATA_ON_ERROR) {
          Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> map = null;
          try {
            tree.restoreIndexedStubs();
            map = tree.getStubIndicesValueMap();
          } catch (Exception ignored) {}
          additionalMessage = ", file " + file.getPath() + ", for key " + key + " existing map " + map;
        }
        else {
          additionalMessage = "";
        }
        LOG.error(mainMessage + additionalMessage);
        onInternalError(file);
      }
      return stubIdList;
    }
    catch (StorageException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void onInternalError(final VirtualFile file) {
    if (SKIP_INDEX_REPAIR_ON_ERROR) return;
    Set<VirtualFile> set = myFilesHavingProblems.get();
    if (set == null) {
      myFilesHavingProblems.set(set = new HashSet<>());
    }
    set.add(file);
  }

  @Nullable
  Set<VirtualFile> takeAccumulatedFilesWithIndexProblems() {
    if (SKIP_INDEX_REPAIR_ON_ERROR) return null;
    Set<VirtualFile> filesWithProblems = myFilesHavingProblems.get();
    if (filesWithProblems != null) myFilesHavingProblems.remove();
    return filesWithProblems;
  }

  @TestOnly
  boolean areAllProblemsProcessedInTheCurrentThread() {
    return ContainerUtil.isEmpty(myFilesHavingProblems.get());
  }
}
