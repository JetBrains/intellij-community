// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionResultsViewComparator;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.icons.AllIcons;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ClickListener;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.*;

public abstract class QuickFixAction extends AnAction implements CustomComponentAction {
  private static final Logger LOG = Logger.getInstance(QuickFixAction.class);
  private static final NotificationGroup BATCH_QUICK_FIX_MESSAGES =
    NotificationGroupManager.getInstance().getNotificationGroup("Batch quick fix");

  public static final QuickFixAction[] EMPTY = new QuickFixAction[0];
  protected final InspectionToolWrapper<?,?> myToolWrapper;

  protected static InspectionResultsView getInvoker(@NotNull AnActionEvent e) {
    return e.getData(InspectionResultsView.DATA_KEY);
  }

  protected QuickFixAction(@NlsActions.ActionText String text, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    this(text, AllIcons.Actions.IntentionBulb, null, toolWrapper);
  }

  protected QuickFixAction(@NlsActions.ActionText String text, Icon icon, KeyStroke keyStroke, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    super(text, null, icon);
    myToolWrapper = toolWrapper;
    if (keyStroke != null) {
      registerCustomShortcutSet(new CustomShortcutSet(keyStroke), null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final InspectionResultsView view = getInvoker(e);
    if (view == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(false);

    Object[] selectedNodes = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (selectedNodes == null) return;

    final InspectionToolWrapper<?,?> toolWrapper = InspectionTree.findWrapper(selectedNodes);
    if (toolWrapper == null || toolWrapper != myToolWrapper) {
      return;
    }

    if (!isProblemDescriptorsAcceptable() && InspectionTree.getSelectedRefElements(e).length > 0 ||
        isProblemDescriptorsAcceptable() && view.getTree().getSelectedDescriptors(e).length > 0) {
      e.getPresentation().setEnabledAndVisible(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected boolean isProblemDescriptorsAcceptable() {
    return false;
  }

  public @NlsActions.ActionText String getText() {
    return getTemplatePresentation().getText();
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final InspectionResultsView view = getInvoker(e);
    final InspectionTree tree = view.getTree();
    try {
      Ref<List<CommonProblemDescriptor[]>> descriptors = Ref.create();
      Set<VirtualFile> readOnlyFiles = new HashSet<>();
      TreePath[] paths = tree.getSelectionPaths();
      if (paths == null) {
        descriptors.set(List.of());
      }
      else {
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.run(() -> {
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          indicator.setText(InspectionsBundle.message("quick.fix.action.checking.problem.progress"));
          descriptors.set(tree.getSelectedDescriptorPacks(true, readOnlyFiles, false, paths));
        }), InspectionsBundle.message("preparing.for.apply.fix"), true, e.getProject())) {
          return;
        }
      }
      if (isProblemDescriptorsAcceptable() && !descriptors.get().isEmpty()) {
        doApplyFix(view.getProject(), descriptors.get(), readOnlyFiles, tree.getContext());
      }
      else {
        doApplyFix(getSelectedElements(view), view);
      }

      view.getTree().removeSelectedProblems();
    }
    finally {
      view.setApplyingFix(false);
    }
  }


  protected @NotNull BatchExecutionResult applyFix(@NotNull Project project,
                                                   @NotNull GlobalInspectionContextImpl context,
                                                   CommonProblemDescriptor @NotNull [] descriptors,
                                                   @NotNull Set<? super PsiElement> ignoredElements) {
    return ModCommandExecutor.Result.SUCCESS;
  }

  private void doApplyFix(@NotNull Project project,
                          @NotNull List<CommonProblemDescriptor[]> descriptors,
                          @NotNull Set<? extends VirtualFile> readOnlyFiles,
                          @NotNull GlobalInspectionContextImpl context) {
    if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(project, readOnlyFiles)) return;

    final Set<PsiElement> resolvedElements = new HashSet<>();
    performFixesInBatch(project, descriptors, context, resolvedElements);

    refreshViews(project, resolvedElements, myToolWrapper);
  }

  protected boolean startInWriteAction() {
    return false;
  }

  protected void performFixesInBatch(@NotNull Project project,
                                     @NotNull List<CommonProblemDescriptor[]> descriptors,
                                     @NotNull GlobalInspectionContextImpl context,
                                     Set<? super PsiElement> ignoredElements) {
    final String templatePresentationText = getTemplatePresentation().getText();
    assert templatePresentationText != null;
    Ref<@Nls String> messageRef = Ref.create();
    CommandProcessor.getInstance().executeCommand(project, () -> {
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
      boolean startInWriteAction = startInWriteAction();
      PerformFixesTask performFixesTask = new PerformFixesTask(project, descriptors, ignoredElements, context);
      if (startInWriteAction) {
        ((ApplicationImpl)ApplicationManager.getApplication())
          .runWriteActionWithCancellableProgressInDispatchThread(templatePresentationText, project, null, performFixesTask::doRun);
      }
      else {
        final SequentialModalProgressTask progressTask =
          new SequentialModalProgressTask(project, templatePresentationText, true);
        progressTask.setMinIterationTime(200);
        progressTask.setTask(performFixesTask);
        ProgressManager.getInstance().run(progressTask);
        messageRef.set(performFixesTask.getResultMessage(templatePresentationText));
      }
    }, templatePresentationText, null);
    String message = messageRef.get();
    if (message != null) {
      BATCH_QUICK_FIX_MESSAGES.createNotification(HtmlChunk.text(message).toString(), NotificationType.WARNING)
        .notify(project);
    }
  }

  private void doApplyFix(final RefEntity @NotNull [] refElements, @NotNull InspectionResultsView view) {
    final boolean[] refreshNeeded = {false};
    if (refElements.length > 0) {
      final Project project = refElements[0].getRefManager().getProject();
      CommandProcessor.getInstance().executeCommand(project, () -> {
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
        ApplicationManager.getApplication().runWriteAction(() -> {
          refreshNeeded[0] = applyFix(refElements);
        });
      }, getTemplatePresentation().getText(), null);
    }
    if (refreshNeeded[0]) {
      refreshViews(view.getProject(), refElements, myToolWrapper);
    }
  }

  public static void removeElements(RefEntity @NotNull [] refElements, @NotNull Project project, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    refreshViews(project, refElements, toolWrapper);
    final ArrayList<RefElement> deletedRefs = new ArrayList<>(1);
    for (RefEntity refElement : refElements) {
      if (!(refElement instanceof RefElement)) continue;
      refElement.getRefManager().removeRefElement((RefElement)refElement, deletedRefs);
    }
  }

  private static Set<VirtualFile> getReadOnlyFiles(RefEntity @NotNull [] refElements) {
    Set<VirtualFile> readOnlyFiles = new HashSet<>();
    for (RefEntity refElement : refElements) {
      PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getPsiElement() : null;
      if (psiElement == null || psiElement.getContainingFile() == null) continue;
      readOnlyFiles.add(psiElement.getContainingFile().getVirtualFile());
    }
    return readOnlyFiles;
  }

  private static RefEntity @NotNull [] getSelectedElements(InspectionResultsView view) {
    if (view == null) return RefEntity.EMPTY_ELEMENTS_ARRAY;
    RefEntity[] selection = view.getTree().getSelectedElements();
    PsiDocumentManager.getInstance(view.getProject()).commitAllDocuments();
    Arrays.sort(selection, InspectionResultsViewComparator::compareEntities);
    return selection;
  }

  private static void refreshViews(@NotNull Project project, @NotNull Set<? extends PsiElement> resolvedElements, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Set<GlobalInspectionContextImpl> runningContexts = managerEx.getRunningContexts();
    for (GlobalInspectionContextImpl context : runningContexts) {
      for (PsiElement element : resolvedElements) {
        context.resolveElement(toolWrapper.getTool(), element);
      }
      context.refreshViews();
    }
  }

  protected static void refreshViews(@NotNull Project project, RefEntity @NotNull [] resolvedElements, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    final Set<PsiElement> ignoredElements = new HashSet<>();
    for (RefEntity element : resolvedElements) {
      final PsiElement psiElement = element instanceof RefElement ? ((RefElement)element).getPsiElement() : null;
      if (psiElement != null && psiElement.isValid()) {
        ignoredElements.add(psiElement);
      }
    }
    refreshViews(project, ignoredElements, toolWrapper);
  }

  /**
   * @return true if immediate UI update needed.
   */
  protected boolean applyFix(RefEntity @NotNull [] refElements) {
    Set<VirtualFile> readOnlyFiles = getReadOnlyFiles(refElements);
    if (!readOnlyFiles.isEmpty()) {
      final Project project = refElements[0].getRefManager().getProject();
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(readOnlyFiles);
      return !operationStatus.hasReadonlyFiles();
    }
    return true;
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    final JButton button = new JButton(presentation.getText());
    Icon icon = presentation.getIcon();
    if (icon == null) {
      icon = AllIcons.Actions.IntentionBulb;
    }
    button.setEnabled(presentation.isEnabled());
    button.setIcon(IconLoader.getTransparentIcon(icon, 0.75f));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        AnActionEvent action = AnActionEvent.createFromAnAction(
          QuickFixAction.this, event, place, ActionToolbar.getDataContextFor(button));
        try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
          actionPerformed(action);
        }
        return true;
      }
    }.installOn(button);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
    panel.setBorder(JBUI.Borders.empty(7, 0, 8, 0));
    panel.add(button);
    return panel;
  }

  private final class PerformFixesTask extends PerformFixesModalTask {
    private final @NotNull GlobalInspectionContextImpl myContext;
    private final @NotNull Set<? super PsiElement> myIgnoredElements;

    PerformFixesTask(@NotNull Project project,
                     @NotNull List<CommonProblemDescriptor[]> descriptors,
                     @NotNull Set<? super PsiElement> ignoredElements,
                     @NotNull GlobalInspectionContextImpl context) {
      super(project, descriptors);
      myContext = context;
      myIgnoredElements = ignoredElements;
    }

    @Override
    protected void applyFix(Project project, CommonProblemDescriptor descriptor) {
      if (descriptor instanceof ProblemDescriptor problemDescriptor &&
          problemDescriptor.getStartElement() == null &&
          problemDescriptor.getEndElement() == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invalidated psi for " + descriptor);
        }
        return;
      }

      try {
        BatchExecutionResult result =
          QuickFixAction.this.applyFix(myProject, myContext, new CommonProblemDescriptor[]{descriptor}, myIgnoredElements);
        myResultCount.merge(result, 1, Integer::sum);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }
}
