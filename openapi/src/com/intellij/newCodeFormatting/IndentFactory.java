package com.intellij.newCodeFormatting;

interface IndentFactory {
  public abstract Indent createNormalIndent();
  public abstract Indent createNormalIndent(int count);
  public abstract Indent createAbsoluteNormalIndent();
  public abstract Indent getNoneIndent();
  public abstract Indent createAbsoluteNoneIndent();
  public abstract Indent createAbsoluteLabelIndent();
  public abstract Indent createLabelIndent();
  public abstract Indent createContinuationIndent();
  public abstract Indent createContinuationWithoutFirstIndent();//is default
  public abstract Indent createSpaceIndent(final int spaces);
}
