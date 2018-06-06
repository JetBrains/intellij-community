// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.editor;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.pages.JavaColorSettingsPage;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ensures that Java attributes are not affected by changes in EditorColorScheme or these changes are planned and intentional.
 */
public class JavaEditorTextAttributesTest extends LightPlatformTestCase {

  private static List<AttributesDescriptor> getDescriptors() {
    JavaColorSettingsPage page = new JavaColorSettingsPage();
    List<AttributesDescriptor> result = Arrays.asList(page.getAttributeDescriptors());
    Collections.sort(result,
                     (o1, o2) -> o1.getKey().getExternalName().compareToIgnoreCase(o2.getKey().getExternalName()));
    return result;
  }

  private static void appendAttributes(@NotNull String name,
                                       @NotNull StringBuilder builder,
                                       @NotNull TextAttributes attributes,
                                       @NotNull Color defaultForeground) {
    builder.append(name).append(" {");
    appendHexColor(builder, "color", ObjectUtils.notNull(attributes.getForegroundColor(), defaultForeground));
    appendHexColor(builder, "background-color", attributes.getBackgroundColor());
    appendFontType(builder, attributes);
    appendEffects(builder, attributes);
    builder.append("}");
  }

  private static void appendEffects(@NotNull StringBuilder builder, @NotNull TextAttributes attributes) {
    if (attributes.getEffectColor() != null) {
      Pair<String,String> textStyle = effectTypeToTextStyle(attributes.getEffectType());
      builder.append("text-decoration: ").append(textStyle.first);
      builder.append(" #").append(ColorUtil.toHex(attributes.getEffectColor()));
      if (!textStyle.second.isEmpty()) {
        builder.append(textStyle.second);
      }
      builder.append("; ");
    }
  }

  private static Pair<String,String> effectTypeToTextStyle(@NotNull EffectType effectType) {
    switch (effectType) {
      case LINE_UNDERSCORE:
        return Pair.create("underline", "");
      case BOLD_LINE_UNDERSCORE:
        return Pair.create("underline", "bold");
      case STRIKEOUT:
        return Pair.create("line-through", "");
      case BOXED:
        return Pair.create("boxed", "");
      case WAVE_UNDERSCORE:
        return Pair.create("underline", "wavy");
      case BOLD_DOTTED_LINE:
        return Pair.create("underline", "dotted");
      case SEARCH_MATCH:
        break;
      case ROUNDED_BOX:
        break;
    }
    return Pair.create("??", "");
  }

  private static void appendFontType(@NotNull StringBuilder builder, @NotNull TextAttributes attributes) {
    builder.append(" font-style: ");
    int fontType = attributes.getFontType();
    builder.append((fontType & Font.ITALIC) != 0 ? "italic" : "normal").append("; ");
    if ((fontType & Font.BOLD) != 0) {
      builder.append(" font-weight: bold").append("; ");
    }
  }

  private static void appendHexColor(@NotNull StringBuilder builder, @NotNull String title, @Nullable Color color) {
    if (color != null) {
      builder.append(" ").append(title).append(": #").append(ColorUtil.toHex(color)).append("; ");
    }
  }

  private static String dumpDefaultColorScheme(@NotNull String name) {
    StringBuilder dumpBuilder = new StringBuilder();
    EditorColorsScheme baseScheme = DefaultColorSchemesManager.getInstance().getScheme(name);
    EditorColorsScheme scheme = new EditorColorsSchemeImpl(baseScheme);
    Color defaultForeground = scheme.getDefaultForeground();
    for (AttributesDescriptor descriptor : getDescriptors()) {
      TextAttributes attributes = scheme.getAttributes(descriptor.getKey());
      appendAttributes(descriptor.getKey().getExternalName(), dumpBuilder, attributes, defaultForeground);
      dumpBuilder.append("\n");
    }
    return dumpBuilder.toString();
  }

