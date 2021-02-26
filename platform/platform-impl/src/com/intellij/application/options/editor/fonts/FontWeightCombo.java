// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

abstract class FontWeightCombo extends ComboBox<FontWeightCombo.MyWeightItem> {

  private final MyModel myModel;
  private final boolean myMarkRecommended;

  FontWeightCombo(boolean markRecommended) {
    myMarkRecommended = markRecommended;
    myModel = new MyModel();
    setModel(myModel);
    setRenderer(new MyListCellRenderer());
  }

  void update(@NotNull FontPreferences fontPreferences) {
    myModel.update(fontPreferences);
  }

  @Nullable
  String getSelectedSubFamily() {
    Object selected = myModel.getSelectedItem();
    return selected instanceof MyWeightItem ? ((MyWeightItem)selected).subFamily : null;
  }

  private class MyModel extends AbstractListModel<MyWeightItem> implements ComboBoxModel<MyWeightItem> {
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

  private class MyListCellRenderer extends ColoredListCellRenderer<MyWeightItem> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MyWeightItem> list,
                                         MyWeightItem value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        append(value.subFamily);
        if (value.isRecommended && myMarkRecommended && list.getModel().getSize() > 2) {
          append("  ");
          append(ApplicationBundle.message("settings.editor.font.recommended"), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }
  }

  static class MyWeightItem {
    private final @NlsSafe String subFamily;
    private final boolean isRecommended;

    MyWeightItem(String subFamily, boolean isRecommended) {
      this.subFamily = subFamily;
      this.isRecommended = isRecommended;
    }
  }

  @Nullable
  abstract String getSubFamily(@NotNull FontPreferences preferences);

  @NotNull
  abstract String getRecommendedSubFamily(@NotNull String family);
}
