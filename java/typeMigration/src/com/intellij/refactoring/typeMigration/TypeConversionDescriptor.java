// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeConversionDescriptor extends TypeConversionDescriptorBase {
  private String myStringToReplace = null;
  private String myReplaceByString = "$";
  private PsiExpression myExpression;
  private PsiType myConversionType;

  public TypeConversionDescriptor(@NonNls final String stringToReplace, @NonNls final String replaceByString) {
    this(stringToReplace, replaceByString, (PsiExpression)null);
  }

  public TypeConversionDescriptor(@NonNls final String stringToReplace, @NonNls final String replaceByString, final PsiExpression expression) {
    myStringToReplace = stringToReplace;
    myReplaceByString = replaceByString;
    myExpression = expression;
  }

  public TypeConversionDescriptor(@NonNls final String stringToReplace, @NonNls final String replaceByString, PsiType conversionType) {
    this(stringToReplace, replaceByString);
    myConversionType = conversionType;
  }

  public TypeConversionDescriptor withConversionType(PsiType conversionType) {
    myConversionType = conversionType;
    return this;
  }

  public void setStringToReplace(String stringToReplace) {
    myStringToReplace = stringToReplace;
  }

  public void setReplaceByString(String replaceByString) {
    myReplaceByString = replaceByString;
  }

  public String getStringToReplace() {
    return myStringToReplace;
  }

  public String getReplaceByString() {
    return myReplaceByString;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public void setExpression(final PsiExpression expression) {
    myExpression = expression;
  }

  @Nullable
  @Override
  public PsiType conversionType() {
    return myConversionType;
  }

  @Override
  public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
    if (getExpression() != null) expression = getExpression();
    expression = adjustExpressionBeforeReplacement(expression);
    return replaceExpression(expression, getStringToReplace(), getReplaceByString());
  }

  @NotNull
  protected PsiExpression adjustExpressionBeforeReplacement(@NotNull PsiExpression expression) {
    return expression;
  }

  @NotNull
  public static PsiExpression replaceExpression(@NotNull PsiExpression expression,
                                                String stringToReplace,
                                                String replaceByString) {
    Project project = expression.getProject();
    final ReplaceOptions options = new ReplaceOptions();
    final MatchOptions matchOptions = options.getMatchOptions();
    matchOptions.setFileType(StdFileTypes.JAVA);
    final String replacement = Replacer.testReplace(expression.getText(), stringToReplace, replaceByString, options, project);
    return (PsiExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression.replace(
      JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(replacement, expression)));
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (myReplaceByString != null) {
      buf.append(myReplaceByString);
    }
    if (myStringToReplace != null) {
      if (buf.length() > 0) buf.append(" ");
      buf.append(myStringToReplace);
    }
    if (myExpression != null) {
      if (buf.length() > 0) buf.append(" ");
      buf.append(myExpression.getText());
    }
    return buf.toString();
  }
}
