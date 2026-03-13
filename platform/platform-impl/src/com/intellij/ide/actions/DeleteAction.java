// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class DeleteAction extends AnAction implements DumbAware, LightEditCompatible, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  private static final Logger LOG = Logger.getInstance(DeleteAction.class);

  public DeleteAction() { }

  @SuppressWarnings("ActionPresentationInstantiatedInCtor")
  public DeleteAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var dataContext = e.getDataContext();
    var provider = getDeleteProvider(dataContext);
    if (provider == null) return;
    try {
      provider.deleteElement(dataContext);
    }
    catch (Throwable t) {
      LOG.error(t);
    }
  }

  protected @Nullable DeleteProvider getDeleteProvider(DataContext dataContext) {
    return PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getData(dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var presentation = e.getPresentation();
    presentation.putClientProperty(ActionRemoteBehavior.SKIP_FALLBACK_UPDATE, null);

    if (e.isFromContextMenu()) {
      presentation.setText(IdeBundle.messagePointer("action.delete.ellipsis"));
    }
    else {
      presentation.setText(IdeBundle.messagePointer("action.delete"));
    }

    if (e.isFromActionToolbar() && presentation.getIcon() == null) {
      presentation.setIcon(IconUtil.getRemoveIcon());
    }

    if (e.getProject() == null) {
      presentation.setEnabled(false);
      return;
    }

    CopyAction.updateWithProvider(e, getDeleteProvider(e.getDataContext()), false, provider -> {
      // if a provider is found on the frontend, don't look for it on the backend
      presentation.putClientProperty(ActionRemoteBehavior.SKIP_FALLBACK_UPDATE, true);
      var isPopupPlace = e.isFromContextMenu();
      var enabled = provider != null && provider.canDeleteElement(e.getDataContext());
      presentation.setEnabled(enabled);
      presentation.setVisible(!isPopupPlace || enabled);
      if (provider instanceof TitledHandler th) {
        presentation.setText(th.getActionTitle());
      }
    });
   }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
