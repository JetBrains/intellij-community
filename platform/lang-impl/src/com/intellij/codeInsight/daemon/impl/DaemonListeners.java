// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSettingListener;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerListener;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteActionListener;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripeEvent;
import com.intellij.openapi.editor.ex.ErrorStripeListener;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.Alarm;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * listen for any daemon-related activities and restart the daemon if needed
 */
public final class DaemonListeners implements Disposable {
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final PsiChangeHandler myPsiChangeHandler;
  private boolean myEscPressed;
  volatile boolean cutOperationJustHappened;
  private List<Editor> myActiveEditors = Collections.emptyList();
  private final AtomicLong myFoldingStateChanged = new AtomicLong();
  // some expensive flags, e.g. isMarkedExcluded and isCodeFragment are computed in BGT
  private final Alarm myRecomputeFlagsInBGT = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  DaemonListeners(@NotNull Project project, @NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;

    if (project.isDefault()) {
      myPsiChangeHandler = null;
      return;
    }

    SimpleMessageBusConnection connection = myProject.getMessageBus().simpleConnect();
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        stopDaemon(false, "App closing");
      }
    });

    EditorFactory editorFactory = EditorFactory.getInstance();
    EditorEventMulticasterEx eventMulticaster = (EditorEventMulticasterEx)editorFactory.getEventMulticaster();
    eventMulticaster.addDocumentListener(new DocumentListener() {
      // clearing highlighters before changing the document because change can damage editor highlighters drastically, so we'll clear more than necessary
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent e) {
        Document document = e.getDocument();
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        Project project = virtualFile == null ? null : guessProject(virtualFile);
        //no need to stop daemon if something happened in the console or in non-physical document
        if (!myProject.isDisposed() && ApplicationManager.getApplication().isDispatchThread() && worthBothering(document, project)) {
          // do not restart daemon yet, wait for the psi events fired after the doc committed, PsiChangeHandler handled these events, updated FileStatusMap and called daemon restart
          stopDaemon(false, "Before document change");
          UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
          myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirtyDefensively(document, e);
        }
      }

      @Override
      public void bulkUpdateStarting(@NotNull Document document) {
        if (worthBothering(document, myProject)) {
          // avoid restarts until bulk mode is finished and daemon restarted
          stopDaemon(false, "Document bulk modifications started");
        }
      }
    }, this);

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    IntentionsUI intentionsUI = IntentionsUI.getInstance(project);
    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        myEscPressed = false; // clear "Escape was pressed" flag on each caret change

        Editor editor = e.getEditor();
        if (ComponentUtil.isShowing(editor.getContentComponent(), true) && worthBothering(editor.getDocument(), editor.getProject())) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!myProject.isDisposed() && ComponentUtil.isShowing(editor.getContentComponent(), true)) {
              intentionsUI.invalidateForEditor(editor);
            }
          }, ModalityState.current(), myProject.getDisposed());
          if (!psiDocumentManager.hasEventSystemEnabledUncommittedDocuments()) {
            // daemon might want to auto-import a reference if the caret is close enough
            // but do not restart a daemon too early before PSI is committed,
            // because the typing would cause canceling daemon twice otherwise: on caret movement during typing and later on PSI commit after the doc modification
            stopDaemon(true, "Caret moved");
          }
        }
      }
    }, this);

    connection.subscribe(EditorTrackerListener.TOPIC, activeEditors -> {
      if (myActiveEditors.equals(activeEditors)) {
        return;
      }

      myActiveEditors = activeEditors.isEmpty() ? Collections.emptyList() : new ArrayList<>(activeEditors);
      // do not stop daemon if idea loses/gains focus
      stopDaemon(true, "Active editor change");
      if (ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext()) {
        // editor appear in modal context, re-enable the daemon
        myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
      }

      if (!activeEditors.isEmpty()) {
        ErrorStripeUpdateManager.getInstance(myProject).launchRepaintErrorStripePanel(activeEditors, true);
      }
    });

    editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project editorProject = editor.getProject();

        if (myProject.isDisposed() || (editorProject != null && editorProject != myProject)) {
          return;
        }

        Document document = editor.getDocument();
        boolean showing = ComponentUtil.isShowing(editor.getContentComponent(), true);
        boolean worthBothering = worthBothering(document, editorProject);
        if (!showing || !worthBothering) {
          if (DaemonCodeAnalyzerImpl.LOG.isDebugEnabled()) {
            DaemonCodeAnalyzerImpl.LOG.debug("Not worth bothering about editor created for: " + editor.getVirtualFile() + " because editor isShowing(): " +
                      showing + "; project is open and file is mine: " + worthBothering);
          }
          return;
        }

        if (!(editor.getMarkupModel() instanceof EditorMarkupModelImpl editorMarkup)) {
          return;
        }

        // worthBothering() checks for getCachedPsiFile, so call getPsiFile
        PsiFile psiFile = editorProject == null ? null : psiDocumentManager.getPsiFile(document);
        ErrorStripeUpdateManager errorStripeManager = ErrorStripeUpdateManager.getInstance(myProject);
        // ScratchLineMarkersTestGenerated/FileEditorManagerTest is failed for some reason, so, let's execute now if test in EDT
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          //noinspection deprecation
          errorStripeManager.repaintErrorStripePanel(editor, psiFile);
        }
        else {
          errorStripeManager.launchRepaintErrorStripePanel(editorMarkup, psiFile);
        }
        Disposable disposable = Disposer.newDisposable();
        FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
        foldingModel.addListener(new FoldingListener() {
          long modCount = ((ModificationTracker)foldingModel).getModificationCount();
          @Override
          public void onFoldRegionStateChange(@NotNull FoldRegion region) {
            long newCount = ((ModificationTracker)foldingModel).getModificationCount();
            if (newCount != modCount) {
              myFoldingStateChanged.incrementAndGet();
              modCount = newCount;
            }
          }
        }, disposable);
        EditorUtil.disposeWithEditor(editor, disposable);
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        myActiveEditors.remove(event.getEditor());
        // clear mem leak via IntentionsUIImpl.myLastIntentionHint
        EdtInvocationManager.invokeLaterIfNeeded(() -> {
          IntentionsUI intentionUI = myProject.isDisposed() ? null : myProject.getServiceIfCreated(IntentionsUI.class);
          if (intentionUI != null) {
            intentionUI.invalidateForEditor(event.getEditor());
          }
        });
      }
    }, this);
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      long modCount;
      @Override
      public void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
        // when the user expanded fold region, the highlighting needs to restart,
        // but only after its most recent restart is finished, because CodeFoldingPass is actively expanding regions itself
        if (myFoldingStateChanged.get() != modCount) {
          modCount = myFoldingStateChanged.get();
          stopDaemon(true, "fold region state changed");
        }
      }

      @Override
      public void daemonCancelEventOccurred(@NotNull String reason) {
        modCount = myFoldingStateChanged.get(); // daemon will restart by its own
      }
    });
    Predicate<Document> isDocumentWorthBothering = document -> worthBothering(document, project);
    myPsiChangeHandler = new PsiChangeHandler(myProject, daemonCodeAnalyzer.getFileStatusMap(), this, isDocumentWorthBothering);
    PsiManager psiManager = PsiManager.getInstance(myProject);
    psiManager.addPsiTreeChangeListener(myPsiChangeHandler, this);

    connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        stopDaemonAndRestartAllFiles("Project roots changed");
        // re-initialize TrafficLightRenderer in each editor since root change event could change highlight-ability
        reInitTrafficLightRendererForAllEditors();
      }
    });
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, (_1, _2, _3, _4) -> stopDaemonAndRestartAllFiles("Additional libraries changed"));

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        stopDaemonAndRestartAllFiles("Dumb mode started");
      }

      @Override
      public void exitDumbMode() {
        stopDaemonAndRestartAllFiles("Dumb mode finished");
      }
    });

    connection.subscribe(PowerSaveMode.TOPIC, () -> {
      if (PowerSaveMode.isEnabled()) {
        clearHighlightingRelatedHighlightersInAllEditors();
        reInitTrafficLightRendererForAllEditors();
        repaintTrafficLightIconForAllEditors();
      }
      stopDaemonAndRestartAllFiles("Power save mode changed to " + PowerSaveMode.isEnabled());
    });
    connection.subscribe(EditorColorsManager.TOPIC, __ -> stopDaemonAndRestartAllFiles("Editor color scheme changed"));
    connection.subscribe(CommandListener.TOPIC, new MyCommandListener());
    connection.subscribe(ProfileChangeAdapter.TOPIC, new MyProfileChangeListener());

    ApplicationManagerEx.getApplicationEx().addWriteActionListener(new WriteActionListener() {
      @Override
      public void beforeWriteActionStart(@NotNull Class<?> action) {
        if (myDaemonCodeAnalyzer.isRunning()) {
          stopDaemon(false, "Write action start: " + action);
        } // we'll restart in writeActionFinished()
      }

      @Override
      public void writeActionFinished(@NotNull Class<?> action) {
        // otherwise we'll restart when PSI commit happens, or changed PSI elements will be handled in PsiChangeHandler
        if (!psiDocumentManager.hasEventSystemEnabledUncommittedDocuments()) {
          stopDaemon(true, "Write action finish: "+action);
        }
      }
    }, this);

    connection.subscribe(TodoConfiguration.PROPERTY_CHANGE, new MyTodoListener());

    connection.subscribe(AnActionListener.TOPIC, new MyAnActionListener());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        boolean isDaemonShouldBeStopped = false;
        for (VFileEvent event : events) {
          if (event instanceof VFilePropertyChangeEvent e) {
            String propertyName = e.getPropertyName();
            if (VirtualFile.PROP_NAME.equals(propertyName)) {
              fileRenamed(e);
            }
            if (!isDaemonShouldBeStopped && !propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
              isDaemonShouldBeStopped = true;
            }
          }
        }

        if (isDaemonShouldBeStopped) {
          stopDaemon(true, "Virtual file property change");
        }
      }

      private void fileRenamed(@NotNull VFilePropertyChangeEvent event) {
        stopDaemonAndRestartAllFiles("Virtual file name changed");
        VirtualFile virtualFile = event.getFile();
        if (!virtualFile.isValid()) {
          return;
        }
        FileManagerEx fileManager = (FileManagerEx)PsiManagerEx.getInstanceEx(myProject).getFileManager();
        PsiFile psiFile = fileManager.getFastCachedPsiFile(virtualFile, CodeInsightContexts.anyContext());
        if (psiFile == null || myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
          return;
        }

        Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
        if (document == null) {
          return;
        }
        // when the file becomes un-highlightable, clear all highlighters from previous HighlightPasses
        removeAllHighlightersFromHighlightPasses(document, project);
      }
    });
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        intentionsUI.invalidate();
      }
    });

    eventMulticaster.addErrorStripeListener(new ErrorStripeListener() {
      @Override
      public void errorMarkerClicked(@NotNull ErrorStripeEvent e) {
        RangeHighlighter highlighter = e.getHighlighter();
        if (!highlighter.isValid()) return;
        HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
        if (info != null) {
          GotoNextErrorHandler.navigateToError(myProject, e.getEditor(), info, null);
        }
      }
    }, this);

    LaterInvocator.addModalityStateListener(new ModalityStateListener() {
      @Override
      public void beforeModalityStateChanged(boolean entering, @NotNull Object modalEntity) {
        // before showing dialog we are in non-modal context yet, and before closing dialog we are still in modal context
        boolean inModalContext = LaterInvocator.isInModalContext();
        stopDaemon(inModalContext, "Modality change. Was modal: " + inModalContext);
        myDaemonCodeAnalyzer.setUpdateByTimerEnabled(inModalContext);
      }
    }, this);

    connection.subscribe(SeverityRegistrar.SEVERITIES_CHANGED_TOPIC, () -> stopDaemonAndRestartAllFiles("Severities changed"));

    //noinspection rawtypes
    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerListener() {
      @Override
      public void facetRenamed(@NotNull Facet facet, @NotNull String oldName) {
        stopDaemonAndRestartAllFiles("facet renamed: " + oldName + " -> " + facet.getName());
      }

      @Override
      public void facetAdded(@NotNull Facet facet) {
        stopDaemonAndRestartAllFiles("facet added: " + facet.getName());
      }

      @Override
      public void facetRemoved(@NotNull Facet facet) {
        stopDaemonAndRestartAllFiles("facet removed: " + facet.getName());
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        stopDaemonAndRestartAllFiles("facet changed: " + facet.getName());
      }
    });

    listenForExtensionChange(LanguageAnnotators.EP_NAME, "annotators list changed");
    listenForExtensionChange(LineMarkerProviders.EP_NAME, "line marker providers list changed");
    listenForExtensionChange(ExternalLanguageAnnotators.EP_NAME, "external annotators list changed");

    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        psiManager.dropPsiCaches();
        stopDaemonAndRestartAllFiles("Plugin installed");
      }

      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        psiManager.dropPsiCaches();
        myDaemonCodeAnalyzer.cancelAllUpdateProgresses(false, "plugin unload: " + pluginDescriptor);
        removeHighlightersOnPluginUnload(pluginDescriptor);
        myDaemonCodeAnalyzer.clearProgressIndicator();
        myDaemonCodeAnalyzer.cleanAllFileLevelHighlights();
        intentionsUI.invalidate();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        stopDaemonAndRestartAllFiles("Plugin unloaded");
      }
    });
    connection.subscribe(FileHighlightingSettingListener.SETTING_CHANGE, (root, setting) ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        PsiFile psiFile = root.getContainingFile();
        if (psiFile != null) {
          // force clearing all PSI caches, including those in WholeFileInspectionFactory
          psiManager.dropPsiCaches();
          for (Editor editor : myActiveEditors) {
            if (Objects.equals(editor.getVirtualFile(), psiFile.getVirtualFile())) {
              ErrorStripeUpdateManager.getInstance(myProject).launchRepaintErrorStripePanel(editor, psiFile);
            }
          }
        }
      }));
    HeavyProcessLatch.INSTANCE.addListener(this, op -> {
      if (!HeavyProcessLatch.Type.Syncing.equals(op.getType())) {
        stopDaemon(true, "re-scheduled to execute after heavy processing finished");
      }
    });
  }

  private static void removeAllHighlightersFromHighlightPasses(@NotNull Document document, @NotNull Project project) {
    MarkupModel model = DocumentMarkupModel.forDocument(document, project, false);
    if (model == null) {
      return;
    }
    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      if (info != null) {
        highlighter.dispose();
      }
    }
  }

  void repaintTrafficLightIconForAllEditors() {
    for (Editor editor : myActiveEditors) {
      MarkupModel markup = editor.getMarkupModel();
      if (markup instanceof EditorMarkupModelImpl editorMarkup) {
        editorMarkup.repaintTrafficLightIcon();
      }
    }
  }

  private void reInitTrafficLightRendererForAllEditors() {
    for (Editor editor : myActiveEditors) {
      EditorMarkupModel editorMarkupModel = (EditorMarkupModel)editor.getMarkupModel();
      ErrorStripeRenderer renderer = editorMarkupModel.getErrorStripeRenderer();
      if (renderer instanceof TrafficLightRenderer tlr) {
        tlr.invalidate();
      }
    }
  }

  private void clearHighlightingRelatedHighlightersInAllEditors() {
    for (Editor editor : myActiveEditors) {
      editor.getMarkupModel().removeAllHighlighters();
      MarkupModel documentMarkupModel = DocumentMarkupModel.forDocument(editor.getDocument(), myProject, false);
      List<RangeHighlighter> toRemove = documentMarkupModel == null ? List.of() : ContainerUtil.filter(documentMarkupModel.getAllHighlighters(), highlighter -> {
        HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
        return info != null && (info.isFromInspection() || info.isFromAnnotator() || info.isFromHighlightVisitor() || info.isFromInjection());
      });
      for (RangeHighlighter highlighter : toRemove) {
        HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
        if (info != null) {
          UpdateHighlightersUtil.disposeWithFileLevelIgnoreErrorsInEDT(highlighter, myProject, info);
        }
      }
    }
  }

  private Project guessProject(@NotNull VirtualFile virtualFile) {
    if (!FileEditorManager.getInstance(myProject).getAllEditorList(virtualFile).isEmpty()) {
      // if at least one editor in myProject frame has opened this file, then we can assume this file does belong to the myProject
      return myProject;
    }
    return ProjectUtil.guessProjectForFile(virtualFile);
  }

  private <T, U extends KeyedLazyInstance<T>> void listenForExtensionChange(@NotNull ExtensionPointName<U> name, @NotNull String message) {
    name.addChangeListener(() -> stopDaemonAndRestartAllFiles(message), this);
  }

  private boolean worthBothering(@Nullable Document document, @Nullable Project guessedProject) {
    if (document == null) {
      return true;
    }
    if (guessedProject != null && guessedProject != myProject) {
      return false;
    }
    if (myProject.isDisposed()) {
      return false;
    }

    // Used to be these lines:

    /*
    // cached is essential here since we do not want to create PSI file in alien project
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document);
    return psiFile != null && psiFile.isPhysical() && psiFile.getOriginalFile() == psiFile;
    */

    // But had to replace them with the heuristics below which are not PSI-related to avoid accessing indexes in EDT
    // see EA-659452 T: DirectoryIndexImpl.getInfoForFile
    // and please don't do anything PSIthic here
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (isMarkedCodeFragment(document)) {
      // if the document is from the debugger evaluate window, even if it contains a light file, it needs to be highlighted
      return true;
    }

    if (virtualFile == null || virtualFile instanceof LightVirtualFile) {
      return false;
    }
    // non-physical docs can be updated outside EDT as a rule
    if (document instanceof DocumentImpl impl && !impl.isWriteThreadOnly()) {
      return false;
    }
    return !isMarkedExcluded(document);
  }

  @ApiStatus.Internal
  @VisibleForTesting
  boolean isMarkedExcluded(@NotNull Document document) {
    ExpensiveFlags flags = getExpensiveFlags(document);
    return flags != null && flags.isExcluded();
  }

  @ApiStatus.Internal
  @VisibleForTesting
  boolean isMarkedCodeFragment(@NotNull Document document) {
    ExpensiveFlags flags = getExpensiveFlags(document);
    return flags != null && flags.isCodeFragment();
  }

  private ExpensiveFlags getExpensiveFlags(@NotNull Document document) {
    ExpensiveFlags flags = document.getUserData(EXPENSIVE_FLAGS);
    if (flags == null || !flags.isUpToDate(myProject)) {
      if (myRecomputeFlagsInBGT.isEmpty()) {
        myRecomputeFlagsInBGT.addRequest(() -> {
          recomputeExpensiveFlags(document);
        }, 0);
      }
      return null;
    }
    return flags;
  }

  private void recomputeExpensiveFlags(@NotNull Document document) {
    ReadAction.runBlocking(() -> {
      if (myProject.isDisposed() || myRecomputeFlagsInBGT.isDisposed()) {
        return;
      }
      ExpensiveFlags flags = document.getUserData(EXPENSIVE_FLAGS);
      if (flags == null || !flags.isUpToDate(myProject)) {
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        boolean isExcluded = virtualFile != null &&
                             (ProjectFileIndex.getInstance(myProject).isExcluded(virtualFile) ||
                              ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile, virtualFile.getFileType()));
        // cached to avoid getting PSI for alien project Document
        boolean isCodeFragment = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document) instanceof PsiCodeFragment;
        document.putUserData(EXPENSIVE_FLAGS, new ExpensiveFlags(isExcluded, isCodeFragment, myProject));
      }
    });
  }

  private record ExpensiveFlags(boolean isExcluded, boolean isCodeFragment, long rootsTimeStamp) {
    private ExpensiveFlags(boolean isExcluded, boolean isCodeFragment, @NotNull Project project) {
      this(isExcluded, isCodeFragment, ProjectRootManager.getInstance(project).getModificationCount());
    }

    boolean isUpToDate(@NotNull Project project) {
      long modCount = ProjectRootManager.getInstance(project).getModificationCount();
      return modCount == rootsTimeStamp();
    }
  }

  /**
   * stores
   *  {@code modCount} (from {@link com.intellij.openapi.roots.ProjectRootManager#getModificationCount}) if the file is excluded,
   *  {@code -modCount} (from {@link com.intellij.openapi.roots.ProjectRootManager#getModificationCount}}) if it's not excluded,
   *  null if unknown
   */
  private static final Key<ExpensiveFlags> EXPENSIVE_FLAGS = Key.create("EXPENSIVE_FLAGS");

  @Override
  public void dispose() {
    myDaemonCodeAnalyzer.stopProcess(false,"Project closed");
  }

  /**
   * @return true if the {@code file} (which does or doesn't lie in this project content roots, depending on {@code isInContent})
   * can be modified without user's explicit permission.
   * By convention, permission is required for
   * - never touched files,
   * - files under explicit write permission version control (such as Perforce, which asks "do you want to edit this file"),
   * - files in the middle of cut-n-paste operation.
   */
  @RequiresEdt
  public static boolean canChangeFileSilently(@NotNull PsiFileSystemItem psiFile,
                                              boolean isInContent,
                                              @NotNull ThreeState extensionsAllowToChangeFileSilently) {
    ThreadingAssertions.assertEventDispatchThread();
    Project project = psiFile.getProject();
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    if (daemonCodeAnalyzer == null) {
      return true;
    }

    if (daemonCodeAnalyzer.cutOperationJustHappened()) {
      return false;
    }
    return HighlightingSessionImpl.canChangeFileSilently(psiFile, isInContent, extensionsAllowToChangeFileSilently);
  }

  private static String CUT_ACTION_NAME;

  private final class MyCommandListener implements CommandListener {
    @Override
    public void commandStarted(@NotNull CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) {
        return;
      }

      String commandName = event.getCommandName();
      cutOperationJustHappened = commandName != null && commandName.equals(getCutActionName());
    }

    private static Document extractDocumentFromCommand(@NotNull CommandEvent event) {
      Document affectedDocument = event.getDocument();
      if (affectedDocument != null) return affectedDocument;
      Object id = event.getCommandGroupId();

      if (id instanceof Document document) {
        affectedDocument = document;
      }
      else if (id instanceof DocCommandGroupId docId) {
        affectedDocument = docId.getDocument();
      }
      return affectedDocument;
    }

    @Override
    public void commandFinished(@NotNull CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) {
        return;
      }

      if (isEscapeJustPressed()) {
        if (affectedDocument != null) {
          // prevent Esc key to leave the document in the not-highlighted state
          // todo IJPL-339 investigate this place
          if (!myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNullFor(affectedDocument)) {
            stopDaemon(true, "Command finish");
          }
        }
      }
    }
  }

  private static String getCutActionName() {
    String cutActionName = CUT_ACTION_NAME;
    if (cutActionName == null) {
      ActionManager actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
      if (actionManager != null) {
        cutActionName = actionManager.getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        CUT_ACTION_NAME = cutActionName;
      }
    }
    return cutActionName;
  }

  private final class MyTodoListener implements PropertyChangeListener {
    @Override
    public void propertyChange(@NotNull PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        stopDaemonAndRestartAllFiles("Todo patterns changed");
      }
      else if (TodoConfiguration.PROP_MULTILINE.equals(evt.getPropertyName())) {
        stopDaemonAndRestartAllFiles("Todo multi-line detection changed");
      }
    }
  }

  private final class MyProfileChangeListener implements ProfileChangeAdapter {
    @Override
    public void profileChanged(@NotNull InspectionProfile profile) {
      stopDaemonAndRestartAllFiles("Profile changed");
    }

    @Override
    public void profileActivated(InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
      stopDaemonAndRestartAllFiles("Profile activated");
    }

    @Override
    public void profilesInitialized() {
      AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> stopDaemonAndRestartAllFiles("Inspection profiles activated"));
    }
  }

  private final class MyAnActionListener implements AnActionListener {
    private AnAction cachedEscapeAction;

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
      if (cachedEscapeAction == null) {
        myEscPressed = IdeActions.ACTION_EDITOR_ESCAPE.equals(event.getActionManager().getId(action));
        if (myEscPressed) {
          cachedEscapeAction = action;
        }
      }
      else {
        myEscPressed = cachedEscapeAction == action;
      }
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      //no need to stop daemon if something happened in the console
      if (editor != null && !worthBothering(editor.getDocument(), editor.getProject())) {
        return;
      }
      stopDaemon(false, "Editor typing"); // daemon will restart later after the document modification/PSI commit
    }

    @Override
    public void beforeShortcutTriggered(@NotNull Shortcut shortcut, @NotNull List<AnAction> actions, @NotNull DataContext dataContext) {
      stopDaemon(true, "Shortcut triggered");
    }
  }

  private void stopDaemon(boolean toRestartAlarm, @NonNls @NotNull String reason) {
    myDaemonCodeAnalyzer.stopProcess(toRestartAlarm, reason);
  }

  private void stopDaemonAndRestartAllFiles(@NotNull String reason) {
    myDaemonCodeAnalyzer.restart(reason);
  }

  private void removeHighlightersOnPluginUnload(@NotNull PluginDescriptor pluginDescriptor) {
    for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getAllEditors()) {
      if (fileEditor instanceof TextEditor textEditor) {
        boolean clearAll = false;
        VirtualFile file = fileEditor.getFile();
        if (file != null) {
          ClassLoader classLoader = file.getFileType().getClass().getClassLoader();
          if (classLoader instanceof PluginAwareClassLoader pluginLoader &&
              pluginLoader.getPluginId().equals(pluginDescriptor.getPluginId())) {
            clearAll = true;
          }
        }

        Editor editor = textEditor.getEditor();
        if (clearAll) {
          editor.getMarkupModel().removeAllHighlighters();
        }
        else {
          removeHighlightersOnPluginUnload(editor.getMarkupModel(), pluginDescriptor);
        }

        MarkupModel documentMarkupModel = DocumentMarkupModel.forDocument(editor.getDocument(), myProject, false);
        if (documentMarkupModel != null) {
          if (clearAll) {
            documentMarkupModel.removeAllHighlighters();
          }
          else {
            removeHighlightersOnPluginUnload(documentMarkupModel, pluginDescriptor);
          }
        }
      }
    }
  }

  private static void removeHighlightersOnPluginUnload(@NotNull MarkupModel model, @NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader pluginClassLoader = pluginDescriptor.getPluginClassLoader();
    for (RangeHighlighter highlighter: model.getAllHighlighters()) {
      if (!(highlighter instanceof RangeHighlighterEx ex)
          || !ex.isPersistent()
          || pluginClassLoader instanceof PluginAwareClassLoader && isHighlighterFromPlugin(highlighter, pluginClassLoader)) {
        model.removeHighlighter(highlighter);
      }
    }
  }

  private static boolean isHighlighterFromPlugin(@NotNull RangeHighlighter highlighter, @NotNull ClassLoader pluginClassLoader) {
    CustomHighlighterRenderer renderer = highlighter.getCustomRenderer();
    if (renderer != null && renderer.getClass().getClassLoader() == pluginClassLoader) {
      return true;
    }

    HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
    if (info != null) {
      IntentionAction quickFixFromPlugin = info.findRegisteredQuickFix((descriptor, range) -> {
          IntentionAction intentionAction = IntentionActionDelegate.unwrap(descriptor.getAction());
          if (intentionAction.getClass().getClassLoader() == pluginClassLoader) {
            return intentionAction;
          }
          LocalQuickFix fix = QuickFixWrapper.unwrap(intentionAction);
          if (fix != null && fix.getClass().getClassLoader() == pluginClassLoader) {
            return intentionAction;
          }
          return null;
        });
      if (quickFixFromPlugin != null) return true;
    }

    LineMarkerInfo<?> lmInfo = LineMarkersUtil.getLineMarkerInfo(highlighter);
    return lmInfo != null && lmInfo.getClass().getClassLoader() == pluginClassLoader;
  }

  boolean isEscapeJustPressed() {
    return myEscPressed;
  }
  @TestOnly
  void waitForUpdateFileStatusQueue() {
    myPsiChangeHandler.waitForUpdateFileStatusQueue();
  }

  void runAfterUpdateFileStatusQueue(@NotNull Runnable runnable) {
    if (myPsiChangeHandler != null) {
      myPsiChangeHandler.runAfterUpdateFileStatusQueue(runnable);
    }
  }
  @RequiresBackgroundThread
  @RequiresReadLock
  void flushUpdateFileStatusQueue() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    myPsiChangeHandler.flushUpdateFileStatusQueue();
  }
  @TestOnly
  void waitUpdateExpensiveFlags(long timeout, @NotNull TimeUnit unit) throws TimeoutException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myRecomputeFlagsInBGT.waitForAllExecuted(timeout, unit);
  }
}
