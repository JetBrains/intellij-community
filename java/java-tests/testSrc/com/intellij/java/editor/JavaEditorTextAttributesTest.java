// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.editor;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
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

import static org.junit.Assert.assertNotEquals;

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

  /**
   * Ensures that Java attributes are not affected by changes in EditorColorScheme or these changes are planned and intentional.
   */
  public void testDefaultColorScheme() {
    assertEquals(
      """
        ABSTRACT_CLASS_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }
        ABSTRACT_METHOD_ATTRIBUTES { color: #000000;  font-style: normal; }
        ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }
        ANNOTATION_NAME_ATTRIBUTES { color: #808000;  font-style: normal; }
        ANONYMOUS_CLASS_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }
        CLASS_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }
        CONSTRUCTOR_CALL_ATTRIBUTES { color: #000000;  font-style: normal; }
        CONSTRUCTOR_DECLARATION_ATTRIBUTES { color: #000000;  font-style: normal; }
        DOC_COMMENT_TAG_VALUE { color: #3d3d3d;  font-style: italic;  font-weight: bold; }
        ENUM_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }
        IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES { color: #660e7a;  font-style: normal; }
        INHERITED_METHOD_ATTRIBUTES { color: #000000;  font-style: normal; }
        INSTANCE_FIELD_ATTRIBUTES { color: #660e7a;  font-style: normal;  font-weight: bold; }
        INSTANCE_FINAL_FIELD_ATTRIBUTES { color: #660e7a;  font-style: normal;  font-weight: bold; }
        INTERFACE_NAME_ATTRIBUTES { color: #000000;  font-style: normal; }
        JAVA_BLOCK_COMMENT { color: #808080;  font-style: italic; }
        JAVA_BRACES { color: #000000;  font-style: normal; }
        JAVA_BRACKETS { color: #000000;  font-style: normal; }
        JAVA_COMMA { color: #000000;  font-style: normal; }
        JAVA_DOC_COMMENT { color: #808080;  font-style: italic; }
        JAVA_DOC_MARKUP { color: #000000;  background-color: #e2ffe2;  font-style: normal; }
        JAVA_DOC_TAG { color: #000000;  font-style: normal;  font-weight: bold; text-decoration: underline #808080; }
        JAVA_DOT { color: #000000;  font-style: normal; }
        JAVA_INVALID_STRING_ESCAPE { color: #008000;  background-color: #ffcccc;  font-style: normal; }
        JAVA_KEYWORD { color: #000080;  font-style: normal;  font-weight: bold; }
        JAVA_LINE_COMMENT { color: #808080;  font-style: italic; }
        JAVA_NUMBER { color: #0000ff;  font-style: normal; }
        JAVA_OPERATION_SIGN { color: #000000;  font-style: normal; }
        JAVA_PARENTH { color: #000000;  font-style: normal; }
        JAVA_SEMICOLON { color: #000000;  font-style: normal; }
        JAVA_STRING { color: #008000;  font-style: normal;  font-weight: bold; }
        JAVA_VALID_STRING_ESCAPE { color: #000080;  font-style: normal;  font-weight: bold; }
        LAMBDA_PARAMETER_ATTRIBUTES { color: #000000;  font-style: normal; }
        LOCAL_VARIABLE_ATTRIBUTES { color: #000000;  font-style: normal; }
        METHOD_CALL_ATTRIBUTES { color: #000000;  font-style: normal; }
        METHOD_DECLARATION_ATTRIBUTES { color: #000000;  font-style: normal; }
        PACKAGE_PRIVATE_REFERENCE { color: #000000;  font-style: normal; }
        PARAMETER_ATTRIBUTES { color: #000000;  font-style: normal; }
        PRIVATE_REFERENCE { color: #000000;  font-style: normal; }
        PROTECTED_REFERENCE { color: #000000;  font-style: normal; }
        PUBLIC_REFERENCE { color: #000000;  font-style: normal; }
        REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES { color: #000000;  font-style: normal; text-decoration: underline #909090; }
        REASSIGNED_PARAMETER_ATTRIBUTES { color: #000000;  font-style: normal; text-decoration: underline #909090; }
        STATIC_FIELD_ATTRIBUTES { color: #660e7a;  font-style: italic; }
        STATIC_FIELD_IMPORTED_ATTRIBUTES { color: #660e7a;  font-style: italic; }
        STATIC_FINAL_FIELD_ATTRIBUTES { color: #660e7a;  font-style: italic;  font-weight: bold; }
        STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES { color: #660e7a;  font-style: italic;  font-weight: bold; }
        STATIC_METHOD_ATTRIBUTES { color: #000000;  font-style: italic; }
        STATIC_METHOD_IMPORTED_ATTRIBUTES { color: #000000;  font-style: italic; }
        TYPE_PARAMETER_NAME_ATTRIBUTES { color: #20999d;  font-style: normal; }
        """,

      dumpDefaultColorScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME));
  }

  public void testDarculaColorScheme() {
    assertEquals(
      """
        ABSTRACT_CLASS_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        ABSTRACT_METHOD_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        ANNOTATION_NAME_ATTRIBUTES { color: #bbb529;  font-style: normal; }
        ANONYMOUS_CLASS_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        CLASS_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        CONSTRUCTOR_CALL_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        CONSTRUCTOR_DECLARATION_ATTRIBUTES { color: #ffc66d;  font-style: normal; }
        DOC_COMMENT_TAG_VALUE { color: #8a653b;  font-style: normal; }
        ENUM_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES { color: #b389c5;  font-style: normal; text-decoration: underline #b389c5; }
        INHERITED_METHOD_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        INSTANCE_FIELD_ATTRIBUTES { color: #9876aa;  font-style: normal; }
        INSTANCE_FINAL_FIELD_ATTRIBUTES { color: #9876aa;  font-style: normal; }
        INTERFACE_NAME_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        JAVA_BLOCK_COMMENT { color: #808080;  font-style: normal; }
        JAVA_BRACES { color: #a9b7c6;  font-style: normal; }
        JAVA_BRACKETS { color: #a9b7c6;  font-style: normal; }
        JAVA_COMMA { color: #cc7832;  font-style: normal; }
        JAVA_DOC_COMMENT { color: #629755;  font-style: italic; }
        JAVA_DOC_MARKUP { color: #77b767;  font-style: normal; }
        JAVA_DOC_TAG { color: #629755;  font-style: italic;  font-weight: bold; text-decoration: underline #629755; }
        JAVA_DOT { color: #a9b7c6;  font-style: normal; }
        JAVA_INVALID_STRING_ESCAPE { color: #6a8759;  font-style: normal; text-decoration: underline #ff0000wavy; }
        JAVA_KEYWORD { color: #cc7832;  font-style: normal; }
        JAVA_LINE_COMMENT { color: #808080;  font-style: normal; }
        JAVA_NUMBER { color: #6897bb;  font-style: normal; }
        JAVA_OPERATION_SIGN { color: #a9b7c6;  font-style: normal; }
        JAVA_PARENTH { color: #a9b7c6;  font-style: normal; }
        JAVA_SEMICOLON { color: #cc7832;  font-style: normal; }
        JAVA_STRING { color: #6a8759;  font-style: normal; }
        JAVA_VALID_STRING_ESCAPE { color: #cc7832;  font-style: normal; }
        LAMBDA_PARAMETER_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        LOCAL_VARIABLE_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        METHOD_CALL_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        METHOD_DECLARATION_ATTRIBUTES { color: #ffc66d;  font-style: normal; }
        PACKAGE_PRIVATE_REFERENCE { color: #a9b7c6;  font-style: normal; }
        PARAMETER_ATTRIBUTES { color: #a9b7c6;  font-style: normal; }
        PRIVATE_REFERENCE { color: #a9b7c6;  font-style: normal; }
        PROTECTED_REFERENCE { color: #a9b7c6;  font-style: normal; }
        PUBLIC_REFERENCE { color: #a9b7c6;  font-style: normal; }
        REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES { color: #a9b7c6;  font-style: normal; text-decoration: underline #707d95; }
        REASSIGNED_PARAMETER_ATTRIBUTES { color: #a9b7c6;  font-style: normal; text-decoration: underline #707d95; }
        STATIC_FIELD_ATTRIBUTES { color: #9876aa;  font-style: italic; }
        STATIC_FIELD_IMPORTED_ATTRIBUTES { color: #9876aa;  font-style: italic; }
        STATIC_FINAL_FIELD_ATTRIBUTES { color: #9876aa;  font-style: italic; }
        STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES { color: #9876aa;  font-style: italic; }
        STATIC_METHOD_ATTRIBUTES { color: #a9b7c6;  font-style: italic; }
        STATIC_METHOD_IMPORTED_ATTRIBUTES { color: #a9b7c6;  font-style: italic; }
        TYPE_PARAMETER_NAME_ATTRIBUTES { color: #507874;  font-style: normal; }
        """,

      dumpDefaultColorScheme("Darcula")
    );
  }

  public void testInheritanceFlagCanBeOverwrittenWithDefaultAttributes() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme editorScheme = (EditorColorsScheme)defaultScheme.clone();
    TextAttributes localVarAttrs = defaultScheme.getAttributes(JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
    editorScheme.setAttributes(JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES, localVarAttrs);
    TextAttributes changedAttrs = new TextAttributes(Color.BLUE, Color.WHITE, null, EffectType.BOXED, 0);
    editorScheme.setAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, changedAttrs);
    TextAttributes attributes = editorScheme.getAttributes(JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
    assertNotEquals(attributes, changedAttrs);
  }
}
