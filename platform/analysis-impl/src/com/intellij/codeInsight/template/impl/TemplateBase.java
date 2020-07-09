// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class TemplateBase extends Template {

  @NotNull private String myString;
  @Nullable private Throwable myBuildingTemplateTrace;
  private String myTemplateText;

  private List<Segment> mySegments;
  private boolean toParseSegments = true;

  public void parseSegments() {
    if(!isToParseSegments()) {
      return;
    }
    if(getSegments() != null) {
      return;
    }

    setSegments(new SmartList<>());
    StringBuilder buffer = new StringBuilder(getString().length());
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(getString());

    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = getString().substring(start, end);
      if (tokenType == TemplateTokenType.VARIABLE){
        String name = token.substring(1, token.length() - 1);
        Segment segment = new Segment(name, buffer.length());
        getSegments().add(segment);
      }
      else if (tokenType == TemplateTokenType.ESCAPE_DOLLAR){
        buffer.append("$");
      }
      else{
        buffer.append(token);
      }
      lexer.advance();
    }
    setTemplateText(buffer.toString());
  }

  protected List<Segment> getSegments() {
    return mySegments;
  }

  protected void setSegments(List<Segment> segments) {
    mySegments = segments;
  }

  protected boolean isToParseSegments() {
    return toParseSegments;
  }

  protected void setToParseSegments(boolean toParseSegments) {
    this.toParseSegments = toParseSegments;
  }

  @NotNull
  @Override
  public String getString() {
    parseSegments();
    return myString;
  }

  /**
   * Set template text as it appears in Live Template settings, including variables surrounded with '$'.
   * The text will be reparsed when needed.
   * @param string template string text
   */
  public void setString(@NotNull String string) {
    myString = StringUtil.convertLineSeparators(string);
    setSegments(null);
    setToParseSegments(true);
    setBuildingTemplateTrace(new Throwable());
  }

  @NotNull
  @Override
  public String getTemplateText() {
    parseSegments();
    return myTemplateText;
  }


  protected void setTemplateText(String templateText) {
    myTemplateText = templateText;
  }

  protected void setBuildingTemplateTrace(Throwable buildingTemplateTrace) {
    myBuildingTemplateTrace = buildingTemplateTrace;
  }

  @Nullable
  Throwable getBuildingTemplateTrace() {
    return myBuildingTemplateTrace;
  }

  int getVariableSegmentNumber(String variableName) {
    parseSegments();
    for (int i = 0; i < getSegments().size(); i++) {
      Segment segment = getSegments().get(i);
      if (segment.name.equals(variableName)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void addTextSegment(@NotNull String text) {
    text = StringUtil.convertLineSeparators(text);
    setTemplateText(getTemplateText() + text);
  }

  @Override
  public void addVariableSegment(@NotNull String name) {
    getSegments().add(new Segment(name, getTemplateText().length()));
  }

  @NotNull
  @Override
  public String getSegmentName(int i) {
    parseSegments();
    return getSegments().get(i).name;
  }

  @Override
  public int getSegmentOffset(int i) {
    parseSegments();
    return getSegments().get(i).offset;
  }

  @Override
  public int getSegmentsCount() {
    parseSegments();
    return getSegments().size();
  }

  protected static final class Segment {
    @NotNull
    public final String name;
    public final int offset;

    protected Segment(@NotNull String name, int offset) {
      this.name = name;
      this.offset = offset;
    }
  }
}
