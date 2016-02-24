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

import com.intellij.codeInsight.FileModificationService;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SequentialModalProgressTask;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class QuickFixAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + QuickFixAction.class.getName());

  public static final QuickFixAction[] EMPTY = new QuickFixAction[0];
  protected final InspectionToolWrapper myToolWrapper;

  public static InspectionResultsView getInvoker(AnActionEvent e) {
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
    final InspectionToolWrapper toolWrapper = tree.getSelectedToolWrapper();
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
  public void actionPerformed(final AnActionEvent e) {
    final InspectionResultsView view = getInvoker(e);
    final InspectionTree tree = view.getTree();
    final CommonProblemDescriptor[] descriptors;
    if (isProblemDescriptorsAcceptable() && (descriptors = tree.getSelectedDescriptors()).length > 0) {
      doApplyFix(view.getProject(), descriptors, tree.getContext());
    } else {
      doApplyFix(getSelectedElements(e), view);
    }
    view.updateRightPanel();
  }


  protected void applyFix(@NotNull Project project,
                          @NotNull GlobalInspectionContextImpl context,
                          @NotNull CommonProblemDescriptor[] descriptors,
                          @NotNull Set<PsiElement> ignoredElements) {
  }

  private void doApplyFix(@NotNull final Project project,
                          @NotNull final CommonProblemDescriptor[] descriptors,
                          @NotNull final GlobalInspectionContextImpl context) {
    final Set<VirtualFile> readOnlyFiles = new THashSet<VirtualFile>();
    for (CommonProblemDescriptor descriptor : descriptors) {
      final PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
      if (psiElement != null && !psiElement.isWritable()) {
        readOnlyFiles.add(psiElement.getContainingFile().getVirtualFile());
      }
    }

    if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(project, readOnlyFiles)) return;
    
    Arrays.sort(descriptors, (c1, c2) -> {
      if (c1 instanceof ProblemDescriptor && c2 instanceof ProblemDescriptor) {
        return PsiUtilCore.compareElementsByPosition(((ProblemDescriptor)c2).getPsiElement(), 
                                                     ((ProblemDescriptor)c1).getPsiElement());
      }
      return c1.getDescriptionTemplate().compareTo(c2.getDescriptionTemplate()); 
    });

    final RefManagerImpl refManager = (RefManagerImpl)context.getRefManager();

    final boolean initial = refManager.isInProcess();

    refManager.inspectionReadActionFinished();

    try {
      final Set<PsiElement> ignoredElements = new HashSet<PsiElement>();

      final String templatePresentationText = getTemplatePresentation().getText();
      assert templatePresentationText != null;
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
          final SequentialModalProgressTask progressTask =
            new SequentialModalProgressTask(project, templatePresentationText, true);
          progressTask.setMinIterationTime(200);
          progressTask.setTask(new PerformFixesTask(project, descriptors, ignoredElements, progressTask, context));
          ProgressManager.getInstance().run(progressTask);
        }
      }, templatePresentationText, null);

      refreshViews(project, ignoredElements, myToolWrapper);
    }
    finally { //to make offline view lazy
      if (initial) refManager.inspectionReadActionStarted();
    }
  }

  public void doApplyFix(@NotNull final RefEntity[] refElements, @NotNull InspectionResultsView view) {
    final RefManagerImpl refManager = (RefManagerImpl)view.getGlobalInspectionContext().getRefManager();

    final boolean initial = refManager.isInProcess();

    refManager.inspectionReadActionFinished();

    try {
      final boolean[] refreshNeeded = {false};
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
        refreshViews(view.getProject(), refElements, myToolWrapper);
      }
    }
    finally {  //to make offline view lazy
      if (initial) refManager.inspectionReadActionStarted();
    }
  }

  public static void removeElements(@NotNull RefEntity[] refElements, @NotNull Project project, @NotNull InspectionToolWrapper toolWrapper) {
    refreshViews(project, refElements, toolWrapper);
    final ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>(1);
    for (RefEntity refElement : refElements) {
      if (!(refElement instanceof RefElement)) continue;
      refElement.getRefManager().removeRefElement((RefElement)refElement, deletedRefs);
    }
  }

  private static Set<VirtualFile> getReadOnlyFiles(@NotNull RefEntity[] refElements) {
    Set<VirtualFile> readOnlyFiles = new THashSet<VirtualFile>();
    for (RefEntity refElement : refElements) {
      PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
      if (psiElement == null || psiElement.getContainingFile() == null) continue;
      readOnlyFiles.add(psiElement.getContainingFile().getVirtualFile());
    }
    return readOnlyFiles;
  }

  private static RefEntity[] getSelectedElements(AnActionEvent e) {
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

    return selection.toArray(new RefEntity[selection.size()]);
  }

  private static void refreshViews(@NotNull Project project, @NotNull Set<PsiElement> selectedElements, @NotNull InspectionToolWrapper toolWrapper) {
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Set<GlobalInspectionContextImpl> runningContexts = managerEx.getRunningContexts();
    for (GlobalInspectionContextImpl context : runningContexts) {
      for (PsiElement element : selectedElements) {
        context.ignoreElement(toolWrapper.getTool(), element);
      }
      context.refreshViews();
    }
  }

  private static void refreshViews(@NotNull Project project, @NotNull RefEntity[] refElements, @NotNull InspectionToolWrapper toolWrapper) {
    final Set<PsiElement> ignoredElements = new HashSet<PsiElement>();
    for (RefEntity element : refElements) {
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

  private class PerformFixesTask extends PerformFixesModalTask {
    @NotNull private final GlobalInspectionContextImpl myContext;
    @NotNull
    private final Set<PsiElement> myIgnoredElements;

    public PerformFixesTask(@NotNull Project project,
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
      QuickFixAction.this.applyFix(myProject, myContext, new CommonProblemDescriptor[]{descriptor}, myIgnoredElements);
    }

    @Override
    public void stop() {
    }
  }
}
