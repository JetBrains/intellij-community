package com.intellij.ide.hierarchy;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

/**
 * @author cdr
 */
abstract class ChangeViewTypeActionBase extends ToggleAction {
  public ChangeViewTypeActionBase(final String shortDescription, final String longDescription, final Icon icon) {
    super(shortDescription, longDescription, icon);
  }

  public final boolean isSelected(final AnActionEvent event) {
    final TypeHierarchyBrowserBase browser = getTypeHierarchyBrowser(event.getDataContext());
    return browser != null && getTypeName().equals(browser.getCurrentViewType());
  }

  protected abstract String getTypeName();

  public final void setSelected(final AnActionEvent event, final boolean flag) {
    if (flag) {
      final TypeHierarchyBrowserBase browser = getTypeHierarchyBrowser(event.getDataContext());
      //        setWaitCursor();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (browser != null) {
            browser.changeView(getTypeName());
          }
        }
      });
    }
  }

  public void update(final AnActionEvent event) {
    // its important to assign the myTypeHierarchyBrowser first
    super.update(event);
    final Presentation presentation = event.getPresentation();
    final TypeHierarchyBrowserBase browser = getTypeHierarchyBrowser(event.getDataContext());
    presentation.setEnabled(browser != null && browser.isValidBase());
  }

  protected static TypeHierarchyBrowserBase getTypeHierarchyBrowser(final DataContext context) {
    return (TypeHierarchyBrowserBase)context.getData(TypeHierarchyBrowserBase.TYPE_HIERARCHY_BROWSER_DATA_KEY);
  }
}
