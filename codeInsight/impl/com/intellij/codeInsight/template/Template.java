package com.intellij.codeInsight.template;

public interface Template {
  void addTextSegment(String text);
  void addVariableSegment(String name);

  void addVariable(String name, Expression expression, Expression defaultValueExpression, boolean isAlwaysStopAt);
  void addVariable(String name, String expression, String defaultValueExpression, boolean isAlwaysStopAt);

  void addEndVariable();
  void addSelectionStartVariable();
  void addSelectionEndVariable();

  String getKey();

  String getDescription();

  void setToReformat(boolean toReformat);

  void setToIndent(boolean toIndent);

  void setInline(boolean isInline);

  int getSegmentsCount();

  String getSegmentName( int segmentIndex);

  int getSegmentOffset(int segmentIndex);

  String getTemplateText();

  void setToShortenLongNames(boolean toShortenLongNames);
}
