// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeleteAction extends AnAction implements DumbAware, LightEditCompatible {
  private static final Logger LOG = Logger.getInstance(DeleteAction.class);

  public DeleteAction() { }

  public DeleteAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    DeleteProvider provider = getDeleteProvider(dataContext);
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
    Presentation presentation = e.getPresentation();

    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setText(IdeBundle.messagePointer("action.delete.ellipsis"));
    }
    else {
      presentation.setText(IdeBundle.messagePointer("action.delete"));
    }
    if (e.isFromActionToolbar() && e.getPresentation().getIcon() == null) {
      e.getPresentation().setIcon(IconUtil.getRemoveIcon());
    }

    if (e.getProject() == null) {
      presentation.setEnabled(false);
      return;
    }

    CopyAction.updateWithProvider(e, getDeleteProvider(e.getDataContext()), false, provider -> {
      boolean isPopupPlace = ActionPlaces.isPopupPlace(e.getPlace());
      boolean enabled = provider.canDeleteElement(e.getDataContext());
      presentation.setEnabled(enabled);
      presentation.setVisible(!isPopupPlace || enabled);
      if (provider instanceof TitledHandler) {
        presentation.setText(((TitledHandler)provider).getActionTitle());
      }
    });
   }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}