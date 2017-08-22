/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ex;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ClickListener;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author max
 */
public class QuickFixAction extends AnAction implements CustomComponentAction {
  private static final Logger LOG = Logger.getInstance(QuickFixAction.class);

  public static final QuickFixAction[] EMPTY = new QuickFixAction[0];
  protected final InspectionToolWrapper myToolWrapper;

  protected static InspectionResultsView getInvoker(AnActionEvent e) {
    return InspectionResultsView.DATA_KEY.getData(e.getDataContext());
  }

  protected QuickFixAction(String text, @NotNull InspectionToolWrapper toolWrapper) {
    this(text, AllIcons.Actions.CreateFromUsage, null, toolWrapper);
  }

  protected QuickFixAction(String text, Icon icon, KeyStroke keyStroke, @NotNull InspectionToolWrapper toolWrapper) {
    super(text, null, icon);
    myToolWrapper = toolWrapper;
    if (keyStroke != null) {
      registerCustomShortcutSet(new CustomShortcutSet(keyStroke), null);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final InspectionResultsView view = getInvoker(e);
    if (view == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setVisible(false);
    e.getPresentation().setEnabled(false);

    final InspectionTree tree = view.getTree();
    final InspectionToolWrapper toolWrapper = tree.getSelectedToolWrapper(true);
    if (!view.isSingleToolInSelection() || toolWrapper != myToolWrapper) {
      return;
    }

    if (!isProblemDescriptorsAcceptable() && tree.getSelectedElements().length > 0 ||
        isProblemDescriptorsAcceptable() && tree.getSelectedDescriptors().length > 0) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(true);
    }
  }

  protected boolean isProblemDescriptorsAcceptable() {
    return false;
  }

  public String getText() {
    return getTemplatePresentation().getText();
  }

  @Override
  @ReviseWhenPortedToJDK("9")
  public void actionPerformed(final AnActionEvent e) {
    final InspectionResultsView view = getInvoker(e);
    final InspectionTree tree = view.getTree();
    try {
      Ref<CommonProblemDescriptor[]> descriptors = Ref.create();
      Set<VirtualFile> readOnlyFiles = new THashSet<>();
      //TODO revise when jdk9 arrives. Until then this redundant cast is a workaround to compile under jdk9 b169
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously((Runnable)() -> ReadAction.run(() -> {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.setText("Checking problem descriptors...");
        descriptors.set(tree.getSelectedDescriptors(true, readOnlyFiles, false, false));
      }), InspectionsBundle.message("preparing.for.apply.fix"), true, e.getProject())) {
        return;
      }
      if (isProblemDescriptorsAcceptable() && descriptors.get().length > 0) {
        doApplyFix(view.getProject(), descriptors.get(), readOnlyFiles, tree.getContext());
      } else {
        doApplyFix(getSelectedElements(view), view);
      }
    } finally {
      view.setApplyingFix(false);
    }
  }


  protected void applyFix(@NotNull Project project,
                          @NotNull GlobalInspectionContextImpl context,
                          @NotNull CommonProblemDescriptor[] descriptors,
                          @NotNull Set<PsiElement> ignoredElements) {
  }

  private void doApplyFix(@NotNull final Project project,
                          @NotNull final CommonProblemDescriptor[] descriptors,
                          @NotNull Set<VirtualFile> readOnlyFiles,
                          @NotNull final GlobalInspectionContextImpl context) {
    if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(project, readOnlyFiles)) return;
    
    final RefManagerImpl refManager = (RefManagerImpl)context.getRefManager();
    final boolean initial = refManager.isInProcess();

    refManager.inspectionReadActionFinished();

