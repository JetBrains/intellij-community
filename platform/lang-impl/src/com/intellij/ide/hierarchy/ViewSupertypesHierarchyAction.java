package com.intellij.ide.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.IconLoader;

/**
 * @author cdr
 */
public final class ViewSupertypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSupertypesHierarchyAction() {
    super(IdeBundle.message("action.view.supertypes.hierarchy"), 
          IdeBundle.message("action.description.view.supertypes.hierarchy"), IconLoader.getIcon("/hierarchy/supertypes.png"));
  }

  protected final String getTypeName() {
    return TypeHierarchyBrowserBase.SUPERTYPES_HIERARCHY_TYPE;
  }
}
