package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Oct 23, 2008
 * Time: 4:28:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class ColorSettingsUtil {
  public static Map<TextAttributesKey, String> keyToDisplayTextMap(final ColorSettingsPage page) {
    final AttributesDescriptor[] attributeDescriptors = page.getAttributeDescriptors();
    final Map<TextAttributesKey, String> displayText = new HashMap<TextAttributesKey, String>();
    for (AttributesDescriptor attributeDescriptor : attributeDescriptors) {
      final TextAttributesKey key = attributeDescriptor.getKey();
      displayText.put(key, attributeDescriptor.getDisplayName());
    }
    return displayText;
  }

  static boolean isSharedScheme(EditorColorsScheme selected) {
      return ((EditorColorsManagerImpl) EditorColorsManager.getInstance()).getSchemesManager().isShared(selected);
  }
}
