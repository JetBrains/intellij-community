// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class EntireFileHistoryDialogModel extends FileHistoryDialogModel {
  public EntireFileHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    super(p, gw, vcs, f);
  }

  @Override
  public FileDifferenceModel getDifferenceModel() {
    return new EntireFileDifferenceModel(myProject, myGateway, getLeftEntry(), getRightEntry(), isCurrentRevisionSelected());
  }
}