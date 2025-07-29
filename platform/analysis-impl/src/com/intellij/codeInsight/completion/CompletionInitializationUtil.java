// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.diagnostic.PluginException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.PsiConsistencyAssertions;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.reference.SoftReference.dereference;


@ApiStatus.Internal
public final class CompletionInitializationUtil {
  private static final Logger LOG = Logger.getInstance(CompletionInitializationUtil.class);

  public static CompletionInitializationContextImpl createCompletionInitializationContext(@NotNull Project project,
                                                                                          @NotNull Editor editor,
                                                                                          @NotNull Caret caret,
                                                                                          int invocationCount,
                                                                                          CompletionType completionType) {
    return WriteAction.compute(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      CompletionAssertions.checkEditorValid(editor);

      final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      assert psiFile != null : "no PSI file: " + FileDocumentManager.getInstance().getFile(editor.getDocument());
      psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);
      PsiConsistencyAssertions.assertNoFileTextMismatch(psiFile, editor.getDocument(), null);

      return runContributorsBeforeCompletion(editor, psiFile, invocationCount, caret, completionType);
    });
  }

  @ApiStatus.Internal
  public static CompletionInitializationContextImpl runContributorsBeforeCompletion(Editor editor,
                                                                                    PsiFile psiFile,
                                                                                    int invocationCount,
                                                                                    @NotNull Caret caret,
                                                                                    CompletionType completionType) {
    final Ref<CompletionContributor> current = Ref.create(null);
    CompletionInitializationContextImpl context =
      new CompletionInitializationContextImpl(editor, caret, psiFile, completionType, invocationCount) {
        CompletionContributor dummyIdentifierChanger;

        @Override
        public void setDummyIdentifier(@NotNull String dummyIdentifier) {
          super.setDummyIdentifier(dummyIdentifier);

          if (dummyIdentifierChanger != null) {
            LOG.error("Changing the dummy identifier twice, already changed by " + dummyIdentifierChanger);
          }
          dummyIdentifierChanger = current.get();
        }
      };
    Project project = psiFile.getProject();
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      for (final CompletionContributor contributor : CompletionContributor.forLanguageHonorDumbness(context.getPositionLanguage(), project)) {
        current.set(contributor);
        contributor.beforeCompletion(context);
        CompletionAssertions.checkEditorValid(editor);
        assert !PsiDocumentManager.getInstance(project).isUncommited(editor.getDocument()) : "Contributor " +
                                                                                             contributor +
                                                                                             " left the document uncommitted";
      }
    });
    return context;
  }

  public static @NotNull CompletionParameters createCompletionParameters(CompletionInitializationContext initContext,
                                                                         CompletionProcess indicator, OffsetsInFile finalOffsets) {
    int offset = finalOffsets.getOffsets().getOffset(CompletionInitializationContext.START_OFFSET);
    PsiFile fileCopy = finalOffsets.getFile();
    PsiFile originalFile = fileCopy.getOriginalFile();
    PsiElement insertedElement = findCompletionPositionLeaf(finalOffsets, offset, originalFile);
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, new CompletionContext(fileCopy, finalOffsets.getOffsets()));
    return new CompletionParameters(insertedElement, originalFile, initContext.getCompletionType(), offset,
                                    initContext.getInvocationCount(),
                                    initContext.getEditor(), indicator);
  }

  public static Supplier<? extends OffsetsInFile> insertDummyIdentifier(CompletionInitializationContext initContext, CompletionProcessEx indicator) {
    OffsetsInFile topLevelOffsets = indicator.getHostOffsets();
    final Consumer<Supplier<Disposable>> registerDisposable = supplier -> indicator.registerChildDisposable(supplier);

    return doInsertDummyIdentifier(initContext, topLevelOffsets, false, registerDisposable);
  }

  private static Supplier<? extends OffsetsInFile> doInsertDummyIdentifier(CompletionInitializationContext initContext,
                                                                 OffsetsInFile topLevelOffsets,
                                                                 boolean noWriteLock, Consumer<? super Supplier<Disposable>> registerDisposable) {

    CompletionAssertions.checkEditorValid(initContext.getEditor());
    if (initContext.getDummyIdentifier().isEmpty()) {
      return () -> topLevelOffsets;
    }

    Editor editor = initContext.getEditor();
    Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    OffsetMap hostMap = topLevelOffsets.getOffsets();

    PsiFile hostCopy = obtainFileCopy(topLevelOffsets.getFile(), noWriteLock);
    Document copyDocument = Objects.requireNonNull(hostCopy.getViewProvider().getDocument());

    String dummyIdentifier = initContext.getDummyIdentifier();
    int startOffset = hostMap.getOffset(CompletionInitializationContext.START_OFFSET);
    int endOffset = hostMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);

    Supplier<OffsetsInFile> apply = topLevelOffsets.replaceInCopy(hostCopy, startOffset, endOffset, dummyIdentifier);


    // despite being non-physical, the copy file should only be modified in a write action,
    // because it's reused in multiple completions and it can also escapes uncontrollably into other threads (e.g. quick doc)

    //kskrygan: this check is non-relevant for CWM (quick doc and other features work separately)
    //and we are trying to avoid useless write locks during completion
    return skipWriteLockIfNeeded(noWriteLock, () -> {
      registerDisposable.accept((Supplier<Disposable>)() -> new OffsetTranslator(hostEditor.getDocument(), initContext.getFile(), copyDocument, startOffset, endOffset, dummyIdentifier));
      OffsetsInFile copyOffsets = apply.get();

      registerDisposable.accept((Supplier<Disposable>)() -> copyOffsets.getOffsets());

      return copyOffsets;
    });
  }

  private static Supplier<? extends OffsetsInFile> skipWriteLockIfNeeded(boolean skipWriteLock, Supplier<? extends OffsetsInFile> toWrap) {
    if (skipWriteLock) {
      return toWrap;
    }
    else {
      return () -> WriteAction.compute(() -> toWrap.get());
    }
  }

  public static OffsetsInFile toInjectedIfAny(PsiFile originalFile, OffsetsInFile hostCopyOffsets) {
    CompletionAssertions.assertHostInfo(hostCopyOffsets.getFile(), hostCopyOffsets.getOffsets());

    int hostStartOffset = hostCopyOffsets.getOffsets().getOffset(CompletionInitializationContext.START_OFFSET);
    OffsetsInFile translatedOffsets = hostCopyOffsets.toInjectedIfAny(hostStartOffset);
    if (translatedOffsets != hostCopyOffsets) {
      PsiFile injected = translatedOffsets.getFile();
      if (originalFile != injected &&
          injected instanceof PsiFileImpl psiFile &&
          InjectedLanguageManager.getInstance(originalFile.getProject()).isInjectedFragment(originalFile)) {
        setOriginalFile(psiFile, originalFile);
      }
      VirtualFile virtualFile = injected.getVirtualFile();
      DocumentWindow documentWindow = null;
      if (virtualFile instanceof VirtualFileWindow window) {
        documentWindow = window.getDocumentWindow();
      }
      CompletionAssertions.assertInjectedOffsets(hostStartOffset, injected, documentWindow);

      if (injected.getTextRange().contains(translatedOffsets.getOffsets().getOffset(CompletionInitializationContext.START_OFFSET))) {
        return translatedOffsets;
      }
    }

    return hostCopyOffsets;
  }

  private static void setOriginalFile(PsiFileImpl copy, PsiFile origin) {
    checkInjectionConsistency(copy);
    PsiFile currentOrigin = copy.getOriginalFile();
    if (currentOrigin == copy) {
      copy.setOriginalFile(origin);
    } else if (currentOrigin != origin) {

      PsiUtilCore.ensureValid(currentOrigin);
      checkInjectionConsistency(origin);
      checkInjectionConsistency(currentOrigin);

      PsiElement host = Objects.requireNonNull(currentOrigin.getContext());
      recoverFromBrokenInjection(host.getContainingFile());
      throw new AssertionError(
        currentOrigin + " != " + origin + "\n" +
        currentOrigin.getViewProvider() + " != " + origin.getViewProvider() + "\n" +
        "host of " + host.getClass());
    }
  }

  private static void recoverFromBrokenInjection(PsiFile hostFile) {
    ApplicationManager.getApplication().invokeLater(() -> FileContentUtilCore.reparseFiles(hostFile.getViewProvider().getVirtualFile()));
  }

  private static void checkInjectionConsistency(PsiFile injectedFile) {
    PsiElement host = injectedFile.getContext();
    if (host instanceof PsiLanguageInjectionHost injectionHost) {
      DocumentWindow document = (DocumentWindow)injectedFile.getViewProvider().getDocument();
      assert document != null;
      TextRange hostRange = host.getTextRange();
      LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = injectionHost.createLiteralTextEscaper();
      TextRange relevantRange = escaper.getRelevantTextRange().shiftRight(hostRange.getStartOffset());
      for (Segment range : document.getHostRanges()) {
        if (hostRange.contains(range) && !relevantRange.contains(range)) {
          String message = "Injection host of " + host.getClass() +
                           " with range " + hostRange +
                           " contains injection at " + range +
                           ", which contradicts literalTextEscaper that only allows injection at " + relevantRange;
          PsiFile hostFile = Objects.requireNonNull(host).getContainingFile();
          recoverFromBrokenInjection(hostFile);

          Attachment fileText = new Attachment(hostFile.getViewProvider().getVirtualFile().getPath(), hostFile.getText());
          throw PluginException.createByClass(new RuntimeExceptionWithAttachments(message, fileText), host.getClass());
        }
      }
    }
  }

  public static @NotNull PsiElement findCompletionPositionLeaf(OffsetsInFile offsets, int offset, PsiFile originalFile) {
    PsiElement insertedElement = offsets.getFile().findElementAt(offset);
    if (insertedElement == null && offsets.getFile().getTextLength() == offset) {
      insertedElement = PsiTreeUtil.getDeepestLast(offsets.getFile());
    }
    CompletionAssertions.assertCompletionPositionPsiConsistent(offsets, offset, originalFile, insertedElement);
    return insertedElement;
  }

  private static PsiFile obtainFileCopy(PsiFile file, boolean forbidCaching) {
    final VirtualFile virtualFile = file.getVirtualFile();
    boolean mayCacheCopy = !forbidCaching && file.isPhysical() &&
                           // we don't want to cache code fragment copies even if they appear to be physical
                           virtualFile != null && virtualFile.isInLocalFileSystem();
    if (mayCacheCopy) {
      final Pair<PsiFile, Document> cached = dereference(file.getUserData(FILE_COPY_KEY));
      if (cached != null && isCopyUpToDate(cached.second, cached.first, file)) {
        PsiFile copy = cached.first;
        CompletionAssertions.assertCorrectOriginalFile("Cached", file, copy);
        return copy;
      }
    }

    final PsiFile copy = (PsiFile)file.copy();
    if (copy.isPhysical() || copy.getViewProvider().isEventSystemEnabled()) {
      LOG.error("File copy should be non-physical and non-event-system-enabled! Language=" +
                file.getLanguage() +
                "; file=" +
                file +
                " of " +
                file.getClass());
    }
    CompletionAssertions.assertCorrectOriginalFile("New", file, copy);

    if (CodeInsightContexts.isSharedSourceSupportEnabled(file.getProject())) {
      CodeInsightContextManagerImpl codeInsightContextManager =
        (CodeInsightContextManagerImpl)CodeInsightContextManager.getInstance(file.getProject());
      CodeInsightContext context = codeInsightContextManager.getCodeInsightContext(file.getViewProvider());
      codeInsightContextManager.setCodeInsightContext(copy.getViewProvider(), context);
    }

    if (mayCacheCopy) {
      final Document document = copy.getViewProvider().getDocument();
      assert document != null;
      syncAcceptSlashR(file.getViewProvider().getDocument(), document);
      file.putUserData(FILE_COPY_KEY, new SoftReference<>(Pair.create(copy, document)));
    }
    return copy;
  }

  private static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  private static boolean isCopyUpToDate(Document document, @NotNull PsiFile copyFile, @NotNull PsiFile originalFile) {
    if (!copyFile.getClass().equals(originalFile.getClass()) ||
        !copyFile.isValid() ||
        !copyFile.getName().equals(originalFile.getName())) {
      return false;
    }
    // the psi file cache might have been cleared by some external activity,
    // in which case PSI-document sync may stop working
    PsiFile current = PsiDocumentManager.getInstance(copyFile.getProject()).getPsiFile(document);
    return current != null && current.getViewProvider().getPsi(copyFile.getLanguage()) == copyFile;
  }

  private static void syncAcceptSlashR(Document originalDocument, Document documentCopy) {
    if (!(originalDocument instanceof DocumentImpl origDocument) || !(documentCopy instanceof DocumentImpl copyDocument)) {
      return;
    }

    copyDocument.setAcceptSlashR(origDocument.acceptsSlashR());
  }
}
