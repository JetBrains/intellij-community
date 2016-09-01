/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.colors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class CustomizedSwitcherPanel extends JPanel implements OptionsPanelImpl.ColorDescriptionPanel {
  private ColorSettingsPage myPage;

  private PreviewPanel myPreviewPanel;
  private ColorAndFontDescriptionPanel myColorAndFontPanel;
  private RainbowDescriptionPanel myRainbowPanel;

  private OptionsPanelImpl.ColorDescriptionPanel myActive;

  public CustomizedSwitcherPanel(@Nullable PreviewPanel previewPanel,
                                 @Nullable ColorSettingsPage page) {
    super();
    myPage = page;
    myPreviewPanel = previewPanel;

    myRainbowPanel = new RainbowDescriptionPanel();
    myColorAndFontPanel = new ColorAndFontDescriptionPanel();

    Dimension sizeR = myRainbowPanel.getPreferredSize();
    Dimension sizeC = myColorAndFontPanel.getPreferredSize();
    Dimension preferredSize = new Dimension();
    preferredSize.setSize(Math.max(sizeR.getWidth(), sizeC.getWidth()),
                          Math.max(sizeR.getHeight(), sizeC.getHeight()));
    setPreferredSize(preferredSize);
  }

  @NotNull
  @Override
  public JComponent getPanel() {
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
    updatePreviewPanel(descriptor);
  }

  protected OptionsPanelImpl.ColorDescriptionPanel getPanelForDescriptor(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    if (descriptor instanceof RainbowAttributeDescriptor) {
      return myRainbowPanel;
    }
    else if (descriptor instanceof ColorAndFontDescription) {
      return myColorAndFontPanel;
    }
    return null;
  }


  @Override
  public void apply(@NotNull EditorSchemeAttributeDescriptor descriptor, EditorColorsScheme scheme) {
    if (myActive != null) {
      myActive.apply(descriptor, scheme);
      updatePreviewPanel(descriptor);
    }
  }

  protected void updatePreviewPanel(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    if (!(myPreviewPanel instanceof SimpleEditorPreview && myPage instanceof RainbowColorSettingsPage)) return;
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> ApplicationManager.getApplication().runWriteAction(() -> {
      SimpleEditorPreview simpleEditorPreview = (SimpleEditorPreview)myPreviewPanel;
      simpleEditorPreview.setupRainbow(descriptor.getScheme(), (RainbowColorSettingsPage)myPage);
      simpleEditorPreview.updateView();
    }));
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myRainbowPanel.addListener(listener);
    myColorAndFontPanel.addListener(listener);
  }

  private static class PaintLocker {
    private Container myPaintHolder;
    private boolean myPaintState;

    public PaintLocker(@NotNull JComponent component) {
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
