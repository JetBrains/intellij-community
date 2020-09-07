// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;

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

  @Nullable
  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
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

    DataContext dataContext = e.getDataContext();
    DeleteProvider provider = getDeleteProvider(dataContext);
    if (e.getInputEvent() instanceof KeyEvent) {
      KeyEvent keyEvent = (KeyEvent)e.getInputEvent();
      Object component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      if (component instanceof JTextComponent) provider = null; // Do not override text deletion
      if (keyEvent.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
        // Do not override text deletion in speed search
        if (component instanceof JComponent) {
          SpeedSearchSupply searchSupply = SpeedSearchSupply.getSupply((JComponent)component);
          if (searchSupply != null) provider = null;
        }

        String activeSpeedSearchFilter = SpeedSearchSupply.SPEED_SEARCH_CURRENT_QUERY.getData(dataContext);
        if (!StringUtil.isEmpty(activeSpeedSearchFilter)) {
          provider = null;
        }
      }
    }
    if (provider instanceof TitledHandler) {
      presentation.setText(((TitledHandler)provider).getActionTitle());
    }
    boolean canDelete = provider != null && provider.canDeleteElement(dataContext);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(canDelete);
    }
    else {
      presentation.setEnabled(canDelete);
    }
  }
}