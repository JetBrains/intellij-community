// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class TemplateBase extends Template {

  private @NotNull String myString;
  private @Nullable Throwable myBuildingTemplateTrace;
  private String myTemplateText;

  private final List<Segment> mySegments;
  private boolean toParseSegments = true;
  private boolean myParsed = false;

  protected TemplateBase(@NotNull String string) {
    mySegments = Collections.synchronizedList(new SmartList<>());
    myString = string;
  }

  public boolean isParsed() {
    synchronized (mySegments) {
      return myParsed;
    }
  }
  
  public void parseSegments() {
    if (!toParseSegments) {
      return;
    }

    String templateRawText = myString;
    StringBuilder buffer;
    synchronized (mySegments) {
      if (myParsed) {
        return;
      }

      buffer = new StringBuilder(templateRawText.length());
      TemplateTextLexer lexer = new TemplateTextLexer();
      lexer.start(templateRawText);

      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == null) break;
        int start = lexer.getTokenStart();
        int end = lexer.getTokenEnd();
        String token = templateRawText.substring(start, end);
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
      myParsed = true;
    }
    myTemplateText = buffer.toString();
  }

  protected List<Segment> getSegments() {
    return mySegments;
  }

  protected void clearSegments() {
    synchronized (mySegments) {
      mySegments.clear();
      myParsed = false;
    }
  }

  protected boolean isToParseSegments() {
    return toParseSegments;
  }

  protected void setToParseSegments(boolean toParseSegments) {
    this.toParseSegments = toParseSegments;
  }

  @Override
  public @NotNull String getString() {
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
    clearSegments();
    toParseSegments = true;
    myBuildingTemplateTrace = new Throwable();
  }

  @Override
  public @NotNull String getTemplateText() {
    parseSegments();
    return myTemplateText;
  }

  protected String templateText() {
    return myTemplateText;
  }

  protected void setTemplateText(String templateText) {
    myTemplateText = templateText;
  }

  protected void setBuildingTemplateTrace(@Nullable Throwable buildingTemplateTrace) {
    myBuildingTemplateTrace = buildingTemplateTrace;
  }

  @Nullable
  Throwable getBuildingTemplateTrace() {
    return myBuildingTemplateTrace;
  }

  int getVariableSegmentNumber(String variableName) {
    parseSegments();
    synchronized (mySegments) {
      for (int i = 0; i < mySegments.size(); i++) {
        if (mySegments.get(i).name.equals(variableName)) {
          return i;
        }
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

  @Override
  public @NotNull String getSegmentName(int i) {
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

  public boolean isSelectionTemplate() {
    parseSegments();
    synchronized (mySegments) {
      for (Segment v : mySegments) {
        if (SELECTION.equals(v.name)) return true;
      }
    }
    return ContainerUtil.exists(getVariables(),
                                v -> containsSelection(v.getExpression()) || containsSelection(v.getDefaultValueExpression()));
  }

  private static boolean containsSelection(Expression expression) {
    if (expression instanceof VariableNode) {
      return SELECTION.equals(((VariableNode)expression).getName());
    }
    if (expression instanceof MacroCallNode) {
      return ContainerUtil.exists(((MacroCallNode)expression).getParameters(), TemplateBase::containsSelection);
    }
    return false;
  }
  
  protected static final class Segment {
    public final @NotNull String name;
    public final int offset;

    private Segment(@NotNull String name, int offset) {
      this.name = name;
      this.offset = offset;
    }
  }
}
