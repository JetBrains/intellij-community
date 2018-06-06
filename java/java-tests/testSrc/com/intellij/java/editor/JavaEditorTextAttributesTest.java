// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.editor;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.pages.JavaColorSettingsPage;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Ensures that Java attributes are not affected by changes in EditorColorScheme or these changes are planned and intentional.
 */
public class JavaEditorTextAttributesTest extends LightPlatformTestCase {

  private static AttributesDescriptor[] getDescriptors() {
    JavaColorSettingsPage page = new JavaColorSettingsPage();
    return page.getAttributeDescriptors();
  }

  private static void appendAttributes(@NotNull String name, @NotNull StringBuilder builder, @NotNull TextAttributes attributes) {
    builder.append(name).append(" {");
    if (appendHexColor(builder, "foreground", attributes.getForegroundColor())) builder.append(", ");
    if (appendHexColor(builder, "background", attributes.getBackgroundColor())) builder.append(", ");
    appendFontType(builder, attributes);
    appendEffects(builder, attributes);
    builder.append(" }");
  }

  private static void appendEffects(@NotNull StringBuilder builder, @NotNull TextAttributes attributes) {
    if (attributes.getEffectColor() != null) {
      builder.append(", ");
      builder.append("effect-type: ").append(attributes.getEffectType()).append(", ");
      appendHexColor(builder, "effect-color", attributes.getEffectColor());
    }
  }

  private static void appendFontType(@NotNull StringBuilder builder, @NotNull TextAttributes attributes) {
    builder.append(" font-type: ");
    switch (attributes.getFontType()) {
      case Font.PLAIN:
        builder.append("normal");
        break;
      case Font.BOLD:
        builder.append("bold");
        break;
      case Font.ITALIC:
        builder.append("italic");
        break;
      case Font.BOLD | Font.ITALIC:
        builder.append("bold-italic");
        break;
      default:
        fail("Unknown font type: " + attributes.getFontType());
    }
  }

  private static boolean appendHexColor(@NotNull StringBuilder builder, @NotNull String title, @Nullable Color color) {
    if (color != null) {
      builder.append(" ").append(title).append(": ").append(ColorUtil.toHex(color));
      return true;
    }
    return false;
  }

  private static String dumpDefaultColorScheme(@NotNull String name) {
    StringBuilder dumpBuilder = new StringBuilder();
    EditorColorsScheme baseScheme = DefaultColorSchemesManager.getInstance().getScheme(name);
    EditorColorsScheme scheme = new EditorColorsSchemeImpl(baseScheme);
    for (AttributesDescriptor descriptor : getDescriptors()) {
      TextAttributes attributes = scheme.getAttributes(descriptor.getKey());
      appendAttributes(descriptor.getKey().getExternalName(), dumpBuilder, attributes);
      dumpBuilder.append("\n");
    }
    return dumpBuilder.toString();
  }

  public void testDefaultColorScheme() {
    assertEquals(
      "JAVA_KEYWORD { foreground: 000080,  font-type: bold }\n" +
      "JAVA_NUMBER { foreground: 0000ff,  font-type: normal }\n" +
      "JAVA_STRING { foreground: 008000,  font-type: bold }\n" +
      "JAVA_VALID_STRING_ESCAPE { foreground: 000080,  font-type: bold }\n" +
      "JAVA_INVALID_STRING_ESCAPE { foreground: 008000,  background: ffcccc,  font-type: normal }\n" +
      "JAVA_OPERATION_SIGN { font-type: normal }\n" +
      "JAVA_PARENTH { font-type: normal }\n" +
      "JAVA_BRACES { font-type: normal }\n" +
      "JAVA_BRACKETS { font-type: normal }\n" +
      "JAVA_COMMA { font-type: normal }\n" +
      "JAVA_SEMICOLON { font-type: normal }\n" +
      "JAVA_DOT { font-type: normal }\n" +
      "JAVA_LINE_COMMENT { foreground: 808080,  font-type: italic }\n" +
      "JAVA_BLOCK_COMMENT { foreground: 808080,  font-type: italic }\n" +
      "JAVA_DOC_COMMENT { foreground: 808080,  font-type: italic }\n" +
      "JAVA_DOC_TAG { font-type: bold, effect-type: LINE_UNDERSCORE,  effect-color: 808080 }\n" +
      "DOC_COMMENT_TAG_VALUE { foreground: 3d3d3d,  font-type: bold-italic }\n" +
      "JAVA_DOC_MARKUP { background: e2ffe2,  font-type: normal }\n" +
      "CLASS_NAME_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "ANONYMOUS_CLASS_NAME_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "TYPE_PARAMETER_NAME_ATTRIBUTES { foreground: 20999d,  font-type: normal }\n" +
      "ABSTRACT_CLASS_NAME_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "INTERFACE_NAME_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "ENUM_NAME_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "LOCAL_VARIABLE_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES { font-type: normal, effect-type: LINE_UNDERSCORE,  effect-color: 909090 }\n" +
      "REASSIGNED_PARAMETER_ATTRIBUTES { font-type: normal, effect-type: LINE_UNDERSCORE,  effect-color: 909090 }\n" +
      "IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES { foreground: 660e7a,  font-type: normal }\n" +
      "INSTANCE_FIELD_ATTRIBUTES { foreground: 660e7a,  font-type: bold }\n" +
      "INSTANCE_FINAL_FIELD_ATTRIBUTES { foreground: 660e7a,  font-type: bold }\n" +
      "STATIC_FIELD_ATTRIBUTES { foreground: 660e7a,  font-type: italic }\n" +
      "STATIC_FIELD_IMPORTED_ATTRIBUTES { foreground: 660e7a,  font-type: italic }\n" +
      "STATIC_FINAL_FIELD_ATTRIBUTES { foreground: 660e7a,  font-type: bold-italic }\n" +
      "STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES { foreground: 660e7a,  font-type: bold-italic }\n" +
      "PARAMETER_ATTRIBUTES { font-type: normal }\n" +
      "LAMBDA_PARAMETER_ATTRIBUTES { font-type: normal }\n" +
      "METHOD_CALL_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "STATIC_METHOD_IMPORTED_ATTRIBUTES { font-type: italic }\n" +
      "METHOD_DECLARATION_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "CONSTRUCTOR_CALL_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "CONSTRUCTOR_DECLARATION_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "STATIC_METHOD_ATTRIBUTES { font-type: italic }\n" +
      "ABSTRACT_METHOD_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "INHERITED_METHOD_ATTRIBUTES { foreground: 000000,  font-type: normal }\n" +
      "ANNOTATION_NAME_ATTRIBUTES { foreground: 808000,  font-type: normal }\n" +
      "ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES { font-type: normal }\n",

      dumpDefaultColorScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME));
  }
}
