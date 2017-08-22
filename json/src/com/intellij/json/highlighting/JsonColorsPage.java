package com.intellij.json.highlighting;

import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.intellij.json.highlighting.JsonSyntaxHighlighterFactory.*;

/**
 * @author Mikhail Golubev
 */
public class JsonColorsPage implements ColorSettingsPage, DisplayPrioritySortable {
  private static final Map<String, TextAttributesKey> ourAdditionalHighlighting = ImmutableMap.of("propertyKey", JSON_PROPERTY_KEY);

  private static final AttributesDescriptor[] ourAttributeDescriptors = new AttributesDescriptor[]{
    new AttributesDescriptor("Property key", JSON_PROPERTY_KEY),

    new AttributesDescriptor("Braces", JSON_BRACES),
    new AttributesDescriptor("Brackets", JSON_BRACKETS),
    new AttributesDescriptor("Comma", JSON_COMMA),
    new AttributesDescriptor("Colon", JSON_COLON),
    new AttributesDescriptor("Number", JSON_NUMBER),
    new AttributesDescriptor("String", JSON_STRING),
    new AttributesDescriptor("Keyword", JSON_KEYWORD),
    new AttributesDescriptor("Line comment", JSON_LINE_COMMENT),
    new AttributesDescriptor("Block comment", JSON_BLOCK_COMMENT),
    //new AttributesDescriptor("", JSON_IDENTIFIER),
    new AttributesDescriptor("Valid escape sequence", JSON_VALID_ESCAPE),
    new AttributesDescriptor("Invalid escape sequence", JSON_INVALID_ESCAPE),
  };

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Json;
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(JsonLanguage.INSTANCE, null, null);
  }

  @NotNull
  @Override
  public String getDemoText() {
    return "{\n" +
           "  // Line comments are not included in standard but nonetheless allowed.\n" +
           "  /* As well as block comments. */\n" +
           "  <propertyKey>\"the only keywords are\"</propertyKey>: [true, false, null],\n" +
           "  <propertyKey>\"strings with\"</propertyKey>: {\n" +
           "    <propertyKey>\"no excapes\"</propertyKey>: \"pseudopolinomiality\"\n" +
           "    <propertyKey>\"valid escapes\"</propertyKey>: \"C-style\\r\\n and unicode\\u0021\",\n" +
           "    <propertyKey>\"illegal escapes\"</propertyKey>: \"\\0377\\x\\\"\n" +
           "  },\n" +
           "  <propertyKey>\"some numbers\"</propertyKey>: [\n" +
           "    42,\n" +
           "    -0.0e-0,\n" +
           "    6.626e-34\n" +
           "  ] \n" +
           "}";
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourAdditionalHighlighting;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ourAttributeDescriptors;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "JSON";
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.LANGUAGE_SETTINGS;
  }
}
