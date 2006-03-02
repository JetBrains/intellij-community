/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.javaee.model.ElementPresentation;
import com.intellij.javaee.model.ElementPresentationManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ClassChooser;
import com.intellij.util.xml.ClassChooserManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.DomCollectionPanel;

import javax.swing.*;

/**
 * User: Sergey.Vasiliev
 */
public abstract class AddDomElementAction<T extends DomElement> extends AnAction {
  public AddDomElementAction() {
    super(ApplicationBundle.message("action.add"), null, DomCollectionPanel.ADD_ICON);
  }

  public void update(AnActionEvent e) {
    if (! isEnabled(e)) return;

    boolean enabled = false;
    final AnAction[] actions = createAdditionActions(e);
    for (final AnAction action : actions) {
      action.update(e);
      if (e.getPresentation().isEnabled()) {
        enabled = true;
      }
    }
    e.getPresentation().setEnabled(enabled);

    e.getPresentation().setText(getActionText(e) + (actions.length > 1 ? "..." : ""));

    super.update(e);
  }

  protected String getActionText(final AnActionEvent e) {
    return e.getPresentation().getText();
  }

  protected boolean isEnabled(final AnActionEvent e) {
    return true;
  }

  public void actionPerformed(AnActionEvent e) {
    final AnAction[] actions = createAdditionActions(e);
    if (actions.length > 1) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final AnAction action : actions) {
        group.add(action);
      }

      final DataContext dataContext = e.getDataContext();
      final ListPopup groupPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(null,//J2EEBundle.message("label.menu.title.add.activation.config.property"),
                                                            group, dataContext, JBPopupFactory.ActionSelectionAid.NUMBERING, true);

      showPopup(groupPopup, e);
    }
    else {
      actions[0].actionPerformed(e);
    }
  }

  protected void showPopup(final ListPopup groupPopup, final AnActionEvent e) {
    groupPopup.showUnderneathOf(e.getInputEvent().getComponent());
  }

  protected AnAction[] createAdditionActions(final AnActionEvent e) {
    final ClassChooser chooser = ClassChooserManager.getClassChooser(DomUtil.getRawType(getDomCollectionChildDescription(e).getType()));
    return ContainerUtil.map2Array(chooser.getChooserClasses(), AnAction.class, new Function<Class, AnAction>() {
      public AnAction fun(final Class s) {
        final ElementPresentation presentation = ElementPresentationManager.getPresentationForClass(s);

        final String name = presentation == null ? s.getSimpleName() : presentation.getElementName();
        final Icon icon = presentation == null ? null : presentation.getIcon();

        return createDefaultAction(e, name, icon, s);
      }
    });
  }

  abstract protected DefaultAddAction createDefaultAction(final AnActionEvent e, final String name, final Icon icon, final Class s);

  protected abstract DomCollectionChildDescription getDomCollectionChildDescription(final AnActionEvent e);

  protected abstract T getParentDomElement(final AnActionEvent e);

}
