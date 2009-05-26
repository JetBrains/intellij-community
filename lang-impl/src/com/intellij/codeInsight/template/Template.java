package com.intellij.codeInsight.template;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class Template {
  public abstract void addTextSegment(@NotNull String text);
  public abstract void addVariableSegment(@NonNls String name);

  public void addVariable(@NonNls String name, @NotNull Expression defaultValueExpression, boolean isAlwaysStopAt) {
    addVariable(name, defaultValueExpression, defaultValueExpression, isAlwaysStopAt);     
  }
  public abstract void addVariable(@NonNls String name, Expression expression, Expression defaultValueExpression, boolean isAlwaysStopAt);
  public abstract void addVariable(@NonNls String name, @NonNls String expression, @NonNls String defaultValueExpression, boolean isAlwaysStopAt);

  public abstract void addEndVariable();
  public abstract void addSelectionStartVariable();
  public abstract void addSelectionEndVariable();

  public abstract String getId();
  public abstract String getKey();

  public abstract String getDescription();

  public abstract void setToReformat(boolean toReformat);

  public abstract void setToIndent(boolean toIndent);

  public abstract void setInline(boolean isInline);

  public abstract int getSegmentsCount();

  public abstract String getSegmentName( int segmentIndex);

  public abstract int getSegmentOffset(int segmentIndex);

  public abstract String getTemplateText();

  public abstract boolean isToShortenLongNames();
  public abstract void setToShortenLongNames(boolean toShortenLongNames);
}
