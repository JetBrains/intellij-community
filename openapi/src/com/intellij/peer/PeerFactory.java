/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.peer;

import com.intellij.execution.runners.ProcessProxyFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.ui.UIHelper;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.errorView.ErrorViewFactory;

public abstract class PeerFactory {
  public static PeerFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(PeerFactory.class);
  }

  public abstract FileStatusFactory getFileStatusFactory();
  public abstract DialogWrapperPeerFactory getDialogWrapperPeerFactory();

  public abstract ProcessProxyFactory getProcessProxyFactory();

  public abstract PackageSetFactory getPackageSetFactory();

  public abstract UIHelper getUIHelper();

  public abstract ErrorViewFactory getErrorViewFactory();

  public abstract ContentFactory getContentFactory();

  public abstract FileSystemTreeFactory getFileSystemTreeFactory();

  public abstract DiffRequestFactory getDiffRequestFactory();

  public abstract VcsContextFactory getVcsContextFactory();
}