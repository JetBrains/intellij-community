/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options.colors.pages;

import com.intellij.application.options.colors.FontEditorPreview;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class GeneralColorsPage implements ColorSettingsPage, InspectionColorSettingsPage {
  private static final String ADDITIONAL_DEMO_TEXT =
    "\n" +
    "<todo>//TODO: Visit JB Web resources:</todo>\n"+
    "JetBrains Home Page: <hyperlink_f>http://www.jetbrains.com</hyperlink_f>\n" +
    "JetBrains Developer Community: <hyperlink>http://www.jetbrains.net/devnet</hyperlink>\n" +
    "\n" +
    "Search:\n" +
    "  <search_result_wr>result</search_result_wr> = \"<search_text>text</search_text>, <search_text>text</search_text>, <search_text>text</search_text>\";\n" +
    "  <identifier_write>i</identifier_write> = <search_result>result</search_result>\n" +
    "  return <identifier>i;</identifier>\n" +
    "\n" +
    "<folded_text>Folded text</folded_text>\n" +
    "Template <template_var>VARIABLE</template_var>\n" +
    "Injected language: <injected_lang>\\.(gif|jpg|png)$</injected_lang>\n" +
    "\n" +
    "Code Inspections:\n" +
    "  <error>Error</error>\n" +
    "  <warning>Warning</warning>\n" +
    "  <info>Info</info>\n" +
    "  <deprecated>Deprecated symbol</deprecated>\n" +
    "  <unused>Unused symbol</unused>\n"+
    "  <wrong_ref>Unknown symbol</wrong_ref>\n" +
    "  <server_error>Problem from server</server_error>\n" +
    "  <server_duplicate>Duplicate from server</server_duplicate>\n" +
    "\n" +
    "Console:\n" +
    "<stdsys>C:\\command.com</stdsys>\n" +
    "-<stdout> C:></stdout>\n" +
    "-<stdin> help</stdin>\n" +
    "<stderr>Bad command or file name</stderr>\n" +
    "<stdsys>Process finished with exit code 1</stdsys>\n" +
    "\n";

  private static final AttributesDescriptor[] ATT_DESCRIPTORS = {
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.default.text"), HighlighterColors.TEXT),

    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.folded.text"), EditorColors.FOLDED_TEXT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.search.result"), EditorColors.SEARCH_RESULT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.search.result.write.access"), EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptior.identifier.under.caret"), EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptior.identifier.under.caret.write"), EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.text.search.result"), EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES),

    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.template.variable"), TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.injected.language.fragment"), EditorColors.INJECTED_LANGUAGE_FRAGMENT),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.console.stdout"), ConsoleViewContentType.NORMAL_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.console.stderr"), ConsoleViewContentType.ERROR_OUTPUT_KEY),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.console.stdin"), ConsoleViewContentType.USER_INPUT_KEY),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.console.system.output"), ConsoleViewContentType.SYSTEM_OUTPUT_KEY),

    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.hyperlink.new"), CodeInsightColors.HYPERLINK_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.hyperlink.followed"), CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES),

    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.todo.defaults"), CodeInsightColors.TODO_DEFAULT_ATTRIBUTES),
  };

  private static final ColorDescriptor[] COLOR_DESCRIPTORS = {
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.background.in.readonly.files"), EditorColors.READONLY_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.deneral.color.descriptor.readonly.fragment.background"), EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.gutter.background"), EditorColors.LEFT_GUTTER_BACKGROUND, ColorDescriptor.Kind.BACKGROUND),

    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.background"), EditorColors.SELECTION_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.foreground"), EditorColors.SELECTION_FOREGROUND_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.caret"), EditorColors.CARET_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.caret.row"), EditorColors.CARET_ROW_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.right.margin"), EditorColors.RIGHT_MARGIN_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.whitespaces"), EditorColors.WHITESPACES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.indent.guide"), EditorColors.INDENT_GUIDE_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.indent.guide.selected"), EditorColors.SELECTED_INDENT_GUIDE_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.line.number"), EditorColors.LINE_NUMBERS_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.annotations"), EditorColors.ANNOTATIONS_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.annotations.merged"), EditorColors.ANNOTATIONS_MERGED_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.folding.outline"), EditorColors.FOLDING_TREE_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selected.folding.outline"), EditorColors.SELECTED_FOLDING_TREE_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.added.lines"), EditorColors.ADDED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.modified.lines"), EditorColors.MODIFIED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.java.color.descriptor.method.separator.color"), CodeInsightColors.METHOD_SEPARATORS_COLOR, ColorDescriptor.Kind.FOREGROUND),

    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.console.background"), ConsoleViewContentType.CONSOLE_BACKGROUND_KEY, ColorDescriptor.Kind.BACKGROUND),
  };

  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new HashMap<String, TextAttributesKey>();

  static{
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("folded_text", EditorColors.FOLDED_TEXT_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("search_result", EditorColors.SEARCH_RESULT_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("search_result_wr", EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("search_text", EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("identifier", EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("identifier_write", EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("template_var", TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("injected_lang", EditorColors.INJECTED_LANGUAGE_FRAGMENT);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stdsys", ConsoleViewContentType.SYSTEM_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stdout", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stdin", ConsoleViewContentType.USER_INPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("stderr", ConsoleViewContentType.ERROR_OUTPUT_KEY);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("todo", CodeInsightColors.TODO_DEFAULT_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("hyperlink", CodeInsightColors.HYPERLINK_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("hyperlink_f", CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);

    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("wrong_ref", CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("unused", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("error", CodeInsightColors.ERRORS_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("warning", CodeInsightColors.WARNINGS_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("info", CodeInsightColors.INFO_ATTRIBUTES);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("server_error", CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("server_duplicate", CodeInsightColors.DUPLICATE_FROM_SERVER);
  }

  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("options.general.display.name");
  }

  public Icon getIcon() {
    return FileTypes.PLAIN_TEXT.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATT_DESCRIPTORS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return COLOR_DESCRIPTORS;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull
  public String getDemoText() {
    return FontEditorPreview.getIDEDemoText() + ADDITIONAL_DEMO_TEXT;
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }
}
