/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.xml.*;
import com.intellij.util.xml.ui.DomCollectionControl;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public abstract class AddDomElementAction extends ActionGroup {

  private final static ShortcutSet shortcutSet = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));

  public AddDomElementAction() {
    super(ApplicationBundle.message("action.add"), false);
  }

  public void update(AnActionEvent e) {
    if (! isEnabled(e)) return;

    boolean enabled = false;
    final AnAction[] actions = getChildren(e);
    for (final AnAction action : actions) {
      action.update(e);
      if (e.getPresentation().isEnabled()) {
        enabled = true;
      }
    }
    e.getPresentation().setEnabled(enabled);

    e.getPresentation().setText(getActionText(e) + (actions.length > 1 ? "..." : ""));
    e.getPresentation().setIcon(DomCollectionControl.ADD_ICON);

    super.update(e);
  }

  protected String getActionText(final AnActionEvent e) {
    return e.getPresentation().getText();
  }

  protected boolean isEnabled(final AnActionEvent e) {
    return true;
  }
/*
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
*/
  protected void showPopup(final ListPopup groupPopup, final AnActionEvent e) {
    groupPopup.showUnderneathOf(e.getInputEvent().getComponent());
  }

  public AnAction[] getChildren(final AnActionEvent e) {
    DomCollectionChildDescription[] descriptions = getDomCollectionChildDescriptions(e);
    List<AnAction> actions = new ArrayList<AnAction>();
    for (DomCollectionChildDescription description: descriptions) {
      final ClassChooser chooser = ClassChooserManager.getClassChooser(DomUtil.getRawType(description.getType()));
      for (Class clazz: chooser.getChooserClasses()) {
        String name = ElementPresentationManager.getTypeName(clazz);
        AnAction action = createAddingAction(e,
                                             ApplicationBundle.message("action.add") + " " + name,
                                             ElementPresentationManager.getIcon(clazz),
                                             clazz,
                                             description);
        actions.add(action);
      }
    }
    if (actions.size() == 1) {
//      actions.get(0).registerCustomShortcutSet(shortcutSet, getComponent(e));
    }
    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  protected abstract AnAction createAddingAction(final AnActionEvent e,
                                                 final String name,
                                                 final Icon icon,
                                                 final Class s,
                                                 final DomCollectionChildDescription description);

  protected abstract DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e);

  @Nullable
  protected abstract DomElement getParentDomElement(final AnActionEvent e);

  protected abstract JComponent getComponent(AnActionEvent e);
}