  public void testDefaultColorScheme() {
    assertEquals(
      "ABSTRACT_CLASS_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "ABSTRACT_METHOD_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "ANNOTATION_NAME_ATTRIBUTES { color: #808000;  font-style: normal; }\n" +
      "ANONYMOUS_CLASS_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "CLASS_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "CONSTRUCTOR_CALL_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "CONSTRUCTOR_DECLARATION_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "DOC_COMMENT_TAG_VALUE { color: #3d3d3d;  font-style: italic;  font-weight: bold; }\n" +
      "ENUM_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES { color: #660e7a;  font-style: normal; }\n" +
      "INHERITED_METHOD_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "INSTANCE_FIELD_ATTRIBUTES { color: #660e7a;  font-style: normal;  font-weight: bold; }\n" +
      "INSTANCE_FINAL_FIELD_ATTRIBUTES { color: #660e7a;  font-style: normal;  font-weight: bold; }\n" +
      "INTERFACE_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "JAVA_BLOCK_COMMENT { color: #808080;  font-style: italic; }\n" +
      "JAVA_BRACES { color: #000000;  font-style: normal; }\n" +
      "JAVA_BRACKETS { color: #000000;  font-style: normal; }\n" +
      "JAVA_COMMA { color: #000000;  font-style: normal; }\n" +
      "JAVA_DOC_COMMENT { color: #808080;  font-style: italic; }\n" +
      "JAVA_DOC_MARKUP { color: #000000;  background-color: #e2ffe2;  font-style: normal; }\n" +
      "JAVA_DOC_TAG { color: #000000;  font-style: normal;  font-weight: bold; text-decoration: underline #808080; }\n" +
      "JAVA_DOT { color: #000000;  font-style: normal; }\n" +
      "JAVA_INVALID_STRING_ESCAPE { color: #008000;  background-color: #ffcccc;  font-style: normal; }\n" +
      "JAVA_KEYWORD { color: #000080;  font-style: normal;  font-weight: bold; }\n" +
      "JAVA_LINE_COMMENT { color: #808080;  font-style: italic; }\n" +
      "JAVA_NUMBER { color: #0000ff;  font-style: normal; }\n" +
      "JAVA_OPERATION_SIGN { color: #000000;  font-style: normal; }\n" +
      "JAVA_PARENTH { color: #000000;  font-style: normal; }\n" +
      "JAVA_SEMICOLON { color: #000000;  font-style: normal; }\n" +
      "JAVA_STRING { color: #008000;  font-style: normal;  font-weight: bold; }\n" +
      "JAVA_VALID_STRING_ESCAPE { color: #000080;  font-style: normal;  font-weight: bold; }\n" +
      "LAMBDA_PARAMETER_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "LOCAL_VARIABLE_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "METHOD_CALL_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "METHOD_DECLARATION_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "PARAMETER_ATTRIBUTES { color: #000000;  font-style: normal; }\n" +
      "REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES { color: #000000;  font-style: normal; text-decoration: underline #909090; }\n" +
      "REASSIGNED_PARAMETER_ATTRIBUTES { color: #000000;  font-style: normal; text-decoration: underline #909090; }\n" +
      "STATIC_FIELD_ATTRIBUTES { color: #660e7a;  font-style: italic; }\n" +
      "STATIC_FIELD_IMPORTED_ATTRIBUTES { color: #660e7a;  font-style: italic; }\n" +
      "STATIC_FINAL_FIELD_ATTRIBUTES { color: #660e7a;  font-style: italic;  font-weight: bold; }\n" +
      "STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES { color: #660e7a;  font-style: italic;  font-weight: bold; }\n" +
      "STATIC_METHOD_ATTRIBUTES { color: #000000;  font-style: italic; }\n" +
      "STATIC_METHOD_IMPORTED_ATTRIBUTES { color: #000000;  font-style: italic; }\n" +
      "TYPE_PARAMETER_NAME_ATTRIBUTES { color: #20999d;  font-style: normal; }\n",

      dumpDefaultColorScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME));
  }

  public void testDarculaColorScheme() {
    assertEquals(
      "ABSTRACT_CLASS_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "ABSTRACT_METHOD_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES { color: #d0d0ff;  font-style: normal; }\n" +
      "ANNOTATION_NAME_ATTRIBUTES { color: #bbb529;  font-style: normal; }\n" +
      "ANONYMOUS_CLASS_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "CLASS_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "CONSTRUCTOR_CALL_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "CONSTRUCTOR_DECLARATION_ATTRIBUTES { color: #ffc66d;  font-style: normal; }\n" +
      "DOC_COMMENT_TAG_VALUE { color: #8a653b;  font-style: normal; }\n" +
      "ENUM_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES { color: #b389c5;  font-style: normal; text-decoration: underline #b389c5; }\n" +
      "INHERITED_METHOD_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "INSTANCE_FIELD_ATTRIBUTES { color: #9876aa;  font-style: normal; }\n" +
      "INSTANCE_FINAL_FIELD_ATTRIBUTES { color: #9876aa;  font-style: normal; }\n" +
      "INTERFACE_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "JAVA_BLOCK_COMMENT { color: #808080;  font-style: normal; }\n" +
      "JAVA_BRACES { color: #a9b7c6;  font-style: normal; }\n" +
      "JAVA_BRACKETS { color: #a9b7c6;  font-style: normal; }\n" +
      "JAVA_COMMA { color: #cc7832;  font-style: normal; }\n" +
      "JAVA_DOC_COMMENT { color: #629755;  font-style: italic; }\n" +
      "JAVA_DOC_MARKUP { color: #77b767;  font-style: normal; }\n" +
      "JAVA_DOC_TAG { color: #629755;  font-style: italic;  font-weight: bold; text-decoration: underline #629755; }\n" +
      "JAVA_DOT { color: #a9b7c6;  font-style: normal; }\n" +
      "JAVA_INVALID_STRING_ESCAPE { color: #6a8759;  font-style: normal; text-decoration: underline #ff0000wavy; }\n" +
      "JAVA_KEYWORD { color: #cc7832;  font-style: normal; }\n" +
      "JAVA_LINE_COMMENT { color: #808080;  font-style: normal; }\n" +
      "JAVA_NUMBER { color: #6897bb;  font-style: normal; }\n" +
      "JAVA_OPERATION_SIGN { color: #a9b7c6;  font-style: normal; }\n" +
      "JAVA_PARENTH { color: #a9b7c6;  font-style: normal; }\n" +
      "JAVA_SEMICOLON { color: #cc7832;  font-style: normal; }\n" +
      "JAVA_STRING { color: #6a8759;  font-style: normal; }\n" +
      "JAVA_VALID_STRING_ESCAPE { color: #cc7832;  font-style: normal; }\n" +
      "LAMBDA_PARAMETER_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "LOCAL_VARIABLE_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "METHOD_CALL_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "METHOD_DECLARATION_ATTRIBUTES { color: #ffc66d;  font-style: normal; }\n" +
      "PARAMETER_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }\n" +
      "REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES { color: #a9b7c6;  font-style: normal; text-decoration: underline #707d95; }\n" +
      "REASSIGNED_PARAMETER_ATTRIBUTES { color: #a9b7c6;  font-style: normal; text-decoration: underline #707d95; }\n" +
      "STATIC_FIELD_ATTRIBUTES { color: #9876aa;  font-style: italic; }\n" +
      "STATIC_FIELD_IMPORTED_ATTRIBUTES { color: #9876aa;  font-style: italic; }\n" +
      "STATIC_FINAL_FIELD_ATTRIBUTES { color: #9876aa;  font-style: italic; }\n" +
      "STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES { color: #9876aa;  font-style: italic; }\n" +
      "STATIC_METHOD_ATTRIBUTES { color: #a9b7c6;  font-style: italic; }\n" +
      "STATIC_METHOD_IMPORTED_ATTRIBUTES { color: #a9b7c6;  font-style: italic; }\n" +
      "TYPE_PARAMETER_NAME_ATTRIBUTES { color: #507874;  font-style: normal; }\n",

      dumpDefaultColorScheme("Darcula")
    );
  }
}
