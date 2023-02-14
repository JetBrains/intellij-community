// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.AbstractFontCombo;
import com.intellij.ui.GroupedComboBoxRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

class FontFamilyCombo extends AbstractFontCombo<FontFamilyCombo.MyFontItem> {

  public static final int ITEM_WIDTH = 230;

  private Dimension myItemSize;
  private final boolean myIsPrimary;

  protected FontFamilyCombo(boolean isPrimary) {
    super(new MyModel(!isPrimary));
    setSwingPopup(false);
    myIsPrimary = isPrimary;
    setRenderer(new GroupedComboBoxRenderer<>(this) {
      @Override
      public void customize(@NotNull SimpleColoredComponent item, MyFontItem value, int index) {
        if (value != null) {
          if (value instanceof MyWarningItem) {
            item.append(value.getFamilyName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
            return;
          }
          SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          if (index > -1 && value.myFont != null) {
            if (value.myFontCanDisplayName) {
              item.setFont(value.myFont);
            }
            else if (myIsPrimary) {
              attributes = SimpleTextAttributes.EXCLUDED_ATTRIBUTES;
            }
          } else {
            item.setFont(JBUI.Fonts.label());
          }
          item.append(value.getFamilyName(), attributes);
        }
      }

      @Nullable
      @Override
      public ListSeparator separatorFor(MyFontItem value) {
        if (getModel() instanceof MyModel m) {
          if (!m.myItems.isEmpty() && ContainerUtil.find(m.myItems, item -> item.myIsMonospaced) == value)
            return new ListSeparator(ApplicationBundle.message("settings.editor.font.monospaced"));
          if (!m.myItems.isEmpty() && ContainerUtil.find(m.myItems, item -> !item.myIsMonospaced && !(item instanceof MyNoFontItem)) == value)
            return new ListSeparator(ApplicationBundle.message("settings.editor.font.proportional"));
        }
        return null;
      }
    });
    updateItemSize();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    updateItemSize();
  }

  private void updateItemSize() {
    FontMetrics fontMetrics = getFontMetrics(getFont());
    myItemSize = new Dimension(JBUI.scale(ITEM_WIDTH), fontMetrics.getHeight());
  }

  @Override
  public @NlsSafe @Nullable String getFontName() {
    Object selectedItem = getModel().getSelectedItem();
    return selectedItem instanceof MyFontItem ? ((MyFontItem)selectedItem).getFamilyName() : null;
  }

  @Override
  public void setFontName(@NlsSafe @Nullable String fontName) {
    getModel().setSelectedItem(fontName);
  }

  @Override
  public boolean isNoFontSelected() {
    return getModel().getSelectedItem() instanceof MyNoFontItem;
  }

  // region Not supported by this implementation
  @Override
  public void setMonospacedOnly(boolean isMonospacedOnly) {
    // Ignored
  }

  @Override
  public boolean isMonospacedOnly() {
    return false;
  }

  @Override
  public boolean isMonospacedOnlySupported() {
    return false;
  }
  // endregion


  protected static class MyFontItem {
    private @NotNull final String myFamilyName;
    private boolean myIsMonospaced;
    private boolean myFontCanDisplayName;
    private @Nullable Font myFont;

    public MyFontItem(@NotNull String familyName, boolean isMonospaced) {
      myFamilyName = familyName;
      myIsMonospaced = isMonospaced;
    }

    @Override
    public String toString() {
      return myFamilyName;
    }

    @NlsSafe
    public @NotNull String getFamilyName() {
      return myFamilyName;
    }

    public boolean isSelectable() {
      return true;
    }
  }

  private static class MyNoFontItem extends MyFontItem {
    private MyNoFontItem() {
      super("<None>", false);
    }
  }

  private static class MyWarningItem extends MyFontItem {
    private MyWarningItem(@NotNull String missingName) {
      super(ApplicationBundle.message("settings.editor.font.missing.custom.font", missingName), false);
    }
  }

  private static class MyModel extends AbstractListModel<MyFontItem> implements ComboBoxModel<MyFontItem> {

    /**
     * The list contains bundled fonts and platform-specific default fonts specified in
     * {@link com.intellij.openapi.editor.colors.FontPreferences}.
     * It is used for quick filtering of monospaced fonts before the actual list is shown.
     */
    private final static String[] KNOWN_MONOSPACED_FAMILIES = {
      "Consolas",
      "DejaVu Sans Mono",
      "Droid Sans Mono",
      "JetBrains Mono",
      "Fira Code",
      "Inconsolata",
      "Menlo",
      "Monospaced",
      "Source Code Pro"
    };

    private final Set<String> myMonospacedFamilies = new HashSet<>();
    private final List<MyFontItem> myItems = new ArrayList<>();
    private final @Nullable MyNoFontItem myNoFontItem;
    private @Nullable MyFontItem mySelectedItem;

    private MyModel(boolean withNoneItem) {
      myMonospacedFamilies.addAll(Arrays.asList(KNOWN_MONOSPACED_FAMILIES));
      if (withNoneItem) {
        myNoFontItem = new MyNoFontItem();
        myItems.add(myNoFontItem);
      }
      else {
        myNoFontItem = null;
      }
      FontFamilyService.getAvailableFamilies().forEach(
        name -> myItems.add(new MyFontItem(name, myMonospacedFamilies.contains(name)))
      );
      Collections.sort(myItems, new MyFontItemComparator());
      retrieveFontInfo();
    }

    @Override
    public void setSelectedItem(Object anItem) {
      if (anItem == null) {
        mySelectedItem = myNoFontItem;
      }
      else if (anItem instanceof String) {
        mySelectedItem = ContainerUtil.find(myItems, item -> item.isSelectable() && item.myFamilyName.equals(anItem));
        if (mySelectedItem == null) {
          mySelectedItem = new MyWarningItem((String)anItem);
        }
      }
      else if (anItem instanceof MyFontItem) {
        mySelectedItem = (MyFontItem)anItem;
      }
      fireContentsChanged(this, -1, -1);
    }

    @Override
    public @Nullable MyFontItem getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public int getSize() {
      return myItems.size();
    }

    @Override
    public MyFontItem getElementAt(int index) {
      return myItems.get(index);
    }

    private void retrieveFontInfo() {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (MyFontItem item : myItems) {
          if (FontFamilyService.isMonospaced(item.myFamilyName)) {
            myMonospacedFamilies.add(item.myFamilyName);
          }
          item.myFont = JBUI.Fonts.create(item.myFamilyName, FontPreferences.DEFAULT_FONT_SIZE);
          item.myFontCanDisplayName = item.myFont.canDisplayUpTo(item.myFamilyName) == -1;
        }
        updateMonospacedInfo();
      });
    }

    private void updateMonospacedInfo() {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          for (MyFontItem item : myItems) {
            item.myIsMonospaced = myMonospacedFamilies.contains(item.myFamilyName);
          }
          if (myNoFontItem == null) { // Primary font
            myItems.removeIf(item -> !item.myFontCanDisplayName);
          }
          Collections.sort(myItems, new MyFontItemComparator());
          fireContentsChanged(this, -1, -1);
        }, ModalityState.any());
    }
  }

  private static class MyFontItemComparator implements Comparator<MyFontItem> {

    @Override
    public int compare(MyFontItem item1, MyFontItem item2) {
      if (item1 instanceof MyNoFontItem) return -1;
      if (item2 instanceof MyNoFontItem) return 1;
      if (item1.myIsMonospaced && !item2.myIsMonospaced) return -1;
      if (!item1.myIsMonospaced && item2.myIsMonospaced) return 1;
      return item1.myFamilyName.compareTo(item2.myFamilyName);
    }

    @Override
    public boolean equals(Object obj) {
      return false;
    }
  }
}
