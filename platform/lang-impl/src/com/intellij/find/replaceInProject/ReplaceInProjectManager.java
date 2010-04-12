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

package com.intellij.find.replaceInProject;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReplaceInProjectManager {
  private final Project myProject;
  private boolean myIsFindInProgress = false;

  public static ReplaceInProjectManager getInstance(Project project) {
    return ServiceManager.getService(project, ReplaceInProjectManager.class);
  }

  public ReplaceInProjectManager(Project project) {
    myProject = project;
  }

  static class ReplaceContext {
    private final UsageView usageView;
    private final FindModel findModel;
    private Set<Usage> excludedSet;

    ReplaceContext(@NotNull UsageView usageView, @NotNull FindModel findModel) {
      this.usageView = usageView;
      this.findModel = findModel;
    }

    @NotNull
    public FindModel getFindModel() {
      return findModel;
    }

    @NotNull
    public UsageView getUsageView() {
      return usageView;
    }

    @NotNull
    public Set<Usage> getExcludedSet() {
      if (excludedSet == null) excludedSet = usageView.getExcludedUsages();
      return excludedSet;
    }
  }

  public void replaceInProject(DataContext dataContext) {
    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel = (FindModel)findManager.getFindInProjectModel().clone();
    findModel.setReplaceState(true);
    FindInProjectUtil.setDirectoryName(findModel, dataContext);

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      String s = editor.getSelectionModel().getSelectedText();
      if (s != null && !s.contains("\r") && !s.contains("\n")) {
        findModel.setStringToFind(s);
      }
    }

    findManager.showFindDialog(findModel, new Runnable() {
      public void run() {
        final PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(findModel, myProject);
        if (!findModel.isProjectScope() &&
            psiDirectory == null &&
            findModel.getModuleName() == null &&
            findModel.getCustomScope() == null) {
          return;
        }

        UsageViewManager manager = UsageViewManager.getInstance(myProject);

        if (manager == null) return;
        findManager.getFindInProjectModel().copyFrom(findModel);
        final FindModel findModelCopy = (FindModel)findModel.clone();

        searchAndShowUsages(manager, new UsageSearcherFactory(findModelCopy, psiDirectory), findModelCopy, findManager);
      }
    });
  }

  public void searchAndShowUsages(@NotNull UsageViewManager manager,
                                  final Factory<UsageSearcher> usageSearcherFactory,
                                  final FindModel findModelCopy,
                                  final FindManager findManager) {
    final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(true, findModelCopy);
    final FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(myProject, true, presentation);

    searchAndShowUsages(manager, usageSearcherFactory, findModelCopy, presentation, processPresentation, findManager);
  }

  public void searchAndShowUsages(UsageViewManager manager,
                                  final Factory<UsageSearcher> usageSearcherFactory,
                                  final FindModel findModelCopy,
                                  UsageViewPresentation presentation,
                                  FindUsagesProcessPresentation processPresentation,
                                  final FindManager findManager) {
    final ReplaceContext[] context = new ReplaceContext[1];

    manager.searchAndShowUsages(new UsageTarget[]{new FindInProjectUtil.StringUsageTarget(findModelCopy.getStringToFind())},
                                usageSearcherFactory, processPresentation, presentation, new UsageViewManager.UsageViewStateListener() {
        public void usageViewCreated(UsageView usageView) {
          context[0] = new ReplaceContext(usageView, findModelCopy);
          addReplaceActions(context[0]);
        }

        public void findingUsagesFinished(final UsageView usageView) {
          if (context[0] != null && findManager.getFindInProjectModel().isPromptOnReplace()) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                replaceWithPrompt(context[0]);
              }
            });
          }
        }
      });
  }

  private void replaceWithPrompt(final ReplaceContext replaceContext) {
    final List<Usage> _usages = replaceContext.getUsageView().getSortedUsages();

    if (FindInProjectUtil.hasReadOnlyUsages(_usages)) {
      WindowManager.getInstance().getStatusBar(myProject)
        .setInfo(FindBundle.message("find.replace.occurrences.found.in.read.only.files.status"));
      return;
    }

    final Usage[] usages = _usages.toArray(new Usage[_usages.size()]);

    //usageView.expandAll();
    for (int i = 0; i < usages.length; ++i) {
      final Usage usage = usages[i];
      final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();

      final PsiElement elt = usageInfo.getElement();
      if (elt == null) continue;
      final PsiFile psiFile = elt.getContainingFile();
      if (!psiFile.isWritable()) continue;

      Runnable selectOnEditorRunnable = new Runnable() {
        public void run() {
          final VirtualFile virtualFile = psiFile.getVirtualFile();

          if (virtualFile != null && ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            public Boolean compute() {
              return virtualFile.isValid() ? Boolean.TRUE : Boolean.FALSE;
            }
          }).booleanValue()) {

            if (usage.isValid()) {
              usage.highlightInEditor();
              replaceContext.getUsageView().selectUsages(new Usage[]{usage});
            }
          }
        }
      };

      CommandProcessor.getInstance()
        .executeCommand(myProject, selectOnEditorRunnable, FindBundle.message("find.replace.select.on.editor.command"), null);
      String title = FindBundle.message("find.replace.found.usage.title", i + 1, usages.length);
      int result = FindManager.getInstance(myProject).showPromptDialog(replaceContext.getFindModel(), title);

      if (result == FindManager.PromptResult.CANCEL) {
        return;
      }
      if (result == FindManager.PromptResult.SKIP) {
        continue;
      }

      final int currentNumber = i;
      if (result == FindManager.PromptResult.OK) {
        Runnable runnable = new Runnable() {
          public void run() {
            doReplace(replaceContext, usage);
            replaceContext.getUsageView().removeUsage(usage);
          }
        };
        CommandProcessor.getInstance().executeCommand(myProject, runnable, FindBundle.message("find.replace.command"), null);
        if (i + 1 == usages.length) {
          replaceContext.getUsageView().close();
          return;
        }
      }

      if (result == FindManager.PromptResult.ALL_IN_THIS_FILE) {
        final int[] nextNumber = new int[1];

        Runnable runnable = new Runnable() {
          public void run() {
            int j = currentNumber;

            for (; j < usages.length; j++) {
              final Usage usage = usages[j];
              final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();

              final PsiElement elt = usageInfo.getElement();
              if (elt == null) continue;
              PsiFile otherPsiFile = elt.getContainingFile();
              if (!otherPsiFile.equals(psiFile)) {
                break;
              }
              doReplace(replaceContext, usage);
              replaceContext.getUsageView().removeUsage(usage);
            }
            if (j == usages.length) {
              replaceContext.getUsageView().close();
            }
            nextNumber[0] = j;
          }
        };

        CommandProcessor.getInstance().executeCommand(myProject, runnable, FindBundle.message("find.replace.command"), null);

        //noinspection AssignmentToForLoopParameter
        i = nextNumber[0] - 1;
      }

      if (result == FindManager.PromptResult.ALL_FILES) {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            doReplace(replaceContext, _usages);
            replaceContext.getUsageView().close();
          }
        }, FindBundle.message("find.replace.command"), null);
        break;
      }
    }
  }

  private void addReplaceActions(final ReplaceContext replaceContext) {
    final Runnable replaceRunnable = new Runnable() {
      public void run() {
        doReplace(replaceContext, replaceContext.getUsageView().getUsages());
      }
    };
    replaceContext.getUsageView().addPerformOperationAction(replaceRunnable, FindBundle.message("find.replace.all.action"), null,
                                                            FindBundle.message("find.replace.all.action.description"));

    final Runnable replaceSelectedRunnable = new Runnable() {
      public void run() {
        doReplaceSelected(replaceContext);
      }
    };

    replaceContext.getUsageView().addButtonToLowerPane(replaceSelectedRunnable, FindBundle.message("find.replace.selected.action"));
  }

  private void doReplace(final ReplaceContext replaceContext, Collection<Usage> usages) {
    for (final Usage usage : usages) {
      doReplace(replaceContext, usage);
    }
    reportNumberReplacedOccurences(myProject, usages.size());
  }

  public static void reportNumberReplacedOccurences(Project project, int occurrences) {
    if (occurrences != 0) {
      WindowManager.getInstance().getStatusBar(project).setInfo(FindBundle.message("0.occurrences.replaced", occurrences));
    }
  }

  private void doReplace(final ReplaceContext replaceContext, final Usage usage) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (replaceContext.getExcludedSet().contains(usage)) {
          return;
        }

        List<RangeMarker> markers = ((UsageInfo2UsageAdapter)usage).getRangeMarkers();
        for (RangeMarker marker : markers) {
          Document document = marker.getDocument();
          if (!document.isWritable()) return;

          final int textOffset = marker.getStartOffset();
          if (textOffset < 0 || textOffset >= document.getTextLength()) {
            return;
          }
          final int textEndOffset = marker.getEndOffset();
          if (textEndOffset < 0 || textOffset > document.getTextLength()) {
            return;
          }
          FindManager findManager = FindManager.getInstance(myProject);
          final CharSequence foundString = document.getCharsSequence().subSequence(textOffset, textEndOffset);
          FindResult findResult = findManager.findString(document.getCharsSequence(), textOffset, replaceContext.getFindModel());
          if (!findResult.isStringFound()) {
            return;
          }
          String stringToReplace =
            findManager.getStringToReplace(foundString.toString(), replaceContext.getFindModel(), textOffset, document.getText());
          if (stringToReplace != null) {
            document.replaceString(textOffset, textEndOffset, stringToReplace);
          }
        }
      }
    });
  }

  private void doReplaceSelected(final ReplaceContext replaceContext) {
    final Set<Usage> selectedUsages = replaceContext.getUsageView().getSelectedUsages();
    if (selectedUsages == null) {
      return;
    }

    Set<VirtualFile> readOnlyFiles = null;
    for (final Usage usage : selectedUsages) {
      final VirtualFile file = ((UsageInFile)usage).getFile();

      if (!file.isWritable()) {
        if (readOnlyFiles == null) readOnlyFiles = new HashSet<VirtualFile>();
        readOnlyFiles.add(file);
      }
    }

    if (readOnlyFiles != null) {
      ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(VfsUtil.toVirtualFileArray(readOnlyFiles));
    }

    if (FindInProjectUtil.hasReadOnlyUsages(selectedUsages)) {
      int result = Messages.showOkCancelDialog(replaceContext.getUsageView().getComponent(),
                                               FindBundle.message("find.replace.occurrences.in.read.only.files.prompt"),
                                               FindBundle.message("find.replace.occurrences.in.read.only.files.title"),
                                               Messages.getWarningIcon());
      if (result != 0) {
        return;
      }
    }

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        doReplace(replaceContext, selectedUsages);
        for (final Usage selectedUsage : selectedUsages) {
          replaceContext.getUsageView().removeUsage(selectedUsage);
        }

        if (replaceContext.getUsageView().getUsages().isEmpty()) {
          replaceContext.getUsageView().close();
          return;
        }
        replaceContext.getUsageView().getComponent().requestFocus();
      }
    }, FindBundle.message("find.replace.command"), null);
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled() {
    return !myIsFindInProgress && !FindInProjectManager.getInstance(myProject).isWorkInProgress();
  }

  private class UsageSearcherFactory implements Factory<UsageSearcher> {
    private final FindModel myFindModelCopy;
    private final PsiDirectory myPsiDirectory;

    public UsageSearcherFactory(FindModel findModelCopy, PsiDirectory psiDirectory) {
      myFindModelCopy = findModelCopy;
      myPsiDirectory = psiDirectory;
    }

    public UsageSearcher create() {
      return new UsageSearcher() {

        public void generate(final Processor<Usage> processor) {
          try {
            myIsFindInProgress = true;

            FindInProjectUtil.findUsages(myFindModelCopy, myPsiDirectory, myProject,
                                         new AdapterProcessor<UsageInfo, Usage>(processor, UsageInfo2UsageAdapter.CONVERTER));
          }
          finally {
            myIsFindInProgress = false;
          }
        }
      };
    }
  }
}
