package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SmartElementDescriptor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;

import javax.swing.*;

public abstract class HierarchyNodeDescriptor extends SmartElementDescriptor {
  protected static final Icon BASE_POINTER_ICON = IconLoader.getIcon("/hierarchy/base.png");
  protected CompositeAppearance myHighlightedText;
  private Object[] myCachedChildren = null;
  protected final boolean myIsBase;

  protected HierarchyNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final PsiElement element, final boolean isBase) {
    super(project, parentDescriptor, element);
    myHighlightedText = new CompositeAppearance();
    myName = "";
    myIsBase = isBase;
  }

  public final Object getElement() {
    return this;
  }

  public abstract boolean isValid();

  public final Object[] getCachedChildren() {
    return myCachedChildren;
  }

  public final void setCachedChildren(final Object[] cachedChildren) {
    myCachedChildren = cachedChildren;
  }

  protected final boolean isMarkReadOnly() {
    return true;
  }

  protected final boolean isMarkModified() {
    return true;
  }

  public final CompositeAppearance getHighlightedText() {
    return myHighlightedText;
  }

  protected static TextAttributes getInvalidPrefixAttributes() {
    return UsageTreeColorsScheme.getInstance().getScheme().getAttributes(UsageTreeColors.INVALID_PREFIX);
  }

  protected static TextAttributes getUsageCountPrefixAttributes() {
    return UsageTreeColorsScheme.getInstance().getScheme().getAttributes(UsageTreeColors.NUMBER_OF_USAGES);
  }

  protected static TextAttributes getPackageNameAttributes() {
    return getUsageCountPrefixAttributes();
  }

  public boolean expandOnDoubleClick() {
    return false;
  }
}
