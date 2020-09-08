// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.Separator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @author Konstantin Bulenkov
 */
public class GotoRelatedItem {
  private final @Separator String myGroup;
  private final int myMnemonic;
  @Nullable private final SmartPsiElementPointer<PsiElement> myElementPointer;
  public static final String DEFAULT_GROUP_NAME = "";

  protected GotoRelatedItem(@Nullable PsiElement element, @Separator String group, final int mnemonic) {
    myElementPointer = element == null ? null : SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    myGroup = group;
    myMnemonic = mnemonic;
  }
  
  public GotoRelatedItem(@NotNull PsiElement element, @Separator String group) {
    this(element, group, -1);
  }

  public GotoRelatedItem(@NotNull PsiElement element) {
    this(element, DEFAULT_GROUP_NAME);
  }

  public void navigate() {
    PsiElement element = getElement();
    if (element != null) {
      PsiNavigateUtil.navigate(element);
    }
  }

  @Nullable
  public @NlsContexts.ListItem String getCustomName() {
    return null;
  }

  @Nullable
  public String getCustomContainerName() {
    return null;
  }

  @Nullable
  public Icon getCustomIcon() {
    return null;
  }

  @Nullable
  public PsiElement getElement() {
    return myElementPointer == null ? null : myElementPointer.getElement();
  }

  public int getMnemonic() {
    return myMnemonic;
  }
  public static List<GotoRelatedItem> createItems(@NotNull Collection<? extends PsiElement> elements) {
    return createItems(elements, DEFAULT_GROUP_NAME);
  }

  public static List<GotoRelatedItem> createItems(@NotNull Collection<? extends PsiElement> elements, @Separator String group) {
    List<GotoRelatedItem> items = new ArrayList<>(elements.size());
    for (PsiElement element : elements) {
      items.add(new GotoRelatedItem(element, group));
    }
    return items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GotoRelatedItem item = (GotoRelatedItem)o;

    if (myElementPointer != null ? !myElementPointer.equals(item.myElementPointer) : item.myElementPointer != null) return false;

    return true;
  }

  @Separator
  public String getGroup() {
    return myGroup;
  }

  @Override
  public int hashCode() {
    return myElementPointer != null ? myElementPointer.hashCode() : 0;
  }
}
