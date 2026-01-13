// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.impl.FoldingUpdate.RegionInfo;
import com.intellij.lang.folding.CustomFoldingProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.WeakList;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.folding.impl.UpdateFoldRegionsOperation.CAN_BE_REMOVED_WHEN_COLLAPSED;

@ApiStatus.Internal
public final class CodeFoldingManagerImpl extends CodeFoldingManager implements Disposable {
  private static final Key<Boolean> FOLDINGS_INITIALIZED_KEY = Key.create("FOLDINGS_INITIALIZED");
  private static final Key<Boolean> ASYNC_FOLDING_UPDATE_KEY = Key.create("ASYNC_FOLDING_UPDATE");
  private static final Key<Map<TextRange, Boolean>> ASYNC_FOLDING_CACHE_KEY = Key.create("ASYNC_FOLDING_CACHE");
  private static final Key<Boolean> AUTO_CREATED_KEY = Key.create("AUTO_CREATED");
  private static final Key<Boolean> FRONTEND_CREATED_KEY = Key.create("FRONTEND_CREATED");
  private static final Key<Boolean> TRANSIENT_KEY = Key.create("TRANSIENT");

  private final Project myProject;
  private final Collection<Document> myDocumentsWithFoldingInfo = new WeakList<>();
  private final Key<DocumentFoldingInfo> MY_FOLDING_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_INFO_IN_DOCUMENT_KEY");