    try {
      final Set<PsiElement> resolvedElements = new HashSet<>();
      performFixesInBatch(project, descriptors, context, resolvedElements);

      refreshViews(project, resolvedElements, myToolWrapper);
    }
    finally { //to make offline view lazy
      if (initial) refManager.inspectionReadActionStarted();
    }
  }

  protected void performFixesInBatch(@NotNull Project project,
                                     @NotNull CommonProblemDescriptor[] descriptors,
                                     @NotNull GlobalInspectionContextImpl context,
                                     Set<PsiElement> ignoredElements) {
    final String templatePresentationText = getTemplatePresentation().getText();
    assert templatePresentationText != null;
    CommandProcessor.getInstance().executeCommand(project, () -> {
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
      final SequentialModalProgressTask progressTask =
        new SequentialModalProgressTask(project, templatePresentationText, true);
      progressTask.setMinIterationTime(200);
      progressTask.setTask(new PerformFixesTask(project, descriptors, ignoredElements, progressTask, context));
      ProgressManager.getInstance().run(progressTask);
    }, templatePresentationText, null);
  }

  private void doApplyFix(@NotNull final RefEntity[] refElements, @NotNull InspectionResultsView view) {
    final RefManagerImpl refManager = (RefManagerImpl)view.getGlobalInspectionContext().getRefManager();

    final boolean initial = refManager.isInProcess();

    refManager.inspectionReadActionFinished();

    try {
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
    finally {  //to make offline view lazy
      if (initial) refManager.inspectionReadActionStarted();
    }
  }

  public static void removeElements(@NotNull RefEntity[] refElements, @NotNull Project project, @NotNull InspectionToolWrapper toolWrapper) {
    refreshViews(project, refElements, toolWrapper);
    final ArrayList<RefElement> deletedRefs = new ArrayList<>(1);
    for (RefEntity refElement : refElements) {
      if (!(refElement instanceof RefElement)) continue;
      refElement.getRefManager().removeRefElement((RefElement)refElement, deletedRefs);
    }
  }

  private static Set<VirtualFile> getReadOnlyFiles(@NotNull RefEntity[] refElements) {
    Set<VirtualFile> readOnlyFiles = new THashSet<>();
    for (RefEntity refElement : refElements) {
      PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
      if (psiElement == null || psiElement.getContainingFile() == null) continue;
      readOnlyFiles.add(psiElement.getContainingFile().getVirtualFile());
    }
    return readOnlyFiles;
  }

  @NotNull
  private static RefEntity[] getSelectedElements(InspectionResultsView view) {
    if (view == null) return new RefElement[0];
    List<RefEntity> selection = new ArrayList<>(Arrays.asList(view.getTree().getSelectedElements()));
    PsiDocumentManager.getInstance(view.getProject()).commitAllDocuments();
    Collections.sort(selection, (o1, o2) -> {
      if (o1 instanceof RefElement && o2 instanceof RefElement) {
        RefElement r1 = (RefElement)o1;
        RefElement r2 = (RefElement)o2;
        final PsiElement element1 = r1.getElement();
        final PsiElement element2 = r2.getElement();
        final PsiFile containingFile1 = element1.getContainingFile();
        final PsiFile containingFile2 = element2.getContainingFile();
        if (containingFile1 == containingFile2) {
          int i1 = element1.getTextOffset();
          int i2 = element2.getTextOffset();
          if (i1 < i2) {
            return 1;
          }
          if (i1 > i2){
            return -1;
          }
          return 0;
        }
        return containingFile1.getName().compareTo(containingFile2.getName());
      }
      if (o1 instanceof RefElement) {
        return 1;
      }
      if (o2 instanceof RefElement) {
        return -1;
      }
      return o1.getName().compareTo(o2.getName());
    });

    return selection.toArray(new RefEntity[selection.size()]);
  }

  private static void refreshViews(@NotNull Project project, @NotNull Set<PsiElement> resolvedElements, @NotNull InspectionToolWrapper toolWrapper) {
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Set<GlobalInspectionContextImpl> runningContexts = managerEx.getRunningContexts();
    for (GlobalInspectionContextImpl context : runningContexts) {
      for (PsiElement element : resolvedElements) {
        context.resolveElement(toolWrapper.getTool(), element);
      }
      context.refreshViews();
    }
  }

  protected static void refreshViews(@NotNull Project project, @NotNull RefEntity[] resolvedElements, @NotNull InspectionToolWrapper toolWrapper) {
    final Set<PsiElement> ignoredElements = new HashSet<>();
    for (RefEntity element : resolvedElements) {
      final PsiElement psiElement = element instanceof RefElement ? ((RefElement)element).getElement() : null;
      if (psiElement != null && psiElement.isValid()) {
        ignoredElements.add(psiElement);
      }
    }
    refreshViews(project, ignoredElements, toolWrapper);
  }

  /**
   * @return true if immediate UI update needed.
   */
  protected boolean applyFix(@NotNull RefEntity[] refElements) {
    Set<VirtualFile> readOnlyFiles = getReadOnlyFiles(refElements);
    if (!readOnlyFiles.isEmpty()) {
      final Project project = refElements[0].getRefManager().getProject();
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(
        VfsUtilCore.toVirtualFileArray(readOnlyFiles));
      if (operationStatus.hasReadonlyFiles()) return false;
    }
    return true;
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    final JButton button = new JButton(presentation.getText());
    Icon icon = presentation.getIcon();
    if (icon == null) {
      icon = AllIcons.Actions.CreateFromUsage;
    }
    button.setEnabled(presentation.isEnabled());
    button.setIcon(IconLoader.getTransparentIcon(icon, 0.75f));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        final ActionToolbar toolbar = UIUtil.getParentOfType(ActionToolbar.class, button);
        actionPerformed(AnActionEvent.createFromAnAction(QuickFixAction.this,
                                                         event,
                                                         ActionPlaces.UNKNOWN,
                                                         toolbar == null ? DataManager.getInstance().getDataContext(button) : toolbar.getToolbarDataContext()));
        return true;
      }
    }.installOn(button);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
    panel.setBorder(JBUI.Borders.empty(7, 0, 8, 0));
    panel.add(button);
    return panel;
  }

  private class PerformFixesTask extends PerformFixesModalTask {
    @NotNull private final GlobalInspectionContextImpl myContext;
    @NotNull
    private final Set<PsiElement> myIgnoredElements;

    PerformFixesTask(@NotNull Project project,
                     @NotNull CommonProblemDescriptor[] descriptors,
                     @NotNull Set<PsiElement> ignoredElements,
                     @NotNull SequentialModalProgressTask task,
                     @NotNull GlobalInspectionContextImpl context) {
      super(project, descriptors, task);
      myContext = context;
      myIgnoredElements = ignoredElements;
    }

    @Override
    protected void applyFix(Project project, CommonProblemDescriptor descriptor) {
      if (descriptor instanceof ProblemDescriptor && 
          ((ProblemDescriptor)descriptor).getStartElement() == null &&
          ((ProblemDescriptor)descriptor).getEndElement() == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invalidated psi for " + descriptor);
        }
        return;
      }

      try {
        QuickFixAction.this.applyFix(myProject, myContext, new CommonProblemDescriptor[]{descriptor}, myIgnoredElements);
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
