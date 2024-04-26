// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.application.options.colors.OptionsPanelImpl.ColorDescriptionPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CompositeColorDescriptionPanel extends JPanel implements ColorDescriptionPanel {
  protected final @NotNull List<ColorDescriptionPanel> myDescriptionPanels = new ArrayList<>();
  protected final @NotNull List<Condition<? super EditorSchemeAttributeDescriptor>> myConditions = new ArrayList<>();

  private final @NotNull List<Listener> myListeners = new ArrayList<>();

  private ColorDescriptionPanel myActive;

  public void addDescriptionPanel(@NotNull ColorDescriptionPanel descriptionPanel,
                                  @NotNull Condition<? super EditorSchemeAttributeDescriptor> condition) {
    myDescriptionPanels.add(descriptionPanel);
    myConditions.add(condition);

    for (Listener listener : myListeners) {
      descriptionPanel.addListener(listener);
    }

    updatePreferredSize();
  }

  private void updatePreferredSize() {
    Dimension preferredSize = new Dimension();
    for (ColorDescriptionPanel panel : myDescriptionPanels) {
      Dimension size = panel.getPanel().getPreferredSize();
      preferredSize.setSize(Math.max(size.getWidth(), preferredSize.getWidth()),
                            Math.max(size.getHeight(), preferredSize.getHeight()));
    }
    setPreferredSize(preferredSize);
  }

  @Override
  public @NotNull JComponent getPanel() {
    return this;
  }

  @Override
  public void resetDefault() {
    if (myActive != null) {
      final PaintLocker locker = new PaintLocker(this);
      try {
        setPreferredSize(getSize());// froze [this] size
        remove(myActive.getPanel());
        myActive = null;
      }
      finally {
        locker.release();
      }
    }
  }

  @Override
  public void reset(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    JComponent oldPanel = myActive == null ? null : myActive.getPanel();
    myActive = getPanelForDescriptor(descriptor);
    JComponent newPanel = myActive == null ? null : myActive.getPanel();

    if (oldPanel != newPanel) {
      final PaintLocker locker = new PaintLocker(this);
      try {
        if (oldPanel != null) {
          remove(oldPanel);
        }
        if (newPanel != null) {
          setPreferredSize(null);// make [this] resizable
          add(newPanel);
        }
      }
      finally {
        locker.release();
      }
    }
    if (myActive != null) {
      myActive.reset(descriptor);
    }
  }

  private @Nullable ColorDescriptionPanel getPanelForDescriptor(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    for (int i = myConditions.size() - 1; i >= 0; i--) {
      Condition<? super EditorSchemeAttributeDescriptor> condition = myConditions.get(i);
      ColorDescriptionPanel panel = myDescriptionPanels.get(i);
      if (condition.value(descriptor)) return panel;
    }
    return null;
  }


  @Override
  public void apply(@NotNull EditorSchemeAttributeDescriptor descriptor, EditorColorsScheme scheme) {
    if (myActive != null) {
      myActive.apply(descriptor, scheme);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    for (ColorDescriptionPanel panel : myDescriptionPanels) {
      panel.addListener(listener);
    }
    myListeners.add(listener);
  }

  private static final class PaintLocker {
    private final Container myPaintHolder;
    private final boolean myPaintState;

    PaintLocker(@NotNull JComponent component) {
      myPaintHolder = component.getParent();
      myPaintState = myPaintHolder.getIgnoreRepaint();
      myPaintHolder.setIgnoreRepaint(true);
    }

    public void release() {
      myPaintHolder.validate();
      myPaintHolder.setIgnoreRepaint(myPaintState);
      myPaintHolder.repaint();
    }
  }
}
