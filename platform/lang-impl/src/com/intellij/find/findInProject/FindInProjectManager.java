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

package com.intellij.find.findInProject;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.FindUtil;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FindInProjectManager {
  private final Project myProject;
  private boolean myToOpenInNewTab = false;
  private volatile boolean myIsFindInProgress = false;

  public static FindInProjectManager getInstance(Project project) {
    return ServiceManager.getService(project, FindInProjectManager.class);
  }

  public FindInProjectManager(Project project) {
    myProject = project;
  }

  public void findInProject(@NotNull DataContext dataContext) {
    final boolean isOpenInNewTabEnabled;
    final boolean[] toOpenInNewTab = new boolean[1];
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    if (selectedContent != null && selectedContent.isPinned()) {
      toOpenInNewTab[0] = true;
      isOpenInNewTabEnabled = false;
    }
    else {
      toOpenInNewTab[0] = myToOpenInNewTab;
      isOpenInNewTabEnabled = UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0;
    }

    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel = (FindModel) findManager.getFindInProjectModel().clone();
    findModel.setReplaceState(false);
    findModel.setOpenInNewTabVisible(true);
    findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
    findModel.setOpenInNewTab(toOpenInNewTab[0]);
    FindInProjectUtil.setDirectoryName(findModel, dataContext);

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    FindUtil.initStringToFindWithSelection(findModel, editor);

    findManager.showFindDialog(findModel, new Runnable() {
      @Override
      public void run() {
        findModel.setOpenInNewTabVisible(false);
        final PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(findModel, myProject);
        if (findModel.getDirectoryName() != null && psiDirectory == null){
          return;
        }
        if (isOpenInNewTabEnabled) {
          myToOpenInNewTab = toOpenInNewTab[0] = findModel.isOpenInNewTab();
        }

        com.intellij.usages.UsageViewManager manager = com.intellij.usages.UsageViewManager.getInstance(myProject);

        if (manager == null) return;
        findManager.getFindInProjectModel().copyFrom(findModel);
        final FindModel findModelCopy = (FindModel)findModel.clone();
        final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(myToOpenInNewTab, findModelCopy);
        final boolean showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage();

        final FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, presentation);
        UsageTarget usageTarget = StringUtil.isEmpty(findModel.getStringToFind()) ? createFileByTypeTarget(findModel)
                                                         : new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind());
        manager.searchAndShowUsages(new UsageTarget[] {usageTarget},
          new Factory<UsageSearcher>() {
            @Override
            public UsageSearcher create() {
              return new UsageSearcher() {
                @Override
                public void generate(@NotNull final Processor<Usage> processor) {
                  myIsFindInProgress = true;

                  try {
                    AdapterProcessor<UsageInfo, Usage> consumer = new AdapterProcessor<UsageInfo, Usage>(processor, UsageInfo2UsageAdapter.CONVERTER);
                    FindInProjectUtil.findUsages(findModelCopy, psiDirectory, myProject, true, consumer, processPresentation);
                  }
                  finally {
                    myIsFindInProgress = false;
                  }
                }
              };
            }
          },
          processPresentation,
          presentation,
          null
        );
      }
    });
    findModel.setOpenInNewTabVisible(false);
  }

  private static UsageTarget createFileByTypeTarget(@NotNull FindModel model) {
    final String filter = model.getFileFilter();
    return new UsageTarget() {
      @Override
      public void findUsages() {
        throw new IncorrectOperationException();
      }

      @Override
      public void findUsagesInEditor(@NotNull FileEditor editor) {
        throw new IncorrectOperationException();
      }

      @Override
      public void highlightUsages(@NotNull PsiFile file, @NotNull Editor editor, boolean clearHighlights) {
        throw new IncorrectOperationException();
      }

      @Override
      public boolean isValid() {
        return true;
      }

      @Override
      public boolean isReadOnly() {
        return false;
      }

      @Nullable
      @Override
      public VirtualFile[] getFiles() {
        return VirtualFile.EMPTY_ARRAY;
      }

      @Override
      public void update() {
      }

      @Nullable
      @Override
      public String getName() {
        return "Files with mask \""+filter+"\"";
      }

      @Nullable
      @Override
      public ItemPresentation getPresentation() {
        return new ItemPresentation() {
          @Nullable
          @Override
          public String getPresentableText() {
            return getName();
          }

          @Nullable
          @Override
          public String getLocationString() {
            return null;
          }

          @Nullable
          @Override
          public Icon getIcon(boolean unused) {
            return null;
          }
        };
      }

      @Override
      public void navigate(boolean requestFocus) {
      }

      @Override
      public boolean canNavigate() {
        return false;
      }

      @Override
      public boolean canNavigateToSource() {
        return false;
      }
    };
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled () {
    return !myIsFindInProgress && !ReplaceInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}
