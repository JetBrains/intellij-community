// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Predicates;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class SchemesCombo<T extends Scheme> extends ComboBox<SchemesCombo.MySchemeListItem<T>> {
  public static final @NotNull Supplier<@Nls String> PROJECT_LEVEL = IdeBundle.messagePointer("scheme.project");
  public static final @NotNull Supplier<@Nls String> IDE_LEVEL = IdeBundle.messagePointer("scheme.ide");

  public SchemesCombo() {
    super(new MyComboBoxModel<>());
    setRenderer(new MyListCellRenderer());
    setSwingPopup(false);
  }

  public void resetSchemes(@NotNull Collection<? extends T> schemes) {
    final MyComboBoxModel<T> model = (MyComboBoxModel<T>)getModel();
    model.removeAllElements();
    if (supportsProjectSchemes()) {
      model.addElement(new MySeparatorItem(PROJECT_LEVEL.get()));
      addItems(schemes, scheme -> isProjectScheme(scheme));
      model.addElement(new MySeparatorItem(IDE_LEVEL.get()));
      addItems(schemes, scheme -> !isProjectScheme(scheme));
    }
    else {
      addItems(schemes, Predicates.alwaysTrue());
    }
  }

  public void selectScheme(@Nullable T scheme) {
    for (int i = 0; i < getItemCount(); i ++) {
      if (getItemAt(i).getScheme() == scheme) {
        setSelectedIndex(i);
        break;
      }
    }
  }

  @Nullable
  public T getSelectedScheme() {
    SchemesCombo.MySchemeListItem<T> item = getSelectedItem();
    return item != null ? item.getScheme() : null;
  }

  @Override
  @Nullable
  public SchemesCombo.MySchemeListItem<T> getSelectedItem() {
    int i = getSelectedIndex();
    return i >= 0 ? getItemAt(i) : null;
  }

  protected abstract boolean supportsProjectSchemes();

  protected boolean isProjectScheme(@NotNull T scheme) {
    throw new UnsupportedOperationException();
  }

  protected int getIndent(@NotNull T scheme) {
    return 0;
  }

  @NotNull
  protected abstract SimpleTextAttributes getSchemeAttributes(T scheme);

  private void addItems(@NotNull Collection<? extends T> schemes, Predicate<? super T> filter) {
    for (T scheme : schemes) {
      if (filter.test(scheme)) {
        ((MyComboBoxModel<T>) getModel()).addElement(new MySchemeListItem<>(scheme));
      }
    }
  }

  static class MySchemeListItem<T extends Scheme> {
    private @Nullable final T myScheme;

    MySchemeListItem(@Nullable T scheme) {
      myScheme = scheme;
    }

    @Nullable
    public String getSchemeName() {
      return myScheme != null ? myScheme.getName() : null;
    }

    @Nullable
    public T getScheme() {
      return myScheme;
    }

    @NotNull
    public @NlsContexts.ListItem String getPresentableText() {
      return myScheme != null ? myScheme.getDisplayName() : "";
    }

    public boolean isSeparator() {
      return false;
    }
  }

  private class MyListCellRenderer extends ColoredListCellRenderer<MySchemeListItem<T>> {
    private final SeparatorWithText mySeparator = new SeparatorWithText();

    @Override
    public Component getListCellRendererComponent(JList<? extends MySchemeListItem<T>> list,
                                                  MySchemeListItem<T> value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value != null && value.isSeparator()) {
        mySeparator.setCaption(IdeBundle.message("separator.scheme.stored.in", value.getPresentableText()));
        return mySeparator;
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MySchemeListItem<T>> list,
                                         MySchemeListItem<T> value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      T scheme = value.getScheme();
      if (scheme != null) {
        append(value.getPresentableText(), getSchemeAttributes(scheme));
        if (supportsProjectSchemes()) {
          if (index == -1) {
            append("  " + (isProjectScheme(scheme) ? PROJECT_LEVEL.get() : IDE_LEVEL.get()),
                   SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
      int indent = index < 0 || scheme == null ? 0 : getIndent(scheme);
      setIpad(JBUI.insetsLeft(indent > 0 ? indent * 10 : 0));
    }
  }

  private static class MyComboBoxModel<T extends Scheme> extends DefaultComboBoxModel<MySchemeListItem<T>> {
    @Override
    public void setSelectedItem(Object anObject) {
      if (anObject instanceof SchemesCombo.MySchemeListItem && ((MySchemeListItem<?>)anObject).isSeparator()) {
        return;
      }
      super.setSelectedItem(anObject);
    }
  }

  private class MySeparatorItem extends MySchemeListItem<T> {

    private final @NlsContexts.ListItem String myTitle;

    MySeparatorItem(@NotNull @NlsContexts.ListItem String title) {
      super(null);
      myTitle = title;
    }

    @Override
    public boolean isSeparator() {
      return true;
    }

    @NotNull
    @Override
    public String getPresentableText() {
      return myTitle;
    }
  }
}