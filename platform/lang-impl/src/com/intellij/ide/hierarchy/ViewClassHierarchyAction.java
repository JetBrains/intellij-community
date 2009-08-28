package com.intellij.ide.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

/**
 * @author cdr
 */
public final class ViewClassHierarchyAction extends ChangeViewTypeActionBase {
  public ViewClassHierarchyAction() {
    super(IdeBundle.message("action.view.class.hierarchy"),
          IdeBundle.message("action.description.view.class.hierarchy"), IconLoader.getIcon("/hierarchy/class.png"));
  }

  protected final String getTypeName() {
    return TypeHierarchyBrowserBase.TYPE_HIERARCHY_TYPE;
  }

  public final void update(final AnActionEvent event) {
    super.update(event);
    final TypeHierarchyBrowserBase browser = getTypeHierarchyBrowser(event.getDataContext());
    event.getPresentation().setEnabled(browser != null && !browser.isInterface());
  }
}
