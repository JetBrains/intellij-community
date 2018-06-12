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

package com.intellij.find.findInProject;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FindInProjectManager {
  private final Project myProject;
  private volatile boolean myIsFindInProgress;

  public static FindInProjectManager getInstance(Project project) {
    return ServiceManager.getService(project, FindInProjectManager.class);
  }

  public FindInProjectManager(Project project) {
    myProject = project;
  }

  /**
   *
   * @param model would be used for search if not null, otherwise shared (project-level) model would be used
   */
  public void findInProject(@NotNull DataContext dataContext, @Nullable FindModel model) {
    final boolean isOpenInNewTabEnabled;
    final boolean toOpenInNewTab;
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    if (selectedContent != null && selectedContent.isPinned()) {
      toOpenInNewTab = true;
      isOpenInNewTabEnabled = false;
    }
    else {
      toOpenInNewTab = FindSettings.getInstance().isShowResultsInSeparateView();
      isOpenInNewTabEnabled = UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0;
    }

    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel;
    if (model != null) {
      findModel = model.clone();
    }
    else {
      findModel = findManager.getFindInProjectModel().clone();
      findModel.setReplaceState(false);
      findModel.setOpenInNewTabVisible(true);
      findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
      findModel.setOpenInNewTab(toOpenInNewTab);
      initModel(findModel, dataContext);
    }

    findManager.showFindDialog(findModel, () -> {
      findModel.setOpenInNewTabVisible(false);
      if (isOpenInNewTabEnabled) {
        FindSettings.getInstance().setShowResultsInSeparateView(findModel.isOpenInNewTab());
      }
      startFindInProject(findModel);
      findModel.setOpenInNewTabVisible(false); //todo check it in both cases: dialog & popup
    });
  }

  @SuppressWarnings("WeakerAccess")
  protected void initModel(@NotNull FindModel findModel, @NotNull DataContext dataContext) {
    FindInProjectUtil.setDirectoryName(findModel, dataContext);

    String text = PlatformDataKeys.PREDEFINED_TEXT.getData(dataContext);
    if (text != null) {
      FindModel.initStringToFindNoMultiline(findModel, text);
    }
    else {
      FindInProjectUtil.initStringToFindFromDataContext(findModel, dataContext);
    }
  }

  public void startFindInProject(@NotNull FindModel findModel) {
    if (findModel.getDirectoryName() != null && FindInProjectUtil.getDirectory(findModel) == null) {
      return;
    }

    com.intellij.usages.UsageViewManager manager = com.intellij.usages.UsageViewManager.getInstance(myProject);

    if (manager == null) return;
    final FindManager findManager = FindManager.getInstance(myProject);
    findManager.getFindInProjectModel().copyFrom(findModel);
    final FindModel findModelCopy = findModel.clone();
    final UsageViewPresentation presentation =
      FindInProjectUtil.setupViewPresentation(FindSettings.getInstance().isShowResultsInSeparateView(), findModelCopy);
    final boolean showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage();

    final FindUsagesProcessPresentation processPresentation =
      FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, presentation);
    ConfigurableUsageTarget usageTarget = new FindInProjectUtil.StringUsageTarget(myProject, findModel);

    ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager().addToHistory(usageTarget);

    manager.searchAndShowUsages(new UsageTarget[]{usageTarget},
                                () -> processor -> {
                                  myIsFindInProgress = true;

                                  try {
                                    Processor<UsageInfo> consumer = info -> {
                                      Usage usage = UsageInfo2UsageAdapter.CONVERTER.fun(info);
                                      usage.getPresentation().getIcon(); // cache icon
                                      return processor.process(usage);
                                    };
                                    FindInProjectUtil.findUsages(findModelCopy, myProject, consumer, processPresentation);
                                  }
                                  finally {
                                    myIsFindInProgress = false;
                                  }
                                },
                                processPresentation,
                                presentation,
                                null
    );
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled() {
    return !myIsFindInProgress && !ReplaceInProjectManager.getInstance(myProject).isWorkInProgress();
  }
}
