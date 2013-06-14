/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class QuickFixAction extends AnAction {
  protected InspectionTool myTool;

  public static InspectionResultsView getInvoker(AnActionEvent e) {
    return InspectionResultsView.DATA_KEY.getData(e.getDataContext());
  }

  protected QuickFixAction(String text, @NotNull InspectionTool tool) {
    this(text, AllIcons.Actions.CreateFromUsage, null, tool);
  }

  protected QuickFixAction(String text, Icon icon, KeyStroke keyStroke, @NotNull InspectionTool tool) {
    super(text, null, icon);
    myTool = tool;
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

    final InspectionTree tree = view.getTree();
    final InspectionTool tool = tree.getSelectedTool();
    if (!view.isSingleToolInSelection() || tool != myTool) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
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

  public String getText(RefEntity where) {
    return getTemplatePresentation().getText();
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final InspectionResultsView view = getInvoker(e);
    final InspectionTree tree = view.getTree();
    if (isProblemDescriptorsAcceptable()) {
      final CommonProblemDescriptor[] descriptors = tree.getSelectedDescriptors();
      if (descriptors.length > 0) {
        doApplyFix(view.getProject(), descriptors);
        return;
      }
    }

    doApplyFix(getSelectedElements(e), view);
  }


  protected void applyFix(@NotNull Project project,
                          @NotNull CommonProblemDescriptor[] descriptors,
                          @NotNull Set<PsiElement> ignoredElements) {
  }

  private void doApplyFix(@NotNull final Project project, @NotNull final CommonProblemDescriptor[] descriptors) {
    final Set<VirtualFile> readOnlyFiles = new THashSet<VirtualFile>();
    for (CommonProblemDescriptor descriptor : descriptors) {
      final PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
      if (psiElement != null && !psiElement.isWritable()) {
        readOnlyFiles.add(psiElement.getContainingFile().getVirtualFile());
      }
    }

    if (!readOnlyFiles.isEmpty()) {
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VfsUtil.toVirtualFileArray(readOnlyFiles));
      if (operationStatus.hasReadonlyFiles()) return;
    }

    final RefManagerImpl refManager = (RefManagerImpl)myTool.getContext().getRefManager();

    final boolean initial = refManager.isInProcess();

    refManager.inspectionReadActionFinished();

    try {
      final Set<PsiElement> ignoredElements = new HashSet<PsiElement>();

      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              final SequentialModalProgressTask progressTask =
                new SequentialModalProgressTask(project, getTemplatePresentation().getText(), false);
              progressTask.setMinIterationTime(200);
              progressTask.setTask(new PerformFixesTask(project, descriptors, ignoredElements, progressTask));
              ProgressManager.getInstance().run(progressTask);
            }
          });
        }
      }, getTemplatePresentation().getText(), null);

      refreshViews(project, ignoredElements, myTool);
    }
    finally { //to make offline view lazy
      if (initial) refManager.inspectionReadActionStarted();
    }
  }

  public void doApplyFix(final RefElement[] refElements, InspectionResultsView view) {
    final RefManagerImpl refManager = (RefManagerImpl)myTool.getContext().getRefManager();

    final boolean initial = refManager.isInProcess();

    refManager.inspectionReadActionFinished();

    try {
      final boolean[] refreshNeeded = new boolean[]{false};
      if (refElements.length > 0) {
        final Project project = refElements[0].getRefManager().getProject();
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                refreshNeeded[0] = applyFix(refElements);
              }
            });
          }
        }, getTemplatePresentation().getText(), null);
      }
      if (refreshNeeded[0]) {
        refreshViews(view.getProject(), refElements, myTool);
      }
    }
    finally {  //to make offline view lazy
      if (initial) refManager.inspectionReadActionStarted();
    }
  }

  public static void removeElements(final RefElement[] refElements, final Project project, final InspectionTool tool) {
    refreshViews(project, refElements, tool);
    final ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>(1);
    for (RefElement refElement : refElements) {
      if (refElement == null) continue;
      refElement.getRefManager().removeRefElement(refElement, deletedRefs);
    }
  }

  private static Set<VirtualFile> getReadOnlyFiles(final RefElement[] refElements) {
    Set<VirtualFile> readOnlyFiles = new THashSet<VirtualFile>();
    for (RefElement refElement : refElements) {
      PsiElement psiElement = refElement.getElement();
      if (psiElement == null || psiElement.getContainingFile() == null) continue;
      readOnlyFiles.add(psiElement.getContainingFile().getVirtualFile());
    }
    return readOnlyFiles;
  }

  private static RefElement[] getSelectedElements(AnActionEvent e) {
    final InspectionResultsView invoker = getInvoker(e);
    if (invoker == null) return new RefElement[0];
    List<RefEntity> selection = new ArrayList<RefEntity>(Arrays.asList(invoker.getTree().getSelectedElements()));
    PsiDocumentManager.getInstance(invoker.getProject()).commitAllDocuments();
    Collections.sort(selection, new Comparator<RefEntity>() {
      @Override
      public int compare(RefEntity o1, RefEntity o2) {
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
            } else if (i1 > i2){
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
      }
    });

    return selection.toArray(new RefElement[selection.size()]);
  }

  private static void refreshViews(final Project project, final Set<PsiElement> selectedElements, final InspectionTool tool) {
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Set<GlobalInspectionContextImpl> runningContexts = managerEx.getRunningContexts();
    for (GlobalInspectionContextImpl context : runningContexts) {
      for (PsiElement element : selectedElements) {
        context.ignoreElement(tool, element);
      }
      context.refreshViews();
    }
  }

  private static void refreshViews(final Project project, final RefElement[] refElements, final InspectionTool tool) {
    final Set<PsiElement> ignoredElements = new HashSet<PsiElement>();
    for (RefElement element : refElements) {
      final PsiElement psiElement = element != null ? element.getElement() : null;
      if (psiElement != null && psiElement.isValid()) {
        ignoredElements.add(psiElement);
      }
    }
    refreshViews(project, ignoredElements, tool);
  }

  /**
   * @return true if immediate UI update needed.
   */
  protected boolean applyFix(RefElement[] refElements) {
    Set<VirtualFile> readOnlyFiles = getReadOnlyFiles(refElements);
    if (!readOnlyFiles.isEmpty()) {
      final Project project = refElements[0].getRefManager().getProject();
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VfsUtil.toVirtualFileArray(readOnlyFiles));
      if (operationStatus.hasReadonlyFiles()) return false;
    }
    return true;
  }

  private class PerformFixesTask implements SequentialTask {
    @NotNull
    private final Project myProject;
    private final CommonProblemDescriptor[] myDescriptors;
    @NotNull
    private final Set<PsiElement> myIgnoredElements;
    private final SequentialModalProgressTask myTask;
    private int myCount = 0;

    public PerformFixesTask(@NotNull Project project,
                            @NotNull CommonProblemDescriptor[] descriptors,
                            @NotNull Set<PsiElement> ignoredElements,
                            @NotNull SequentialModalProgressTask task) {
      myProject = project;
      myDescriptors = descriptors;
      myIgnoredElements = ignoredElements;
      myTask = task;
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return myCount > myDescriptors.length - 1;
    }

    @Override
    public boolean iteration() {
      final CommonProblemDescriptor descriptor = myDescriptors[myCount++];
      ProgressIndicator indicator = myTask.getIndicator();
      if (indicator != null) {
        indicator.setFraction((double)myCount / myDescriptors.length);
        if (descriptor instanceof ProblemDescriptor) {
          final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
          if (psiElement != null) {
            indicator.setText("Processing " + SymbolPresentationUtil.getSymbolPresentableText(psiElement));
          }
        }
      }
      applyFix(myProject, new CommonProblemDescriptor[]{descriptor}, myIgnoredElements);
      return isDone();
    }

    @Override
    public void stop() {
    }
  }
}
