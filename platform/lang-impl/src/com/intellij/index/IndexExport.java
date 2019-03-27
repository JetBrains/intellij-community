// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.SerializedStubTree;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.stubs.StubUpdatingIndexExporter;
import com.intellij.util.Function;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public class IndexExport {
  public static void exportStubs(@NotNull File dir, @NotNull GlobalSearchScope scope, @NotNull Project project) throws Exception {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    ID<Integer, SerializedStubTree> indexId = StubUpdatingIndex.INDEX_ID;
    fileBasedIndex.ensureUpToDate(indexId, project, GlobalSearchScope.projectScope(project));
    List<VirtualFile> files = new ArrayList<>();
    ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    fileBasedIndex.iterateIndexableFiles(fileOrDir -> {
      if (!fileOrDir.isDirectory() && scope.contains(fileOrDir) && !SingleRootFileViewProvider.isTooLargeForIntelligence(fileOrDir)) {
        files.add(fileOrDir);
      }
      return true;
    }, project, indicator);
    if (indicator.isCanceled()) {
      throw new Exception();
    }
    UpdatableIndex<Integer, SerializedStubTree, FileContent> index = ((FileBasedIndexImpl)fileBasedIndex).getIndex(indexId);
    new StubUpdatingIndexExporter().export(dir, index, getFileContentHashFunction(), getInputIdFunction(), files.stream().map(f -> {
      try {
        return new FileContentImpl(f, f.contentsToByteArray());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }));
  }

  @NotNull
  private static Function<FileContent, ByteArraySequence> getFileContentHashFunction() {
    return f -> new ByteArraySequence(ContentHashesSupport.calcContentHash(f.getContent(), f.getFileType()));
  }

  @NotNull
  private static ToIntFunction<FileContent> getInputIdFunction() {
    return f -> FileBasedIndex.getFileId(f.getFile());
  }
}
