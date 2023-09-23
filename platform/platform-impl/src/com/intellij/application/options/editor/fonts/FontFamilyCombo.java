// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

final class FontFamilyCombo extends AbstractFontCombo<FontFamilyCombo.MyFontItem> {

  public static final int ITEM_WIDTH = 230;

  private final boolean myIsPrimary;

  FontFamilyCombo(boolean isPrimary) {
    super(new MyModel(!isPrimary));
    setSwingPopup(false);
    myIsPrimary = isPrimary;
    ClientProperty.put(this, ANIMATION_IN_RENDERER_ALLOWED, true);
    setRenderer(new GroupedComboBoxRenderer<>(this) {
      @Override
      public void customize(@NotNull SimpleColoredComponent item, MyFontItem value, int index, boolean isSelected, boolean hasFocus) {
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

      @Override
      public @Nullable ListSeparator separatorFor(MyFontItem value) {
        if (getModel() instanceof MyModel m) {
          if (!m.myItems.isEmpty() && ContainerUtil.find(m.myItems, item -> item.myIsMonospaced) == value)
            return new ListSeparator(ApplicationBundle.message("settings.editor.font.monospaced"));
          if (!m.myItems.isEmpty() && ContainerUtil.find(m.myItems, item -> !item.myIsMonospaced && !(item instanceof MyNoFontItem)) == value)
            return new ListSeparator(ApplicationBundle.message("settings.editor.font.proportional"));
        }
        return null;
      }

      @Override
      public int getMaxWidth() {
        return ITEM_WIDTH;
      }

      @Override
      public @NotNull Component getListCellRendererComponent(@Nullable JList<? extends MyFontItem> list,
                                                             MyFontItem value,
                                                             int index,
                                                             boolean isSelected,
                                                             boolean cellHasFocus) {
        final var component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (index != -1 || !((MyModel)dataModel).isUpdating()) {
          return component;
        } else {
          JPanel panel = new CellRendererPanel(new BorderLayout()) {
            @Override
            public AccessibleContext getAccessibleContext() { return component.getAccessibleContext(); }
          };
          component.setBackground(null);
          panel.add(component, BorderLayout.CENTER);
          JBLabel progressIcon = new JBLabel(AnimatedIcon.Default.INSTANCE);
          panel.add(progressIcon, BorderLayout.EAST);
          return panel;
        }
      }
    });
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
    private final @NotNull String myFamilyName;
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

    public @NlsSafe @NotNull String getFamilyName() {
      return myFamilyName;
    }

    public boolean isSelectable() {
      return true;
    }
  }

  private static final class MyNoFontItem extends MyFontItem {
    private MyNoFontItem() {
      super("<None>", false);
    }
  }

  private static final class MyWarningItem extends MyFontItem {
    private MyWarningItem(@NotNull String missingName) {
      super(ApplicationBundle.message("settings.editor.font.missing.custom.font", missingName), false);
    }
  }

  private static final class MyModel extends AbstractListModel<MyFontItem> implements ComboBoxModel<MyFontItem> {

    /**
     * The list contains bundled fonts and platform-specific default fonts specified in
     * {@link com.intellij.openapi.editor.colors.FontPreferences}.
     * It is used for quick filtering of monospaced fonts before the actual list is shown.
     */
    private static final String[] KNOWN_MONOSPACED_FAMILIES = {
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

    private boolean myIsUpdating = true;
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

    public boolean isUpdating() {
      return myIsUpdating;
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
          myIsUpdating = false;
          Collections.sort(myItems, new MyFontItemComparator());
          fireContentsChanged(this, -1, -1);
        }, ModalityState.any());
    }
  }

  private static final class MyFontItemComparator implements Comparator<MyFontItem> {

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
