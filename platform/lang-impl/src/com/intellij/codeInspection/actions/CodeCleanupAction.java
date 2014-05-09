/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.IDEInspectionToolsConfigurable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class CodeCleanupAction extends CodeInspectionAction {
  public CodeCleanupAction() {
    super("Code Cleanup", "Code Cleanup");
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    final InspectionProfile profile = myExternalProfile != null ? myExternalProfile : InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final List<LocalInspectionToolWrapper> lTools = new ArrayList<LocalInspectionToolWrapper>();
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final GlobalInspectionContextImpl context = managerEx.createNewGlobalContext(false);

    final LinkedHashMap<PsiFile, List<HighlightInfo>> results = new LinkedHashMap<PsiFile, List<HighlightInfo>>();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Inspect code...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        scope.accept(new PsiElementVisitor() {
          @Override
          public void visitFile(PsiFile file) {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) return;
            for (final Tools tools : profile.getAllEnabledInspectionTools(project)) {
              if (tools.getTool().getTool() instanceof CleanupLocalInspectionTool) {
                final InspectionToolWrapper tool = tools.getEnabledTool(file);
                if (tool instanceof LocalInspectionToolWrapper) {
                  lTools.add((LocalInspectionToolWrapper)tool);
                  tool.initialize(context);
                }
              }
            }

            if (!lTools.isEmpty()) {
              final LocalInspectionsPass pass = new LocalInspectionsPass(file, PsiDocumentManager.getInstance(project).getDocument(file), 0,
                                                                         file.getTextLength(), LocalInspectionsPass.EMPTY_PRIORITY_RANGE, true,
                                                                         HighlightInfoProcessor.getEmpty());
              Runnable runnable = new Runnable() {
                public void run() {
                  pass.doInspectInBatch(context, managerEx, lTools);
                }
              };
              ApplicationManager.getApplication().runReadAction(runnable);
              results.put(file, pass.getInfos());
            }
          }
        });
      }

      @Override
      public void onSuccess() {
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(results.keySet())) return;

        final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, "Code Cleanup", true);
        progressTask.setMinIterationTime(200);
        progressTask.setTask(new SequentialCleanupTask(project, results, progressTask));
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                ProgressManager.getInstance().run(progressTask);
              }
            });
          }
        }, getTemplatePresentation().getText(), null);
      }
    });
  }

  @Override
  protected IDEInspectionToolsConfigurable createConfigurable(InspectionProjectProfileManager projectProfileManager,
                                                              InspectionProfileManager profileManager) {
    return new IDEInspectionToolsConfigurable(projectProfileManager, profileManager) {
      @Override
      protected boolean acceptTool(InspectionProfileEntry entry) {
        return super.acceptTool(entry) && entry instanceof CleanupLocalInspectionTool;
      }
    };
  }
}

class SequentialCleanupTask implements SequentialTask {

  private final Project myProject;
  private final LinkedHashMap<PsiFile, List<HighlightInfo>> myResults;
  private Iterator<PsiFile> myFileIterator;
  private final SequentialModalProgressTask myProgressTask;
  private int myCount = 0;
  
  public SequentialCleanupTask(Project project, LinkedHashMap<PsiFile, List<HighlightInfo>> results, SequentialModalProgressTask task) {
    myProject = project;
    myResults = results;
    myProgressTask = task;
    myFileIterator = myResults.keySet().iterator();
  }

  @Override
  public void prepare() {}

  @Override
  public boolean isDone() {
    return myFileIterator == null || !myFileIterator.hasNext();
  }

  @Override
  public boolean iteration() {
    final ProgressIndicator indicator = myProgressTask.getIndicator();
    if (indicator != null) {
      indicator.setFraction((double) myCount++/myResults.size());
    }
    final PsiFile file = myFileIterator.next();
    final List<HighlightInfo> infos = myResults.get(file);
    Collections.reverse(infos); //sort bottom - top
    for (HighlightInfo info : infos) {
      for (final Pair<HighlightInfo.IntentionActionDescriptor, TextRange> actionRange : info.quickFixActionRanges) {
        actionRange.getFirst().getAction().invoke(myProject, null, file);
      }
    }
    return true;
  }

  @Override
  public void stop() {
    myFileIterator = null;
  }
}
