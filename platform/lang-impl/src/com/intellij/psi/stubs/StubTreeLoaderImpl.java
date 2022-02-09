// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.NoAccessDuringPsiEvents;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


final class StubTreeLoaderImpl extends StubTreeLoader {
  private static final Logger LOG = Logger.getInstance(StubTreeLoaderImpl.class);
  // todo remove once we don't need this for stub-ast mismatch debug info
  private static final FileAttribute INDEXED_STAMP = new FileAttribute("stubIndexStamp", 3, true);
  private static final byte IS_BINARY_MASK = 1;
  private static final byte BYTE_AND_CHAR_LENGTHS_ARE_THE_SAME_MASK = 1 << 1;
  private static volatile boolean ourStubReloadingProhibited;

  @Override
  @Nullable
  public ObjectStubTree<?> readOrBuild(@NotNull Project project, @NotNull VirtualFile vFile, @Nullable PsiFile psiFile) {
    ObjectStubTree<?> fromIndices = readFromVFile(project, vFile);
    if (fromIndices != null) {
      return fromIndices;
    }

    return build(project, vFile, psiFile);
  }

  @Override
  public @Nullable ObjectStubTree<?> build(@Nullable Project project,
                                           @NotNull VirtualFile vFile,
                                           @Nullable PsiFile psiFile) {
    try {
      byte[] content = vFile.contentsToByteArray();
      return vFile.computeWithPreloadedContentHint(content, () -> {
        FileContentImpl fc = (FileContentImpl)FileContentImpl.createByContent(vFile, content);
        if (project != null) {
          LOG.assertTrue(!project.isDefault());
          fc.setProject(project);
        }
        if (psiFile != null && !vFile.getFileType().isBinary()) {
          fc.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, psiFile.getViewProvider().getContents());
          // but don't reuse psiFile itself to avoid loading its contents. If we load AST, the stub will be thrown out anyway.
        }

        Stub element = RecursionManager.doPreventingRecursion(vFile, false, () -> StubTreeBuilder.buildStubTree(fc));
        ObjectStubTree<?> tree;
        if (element instanceof PsiFileStub) {
          tree = new StubTree((PsiFileStub<?>)element);
        }
        else {
          tree = element instanceof ObjectStubBase ? new ObjectStubTree<>((ObjectStubBase<?>)element, true) : null;
        }
        if (tree != null) {
          tree.setDebugInfo("created from file content");
          return tree;
        }
        return null;
      });
    }
    catch (IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      else {
        // content can be not cached yet, and the file can be deleted on disk already, without refresh
        LOG.info("Can't load file content for stub building: " + e.getMessage());
      }
    }

