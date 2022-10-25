// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class BrowserSelector {
  private final ComboboxWithBrowseButton myBrowserComboWithBrowse;
  private MutableCollectionComboBoxModel<WebBrowser> myModel;

  public BrowserSelector() {
    this(true);
  }

  public BrowserSelector(final boolean allowDefaultBrowser) {
    this(browser -> allowDefaultBrowser || browser != null);
  }

  public BrowserSelector(final @NotNull Condition<? super WebBrowser> browserCondition) {
    myModel = createBrowsersComboModel(browserCondition);
    myBrowserComboWithBrowse = new ComboboxWithBrowseButton(new ComboBox(myModel));
    myBrowserComboWithBrowse.addActionListener(e -> {
      WebBrowserManager browserManager = WebBrowserManager.getInstance();
      long modificationCount = browserManager.getModificationCount();
      ShowSettingsUtil.getInstance().editConfigurable(myBrowserComboWithBrowse, new BrowserSettings());

      WebBrowser selectedItem = getSelected();
      if (modificationCount != browserManager.getModificationCount()) {
        myModel = createBrowsersComboModel(browserCondition);
        //noinspection unchecked
        myBrowserComboWithBrowse.getComboBox().setModel(myModel);
      }
      if (selectedItem != null) {
        setSelected(selectedItem);
      }
    });

    //noinspection unchecked
    myBrowserComboWithBrowse.getComboBox().setRenderer(
      SimpleListCellRenderer.<WebBrowser>create((label, value, index) -> {
        Icon baseIcon = value != null ? value.getIcon() : AllIcons.General.Web;
        label.setIcon(myBrowserComboWithBrowse.isEnabled() ? baseIcon : IconLoader.getDisabledIcon(baseIcon));
        label.setText(value != null ? value.getName() : IdeBundle.message("default"));
      }));
  }

  public JComponent getMainComponent() {
    return myBrowserComboWithBrowse;
  }

  private static MutableCollectionComboBoxModel<WebBrowser> createBrowsersComboModel(@NotNull Condition<? super WebBrowser> browserCondition) {
    List<WebBrowser> list = new ArrayList<>();
    if (browserCondition.value(null)) {
      list.add(null);
    }
    list.addAll(WebBrowserManager.getInstance().getBrowsers(browserCondition));
    return new MutableCollectionComboBoxModel<>(list);
  }

  @Nullable
  public WebBrowser getSelected() {
    return myModel.getSelected();
  }

  @Nullable
  public String getSelectedBrowserId() {
    WebBrowser browser = getSelected();
    return browser != null ? browser.getId().toString() : null;
  }

  public void setSelected(@Nullable WebBrowser selectedItem) {
    myBrowserComboWithBrowse.getComboBox().setSelectedItem(selectedItem);
  }

  public boolean addAndSelect(@NotNull WebBrowser browser) {
    if (myModel.contains(browser)) {
      return false;
    }

    myModel.addItem(browser);
    return true;
  }

  public int getSize() {
    return myModel.getSize();
  }
}
