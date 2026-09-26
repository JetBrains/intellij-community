// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SmartElementDescriptor;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.usageView.UsageTreeColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Font;

public abstract class HierarchyNodeDescriptor extends SmartElementDescriptor {
  public static final HierarchyNodeDescriptor[] EMPTY_ARRAY = new HierarchyNodeDescriptor[0];
  protected @NotNull CompositeAppearance myHighlightedText;
  private Object[] myCachedChildren;
  protected final boolean myIsBase;
  private Color myBackgroundColor;

  protected HierarchyNodeDescriptor(@NotNull Project project,
                                    @Nullable NodeDescriptor parentDescriptor,
                                    @NotNull PsiElement element,
                                    boolean isBase) {
    super(project, parentDescriptor, element);
    myHighlightedText = new CompositeAppearance();
    myName = "";
    myIsBase = isBase;
  }

  @Override
  public final Object getElement() {
    return this;
  }

  public @Nullable PsiFile getContainingFile() {
    PsiElement element = getPsiElement();
    return element != null ? element.getContainingFile() : null;
  }

  public boolean isValid() {
    return getPsiElement() != null;
  }

  public final Object[] getCachedChildren() {
    return myCachedChildren;
  }

  public final void setCachedChildren(Object[] cachedChildren) {
    myCachedChildren = cachedChildren;
  }

  public final @Nullable Color getBackgroundColorCached() {
    return myBackgroundColor;
  }

  @Override
  public boolean update() {
    boolean changed = super.update();
    myBackgroundColor = ProjectViewTree.getColorForElement(getContainingFile());
    return changed;
  }

  @Override
  protected final boolean isMarkReadOnly() {
    return true;
  }

  @Override
  protected final boolean isMarkModified() {
    return true;
  }

  public final @NotNull CompositeAppearance getHighlightedText() {
    return myHighlightedText;
  }

  protected static TextAttributes getInvalidPrefixAttributes() {
    return UsageTreeColors.INVALID_ATTRIBUTES.toTextAttributes();
  }

  protected static TextAttributes getUsageCountPrefixAttributes() {
    return UsageTreeColors.NUMBER_OF_USAGES_ATTRIBUTES.toTextAttributes();
  }

  protected static TextAttributes getPackageNameAttributes() {
    return getUsageCountPrefixAttributes();
  }

  protected final @Nullable TextAttributes textAttributesFor(@Nullable PsiElement element) {
    return element instanceof NavigationItem item ? textAttributesForItem(item) : baseColorAttributes();
  }

  private @Nullable TextAttributes textAttributesForItem(@Nullable NavigationItem item) {
    return item == null ? baseColorAttributes() : TextAttributes.merge(presentationAttributesFor(item), baseColorAttributes());
  }

  private static @Nullable TextAttributes presentationAttributesFor(@NotNull NavigationItem item) {
    ItemPresentation presentation = item.getPresentation();
    if (presentation == null) {
      presentation = ItemPresentationProviders.getItemPresentation(item);
    }
    if (presentation instanceof ColoredItemPresentation coloredItemPresentation) {
      return EditorColorsUtil.getGlobalOrDefaultColorScheme().getAttributes(coloredItemPresentation.getTextAttributesKey());
    }
    return null;
  }

  /// @return the text attributes that use this node descriptor color.
  protected @Nullable TextAttributes baseColorAttributes() {
    return myColor == null ? null : new TextAttributes(myColor, null, null, null, Font.PLAIN);
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  protected final boolean invalidElement() {
    String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
    if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
      myHighlightedText.getBeginning().addText(invalidPrefix, getInvalidPrefixAttributes());
    }
    return true;
  }

  protected final void installIcon(@Nullable Icon elementIcon, boolean changes) {
    if (changes && myIsBase) {
      //add 'base' marker to the element icon
      setIcon(getBaseMarkerIcon(elementIcon));
    }
    else {
      setIcon(elementIcon);
    }
  }

  protected @NotNull Icon getBaseMarkerIcon(@Nullable Icon sourceIcon) {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(sourceIcon, 0);
    icon.setIcon(AllIcons.General.Modified, 1, -AllIcons.General.Modified.getIconWidth(), 0);
    return icon;
  }

  protected final void installIcon(@NotNull PsiElement element, boolean changes) {
    Icon icon = getIcon(element);
    installIcon(icon, changes);
  }

  protected final void installIcon(boolean changes) {
    installIcon(getIcon(), changes);
  }
}
