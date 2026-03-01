// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.folding.CustomFoldingProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.WeakList;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
          if (fileEditor instanceof TextEditor te) {
            scheduleAsyncFoldingUpdate(te.getEditor());
          }
        }
      }

      @Override
      public void extensionRemoved(@NotNull KeyedLazyInstance<FoldingBuilder> extension, @NotNull PluginDescriptor pluginDescriptor) {
        // Synchronously remove foldings when an extension is removed
        for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
          if (fileEditor instanceof TextEditor te) {
            updateFoldRegions(te.getEditor());
          }
        }
      }
    }, this);

    Runnable listener = () -> {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor instanceof TextEditor te) {
          FoldingUpdate.clearFoldingCache(te.getEditor());
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
  @Deprecated
  public void buildInitialFoldings(@NotNull Editor editor) {
    // see buildInitialFoldings(Document)
  }

  @Deprecated
  @Override
  public @Nullable CodeFoldingState buildInitialFoldings(@NotNull Document document) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    return null;
  }

  @RequiresReadLock
  static PsiFile getPsiFileForFolding(@NotNull Project project, @NotNull Document document) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null || !psiFile.isValid() || !psiFile.getViewProvider().isPhysical() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }
    if (psiFile instanceof PsiCompiledFile compiled) {
      psiFile = compiled.getDecompiledPsiFile();
    }
    return psiFile;
  }

  @Override
  public @Nullable Boolean isCollapsedByDefault(@NotNull FoldRegion region) {
    return getCollapsedByDefault(region);
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

  public static Boolean getCollapsedByDefault(@NotNull FoldRegion region) {
    return UpdateFoldRegionsOperation.COLLAPSED_BY_DEFAULT.get(region);
  }

  public static void setCollapsedByDefault(@NotNull FoldRegion region, boolean isCollapsed) {
    UpdateFoldRegionsOperation.COLLAPSED_BY_DEFAULT.set(region, isCollapsed);
  }

  /// Do not store the folding region in user config
  public static void markTransient(@NotNull FoldRegion region) {
    TRANSIENT_KEY.set(region, true);
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

  @Override
  public @Nullable FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset) {
    return FoldingUtil.findFoldRegion(editor, startOffset, endOffset);
  }

  @Override
  public @NotNull FoldRegion @NotNull [] getFoldRegionsAtOffset(@NotNull Editor editor, int offset) {
    return FoldingUtil.getFoldRegionsAtOffset(editor, offset);
  }

  @Override
  @RequiresReadLock
  public void updateFoldRegions(@NotNull Editor editor) {
    if (!editor.getSettings().isAutoCodeFoldingEnabled()) {
      return;
    }
    PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
    FoldingUpdate.Result result = updateFoldRegions(editor, false, false);
    if (result != null) {
      result.edtRunnable().run();
    }
  }

  @Override
  @RequiresBackgroundThread
  @RequiresReadLock
  public @Nullable Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime) {
    return updateFoldRegionsAsync(editor, firstTime, false);
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  @RequiresReadLock
  public @Nullable Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime, boolean quick) {
    ThreadingAssertions.assertBackgroundThread();
    ThreadingAssertions.assertReadAccess();
    if (!editor.getSettings().isAutoCodeFoldingEnabled()) {
      return null;
    }
    FoldingUpdate.Result result = updateFoldRegions(editor, firstTime, quick);
    Document document = editor.getDocument();
    DocumentFoldingInfo documentFoldingInfo = getOrCreateDocumentFoldingInfo(document);
    EditorFactory.getInstance().editors(document, myProject)
      .filter(otherEditor -> otherEditor != editor && isFoldingsInitializedInEditor(otherEditor))
      .findFirst()
      .ifPresent(otherEditor -> documentFoldingInfo.loadFromEditor(otherEditor));
    if (firstTime && !isFoldingsInitializedInEditor(editor)) {
      documentFoldingInfo.computeExpandRanges();
    }
    return () -> {
      ThreadingAssertions.assertEventDispatchThread();
      if (result != null) {
        result.edtRunnable().run();
      }
      if (firstTime && !isFoldingsInitializedInEditor(editor)) {
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
          documentFoldingInfo.applyFoldingExpandedState(editor);
          documentFoldingInfo.clear();
        });
        setFoldingsInitializedInEditor(editor);
      }
    };
  }

  private @Nullable FoldingUpdate.Result updateFoldRegions(@NotNull Editor editor, boolean firstTime, boolean quick) {
    PsiFile psiFile = getPsiFileForFolding(myProject, editor.getDocument());
    return psiFile == null ? null : FoldingUpdate.updateFoldRegions(editor, psiFile, firstTime, quick);
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

  @RequiresEdt
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
  private static void setFoldingsInitializedInEditor(@NotNull Editor editor) {
    editor.putUserData(FOLDINGS_INITIALIZED_KEY, Boolean.TRUE);
  }
}
