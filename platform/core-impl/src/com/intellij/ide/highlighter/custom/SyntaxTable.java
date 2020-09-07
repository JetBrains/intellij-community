// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter.custom;

import com.intellij.ide.highlighter.custom.tokens.KeywordParser;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public final class SyntaxTable implements Cloneable {
  private Set<String> myKeywords1 = new HashSet<>();
  private Set<String> myKeywords2 = new HashSet<>();
  private Set<String> myKeywords3 = new HashSet<>();
  private Set<String> myKeywords4 = new HashSet<>();

  private String myLineComment = "";
  public boolean lineCommentOnlyAtStart;
  private String myStartComment;
  private String myEndComment;

  private String myHexPrefix;
  private String myNumPostfixChars;

  private boolean myIgnoreCase;
  private boolean myHasBraces;
  private boolean myHasBrackets;
  private boolean myHasParens;
  private boolean myHasStringEscapes;
  private volatile SoftReference<KeywordParser> myKeywordParser;

  public KeywordParser getKeywordParser() {
    KeywordParser parser = SoftReference.dereference(myKeywordParser);
    if (parser == null) {
      synchronized (this) {
        parser = SoftReference.dereference(myKeywordParser);
        if (parser == null) {
          myKeywordParser = new SoftReference<>(
            parser = new KeywordParser(Arrays.asList(myKeywords1, myKeywords2, myKeywords3, myKeywords4), myIgnoreCase));
        }
      }
    }
    return parser;
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    SyntaxTable cl = (SyntaxTable)super.clone();
    cl.myKeywords1 = new HashSet<>(myKeywords1);
    cl.myKeywords2 = new HashSet<>(myKeywords2);
    cl.myKeywords3 = new HashSet<>(myKeywords3);
    cl.myKeywords4 = new HashSet<>(myKeywords4);
    cl.myKeywordParser = null;
    return cl;
  }

  public void addKeyword1(String keyword) {
    myKeywords1.add(keyword);
    myKeywordParser = null;
  }

  public Set<String> getKeywords1() {
    return myKeywords1;
  }

  public void addKeyword2(String keyword) {
    myKeywords2.add(keyword);
    myKeywordParser = null;
  }

  public Set<String> getKeywords2() {
    return myKeywords2;
  }

  public void addKeyword3(String keyword) {
    myKeywords3.add(keyword);
    myKeywordParser = null;
  }

  public Set<String> getKeywords3() {
    return myKeywords3;
  }

  public void addKeyword4(String keyword) {
    myKeywords4.add(keyword);
    myKeywordParser = null;
  }

  public Set<String> getKeywords4() {
    return myKeywords4;
  }

  @NotNull
  public String getLineComment() {
    return myLineComment;
  }

  public void setLineComment(@NotNull String lineComment) {
    myLineComment = lineComment;
  }

  public String getStartComment() {
    return myStartComment;
  }

  public void setStartComment(String startComment) {
    myStartComment = startComment;
  }

  public String getEndComment() {
    return myEndComment;
  }

  public void setEndComment(String endComment) {
    myEndComment = endComment;
  }

  public String getHexPrefix() {
    return myHexPrefix;
  }

  public void setHexPrefix(String hexPrefix) {
    myHexPrefix = hexPrefix;
  }

  public String getNumPostfixChars() {
    return myNumPostfixChars;
  }

  public void setNumPostfixChars(String numPostfixChars) {
    myNumPostfixChars = numPostfixChars;
  }

  public boolean isIgnoreCase() {
    return myIgnoreCase;
  }

  public void setIgnoreCase(boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
    myKeywordParser = null;
  }

  public boolean isHasBraces() {
    return myHasBraces;
  }

  public void setHasBraces(boolean hasBraces) {
    myHasBraces = hasBraces;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SyntaxTable)) return false;

    final SyntaxTable syntaxTable = (SyntaxTable)o;

    if (myIgnoreCase != syntaxTable.myIgnoreCase) return false;
    if (myEndComment != null ? !myEndComment.equals(syntaxTable.myEndComment) : syntaxTable.myEndComment != null) return false;
    if (myHexPrefix != null ? !myHexPrefix.equals(syntaxTable.myHexPrefix) : syntaxTable.myHexPrefix != null) return false;
    if (!myKeywords1.equals(syntaxTable.myKeywords1)) return false;
    if (!myKeywords2.equals(syntaxTable.myKeywords2)) return false;
    if (!myKeywords3.equals(syntaxTable.myKeywords3)) return false;
    if (!myKeywords4.equals(syntaxTable.myKeywords4)) return false;
    if (myLineComment != null ? !myLineComment.equals(syntaxTable.myLineComment) : syntaxTable.myLineComment != null) return false;
    if (myNumPostfixChars != null ? !myNumPostfixChars.equals(syntaxTable.myNumPostfixChars) : syntaxTable.myNumPostfixChars != null) return false;
    if (myStartComment != null ? !myStartComment.equals(syntaxTable.myStartComment) : syntaxTable.myStartComment != null) return false;

    if (myHasBraces != syntaxTable.myHasBraces) return false;
    if (myHasBrackets != syntaxTable.myHasBrackets) return false;
    if (myHasParens != syntaxTable.myHasParens) return false;
    if (myHasStringEscapes != syntaxTable.myHasStringEscapes) return false;
    if (lineCommentOnlyAtStart != syntaxTable.lineCommentOnlyAtStart) return false;

    return true;
  }

  public int hashCode() {
    return myKeywords1.hashCode();
  }

  public boolean isHasBrackets() {
    return myHasBrackets;
  }

  public boolean isHasParens() {
    return myHasParens;
  }

  public void setHasBrackets(boolean hasBrackets) {
    myHasBrackets = hasBrackets;
  }

  public void setHasParens(boolean hasParens) {
    myHasParens = hasParens;
  }

  public boolean isHasStringEscapes() {
    return myHasStringEscapes;
  }

  public void setHasStringEscapes(final boolean hasEscapes) {
    myHasStringEscapes = hasEscapes;
  }
}
