// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.util.text.HtmlChunk;

public interface DocumentationMarkup {
  String DEFINITION_START = "<div class='definition'><pre>";
  String DEFINITION_END = "</pre></div>";
  String CONTENT_START = "<div class='content'>";
  String CONTENT_END = "</div>";
  String SECTIONS_START = "<table class='sections'>";
  String SECTIONS_END = "</table>";
  String SECTION_HEADER_START = "<tr><td valign='top' class='section'><p>";
  String SECTION_SEPARATOR = "</td><td valign='top'>";
  String SECTION_START = "<td valign='top'>";
  String SECTION_END = "</td>";
  String GRAYED_START = "<span class='grayed'>";
  String GRAYED_END = "</span>";

  HtmlChunk.Element SECTION_CONTENT_CELL = HtmlChunk.tag("td").attr("valign", "top");
  HtmlChunk.Element SECTION_HEADER_CELL = HtmlChunk.tag("td").attr("valign", "top").attr("class", "section");
  HtmlChunk.Element SECTIONS_TABLE = HtmlChunk.tag("table").attr("class", "sections");
  HtmlChunk.Element CONTENT_ELEMENT = HtmlChunk.div().attr("class", "content");
  HtmlChunk.Element DEFINITION_ELEMENT = HtmlChunk.div().attr("class", "definition");
}
