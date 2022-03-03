// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.AbstractFontCombo;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

class FontFamilyCombo extends AbstractFontCombo<FontFamilyCombo.MyFontItem> {

  public static final int ITEM_WIDTH = 230;

  private final Dimension myItemSize;
  private final boolean myIsPrimary;

  protected FontFamilyCombo(boolean isPrimary) {
    super(new MyModel(!isPrimary));
    setSwingPopup(false);
    myIsPrimary = isPrimary;
    setRenderer(new MyListCellRenderer());
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

  private static class MySeparatorItem extends MyFontItem {
    private boolean isUpdating = true;

    private MySeparatorItem(@NotNull String title, boolean isMonospaced) {
      super(title, isMonospaced);
    }

    @Override
    public boolean isSelectable() {
      return false;
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
    private final MySeparatorItem myMonospacedSeparatorItem;
    private final MySeparatorItem myProportionalSeparatorItem;

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
      myMonospacedSeparatorItem = new MySeparatorItem(
        ApplicationBundle.message("settings.editor.font.monospaced"), true);
      myItems.add(myMonospacedSeparatorItem);
      myProportionalSeparatorItem = new MySeparatorItem(
        ApplicationBundle.message("settings.editor.font.proportional"), false);
      myItems.add(myProportionalSeparatorItem);
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
      else if (anItem instanceof MySeparatorItem) {
        return;
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
          item.myFont = JBUI.Fonts.create(item.myFamilyName, JBUI.Fonts.label().getSize());
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
          myMonospacedSeparatorItem.isUpdating = false;
          myProportionalSeparatorItem.isUpdating = false;
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
      if (item1 instanceof MySeparatorItem) return -1;
      if (item2 instanceof MySeparatorItem) return 1;
      return item1.myFamilyName.compareTo(item2.myFamilyName);
    }

    @Override
    public boolean equals(Object obj) {
      return false;
    }
  }

  private class MyListCellRenderer extends ColoredListCellRenderer<MyFontItem> {

    @Override
    public Component getListCellRendererComponent(JList<? extends MyFontItem> list,
                                                  MyFontItem value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof MySeparatorItem) {
        return new MyTitledSeparator(value.getFamilyName(), !value.myIsMonospaced, ((MySeparatorItem)value).isUpdating);
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      return myItemSize;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MyFontItem> list,
                                         MyFontItem value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        if (value instanceof MyWarningItem) {
          append(value.getFamilyName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        if (value.myFont != null) {
          if (value.myFontCanDisplayName) {
            setFont(value.myFont);
          }
          else if (myIsPrimary) {
            attributes = SimpleTextAttributes.EXCLUDED_ATTRIBUTES;
          }
        }
        append(value.getFamilyName(), attributes);
      }
    }
  }

  private static class MyTitledSeparator extends JPanel {
    private final JLabel myLabel;

    @Override
    public AccessibleContext getAccessibleContext() {
      return myLabel.getAccessibleContext();
    }

    MyTitledSeparator(@NlsContexts.Separator @NotNull String titleText, boolean withTopLine, boolean isUpdating) {
      setBackground(JBColor.background());
      setLayout(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      c.weightx = 1.0;
      c.gridx = 0;
      c.gridy = 0;
      c.insets = JBInsets.emptyInsets();
      if (withTopLine) {
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 2;
        add(new JSeparator(), c);
        c.gridy ++;
      }
      myLabel = new JLabel(titleText);
      myLabel.setForeground(JBColor.gray);
      myLabel.setFont(JBUI.Fonts.smallFont());
      c.gridwidth = 1;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.LINE_START;
      add(myLabel, c);
      if (isUpdating) {
        c.gridx = 1;
        JLabel updatingLabel = new JLabel(ApplicationBundle.message("settings.editor.font.updating"));
        updatingLabel.setForeground(JBColor.gray);
        updatingLabel.setFont(JBUI.Fonts.miniFont());
        add(updatingLabel, c);
      }
    }

  }
}
