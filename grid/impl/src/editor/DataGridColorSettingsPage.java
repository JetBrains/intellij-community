package com.intellij.database.editor;

import com.intellij.database.DataGridBundle;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ignatov
 */
public class DataGridColorSettingsPage implements ColorSettingsPage {
  public static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
    new AttributesDescriptor(DataGridBundle.messagePointer("attribute.descriptor.data.grid.null.data"), DataGridColors.GRID_NULL_VALUE),
    new AttributesDescriptor(DataGridBundle.messagePointer("attribute.descriptor.data.grid.image.data"), DataGridColors.GRID_IMAGE_VALUE),
    new AttributesDescriptor(DataGridBundle.messagePointer("attribute.descriptor.data.grid.error.data"), DataGridColors.GRID_ERROR_VALUE),
  };

  private static final ColorDescriptor[] COLOR_DESCRIPTORS = {
    new ColorDescriptor(DataGridBundle.messagePointer("attribute.descriptor.data.grid.stripe.color"), DataGridColors.GRID_STRIPE_COLOR,
                        ColorDescriptor.Kind.BACKGROUND)
  };

  @Override
  public @Nullable Icon getIcon() {
    return null;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return """
      <gnv>null</gnv>
      <giv>400x300 PNG image 7.14K</giv>
      <gev><failed to load></gev>""";
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = new HashMap<>();
    map.put("gnv", DataGridColors.GRID_NULL_VALUE);
    map.put("giv", DataGridColors.GRID_IMAGE_VALUE);
    map.put("gev", DataGridColors.GRID_ERROR_VALUE);
    return map;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return COLOR_DESCRIPTORS;
  }

  @Override
  public @NotNull String getDisplayName() {
    return DataGridBundle.message("DataGridColorSettingsPage.configurable.name.database");
  }
}
