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
    if(!toParseSegments) {
      return;
    }
    if(mySegments != null) {
      return;
    }

    mySegments = new SmartList<>();
    StringBuilder buffer = new StringBuilder(myString.length());
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(myString);

    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = myString.substring(start, end);
      if (tokenType == TemplateTokenType.VARIABLE){
        String name = token.substring(1, token.length() - 1);
        Segment segment = new Segment(name, buffer.length());
        mySegments.add(segment);
      }
      else if (tokenType == TemplateTokenType.ESCAPE_DOLLAR){
        buffer.append("$");
      }
      else{
        buffer.append(token);
      }
      lexer.advance();
    }
    myTemplateText = buffer.toString();
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

  protected String string() {
    return myString;
  }

  /**
   * Set template text as it appears in Live Template settings, including variables surrounded with '$'.
   * The text will be reparsed when needed.
   * @param string template string text
   */
  public void setString(@NotNull String string) {
    myString = StringUtil.convertLineSeparators(string);
    mySegments = null;
    toParseSegments = true;
    myBuildingTemplateTrace = new Throwable();
  }

  @NotNull
  @Override
  public String getTemplateText() {
    parseSegments();
    return myTemplateText;
  }

  protected String templateText() {
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
    for (int i = 0; i < mySegments.size(); i++) {
      Segment segment = mySegments.get(i);
      if (segment.name.equals(variableName)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void addTextSegment(@NotNull String text) {
    text = StringUtil.convertLineSeparators(text);
    myTemplateText += text;
  }

  @Override
  public void addVariableSegment(@NotNull String name) {
    mySegments.add(new Segment(name, myTemplateText.length()));
  }

  @NotNull
  @Override
  public String getSegmentName(int i) {
    parseSegments();
    return mySegments.get(i).name;
  }

  @Override
  public int getSegmentOffset(int i) {
    parseSegments();
    return mySegments.get(i).offset;
  }

  @Override
  public int getSegmentsCount() {
    parseSegments();
    return mySegments.size();
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
