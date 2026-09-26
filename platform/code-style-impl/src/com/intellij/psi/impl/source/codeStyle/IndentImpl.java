// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.Indent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class IndentImpl implements Indent{
  private final CodeStyleSettings mySettings;
  private final int myIndentLevel;
  private final int mySpaceCount;
  private final FileType myFileType;

  public IndentImpl(CodeStyleSettings settings, int indentLevel, int spaceCount, FileType fileType) {
    mySettings = settings;
    myIndentLevel = indentLevel;
    mySpaceCount = spaceCount;
    myFileType = fileType;
  }

  int getIndentLevel() {
    return myIndentLevel;
  }

  int getSpaceCount() {
    return mySpaceCount;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof IndentImpl indent)) return false;

    if (myIndentLevel != indent.myIndentLevel) return false;
    if (mySpaceCount != indent.mySpaceCount) return false;
    if (!mySettings.equals(indent.mySettings)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myIndentLevel + mySpaceCount;
  }

  @Override
  public boolean isGreaterThan(Indent indent) {
    return getSize() > ((IndentImpl)indent).getSize();
  }

  @Override
  public Indent min(Indent anotherIndent) {
    return isGreaterThan(anotherIndent) ? anotherIndent : this;
  }

  @Override
  public Indent max(Indent anotherIndent) {
    return isGreaterThan(anotherIndent) ? this : anotherIndent;
  }

  @Override
  public Indent add(Indent indent) {
    IndentImpl indent1 = (IndentImpl)indent;
    return new IndentImpl(mySettings, myIndentLevel + indent1.myIndentLevel, mySpaceCount + indent1.mySpaceCount, myFileType);
  }

  @Override
  public Indent subtract(Indent indent) {
    IndentImpl indent1 = (IndentImpl)indent;
    return new IndentImpl(mySettings, myIndentLevel - indent1.myIndentLevel, mySpaceCount - indent1.mySpaceCount, myFileType);
  }

  @Override
  public boolean isZero() {
    return myIndentLevel == 0 && mySpaceCount == 0;
  }

  private int getSize(){
    return myIndentLevel * mySettings.getIndentSize(myFileType) + mySpaceCount;
  }
}
