/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SmartElementDescriptor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class HierarchyNodeDescriptor extends SmartElementDescriptor {
  protected CompositeAppearance myHighlightedText;
  private Object[] myCachedChildren = null;
  protected final boolean myIsBase;

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

  @Nullable
  public PsiFile getContainingFile() {
    PsiElement element = getPsiElement();
    return element != null ? element.getContainingFile() : null;
  }

  public boolean isValid() {
    return getPsiElement() != null;
  }

  public final Object[] getCachedChildren() {
    return myCachedChildren;
  }

  public final void setCachedChildren(final Object[] cachedChildren) {
    myCachedChildren = cachedChildren;
  }

  @Override
  protected final boolean isMarkReadOnly() {
    return true;
  }

  @Override
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
      //add right arrow to the base element
      LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(elementIcon, 0);
      icon.setIcon(AllIcons.Actions.Forward, 1, -AllIcons.Actions.Forward.getIconWidth() / 2, 0);
      setIcon(icon);
    }
    else {
      setIcon(elementIcon);
    }
  }

  protected final void installIcon(@NotNull PsiElement element, boolean changes) {
    Icon icon = getIcon(element);
    installIcon(icon, changes);
  }

  protected final void installIcon(boolean changes) {
    installIcon(getIcon(), changes);
  }
}
