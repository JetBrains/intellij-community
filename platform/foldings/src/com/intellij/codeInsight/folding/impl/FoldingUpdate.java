// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.folding.CompositeFoldingBuilder;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.FoldingRegionWindow;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class FoldingUpdate {
  private static final Logger LOG = Logger.getInstance(FoldingUpdate.class);

  private static final Key<CachedValue<Result>> CODE_FOLDING_KEY = Key.create("code folding");

  public static final Key<Boolean> INJECTED_CODE_FOLDING_ENABLED = Key.create("injected code folding is enabled");

  private FoldingUpdate() {
  }

  @RequiresReadLock
  static @Nullable Result updateFoldRegions(@NotNull Editor editor, @NotNull PsiFile psiFile, boolean firstTime, boolean quick) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Project project = psiFile.getProject();
    Document document = editor.getDocument();
    LOG.assertTrue(PsiDocumentManager.getInstance(project).isCommitted(document));
    if (document.getTextLength() != psiFile.getTextLength()) {
      LOG.error(DebugUtil.diagnosePsiDocumentInconsistency(psiFile, document));
      return null;
    }

    CachedValue<Result> value = editor.getUserData(CODE_FOLDING_KEY);

    if (value != null && !firstTime) {
      Supplier<Result> cached = value.getUpToDateOrNull();
      if (cached != null) {
        return cached.get();
      }
    }
    if (firstTime) {
      return getUpdateResult(psiFile, document, project, editor, true, quick);
    }

    return CachedValuesManager.getManager(project).getCachedValue(
      editor, CODE_FOLDING_KEY, () -> {
        PsiFile psiFile1 = CodeFoldingManagerImpl.getPsiFileForFolding(project, document);
        if (psiFile1 == null) {
          return null;
        }
        Result result = getUpdateResult(psiFile1, document, project, editor, false, quick);
        return CachedValueProvider.Result.create(result, result.dependencies());
      }, false);
  }

  record Result(@NotNull @Unmodifiable List<RegionInfo> foldings,
                @NotNull UpdateFoldRegionsOperation foldBatchOperation,
                @NotNull Object @NotNull [] dependencies,
                @NotNull Runnable edtRunnable) {}

  private static @NotNull Result getUpdateResult(@NotNull PsiFile psiFile,
                                                 @NotNull Document document,
                                                 @NotNull Project project,
                                                 @NotNull Editor editor,
                                                 boolean applyDefaultState,
                                                 boolean quick) {
    PsiUtilCore.ensureValid(psiFile);
    List<RegionInfo> elementsToFold = getFoldingsFor(psiFile, quick);
    UpdateFoldRegionsOperation operation = new UpdateFoldRegionsOperation(project, editor, psiFile, elementsToFold,
                                                                          applyDefaultStateMode(applyDefaultState),
                                                                          !applyDefaultState, false);
    int documentLength = document.getTextLength();
    AtomicBoolean alreadyExecuted = new AtomicBoolean();
    Runnable runnable = () -> {
      if (alreadyExecuted.compareAndSet(false, true)) {
        if (documentLength != document.getTextLength() || !PsiDocumentManager.getInstance(project).isCommitted(document)) {
          reportUnexpectedDocumentChange(psiFile, document, documentLength);
        }
        editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
      }
    };
    Set<Object> dependencies = new HashSet<>();
    dependencies.add(psiFile);
    dependencies.add(editor.getFoldingModel());
    for (RegionInfo info : elementsToFold) {
      dependencies.addAll(info.descriptor.getDependencies());
    }
    return new Result(elementsToFold, operation, ArrayUtil.toObjectArray(dependencies), runnable);
  }

  private static void reportUnexpectedDocumentChange(@NotNull PsiFile psiFile, @NotNull Document document, int prevLength) {
    Document fileDoc = psiFile.getViewProvider().getDocument();
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    PsiDocumentManager pdm = PsiDocumentManager.getInstance(psiFile.getProject());
    PsiFile docFile = pdm.getCachedPsiFile(document);
    LOG.error("Document has changed since fold regions were calculated:\n" +
              "  lengths: " + prevLength + " vs " + document.getTextLength() + "\n" +
              "  document=" + document + "\n" +
              "  file.document=" + (fileDoc == document ? "same" : fileDoc) + "\n" +
              "  document.file=" + (docFile == psiFile ? "same" : docFile) + "\n" +
              "  committed=" + pdm.isCommitted(document) + "\n" +
              "  psiFile=" + psiFile + "\n" +
              "  vFile.length=" + (vFile.isValid() ? vFile.getLength() : -1));
  }

  private static @NotNull UpdateFoldRegionsOperation.ApplyDefaultStateMode applyDefaultStateMode(boolean applyDefaultState) {
    return applyDefaultState ? UpdateFoldRegionsOperation.ApplyDefaultStateMode.EXCEPT_CARET_REGION : UpdateFoldRegionsOperation.ApplyDefaultStateMode.NO;
  }

  private static final Key<Long> LAST_UPDATE_INJECTED_STAMP_KEY = Key.create("LAST_UPDATE_INJECTED_STAMP_KEY");
  static @Nullable Runnable updateInjectedFoldRegions(@NotNull Editor hostEditor, @NotNull PsiFile hostPsiFile, boolean applyDefaultState) {
    if (hostPsiFile instanceof PsiCompiledElement) {
      return null;
    }
    boolean codeFoldingForInjectedEnabled = hostEditor.getUserData(INJECTED_CODE_FOLDING_ENABLED) != Boolean.FALSE;

    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Project project = hostPsiFile.getProject();
    Document document = hostEditor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));
    FoldingModel foldingModel = hostEditor.getFoldingModel();

    long timeStamp = document.getModificationStamp();
    Long lastTimeStamp = hostEditor.getUserData(LAST_UPDATE_INJECTED_STAMP_KEY);
    if (lastTimeStamp != null && lastTimeStamp.longValue() == timeStamp) {
      return null;
    }

    // we assume the injections are already done in InjectedGeneralHighlightingPass
    List<DocumentWindow> injectedDocuments = InjectedLanguageManager.getInstance(project).getCachedInjectedDocumentsInRange(hostPsiFile, hostPsiFile.getTextRange());
    if (injectedDocuments.isEmpty()) {
      return null;
    }
    List<EditorWindow> injectedEditors = new ArrayList<>(injectedDocuments.size());
    List<PsiFile> injectedFiles = new ArrayList<>(injectedDocuments.size());
    List<List<RegionInfo>> lists = new ArrayList<>(injectedDocuments.size());
    for (DocumentWindow injectedDocument : injectedDocuments) {
      if (!injectedDocument.isValid()) {
        continue;
      }
      InjectedLanguageUtil.enumerate(injectedDocument, hostPsiFile, (injectedFile, places) -> {
        Editor injectedEditor = injectedFile.isValid() ? InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile) : null;
        if (injectedEditor instanceof EditorWindow window) {
          injectedEditors.add(window);
          injectedFiles.add(injectedFile);
          lists.add(codeFoldingForInjectedEnabled ? getFoldingsFor(injectedFile, false) : List.of());
        }
      });
    }

    return () -> {
      List<Runnable> updateOperations = new ArrayList<>(injectedEditors.size());
      for (int i = 0; i < injectedEditors.size(); i++) {
        EditorWindow injectedEditor = injectedEditors.get(i);
        PsiFile injectedFile = injectedFiles.get(i);
        if (!injectedEditor.getDocument().isValid()) continue;
        List<RegionInfo> list = lists.get(i);
        updateOperations.add(new UpdateFoldRegionsOperation(project, injectedEditor, injectedFile, list,
                                                            applyDefaultStateMode(applyDefaultState), !applyDefaultState, true));
      }
      foldingModel.runBatchFoldingOperation(() -> {
        for (Runnable operation : updateOperations) {
          operation.run();
        }
      });
      EditorFoldingInfo info = EditorFoldingInfo.get(hostEditor);
      for (FoldRegion region : hostEditor.getFoldingModel().getAllFoldRegions()) {
        FoldingRegionWindow injectedRegion = FoldingRegionWindow.getInjectedRegion(region);
        if (injectedRegion != null && !injectedRegion.isValid()) {
          info.removeRegion(region);
        }
      }

      hostEditor.putUserData(LAST_UPDATE_INJECTED_STAMP_KEY, timeStamp);
    };
  }

  /**
   * Checks the ability to initialize folding in the dumb mode for file.
   *
   * @param psiFile the file to test
   * @return true if folding initialization available in the dumb mode
   */
  static boolean supportsDumbModeFolding(@NotNull PsiFile psiFile) {
    FileViewProvider viewProvider = psiFile.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if(foldingBuilder != null && !DumbService.isDumbAware(foldingBuilder))
        return false;
    }
    return true;
  }

  @VisibleForTesting
  public static @NotNull @Unmodifiable List<RegionInfo> getFoldingsFor(@NotNull PsiFile psiFile, boolean quick) {
    FileViewProvider viewProvider = psiFile.getViewProvider();
    Document document = viewProvider.getDocument();
    if (document == null) {
      LOG.error("No document for " + viewProvider);
      return List.of();
    }

    LOG.assertTrue(PsiDocumentManager.getInstance(psiFile.getProject()).isCommitted(document));

    int textLength = document.getTextLength();
    List<RangeMarker> hardRefToRangeMarkers = new ArrayList<>();

    Comparator<PsiFile> preferBaseLanguage = Comparator.comparing((PsiFile f) -> f.getLanguage() != viewProvider.getBaseLanguage()).thenComparing(f->f.getLanguage().getID());
    List<PsiFile> allFiles = ContainerUtil.sorted(viewProvider.getAllFiles(), preferBaseLanguage);
    DocumentEx copyDoc = allFiles.size() > 1 ? new DocumentImpl(document.getImmutableCharSequence(), document instanceof DocumentImpl docImpl && docImpl.acceptsSlashR(), true) : null;
    List<RegionInfo> elementsToFold = null;
    for (PsiFile psiRoot : allFiles) {
      Language language = psiRoot.getLanguage();
      FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if (foldingBuilder != null) {
        if (psiRoot.getTextLength() != textLength) {
          LOG.error(DebugUtil.diagnosePsiDocumentInconsistency(psiRoot, document));
          return List.of();
        }

        PsiFile containingFile = PsiUtilCore.getTemplateLanguageFile(psiFile);
        FoldingDescriptor[] descriptors = LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiRoot, document, quick);
        if (elementsToFold == null) {
          elementsToFold = new ArrayList<>(descriptors.length);
        }
        for (FoldingDescriptor descriptor : descriptors) {
          PsiElement psiElement = descriptor.getElement().getPsi();
          if (psiElement == null) {
            LOG.error("No PSI for folding descriptor " + descriptor);
            continue;
          }
          // in case of CompositeBuilder, the assertion was already checked in CompositeFoldingBuilder.buildFoldRegions
          if (!(foldingBuilder instanceof CompositeFoldingBuilder)) {
            CompositeFoldingBuilder.assertSameFile(containingFile, descriptor, psiElement, foldingBuilder);
          }
          TextRange range = descriptor.getRange();
          if (range.getEndOffset() > textLength) {
            diagnoseIncorrectRange(psiRoot, document, language, foldingBuilder, descriptor, psiElement);
            continue;
          }

          if (copyDoc == null || addNonConflictingRegion(copyDoc, range, hardRefToRangeMarkers)) {
            RegionInfo regionInfo = new RegionInfo(descriptor, psiElement, foldingBuilder);
            elementsToFold.add(regionInfo);
          }
        }
      }
    }
    return ContainerUtil.notNullize(elementsToFold);
  }

  private static boolean addNonConflictingRegion(@NotNull DocumentEx document, @NotNull TextRange range, @NotNull List<? super RangeMarker> hardRefToRangeMarkers) {
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    if (!document.processRangeMarkersOverlappingWith(start, end, rm -> !areConflicting(range, rm.getTextRange()))) {
      return false;
    }
    RangeMarker marker = document.createRangeMarker(start, end);
    hardRefToRangeMarkers.add(marker); //prevent immediate GC
    return true;
  }

  private static boolean areConflicting(@NotNull TextRange range1, @NotNull TextRange range2) {
    return range1.equals(range2) || !range1.contains(range2) && !range2.contains(range1) && range1.intersectsStrict(range2);
  }

  private static void diagnoseIncorrectRange(@NotNull PsiFile psiFile,
                                             @NotNull Document document,
                                             @NotNull Language language,
                                             @NotNull FoldingBuilder foldingBuilder,
                                             @NotNull FoldingDescriptor descriptor, @NotNull PsiElement psiElement) {
    String message = "Folding descriptor " + descriptor + " made by " + foldingBuilder +
                     " for " + language + " is outside document range, PSI element: " + psiElement +
                     ", PSI element range: " + psiElement.getTextRange() + "; " + DebugUtil.diagnosePsiDocumentInconsistency(psiElement, document);
    LOG.error(message, ApplicationManager.getApplication().isInternal()
                       ? new Attachment[]{CoreAttachmentFactory.createAttachment(document), new Attachment("psiTree.txt", DebugUtil.psiToString(psiFile, true, true))}
                       : Attachment.EMPTY_ARRAY);
  }

  static void clearFoldingCache(@NotNull Editor editor) {
    editor.putUserData(CODE_FOLDING_KEY, null);
    editor.putUserData(LAST_UPDATE_INJECTED_STAMP_KEY, null);
  }

  @ApiStatus.Internal
  public record RegionInfo(
    @NotNull FoldingDescriptor descriptor,
    @NotNull PsiElement psiElement,
    @Nullable String signature,
    boolean collapsedByDefault,
    boolean keepExpandedOnFirstCollapseAll) {

    private RegionInfo(@NotNull FoldingDescriptor descriptor,
                       @NotNull PsiElement psiElement,
                       @NotNull FoldingBuilder foldingBuilder) {
      this(descriptor, psiElement, createSignature(psiElement), isCollapsedByDefault(descriptor, foldingBuilder), FoldingPolicy.keepExpandedOnFirstCollapseAll(descriptor, foldingBuilder));
    }

    private static boolean isCollapsedByDefault(@NotNull FoldingDescriptor descriptor, @NotNull FoldingBuilder foldingBuilder) {
      Boolean hardCoded = descriptor.isCollapsedByDefault();
      return hardCoded == null ? FoldingPolicy.isCollapsedByDefault(descriptor, foldingBuilder) : hardCoded;
    }

    private static String createSignature(@NotNull PsiElement element) {
      String signature = FoldingPolicy.getSignature(element);
      if (signature != null && Registry.is("folding.signature.validation")) {
        PsiFile containingFile = element.getContainingFile();
        PsiElement restoredElement = FoldingPolicy.restoreBySignature(containingFile, signature);
        if (!element.equals(restoredElement)) {
          StringBuilder trace = new StringBuilder();
          PsiElement restoredAgain = FoldingPolicy.restoreBySignature(containingFile, signature, trace);
          LOG.error("element: " + element + "(" + element.getText()
                    + "); restoredElement: " + restoredElement
                    + "; signature: '" + signature
                    + "'; file: " + containingFile
                    + "; injected: " + InjectedLanguageManager.getInstance(element.getProject()).isInjectedFragment(containingFile)
                    + "; languages: " + containingFile.getViewProvider().getLanguages()
                    + "; restored again: " + restoredAgain +
                    "; restore produces same results: " + (restoredAgain == restoredElement)
                    + "; trace:\n" + trace);
        }
      }
      return signature;
    }
  }
}
