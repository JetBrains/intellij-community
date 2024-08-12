// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@SuppressWarnings("WeakerAccess")
public final class FindUIHelper implements Disposable {
  private final @NotNull Project myProject;
  private @NotNull FindModel myModel;
  FindModel myPreviousModel;
  private @NotNull Runnable myOkHandler;

  FindUI myUI;

  FindUIHelper(@NotNull Project project, @NotNull FindModel model, @NotNull Runnable okHandler) {
    myProject = project;
    myModel = model;
    myOkHandler = okHandler;
    myUI = getOrCreateUI();
    myUI.initByModel();
  }

  private FindUI getOrCreateUI() {
    if (myUI == null) {
      JComponent component;
      FindPopupPanel panel = new FindPopupPanel(this);
      component = panel;
      myUI = panel;

      registerAction("ReplaceInPath", true, component, myUI);
      registerAction("FindInPath", false, component, myUI);
      Disposer.register(myUI.getDisposable(), this);
    }
    return myUI;
  }

  private void registerAction(String actionName, boolean replace, JComponent component, FindUI ui) {
    AnAction action = ActionManager.getInstance().getAction(actionName);
    new AnAction() {
      @Override
      public boolean isDumbAware() {
        return action.isDumbAware();
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ui.saveSettings();
        myModel.copyFrom(FindManager.getInstance(myProject).getFindInProjectModel());
        FindUtil.initStringToFindWithSelection(myModel, e.getData(CommonDataKeys.EDITOR));
        myModel.setReplaceState(replace);
        ui.initByModel();
      }
    }.registerCustomShortcutSet(action.getShortcutSet(), component);
  }


  boolean canSearchThisString() {
    return myUI != null && (!StringUtil.isEmpty(myUI.getStringToFind()) || !myModel.isReplaceState() && !myModel.isFindAllEnabled() && myUI.getFileTypeMask() != null);
  }


  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull FindModel getModel() {
    return myModel;
  }

  public void setModel(@NotNull FindModel model) {
    myModel = model;
    myUI.initByModel();
  }

  public void setOkHandler(@NotNull Runnable okHandler) {
    myOkHandler = okHandler;
  }

  public void showUI() {
    myUI = getOrCreateUI();
    myUI.showUI();
  }

  public void closeUI() {
    if (myUI != null) {
      myUI.closeIfPossible();
    }
  }

  @Override
  public void dispose() {
    if (myUI != null && !Disposer.isDisposed(myUI.getDisposable())) {
      Disposer.dispose(myUI.getDisposable());
    }
    myUI = null;
  }

  void updateFindSettings() {
    ((FindManagerImpl)FindManager.getInstance(myProject)).changeGlobalSettings(myModel);
    FindSettings findSettings = FindSettings.getInstance();
    findSettings.setCaseSensitive(myModel.isCaseSensitive());
    if (myModel.isReplaceState()) {
      findSettings.setPreserveCase(myModel.isPreserveCase());
    }

    findSettings.setWholeWordsOnly(myModel.isWholeWordsOnly());
    findSettings.setInStringLiteralsOnly(false);
    findSettings.setInCommentsOnly(false);
    findSettings.setExceptComments(false);
    findSettings.setExceptStringLiterals(false);
    findSettings.setExceptCommentsAndLiterals(false);

    findSettings.setRegularExpressions(myModel.isRegularExpressions());
    if (!myModel.isMultipleFiles()){
      findSettings.setForward(myModel.isForward());
      findSettings.setFromCursor(myModel.isFromCursor());

      findSettings.setGlobal(myModel.isGlobal());
    } else{
      String directoryName = myModel.getDirectoryName();
      if (directoryName != null && !directoryName.isEmpty()) {
        findSettings.setWithSubdirectories(myModel.isWithSubdirectories());
      }
      else if (!StringUtil.isEmpty(myModel.getModuleName())) {
        //do nothing here
      }
      else if (myModel.getCustomScopeName() != null) {
        findSettings.setCustomScope(myModel.getCustomScopeName());
      }
    }

    findSettings.setFileMask(myModel.getFileFilter());
  }

  @Nls String getTitle() {
    if (myModel.isReplaceState()){
      return myModel.isMultipleFiles()
             ? FindBundle.message("find.replace.in.project.dialog.title")
             : FindBundle.message("find.replace.text.dialog.title");
    }
    return myModel.isMultipleFiles() ?
           FindBundle.message("find.in.path.dialog.title") :
           FindBundle.message("find.text.dialog.title");
  }

  public boolean isReplaceState() {
    return myModel.isReplaceState();
  }

  public @NotNull Runnable getOkHandler() {
    return myOkHandler;
  }

  public void doOKAction() {
    myOkHandler.run();
  }
}
