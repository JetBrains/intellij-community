// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CustomizedSwitcherPanel extends CompositeColorDescriptionPanel {
  private final ColorSettingsPage myPage;
  private final PreviewPanel myPreviewPanel;

  CustomizedSwitcherPanel(@Nullable PreviewPanel previewPanel,
                                 @Nullable ColorSettingsPage page) {
    myPage = page;
    myPreviewPanel = previewPanel;

    addDescriptionPanel(new ColorAndFontDescriptionPanel(), it -> it instanceof ColorAndFontDescription);
    addDescriptionPanel(new RainbowDescriptionPanel(), it -> it instanceof RainbowAttributeDescriptor);
  }

  @Override
  public void reset(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    super.reset(descriptor);
    updatePreviewPanel(descriptor);
  }

  @Override
  public void apply(@NotNull EditorSchemeAttributeDescriptor descriptor, EditorColorsScheme scheme) {
    super.apply(descriptor, scheme);
    updatePreviewPanel(descriptor);
  }

  private void updatePreviewPanel(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    if (!(myPreviewPanel instanceof SimpleEditorPreview && myPage instanceof RainbowColorSettingsPage)) return;
    UIUtil.invokeAndWaitIfNeeded(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      SimpleEditorPreview simpleEditorPreview = (SimpleEditorPreview)myPreviewPanel;
      simpleEditorPreview.setupRainbow(descriptor.getScheme(), (RainbowColorSettingsPage)myPage);
      simpleEditorPreview.updateView();
    }));
  }
}