    return null;
  }

  @Override
  @Nullable
  public ObjectStubTree<?> readFromVFile(@NotNull Project project, final @NotNull VirtualFile vFile) {
    if ((DumbService.getInstance(project).isDumb() &&
         (!FileBasedIndex.isIndexAccessDuringDumbModeEnabled() ||
          FileBasedIndex.getInstance().getCurrentDumbModeAccessType() != DumbModeAccessType.RELIABLE_DATA_ONLY)) ||
        NoAccessDuringPsiEvents.isInsideEventProcessing()) {
      return null;
    }

    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    if (!(fileBasedIndex instanceof FileBasedIndexImpl)) {
      return null;
    }
    boolean wasIndexedAlready = ((FileBasedIndexImpl)fileBasedIndex).isFileUpToDate(vFile);

    Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
    boolean saved = document == null || !FileDocumentManager.getInstance().isDocumentUnsaved(document);

    final SerializedStubTree stubTree = FileBasedIndex.getInstance().getSingleEntryIndexData(StubUpdatingIndex.INDEX_ID, vFile, project);

    if (stubTree != null) {
      if (vFile instanceof VirtualFileWithId && !checkLengthMatch(project, vFile, wasIndexedAlready, document, saved)) {
        return null;
      }

      Stub stub;
      try {
        stub = stubTree.getStub();
      }
      catch (SerializerNotFoundException e) {
        return processError(vFile, "No stub serializer: " + vFile.getPresentableUrl() + ": " + e.getMessage(), e);
      }
      if (stub == SerializedStubTree.NO_STUB) {
        return null;
      }
      ObjectStubTree<?> tree =
        stub instanceof PsiFileStub ? new StubTree((PsiFileStub<?>)stub) : new ObjectStubTree<>((ObjectStubBase<?>)stub, true);
      tree.setDebugInfo("created from index");
      checkDeserializationCreatesNoPsi(tree);
      return tree;
    }

    return null;
  }

  private boolean checkLengthMatch(Project project,
                                   VirtualFile vFile,
                                   boolean wasIndexedAlready,
                                   Document document,
                                   boolean saved) {
    PsiFile cachedPsi = PsiManagerEx.getInstanceEx(project).getFileManager().getCachedPsiFile(vFile);
    IndexingStampInfo indexingStampInfo = getIndexingStampInfo(vFile);
    if (indexingStampInfo != null &&
        !indexingStampInfo.contentLengthMatches(vFile.getLength(), getCurrentTextContentLength(project, vFile, document, cachedPsi))) {
      diagnoseLengthMismatch(vFile, wasIndexedAlready, document, saved, cachedPsi);
      return false;
    }
    return true;
  }

  private void diagnoseLengthMismatch(@NotNull VirtualFile vFile,
                                      boolean wasIndexedAlready,
                                      @Nullable Document document,
                                      boolean saved,
                                      @Nullable PsiFile cachedPsi) {
    String message = "Outdated stub in index: " + vFile + " " + getIndexingStampInfo(vFile) +
                     ", doc=" + document +
                     ", docSaved=" + saved +
                     ", wasIndexedAlready=" + wasIndexedAlready +
                     ", queried at " + vFile.getTimeStamp();
    message += "\ndoc length=" + (document == null ? -1 : document.getTextLength()) +
               "\nfile length=" + vFile.getLength();
    if (cachedPsi != null) {
      message += "\ncached PSI " + cachedPsi.getClass();
      if (cachedPsi instanceof PsiFileImpl && ((PsiFileImpl)cachedPsi).isContentsLoaded()) {
        message += "\nPSI length=" + cachedPsi.getTextLength();
      }
      List<Project> projects = ContainerUtil.findAll(ProjectManager.getInstance().getOpenProjects(),
                                                     p -> PsiManagerEx.getInstanceEx(p).getFileManager().findCachedViewProvider(vFile) !=
                                                          null);
      message += "\nprojects with file: " + (LOG.isDebugEnabled() ? projects.toString() : projects.size());
    }

    Path nioPath = vFile.getFileSystem().getNioPath(vFile);
    if (nioPath != null) {
      message += getPhysicalFileReport(nioPath);
    }

    processError(vFile, message, new Exception());
  }

  private static String getPhysicalFileReport(@NotNull Path file) {
    String message = "\nphysical file " + (Files.exists(file) ? "exists" : "doesn't exist") + "; length = ";
    try {
      message += Files.size(file);
    }
    catch (IOException e) {
      message += e.getMessage();
    }
    return message;
  }

  private static void checkDeserializationCreatesNoPsi(ObjectStubTree<?> tree) {
    if (ourStubReloadingProhibited || !(tree instanceof StubTree)) return;

    for (PsiFileStub<?> root : ((PsiFileStubImpl<?>)tree.getRoot()).getStubRoots()) {
      if (root instanceof StubBase) {
        StubList stubList = ((StubBase<?>)root).myStubList;
        for (int i = 0; i < stubList.size(); i++) {
          StubBase<?> each = stubList.getCachedStub(i);
          PsiElement cachedPsi = each == null ? null : each.getCachedPsi();
          if (cachedPsi != null) {
            ourStubReloadingProhibited = true;
            throw new AssertionError("Stub deserialization shouldn't create PSI: " + cachedPsi + "; " + each);
          }
        }
      }
    }
  }

  private static int getCurrentTextContentLength(Project project, VirtualFile vFile, Document document, PsiFile psiFile) {
    if (vFile.getFileType().isBinary()) {
      return -1;
    }
    if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded()) {
      return psiFile.getTextLength();
    }

    if (document != null) {
      return PsiDocumentManager.getInstance(project).getLastCommittedText(document).length();
    }
    return -1;
  }

  private static ObjectStubTree<?> processError(final VirtualFile vFile, String message, @Nullable Exception e) {
    LOG.error(message, e);

    AppUIExecutor.onWriteThread(ModalityState.NON_MODAL).later().submit(() -> {
      final Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
      if (doc != null) {
        FileDocumentManager.getInstance().saveDocument(doc);
      }

      // avoid deadlock by requesting reindex later.
      // processError may be invoked under stub index's read action and requestReindex in EDT starts dumb mode in writeAction (IDEA-197296)
      FileBasedIndex.getInstance().requestReindex(vFile);
    });

    return null;
  }

  @Override
  public void rebuildStubTree(VirtualFile virtualFile) {
    FileBasedIndex.getInstance().requestReindex(virtualFile);
  }

  @Override
  public boolean canHaveStub(VirtualFile file) {
    return StubUpdatingIndex.canHaveStub(file);
  }

  @Override
  protected boolean hasPsiInManyProjects(@NotNull final VirtualFile virtualFile) {
    int count = 0;
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (PsiManagerEx.getInstanceEx(project).getFileManager().findCachedViewProvider(virtualFile) != null) {
        count++;
      }
    }
    return count > 1;
  }

  @Override
  protected IndexingStampInfo getIndexingStampInfo(@NotNull VirtualFile file) {
    return readSavedIndexingStampInfo(file);
  }

  @Override
  protected boolean isTooLarge(@NotNull VirtualFile file) {
    return ((FileBasedIndexImpl)FileBasedIndex.getInstance()).isTooLarge(file);
  }

  static void saveIndexingStampInfo(@Nullable IndexingStampInfo indexingStampInfo, int fileId) {
    try (DataOutputStream stream = FSRecords.writeAttribute(fileId, INDEXED_STAMP)) {
      if (indexingStampInfo == null) return;
      DataInputOutputUtil.writeTIME(stream, indexingStampInfo.indexingFileStamp);
      DataInputOutputUtil.writeLONG(stream, indexingStampInfo.indexingByteLength);

      boolean lengthsAreTheSame = indexingStampInfo.indexingCharLength == indexingStampInfo.indexingByteLength;
      byte flags = 0;
      flags = BitUtil.set(flags, IS_BINARY_MASK, indexingStampInfo.isBinary);
      flags = BitUtil.set(flags, BYTE_AND_CHAR_LENGTHS_ARE_THE_SAME_MASK, lengthsAreTheSame);
      stream.writeByte(flags);

      if (!lengthsAreTheSame && !indexingStampInfo.isBinary) {
        DataInputOutputUtil.writeINT(stream, indexingStampInfo.indexingCharLength);
      }
    }
    catch (IOException e) {
      StubUpdatingIndex.LOG.error(e);
    }
  }

  @Nullable
  static IndexingStampInfo readSavedIndexingStampInfo(@NotNull VirtualFile file) {
    try (DataInputStream stream = INDEXED_STAMP.readAttribute(file)) {
      if (stream == null || stream.available() <= 0) {
        return null;
      }
      long stamp = DataInputOutputUtil.readTIME(stream);
      long byteLength = DataInputOutputUtil.readLONG(stream);

      byte flags = stream.readByte();
      boolean isBinary = BitUtil.isSet(flags, IS_BINARY_MASK);
      boolean readOnlyOneLength = BitUtil.isSet(flags, BYTE_AND_CHAR_LENGTHS_ARE_THE_SAME_MASK);

      int charLength;
      if (isBinary) {
        charLength = -1;
      }
      else if (readOnlyOneLength) {
        charLength = (int)byteLength;
      }
      else {
        charLength = DataInputOutputUtil.readINT(stream);
      }
      return new IndexingStampInfo(stamp, byteLength, charLength, isBinary);
    }
    catch (IOException e) {
      StubUpdatingIndex.LOG.error(e);
      return null;
    }
  }
}
