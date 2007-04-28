package com.intellij.codeInsight.template;

import org.jetbrains.annotations.NonNls;

public interface Template {
  void addTextSegment(String text);
  void addVariableSegment(@NonNls String name);

  void addVariable(@NonNls String name, Expression expression, Expression defaultValueExpression, boolean isAlwaysStopAt);
  void addVariable(@NonNls String name, @NonNls String expression, @NonNls String defaultValueExpression, boolean isAlwaysStopAt);

  void addEndVariable();
  void addSelectionStartVariable();
  void addSelectionEndVariable();

  String getId();
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