  public CodeFoldingManagerImpl(Project project) {
    myProject = project;

    LanguageFolding.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull KeyedLazyInstance<FoldingBuilder> extension, @NotNull PluginDescriptor pluginDescriptor) {
        // Asynchronously update foldings when an extension is added
        for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
          if (fileEditor instanceof TextEditor) {
            scheduleAsyncFoldingUpdate(((TextEditor)fileEditor).getEditor());
          }
        }
      }

      @Override
      public void extensionRemoved(@NotNull KeyedLazyInstance<FoldingBuilder> extension, @NotNull PluginDescriptor pluginDescriptor) {
        // Synchronously remove foldings when an extension is removed
        for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
          if (fileEditor instanceof TextEditor) {
            updateFoldRegions(((TextEditor)fileEditor).getEditor());
          }
        }
      }
    }, this);

    Runnable listener = () -> {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor instanceof TextEditor) {
          FoldingUpdate.clearFoldingCache(((TextEditor)fileEditor).getEditor());
        }
      }
    };
    MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.addChangeListener(project, listener, this);
    LanguageInjector.EXTENSION_POINT_NAME.addChangeListener(listener, this);
    CustomFoldingProvider.EP_NAME.addChangeListener(listener, this);
  }

  @Override
  public void dispose() {
    for (Document document : myDocumentsWithFoldingInfo) {
      if (document != null) {
        document.putUserData(MY_FOLDING_INFO_IN_DOCUMENT_KEY, null);
      }
    }
  }

  @Override
  @RequiresEdt
  public void releaseFoldings(@NotNull Editor editor) {
    EditorFoldingInfo.disposeForEditor(editor);
  }

  @Override
  public void buildInitialFoldings(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null || !project.equals(myProject) || editor.isDisposed()) return;
    if (!((FoldingModelEx)editor.getFoldingModel()).isFoldingEnabled()) return;
    if (!FoldingUpdate.supportsDumbModeFolding(editor)) return;

    Document document = editor.getDocument();
    PsiDocumentManager.getInstance(myProject).commitDocument(document);
    CodeFoldingState foldingState = buildInitialFoldings(document);
    if (foldingState != null) {
      foldingState.setToEditor(editor);
    }
  }

  @Override
  public @Nullable CodeFoldingState buildInitialFoldings(@NotNull Document document) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    if (myProject.isDisposed()) {
      return null;
    }
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
    if (psiDocumentManager.isUncommited(document)) {
      // skip building foldings for an uncommitted document, CodeFoldingPass invoked by daemon will do it later
      return null;
    }
    //Do not save/restore folding for code fragments
    PsiFile psiFile = psiDocumentManager.getPsiFile(document);
    if (psiFile == null || !psiFile.isValid() || !psiFile.getViewProvider().isPhysical() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }

    List<RegionInfo> regionInfos = FoldingUpdate.getFoldingsFor(psiFile, true);
    boolean supportsDumbModeFolding = FoldingUpdate.supportsDumbModeFolding(psiFile);
    long modStamp = document.getModificationStamp();

    return editor -> {
      ThreadingAssertions.assertEventDispatchThread();
      if (myProject.isDisposed() || editor.isDisposed() || modStamp != document.getModificationStamp()) {
        return;
      }
      FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
      if (!foldingModel.isFoldingEnabled()) return;
      if (isFoldingsInitializedInEditor(editor)) return;
      if (DumbService.isDumb(myProject) && !supportsDumbModeFolding) return;
      updateAndInitFolding(editor, foldingModel, psiFile, regionInfos);
    };
  }

  private void updateAndInitFolding(@NotNull Editor editor,
                                    @NotNull FoldingModelEx foldingModel,
                                    @NotNull PsiFile psiFile,
                                    @NotNull List<? extends RegionInfo> regionInfos) {
    foldingModel.runBatchFoldingOperationDoNotCollapseCaret(new UpdateFoldRegionsOperation(myProject, editor, psiFile, regionInfos,
                                                                                           UpdateFoldRegionsOperation.ApplyDefaultStateMode.YES,
                                                                                           false, false));
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-319892, EA-838676")) {
      initFolding(editor);
    }
  }

  @Override
  public @Nullable Boolean isCollapsedByDefault(@NotNull FoldRegion region) {
    return getCollapsedByDef(region);
  }

  @Override
  public @Nullable Boolean keepExpandedOnFirstCollapseAll(@NotNull FoldRegion region) {
    return region.getUserData(UpdateFoldRegionsOperation.KEEP_EXPANDED_ON_FIRST_COLLAPSE_ALL);
  }

  public void markForUpdate(@NotNull FoldRegion region) {
    UpdateFoldRegionsOperation.UPDATE_REGION.set(region, Boolean.TRUE);
  }

  public void markUpdated(@NotNull FoldRegion region) {
    UpdateFoldRegionsOperation.UPDATE_REGION.set(region, null);
  }

  public static void markAsAutoCreated(@NotNull FoldRegion region) {
    AUTO_CREATED_KEY.set(region, true);
  }

  /**
   * Returns true if the region was created by the code folding manager automatically without user activity.
   * Used to determine whether the region expansion state should be stored in the editor state or not.
   */
  static boolean isAutoCreated(@NotNull FoldRegion region) {
    return AUTO_CREATED_KEY.isIn(region);
  }

  public static void markAsFrontendCreated(@NotNull FoldRegion region) {
    FRONTEND_CREATED_KEY.set(region, true);
  }

  public static boolean isFrontendCreated(@NotNull FoldRegion region) {
    return FRONTEND_CREATED_KEY.isIn(region);
  }

  public static void markAsCanBeRemovedWhenCollapsed(@NotNull FoldRegion region) {
    CAN_BE_REMOVED_WHEN_COLLAPSED.set(region, true);
  }

  public static boolean canBeRemovedWhenCollapsed(@NotNull FoldRegion region) {
    return CAN_BE_REMOVED_WHEN_COLLAPSED.get(region) == Boolean.TRUE;
  }

  public static Boolean getCollapsedByDef(@NotNull FoldRegion region) {
    return UpdateFoldRegionsOperation.COLLAPSED_BY_DEFAULT.get(region);
  }

  public static void setCollapsedByDef(@NotNull FoldRegion region, boolean isCollapsed) {
    UpdateFoldRegionsOperation.COLLAPSED_BY_DEFAULT.set(region, isCollapsed);
  }

  /// Do not store the folding region in user config
  public static void markTransient(@NotNull FoldRegion region) {
    TRANSIENT_KEY.set(region, true);
  }

  /**
   * @deprecated use {@link #markTransient(FoldRegion)}
   */
  @Deprecated
  public static void markAsNotPersistent(@NotNull FoldRegion region) {
    markTransient(region);
  }

  static boolean isTransient(@Nullable FoldRegion region) {
    return TRANSIENT_KEY.isIn(region);
  }

  public static Map<TextRange, Boolean> getAsyncExpandStatusMap(@Nullable Editor editor) {
    return ASYNC_FOLDING_CACHE_KEY.get(editor);
  }

  public static void setAsyncExpandStatusMap(@Nullable Editor editor, @Nullable Map<TextRange, Boolean> regionExpansionStates) {
    ASYNC_FOLDING_CACHE_KEY.set(editor, regionExpansionStates);
  }

  public static void markAsAsyncFoldingUpdater(@Nullable Editor editor) {
    ASYNC_FOLDING_UPDATE_KEY.set(editor, true);
  }

  public static boolean isAsyncFoldingUpdater(@Nullable Editor editor) {
    return ASYNC_FOLDING_UPDATE_KEY.get(editor) == Boolean.TRUE;
  }

  @Override
  public void scheduleAsyncFoldingUpdate(@NotNull Editor editor) {
    FoldingUpdate.clearFoldingCache(editor);
    DaemonCodeAnalyzerEx.getInstanceEx(myProject).restart("CodeFoldingManagerImpl.scheduleAsyncFoldingUpdate");
  }

  private void initFolding(@NotNull Editor editor) {
    Document document = editor.getDocument();
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      DocumentFoldingInfo documentFoldingInfo = getOrCreateDocumentFoldingInfo(document);
      EditorFactory.getInstance().editors(document, myProject)
        .filter(otherEditor -> otherEditor != editor && isFoldingsInitializedInEditor(otherEditor))
        .findFirst()
        .ifPresent(documentFoldingInfo::loadFromEditor);
      documentFoldingInfo.setToEditor(editor);
      documentFoldingInfo.clear();

      editor.putUserData(FOLDINGS_INITIALIZED_KEY, Boolean.TRUE);
    });
  }

  @Override
  public @Nullable FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset) {
    return FoldingUtil.findFoldRegion(editor, startOffset, endOffset);
  }

  @Override
  public @NotNull FoldRegion @NotNull [] getFoldRegionsAtOffset(@NotNull Editor editor, int offset) {
    return FoldingUtil.getFoldRegionsAtOffset(editor, offset);
  }

  @Override
  public void updateFoldRegions(@NotNull Editor editor) {
    updateFoldRegions(editor, false);
  }

  public void updateFoldRegions(@NotNull Editor editor, boolean quick) {
    if (!editor.getSettings().isAutoCodeFoldingEnabled()) {
      return;
    }
    PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
    Runnable runnable = updateFoldRegions(editor, false, quick);
    if (runnable != null) {
      runnable.run();
    }
  }

  @Override
  public @Nullable Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime) {
    if (!editor.getSettings().isAutoCodeFoldingEnabled()) {
      return null;
    }
    Runnable runnable = updateFoldRegions(editor, firstTime, false);
    return () -> {
      if (runnable != null) {
        runnable.run();
      }
      if (firstTime && !isFoldingsInitializedInEditor(editor)) {
        initFolding(editor);
      }
    };
  }

  private @Nullable Runnable updateFoldRegions(@NotNull Editor editor, boolean applyDefaultState, boolean quick) {
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    return psiFile == null ? null : FoldingUpdate.updateFoldRegions(editor, psiFile, applyDefaultState, quick);
  }

  @Override
  public @NotNull CodeFoldingState saveFoldingState(@NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    DocumentFoldingInfo info = getOrCreateDocumentFoldingInfo(editor.getDocument());
    if (isFoldingsInitializedInEditor(editor)) {
      info.loadFromEditor(editor);
    }
    return info;
  }

  @Override
  public void restoreFoldingState(@NotNull Editor editor, @NotNull CodeFoldingState state) {
    ThreadingAssertions.assertEventDispatchThread();
    if (isFoldingsInitializedInEditor(editor)) {
      state.setToEditor(editor);
    }
  }

  @Override
  public void writeFoldingState(@NotNull CodeFoldingState state, @NotNull Element element) {
    if (state instanceof DocumentFoldingInfo) {
      ((DocumentFoldingInfo)state).writeExternal(element);
    }
  }

  @Override
  public @NotNull CodeFoldingState readFoldingState(@NotNull Element element, @NotNull Document document) {
    DocumentFoldingInfo info = getOrCreateDocumentFoldingInfo(document);
    info.readExternal(element);
    return info;
  }

  private @NotNull DocumentFoldingInfo getOrCreateDocumentFoldingInfo(@NotNull Document document) {
    DocumentFoldingInfo info = document.getUserData(MY_FOLDING_INFO_IN_DOCUMENT_KEY);
    if (info == null) {
      info = new DocumentFoldingInfo(myProject, document);
      DocumentFoldingInfo written = ((UserDataHolderEx)document).putUserDataIfAbsent(MY_FOLDING_INFO_IN_DOCUMENT_KEY, info);
      if (written == info) {
        myDocumentsWithFoldingInfo.add(document);
      }
      else {
        info = written;
      }
    }
    return info;
  }

  static boolean isFoldingsInitializedInEditor(@NotNull Editor editor) {
    return Boolean.TRUE.equals(editor.getUserData(FOLDINGS_INITIALIZED_KEY));
  }
}
