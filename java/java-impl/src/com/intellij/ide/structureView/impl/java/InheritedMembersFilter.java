package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.FileStructureFilter;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InheritedMembersFilter implements FileStructureFilter {
  @NonNls public static final String ID = "SHOW_INHERITED";

  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof JavaClassTreeElementBase) {
      return !((JavaClassTreeElementBase)treeNode).isInherited();
    }
    else {
      return true;
    }
  }

  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.inherited"), null, IconLoader.getIcon("/hierarchy/supertypes.png"));
  }

  @NotNull
  public String getName() {
    return ID;
  }

  public boolean isReverted() {
    return true;
  }

  public String getCheckBoxText() {
    return IdeBundle.message("file.structure.toggle.show.inherited");
  }

  public Shortcut[] getShortcut() {
    return KeymapManager.getInstance().getActiveKeymap().getShortcuts("FileStructurePopup");
  }
}
