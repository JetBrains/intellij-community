package com.intellij.ide.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.IconLoader;

/**
 * @author cdr
 */
public final class ViewSubtypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSubtypesHierarchyAction() {
    super(IdeBundle.message("action.view.subtypes.hierarchy"),
          IdeBundle.message("action.description.view.subtypes.hierarchy"), IconLoader.getIcon("/hierarchy/subtypes.png"));
  }

  protected final String getTypeName() {
    return TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE;
  }
}
