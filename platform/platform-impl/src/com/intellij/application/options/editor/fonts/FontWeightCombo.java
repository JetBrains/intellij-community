// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts;

import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

abstract class FontWeightCombo extends ComboBox<FontWeightCombo.MyWeightItem> {

  private final @NotNull MyModel myModel;

  FontWeightCombo(boolean markRecommended) {
    myModel = new MyModel();
    setModel(myModel);
    setSwingPopup(false);
    setRenderer(FontWeightComboUI.getRenderer(markRecommended));
  }

  void update(@NotNull FontPreferences fontPreferences) {
    myModel.update(fontPreferences);
  }

  @Nullable
  String getSelectedSubFamily() {
    return myModel.getSelectedItem() instanceof MyWeightItem weightItem ? weightItem.subFamily : null;
  }

  private final class MyModel extends AbstractListModel<MyWeightItem> implements ComboBoxModel<MyWeightItem> {
    private final @NotNull List<MyWeightItem> myItems = new ArrayList<>();

    private @Nullable MyWeightItem mySelectedItem;

    @Override
    public void setSelectedItem(Object anItem) {
      if (anItem instanceof MyWeightItem) {
        mySelectedItem = (MyWeightItem)anItem;
      }
      else if (anItem instanceof String) {
        mySelectedItem = ContainerUtil.find(myItems, item -> item.subFamily.equals(anItem));
      }
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public int getSize() {
      return myItems.size();
    }

    @Override
    public MyWeightItem getElementAt(int index) {
      return myItems.get(index);
    }

    private void update(@NotNull FontPreferences currPreferences) {
      myItems.clear();
      String currFamily = currPreferences.getFontFamily();
      String recommended = getRecommendedSubFamily(currFamily);
      FontFamilyService.getSubFamilies(currFamily).forEach(
        subFamily -> myItems.add(new MyWeightItem(subFamily, subFamily.equals(recommended)))
      );
      String subFamily = getSubFamily(currPreferences);
      setSelectedItem(subFamily != null ? subFamily : recommended);
      fireContentsChanged(this, -1, -1);
    }
  }

  static final class MyWeightItem {
    public final @NlsSafe @NotNull String subFamily;
    public final boolean isRecommended;

    MyWeightItem(@NotNull String subFamily, boolean isRecommended) {
      this.subFamily = subFamily;
      this.isRecommended = isRecommended;
    }
  }

  abstract @Nullable String getSubFamily(@NotNull FontPreferences preferences);

  abstract @NotNull String getRecommendedSubFamily(@NotNull String family);
}
