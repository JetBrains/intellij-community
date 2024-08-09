// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSettingListener;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.diagnostic.PluginException;
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
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
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * listen for any daemon-related activities and restart the daemon if needed
 */
public final class DaemonListeners implements Disposable {
  private static final Logger LOG = Logger.getInstance(DaemonListeners.class);
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private boolean myEscPressed;
  volatile boolean cutOperationJustHappened;
  private List<Editor> myActiveEditors = Collections.emptyList();

  DaemonListeners(@NotNull Project project, @NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;

    if (project.isDefault()) {
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
          stopDaemon(true, "Document change");
          UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
        }
      }

      @Override
      public void bulkUpdateStarting(@NotNull Document document) {
        if (worthBothering(document, myProject)) {
          // avoid restarts until bulk mode is finished and daemon restarted
          stopDaemon(false, "Document bulk modifications started");
        }
      }

      @Override
      public void bulkUpdateFinished(@NotNull Document document) {
        if (worthBothering(document, myProject)) {
          stopDaemon(true, "Document bulk modifications finished");
        }
      }
    }, this);

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        myEscPressed = false; // clear "Escape was pressed" flag on each caret change

        Editor editor = e.getEditor();
        if (ComponentUtil.isShowing(editor.getContentComponent(), true) && worthBothering(editor.getDocument(), editor.getProject())) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!myProject.isDisposed() && ComponentUtil.isShowing(editor.getContentComponent(), true)) {
              IntentionsUI.getInstance(myProject).invalidateForEditor(editor);
            }
          }, ModalityState.current(), myProject.getDisposed());
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

      ErrorStripeUpdateManager errorStripeUpdateManager = ErrorStripeUpdateManager.getInstance(myProject);
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-333913, EA-765304")) {
        for (Editor editor : activeEditors) {
          PsiFile file = ReadAction.compute(() -> psiDocumentManager.getCachedPsiFile(editor.getDocument()));
          errorStripeUpdateManager.repaintErrorStripePanel(editor, file);
        }
      }
    });

    editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        if (myProject.isDisposed()) {
          return;
        }

        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        Project editorProject = editor.getProject();
        boolean showing = ComponentUtil.isShowing(editor.getContentComponent(), true);
        boolean worthBothering = worthBothering(document, editorProject);
        if (!showing || !worthBothering) {
          LOG.debug("Not worth bothering about editor created for: " + editor.getVirtualFile() + " because editor isShowing(): " +
                    showing + "; project is open and file is mine: " + worthBothering);
          return;
        }
        // worthBothering() checks for getCachedPsiFile, so call getPsiFile here
        PsiFile file = editorProject == null ? null : PsiDocumentManager.getInstance(editorProject).getPsiFile(document);
        ErrorStripeUpdateManager.getInstance(myProject).repaintErrorStripePanel(editor, file);
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        myActiveEditors.remove(event.getEditor());
        // mem leak after closing last editor otherwise
        if (myActiveEditors.isEmpty()) {
          EdtInvocationManager.invokeLaterIfNeeded(() -> {
            IntentionsUI intentionUI = myProject.getServiceIfCreated(IntentionsUI.class);
            if (intentionUI != null) {
              intentionUI.invalidateForEditor(event.getEditor());
            }
          });
        }
      }
    }, this);

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiChangeHandler(myProject, connection, daemonCodeAnalyzer, this), this);

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
      stopDaemonAndRestartAllFiles("Power save mode changed to " + PowerSaveMode.isEnabled());
      if (PowerSaveMode.isEnabled()) {
        clearHighlightingRelatedHighlightersInAllEditors();
        reInitTrafficLightRendererForAllEditors();
        repaintTrafficLightIconForAllEditors();
      }
      else {
        daemonCodeAnalyzer.restart();
      }
    });
    connection.subscribe(EditorColorsManager.TOPIC, __ -> stopDaemonAndRestartAllFiles("Editor color scheme changed"));
    connection.subscribe(CommandListener.TOPIC, new MyCommandListener());
    connection.subscribe(ProfileChangeAdapter.TOPIC, new MyProfileChangeListener());

    ApplicationManager.getApplication().addApplicationListener(new MyApplicationListener(), project);

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
        PsiFile psiFile = !virtualFile.isValid() ? null : ((FileManagerImpl)PsiManagerEx.getInstanceEx(myProject).getFileManager()).getFastCachedPsiFile(virtualFile);
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
        IntentionsUI.getInstance(project).invalidate();
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
        boolean inModalContext = Registry.is("ide.perProjectModality") || LaterInvocator.isInModalContext();
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
        PsiManager.getInstance(myProject).dropPsiCaches();
        stopDaemonAndRestartAllFiles("Plugin installed");
      }

      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        PsiManager.getInstance(myProject).dropPsiCaches();
        myDaemonCodeAnalyzer.cancelAllUpdateProgresses(false, "plugin unload: " + pluginDescriptor);
        removeHighlightersOnPluginUnload(pluginDescriptor);
        myDaemonCodeAnalyzer.clearProgressIndicator();
        myDaemonCodeAnalyzer.cleanAllFileLevelHighlights();
        IntentionsUI.getInstance(project).invalidate();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        stopDaemonAndRestartAllFiles("Plugin unloaded");
      }
    });
    connection.subscribe(FileHighlightingSettingListener.SETTING_CHANGE, (root, setting) ->
      WriteAction.run(() -> {
        PsiFile file = root.getContainingFile();
        if (file != null) {
          // force clearing all PSI caches, including those in WholeFileInspectionFactory
          PsiManager.getInstance(myProject).dropPsiCaches();
          for (Editor editor : myActiveEditors) {
            if (Objects.equals(editor.getVirtualFile(), file.getVirtualFile())) {
              ErrorStripeUpdateManager.getInstance(myProject).repaintErrorStripePanel(editor, file);
            }
          }
        }
      }));
    HeavyProcessLatch.INSTANCE.addListener(this, __ -> stopDaemon(true, "re-scheduled to execute after heavy processing finished"));
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
        return info != null && (info.isFromInspection() || info.isFromAnnotator() || info.isFromHighlightVisitor() || info.isInjectionRelated());
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
    if (virtualFile == null || virtualFile instanceof LightVirtualFile) {
      return false;
    }
    // non-physical docs can be updated outside EDT as a rule
    return !(document instanceof DocumentImpl impl) || impl.isWriteThreadOnly();
  }

  @Override
  public void dispose() {
    stopDaemonAndRestartAllFiles("Project closed");
  }

  /**
   * @return true if the {@code file} (which does or doesn't lie in this project content roots, depending on {@code isInContent})
   * can be modified without user's explicit permission.
   * By convention, permission is required for
   * - never touched files,
   * - files under explicit write permission version control (such as Perforce, which asks "do you want to edit this file"),
   * - files in the middle of cut-n-paste operation.
   */
  public static boolean canChangeFileSilently(@NotNull PsiFileSystemItem file, boolean isInContent,
                                              @NotNull ThreeState extensionsAllowToChangeFileSilently) {
    ThreadingAssertions.assertEventDispatchThread();
    Project project = file.getProject();
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    if (daemonCodeAnalyzer == null) {
      return true;
    }

    if (daemonCodeAnalyzer.cutOperationJustHappened()) {
      return false;
    }
    return CanISilentlyChange.thisFile(file).canIReally(isInContent, extensionsAllowToChangeFileSilently);
  }

  /**
   * @deprecated use {@link #canChangeFileSilently(PsiFileSystemItem, boolean, ThreeState)} instead
   */
  @Deprecated(forRemoval = true)
  public static boolean canChangeFileSilently(@NotNull PsiFileSystemItem file) {
    PluginException.reportDeprecatedUsage("this method", "");
    return canChangeFileSilently(file, true, ThreeState.UNSURE);
  }

  @Deprecated
  public static boolean canChangeFileSilently(@NotNull PsiFileSystemItem file, boolean isInContent) {
    PluginException.reportDeprecatedUsage("this method", "");
    return canChangeFileSilently(file, isInContent, ThreeState.UNSURE);
  }

  private final class MyApplicationListener implements ApplicationListener {
    @Override
    public void beforeWriteActionStart(@NotNull Object action) {
      if (!myDaemonCodeAnalyzer.isRunning()) return; // we'll restart in writeActionFinished()
      stopDaemon(true, "Write action start");
    }

    @Override
    public void writeActionFinished(@NotNull Object action) {
      stopDaemon(true, "Write action finish");
    }
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
      if (!myDaemonCodeAnalyzer.isRunning()) {
        return;
      }
      stopDaemon(false, ObjectUtils.notNull(event.getCommandName(), "command started"));
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

      if (myEscPressed) {
        if (affectedDocument != null) {
          // prevent Esc key to leave the document in the not-highlighted state
          if (!myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNull(affectedDocument)) {
            stopDaemon(true, "Command finish");
          }
        }
      }
      else if (!myDaemonCodeAnalyzer.isRunning()) {
        stopDaemon(true, "Command finish");
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
      stopDaemon(true, "Editor typing");
    }

    @Override
    public void beforeShortcutTriggered(@NotNull Shortcut shortcut,
                                        @NotNull List<AnAction> actions,
                                        @NotNull DataContext dataContext) {
      stopDaemon(true, "Shortcut triggered");
    }
  }

  private void stopDaemon(boolean toRestartAlarm, @NonNls @NotNull String reason) {
    myDaemonCodeAnalyzer.stopProcess(toRestartAlarm, reason);
  }

  private void stopDaemonAndRestartAllFiles(@NotNull String reason) {
    myDaemonCodeAnalyzer.stopProcessAndRestartAllFiles(reason);
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
}
