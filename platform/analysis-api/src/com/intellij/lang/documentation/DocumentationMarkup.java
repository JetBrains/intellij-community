// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.ApiStatus;

/**
 * HTML building blocks for content layout in {@link DocumentationProvider#generateDoc}.
 */
public interface DocumentationMarkup {

  @ApiStatus.Internal String CLASS_DEFINITION = "definition";
  @ApiStatus.Internal String CLASS_CONTENT = "content";
  @ApiStatus.Internal String CLASS_SECTIONS = "sections";
  @ApiStatus.Internal String CLASS_SECTION = "section";
  @ApiStatus.Internal String CLASS_GRAYED = "grayed";
  @ApiStatus.Internal String CLASS_CENTERED = "centered";
  @ApiStatus.Internal String CLASS_BOTTOM = "bottom";
  @ApiStatus.Internal String CLASS_TOP = "top";

  @NlsSafe String DEFINITION_START = "<div class='" + CLASS_DEFINITION + "'><pre>";
  @NlsSafe String DEFINITION_END = "</pre></div>";
  @NlsSafe String CONTENT_START = "<div class='" + CLASS_CONTENT + "'>";
  @NlsSafe String CONTENT_END = "</div>";
  @NlsSafe String SECTIONS_START = "<table class='" + CLASS_SECTIONS + "'>";
  @NlsSafe String SECTIONS_END = "</table>";
  @NlsSafe String SECTION_HEADER_START = "<tr><td valign='top' class='" + CLASS_SECTION + "'><p>";
  @NlsSafe String SECTION_SEPARATOR = "</td><td valign='top'>";
  @NlsSafe String SECTION_START = "<td valign='top'>";
  @NlsSafe String SECTION_END = "</td>";
  @NlsSafe String GRAYED_START = "<span class='" + CLASS_GRAYED + "'>";
  @NlsSafe String GRAYED_END = "</span>";

  HtmlChunk.Element SECTION_CONTENT_CELL = HtmlChunk.tag("td").attr("valign", "top");
  HtmlChunk.Element SECTION_HEADER_CELL = HtmlChunk.tag("td").attr("valign", "top").setClass(CLASS_SECTION);
  HtmlChunk.Element SECTIONS_TABLE = HtmlChunk.tag("table").setClass(CLASS_SECTIONS);
  HtmlChunk.Element CONTENT_ELEMENT = HtmlChunk.div().setClass(CLASS_CONTENT);
  HtmlChunk.Element DEFINITION_ELEMENT = HtmlChunk.div().setClass(CLASS_DEFINITION);
  HtmlChunk.Element TOP_ELEMENT = HtmlChunk.div().setClass(CLASS_TOP);
  HtmlChunk.Element BOTTOM_ELEMENT = HtmlChunk.div().setClass(CLASS_BOTTOM);
  HtmlChunk.Element PRE_ELEMENT = HtmlChunk.tag("pre");
  HtmlChunk.Element GRAYED_ELEMENT = HtmlChunk.span().setClass(CLASS_GRAYED);
  HtmlChunk.Element CENTERED_ELEMENT = HtmlChunk.p().setClass(CLASS_CENTERED);
  HtmlChunk.Element EXTERNAL_LINK_ICON = HtmlChunk.tag("icon").attr("src", "AllIcons.Ide.External_link_arrow");
  HtmlChunk.Element INFORMATION_ICON = HtmlChunk.tag("icon").attr("src", "AllIcons.General.Information");
}
