// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

class RunAnythingIconHandler implements PropertyChangeListener {
  private static final String FOREGROUND_PROPERTY = "foreground";
  protected static final String MATCHED_PROVIDER_PROPERTY = "JTextField.match";

  private final Consumer<ExtendableTextComponent.Extension> myConsumer;
  private final JTextComponent myComponent;

  public RunAnythingIconHandler(@NotNull Consumer<ExtendableTextComponent.Extension> consumer, @NotNull JTextComponent component) {
    myConsumer = consumer;
    myComponent = component;

    setConfigurationIcon(component.getClientProperty(MATCHED_PROVIDER_PROPERTY));
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (myComponent == null) return;

    if (MATCHED_PROVIDER_PROPERTY.equals(event.getPropertyName())) {
      setConfigurationIcon(event.getNewValue());
    }
    else if (FOREGROUND_PROPERTY.equals(event.getPropertyName())) {
      myComponent.setForeground(UIUtil.getTextFieldForeground());
    }

    myComponent.repaint();
  }

  private void setConfigurationIcon(Object variant) {
    if (!(variant instanceof Icon)) return;

    myConsumer.consume(new RunConfigurationTypeExtension((Icon)variant));
  }

  private static void installIconListeners(@NotNull Consumer<ExtendableTextComponent.Extension> extensionConsumer,
                                           @NotNull JTextComponent component) {
    RunAnythingIconHandler handler = new RunAnythingIconHandler(extensionConsumer, component);
    component.addPropertyChangeListener(handler);
  }

  public static class MyDarcula extends DarculaTextFieldUI {
    @Override
    protected Icon getSearchIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected Icon getClearIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      installIconListeners(this::addExtension, getComponent());
    }
  }

  public static class MyMacUI extends MacIntelliJTextFieldUI {
    @Override
    protected Icon getSearchIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected Icon getClearIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      installIconListeners(this::addExtension, getComponent());
    }
  }

  public static class MyWinUI extends WinIntelliJTextFieldUI {
    @Override
    protected Icon getSearchIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected Icon getClearIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    public void installListeners() {
      super.installListeners();
      installIconListeners(this::addExtension, getComponent());
    }
  }

  private static class RunConfigurationTypeExtension implements ExtendableTextComponent.Extension {
    private final Icon myVariant;

    public RunConfigurationTypeExtension(Icon variant) {
      myVariant = variant;
    }

    @Override
    public Icon getIcon(boolean hovered) {
      return myVariant;
    }

    @Override
    public boolean isIconBeforeText() {
      return true;
    }

    @Override
    public String toString() {
      return MATCHED_PROVIDER_PROPERTY;
    }
  }
}


