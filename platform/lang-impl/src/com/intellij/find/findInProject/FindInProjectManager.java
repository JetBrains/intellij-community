// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findInProject;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUsagesCollector;
import com.intellij.find.impl.FindAndReplaceExecutor;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FindInProjectManager {
  private final Project myProject;
  private volatile boolean myIsFindInProgress;

  public static FindInProjectManager getInstance(Project project) {
    return project.getService(FindInProjectManager.class);
  }

  public FindInProjectManager(Project project) {
    myProject = project;
  }

  /**
   *
   * @param model would be used for search if not null, otherwise shared (project-level) model would be used
   */
  public void findInProject(@NotNull DataContext dataContext, @Nullable FindModel model) {
    FindManager findManager = FindManager.getInstance(myProject);
    FindModel findModel;
    boolean stringToFindChanged = false;
    if (model != null) {
      findModel = model.clone();
    }
    else {
      findModel = findManager.getFindInProjectModel().clone();
      String initString = findModel.getStringToFind();
      findModel.setReplaceState(false);
      initModel(findModel, dataContext);
      stringToFindChanged = !Objects.equals(initString, findModel.getStringToFind());
    }

    FindUsagesCollector.findPopupShown(dataContext, findModel, stringToFindChanged);
    findManager.showFindDialog(findModel, () -> {
      FindAndReplaceExecutor.getInstance().performFindAllOrReplaceAll(findModel, myProject);
    });
  }

  public void findInPath(@NotNull FindModel findModel) {
    startFindInProject(findModel);
  }

  protected void initModel(@NotNull FindModel findModel, @NotNull DataContext dataContext) {
    FindInProjectUtil.setScope(myProject, findModel, dataContext);

    String text = PlatformDataKeys.PREDEFINED_TEXT.getData(dataContext);
    if (text != null) {
      FindModel.initStringToFind(findModel, text);
    }
    else {
      FindInProjectUtil.initStringToFindFromDataContext(findModel, dataContext);
    }
  }

  public void startFindInProject(@NotNull FindModel findModel) {
    if (findModel.getDirectoryName() != null && FindInProjectUtil.getDirectory(findModel) == null) {
      return;
    }

    UsageViewManager manager = UsageViewManager.getInstance(myProject);

    if (manager == null) return;
    FindManager findManager = FindManager.getInstance(myProject);
    findManager.getFindInProjectModel().copyFrom(findModel);
    FindModel findModelCopy = findModel.clone();
    UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(findModelCopy);
    FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(presentation);
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
