// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.util.Function;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;


public abstract class StubTreeLoader {

  public static StubTreeLoader getInstance() {
    return ApplicationManager.getApplication().getService(StubTreeLoader.class);
  }

  public abstract @Nullable ObjectStubTree<?> readOrBuild(@NotNull Project project, @NotNull VirtualFile vFile, @Nullable PsiFile psiFile);

  public abstract @Nullable ObjectStubTree<?> build(@Nullable Project project, @NotNull VirtualFile vFile, @Nullable PsiFile psiFile);

  public abstract @Nullable ObjectStubTree<?> readFromVFile(@NotNull Project project, @NotNull VirtualFile vFile);

  public abstract void rebuildStubTree(VirtualFile virtualFile);

  public abstract boolean canHaveStub(VirtualFile file);

  protected boolean hasPsiInManyProjects(@NotNull VirtualFile virtualFile) {
    return false;
  }

  @ApiStatus.Internal
  public @Nullable IndexingStampInfo getIndexingStampInfo(@NotNull VirtualFile file) {
    return null;
  }

  protected boolean isTooLarge(@NotNull VirtualFile file) {
    return false;
  }

  /**
   * Creates exception with full description.
   * Should be used when requesting indexes is safe, in particular, counting indexes for changed files won't need some already taken lock.
   * <p/>
   * From under lock, which may be needed to compute indexes for changed files,
   * use {@link StubTreeLoader#createCoarseExceptionStubTreeAndIndexDoNotMatch(ObjectStubTree, PsiFileWithStubSupport, Throwable,
   * StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource)}
   * and invoke {@link StubTreeAndIndexUnmatchCoarseException#createCompleteException()} outside the lock.
   */
  @ApiStatus.Internal
  public @NotNull RuntimeException stubTreeAndIndexDoNotMatch(
    @Nullable ObjectStubTree<?> stubTree,
    @NotNull PsiFileWithStubSupport psiFile,
    @Nullable Throwable cause,
    @NotNull StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource source
  ) {
    return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
      return doCreateCoarseExceptionStubTreeAndIndexDoNotMatch(stubTree, psiFile, cause, source).doCreateCompleteException();
    });
  }

  /**
   * @see StubTreeLoader#stubTreeAndIndexDoNotMatch(ObjectStubTree, PsiFileWithStubSupport, Throwable,
   * StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource)
   */
  @ApiStatus.Internal
  public @NotNull StubTreeAndIndexUnmatchCoarseException createCoarseExceptionStubTreeAndIndexDoNotMatch(
    @Nullable ObjectStubTree<?> stubTree,
    @NotNull PsiFileWithStubSupport psiFile,
    @Nullable Throwable cause,
    @NotNull StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource source
  ) {
    return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
      return doCreateCoarseExceptionStubTreeAndIndexDoNotMatch(stubTree, psiFile, cause, source);
    });
  }

  private @NotNull StubTreeAndIndexUnmatchCoarseException doCreateCoarseExceptionStubTreeAndIndexDoNotMatch(
    @Nullable ObjectStubTree<?> stubTree,
    @NotNull PsiFileWithStubSupport psiFile,
    @Nullable Throwable cause,
    @NotNull StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource source
  ) {
    return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
      VirtualFile file = psiFile.getViewProvider().getVirtualFile();
      boolean compiled = psiFile instanceof PsiCompiledElement;
      Document document = compiled ? null : FileDocumentManager.getInstance().getDocument(file);
      IndexingStampInfo indexingStampInfo = getIndexingStampInfo(file);
      boolean upToDate = indexingStampInfo != null && indexingStampInfo.isUpToDate(document, file, psiFile);

      @NonNls String msg = "PSI and index do not match.\nPlease report the problem to JetBrains with the files attached\n";

      if (upToDate) {
        msg += "INDEXED VERSION IS THE CURRENT ONE";
      }

      msg += " file=" + psiFile;
      msg += ", file.class=" + psiFile.getClass();
      msg += ", file.lang=" + psiFile.getLanguage();
      msg += ", modStamp=" + psiFile.getModificationStamp();
      msg += ", psi.length=" + psiFile.getTextLength();

      if (!compiled) {
        String text = psiFile.getText();
        PsiFile fromText =
          PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText(psiFile.getName(), psiFile.getFileType(), text);
        if (fromText.getLanguage().equals(psiFile.getLanguage())) {
          boolean consistent = DebugUtil.psiToString(psiFile, false).equals(DebugUtil.psiToString(fromText, false));
          msg += consistent ? "\n tree consistent" : "\n AST INCONSISTENT, perhaps after incremental reparse; " + fromText;
        }
      }

      if (stubTree != null) {
        msg += "\n stub debugInfo=" + stubTree.getDebugInfo();
      }


      FileViewProvider viewProvider = psiFile.getViewProvider();
      msg += "\n viewProvider=" + viewProvider;
      msg += "\n viewProvider stamp: " + viewProvider.getModificationStamp();

      msg += "; file stamp: " + file.getModificationStamp();
      msg += "; file modCount: " + file.getModificationCount();
      msg += "; file length: " + file.getLength();

      if (document != null) {
        msg += "\n doc saved: " + !FileDocumentManager.getInstance().isDocumentUnsaved(document);
        msg += "; doc stamp: " + document.getModificationStamp();
        msg += "; doc size: " + document.getTextLength();
        msg += "; committed: " + PsiDocumentManager.getInstance(psiFile.getProject()).isCommitted(document);
      }

      msg += "\nindexing info: " + indexingStampInfo;
      msg += "\nref: 20250127";

      ArrayList<Attachment> attachments = createAttachments(stubTree, psiFile, file);
      StubInconsistencyReporter.getInstance().reportStubTreeAndIndexDoNotMatch(psiFile.getProject(), source);
      return new StubTreeAndIndexUnmatchCoarseException(psiFile, file, msg, attachments, upToDate, cause, stubTree);
    });
  }

  /**
   * @see StubTreeLoader#stubTreeAndIndexDoNotMatch(ObjectStubTree, PsiFileWithStubSupport, Throwable,
   * StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource)
   */
  public static class StubTreeAndIndexUnmatchCoarseException extends Exception {
    private final @NotNull Project project;
    private final @NotNull VirtualFile file;

    private final @NotNull String coarseMessage;

    private final @NotNull ArrayList<Attachment> coarseAttachments;

    private final @Nullable Throwable cause;

    private final boolean upToDate;

    private final int stubTreePlainListSize;

    private StubTreeAndIndexUnmatchCoarseException(@NotNull PsiFile psiFile,
                                                   @NotNull VirtualFile file,
                                                   @NotNull String msg,
                                                   @NotNull ArrayList<Attachment> attachments,
                                                   boolean upToDate,
                                                   @Nullable Throwable cause,
                                                   @Nullable ObjectStubTree<?> stubTree) {
      this.project = psiFile.getProject();
      this.file = file;
      this.coarseMessage = msg;
      this.coarseAttachments = attachments;
      this.cause = cause;
      this.upToDate = upToDate;
      this.stubTreePlainListSize = stubTree == null ? -1 : stubTree.getPlainList().size();
    }


    public @NotNull RuntimeException createCompleteException() {
      return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
        return doCreateCompleteException();
      });
    }

    private @NotNull RuntimeException doCreateCompleteException() {
      StubTreeLoader instance = getInstance();
      StubTree stubTreeFromIndex = (StubTree)instance.readFromVFile(project, file);

      String msg = coarseMessage;
      msg += "\nlatestIndexedStub=" + stubTreeFromIndex;
      if (stubTreeFromIndex != null) {
        if (stubTreePlainListSize != -1) {
          msg += "\n   same size=" + (stubTreePlainListSize == stubTreeFromIndex.getPlainList().size());
        }
        msg += "\n   debugInfo=" + stubTreeFromIndex.getDebugInfo();
      }

      if (stubTreeFromIndex != null) {
        coarseAttachments.add(new Attachment("stubTreeFromIndex.txt", ((PsiFileStubImpl<?>)stubTreeFromIndex.getRoot()).printTree()));
      }
      Attachment[] attachments = coarseAttachments.toArray(Attachment.EMPTY_ARRAY);

      // separate methods and separate exception classes for EA to treat these situations differently
      return instance.hasPsiInManyProjects(file) ? handleManyProjectsMismatch(msg, attachments, cause) :
             upToDate ? handleUpToDateMismatch(msg, attachments, cause) :
             new RuntimeExceptionWithAttachments(msg, cause, attachments);
    }
  }

  private static RuntimeExceptionWithAttachments handleUpToDateMismatch(@NotNull String message,
                                                                        Attachment[] attachments,
                                                                        @Nullable Throwable cause) {
    return new UpToDateStubIndexMismatch(message, cause, attachments);
  }

  private static RuntimeExceptionWithAttachments handleManyProjectsMismatch(@NotNull String message,
                                                                            Attachment[] attachments,
                                                                            @Nullable Throwable cause) {
    return new ManyProjectsStubIndexMismatch(message, cause, attachments);
  }

  private static @NotNull ArrayList<Attachment> createAttachments(@Nullable ObjectStubTree<?> stubTree,
                                                                  @NotNull PsiFileWithStubSupport psiFile,
                                                                  @NotNull VirtualFile file) {
    ArrayList<Attachment> attachments = new ArrayList<>();
    attachments.add(new Attachment(file.getPath() + "_file.txt", psiFile instanceof PsiCompiledElement ? "compiled" : psiFile.getText()));
    if (stubTree != null) {
      attachments.add(new Attachment("stubTree.txt", ((PsiFileStubImpl<?>)stubTree.getRoot()).printTree()));
    }
    return attachments;
  }

  public static @NonNls String getFileViewProviderMismatchDiagnostics(@NotNull FileViewProvider provider) {
    Function<PsiFile, String> fileClassName = file -> file.getClass().getSimpleName();
    Function<Pair<LanguageStubDescriptor, PsiFile>, String> stubRootToString =
      pair -> "(" + pair.first.getFileElementType() + ", " + pair.first.getLanguage() + " -> " + fileClassName.fun(pair.second) + ")";
    List<Pair<LanguageStubDescriptor, PsiFile>> roots = StubTreeBuilder.getStubbedRootDescriptors(provider);
    return ", stubBindingRoot = " + fileClassName.fun(provider.getStubBindingRoot()) +
           ", languages = [" + StringUtil.join(provider.getLanguages(), Language::getID, ", ") +
           "], fileTypes = [" + StringUtil.join(provider.getAllFiles(), file -> file.getFileType().getName(), ", ") +
           "], files = [" + StringUtil.join(provider.getAllFiles(), fileClassName, ", ") +
           "], roots = [" + StringUtil.join(roots, stubRootToString, ", ") +
           "], indexingInfo = " + getInstance().getIndexingStampInfo(provider.getVirtualFile()) +
           ", isTooLarge = " + getInstance().isTooLarge(provider.getVirtualFile());
  }
}

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
class UpToDateStubIndexMismatch extends RuntimeExceptionWithAttachments {
  UpToDateStubIndexMismatch(String message, Throwable cause, Attachment... attachments) {
    super(message, cause, attachments);
  }
}

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
class ManyProjectsStubIndexMismatch extends RuntimeExceptionWithAttachments {
  ManyProjectsStubIndexMismatch(String message, Throwable cause, Attachment... attachments) {
    super(message, cause, attachments);
  }
}
