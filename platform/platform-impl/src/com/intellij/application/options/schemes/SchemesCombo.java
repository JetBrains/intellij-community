// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.schemes;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.GroupedComboBoxRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class SchemesCombo<T extends Scheme> extends ComboBox<SchemesCombo.MySchemeListItem<T>> {
  public static final @NotNull Supplier<@Nls String> PROJECT_LEVEL = IdeBundle.messagePointer("scheme.project");
  public static final @NotNull Supplier<@Nls String> IDE_LEVEL = IdeBundle.messagePointer("scheme.ide");
  private T firstProjectScheme = null;
  private T firstIDEScheme = null;

  public SchemesCombo() {
    super(new DefaultComboBoxModel<>());
    setRenderer(new GroupedComboBoxRenderer<>(this) {
      @Override
      public void customize(@NotNull SimpleColoredComponent item, MySchemeListItem<T> value, int index, boolean isSelected, boolean hasFocus) {
        customizeComponent(item, value, index);
      }

      @Override
      public @Nullable ListSeparator separatorFor(MySchemeListItem<T> value) {
        if (!supportsProjectSchemes()) return null;
        if (firstProjectScheme != null && firstProjectScheme.equals(value.getScheme()))
          return new ListSeparator(IdeBundle.message("separator.scheme.stored.in", PROJECT_LEVEL.get()));
        if (firstIDEScheme != null && firstIDEScheme.equals(value.getScheme()))
          return new ListSeparator(IdeBundle.message("separator.scheme.stored.in", IDE_LEVEL.get()));
        return null;
      }
    });
    setSwingPopup(false);
  }

  public void resetSchemes(@NotNull Collection<? extends T> schemes) {
    final DefaultComboBoxModel<MySchemeListItem<T>> model = (DefaultComboBoxModel<MySchemeListItem<T>>)getModel();
    model.removeAllElements();
    firstProjectScheme = null;
    firstIDEScheme = null;
    if (supportsProjectSchemes()) {
      addItems(schemes, scheme -> scheme != null && isProjectScheme(scheme));
      addItems(schemes, scheme -> scheme != null && !isProjectScheme(scheme));
    }
    else {
      addItems(schemes, scheme -> scheme != null);
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

  public @Nullable T getSelectedScheme() {
    SchemesCombo.MySchemeListItem<T> item = getSelectedItem();
    return item != null ? item.getScheme() : null;
  }

  @Override
  public @Nullable SchemesCombo.MySchemeListItem<T> getSelectedItem() {
    int i = getSelectedIndex();
    return i >= 0 ? getItemAt(i) : null;
  }

  protected abstract boolean supportsProjectSchemes();

  protected boolean isProjectScheme(@NotNull T scheme) {
    throw new UnsupportedOperationException();
  }

  protected boolean isDefaultScheme(@NotNull T scheme) {
    return false;
  }

  protected int getIndent(@NotNull T scheme) {
    return 0;
  }

  protected abstract @NotNull SimpleTextAttributes getSchemeAttributes(T scheme);

  private void addItems(@NotNull Collection<? extends T> schemes, Predicate<? super T> filter) {
    for (T scheme : schemes) {
      if (filter.test(scheme)) {
        if (firstProjectScheme == null && isProjectScheme(scheme)) firstProjectScheme = scheme;
        if (firstIDEScheme == null && !isProjectScheme(scheme)) firstIDEScheme = scheme;
        ((DefaultComboBoxModel<MySchemeListItem<T>>) getModel()).addElement(new MySchemeListItem<>(scheme));
      }
    }
  }

  static final class MySchemeListItem<T extends Scheme> {
    private final @Nullable T myScheme;

    MySchemeListItem(@Nullable T scheme) {
      myScheme = scheme;
    }

    public @Nullable String getSchemeName() {
      return myScheme != null ? myScheme.getName() : null;
    }

    public @Nullable T getScheme() {
      return myScheme;
    }

    public @NotNull @NlsContexts.ListItem String getPresentableText() {
      return myScheme != null ? myScheme.getDisplayName() : "";
    }
  }

  private void customizeComponent(@NotNull SimpleColoredComponent item, MySchemeListItem<T> value, int index) {
    final var scheme = value.getScheme();
    if (scheme != null) {
      item.append(value.getPresentableText(), getSchemeAttributes(scheme));
      if (isDefaultScheme(scheme)) {
        item.append(" " + IdeBundle.message("scheme.theme.default"), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      if (supportsProjectSchemes()) {
        if (index == -1) {
          item.append("  " + (isProjectScheme(scheme) ? PROJECT_LEVEL.get() : IDE_LEVEL.get()),
                      SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }
    int indent = index < 0 || scheme == null ? 0 : getIndent(scheme);
    item.setIpad(JBUI.insetsLeft(indent > 0 ? indent * 10 : 0));
  }
}