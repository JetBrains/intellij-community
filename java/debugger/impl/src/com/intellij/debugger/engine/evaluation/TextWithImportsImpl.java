// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TextWithImportsImpl implements TextWithImports{

  private final CodeFragmentKind myKind;
  private String myText;
  private final FileType myFileType;
  private final String myImports;

  private static final char SEPARATOR = 13;

  public TextWithImportsImpl(@NotNull PsiElement expression) {
    myKind = CodeFragmentKind.EXPRESSION;
    final String text = expression.getText();
    PsiFile containingFile = expression.getContainingFile();
    if(containingFile instanceof PsiExpressionCodeFragment) {
      myText = text;
      myImports = ((JavaCodeFragment)containingFile).importsToString();
      myFileType = StdFileTypes.JAVA;
    }
    else {
      Trinity<String, String, FileType> trinity = parseExternalForm(text);
      myText = trinity.first;
      myImports = trinity.second;
      myFileType = trinity.third;
    }
  }

  public TextWithImportsImpl (CodeFragmentKind kind, @NotNull String text, @NotNull String imports, @Nullable FileType fileType) {
    myKind = kind;
    myText = text;
    myImports = imports;
    myFileType = fileType;
  }

  public TextWithImportsImpl(CodeFragmentKind kind, @NotNull String text) {
    myKind = kind;
    Trinity<String, String, FileType> trinity = parseExternalForm(text);
    myText = trinity.first;
    myImports = trinity.second;
    myFileType = trinity.third;
  }

  private static Trinity<String, String, FileType> parseExternalForm(String s) {
    String[] split = s.split(String.valueOf(SEPARATOR));
    return Trinity.create(split[0], split.length > 1 ? split[1] : "", split.length > 2 ? FileTypeManager.getInstance().getStdFileType(split[2]) : null);
  }

  @Override
  public CodeFragmentKind getKind() {
    return myKind;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public @NotNull String getImports() {
    return myImports;
  }

  public boolean equals(Object object) {
    if(!(object instanceof TextWithImportsImpl)) {
      return false;
    }
    TextWithImportsImpl item = ((TextWithImportsImpl)object);
    return Comparing.equal(item.myText, myText) && Comparing.equal(item.myImports, myImports);
  }

  public String toString() {
    return getText();
  }

  @Override
  public String toExternalForm() {
    String result = myText;
    if (StringUtil.isNotEmpty(myImports) || myFileType != null) {
      result += SEPARATOR + myImports;
    }
    if (myFileType != null) {
      result += SEPARATOR + myFileType.getName();
    }
    return result;
  }

  public int hashCode() {
    return myText.hashCode();
  }

  @Override
  public boolean isEmpty() {
    return StringUtil.isEmptyOrSpaces(getText());
  }

  @Override
  public void setText(String newText) {
    myText = newText;
  }

  @Override
  public FileType getFileType() {
    return myFileType;
  }

  @Nullable
  public static XExpression toXExpression(@Nullable TextWithImports text) {
    if (text != null && !text.getText().isEmpty()) {
      return new XExpressionImpl(text.getText(),
                                 LanguageUtil.getFileTypeLanguage(text.getFileType()),
                                 StringUtil.nullize(text.getImports()),
                                 getMode(text.getKind()));
    }
    return null;
  }

  @NotNull
  private static EvaluationMode getMode(@NotNull CodeFragmentKind kind) {
    switch (kind) {
      case EXPRESSION: return EvaluationMode.EXPRESSION;
      case CODE_BLOCK: return EvaluationMode.CODE_FRAGMENT;
    }
    throw new IllegalStateException("Unknown kind " + kind);
  }

  @NotNull
  private static CodeFragmentKind getKind(@NotNull EvaluationMode mode) {
    switch (mode) {
      case EXPRESSION: return CodeFragmentKind.EXPRESSION;
      case CODE_FRAGMENT: return CodeFragmentKind.CODE_BLOCK;
    }
    throw new IllegalStateException("Unknown mode " + mode);
  }

  public static TextWithImports fromXExpression(@Nullable XExpression expression) {
    if (expression == null) return null;

    if (expression.getCustomInfo() == null && expression.getLanguage() == null) {
      return new TextWithImportsImpl(getKind(expression.getMode()), expression.getExpression());
    }
    else {
      return new TextWithImportsImpl(getKind(expression.getMode()),
                                     expression.getExpression(),
                                     StringUtil.notNullize(expression.getCustomInfo()),
                                     LanguageUtil.getLanguageFileType(expression.getLanguage()));
    }
  }
}
