// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class NlsCapitalizationUtil {

  public static boolean isCapitalizationSatisfied(String value, Nls.Capitalization capitalization) {
    if (StringUtil.isEmpty(value) || capitalization == Nls.Capitalization.NotSpecified) {
      return true;
    }
    value = value.replace("&", "");
    return capitalization == Nls.Capitalization.Title
           ? StringUtil.wordsToBeginFromUpperCase(value).equals(value)
           : checkSentenceCapitalization(value);
  }

  private static boolean checkSentenceCapitalization(@NotNull String value) {
    List<String> words = StringUtil.split(value, " ");
    final int wordCount = words.size();
    if (wordCount == 0) return true;

    if (Character.isLetter(words.get(0).charAt(0)) && !isCapitalizedWord(words.get(0))) return false;
    if (wordCount == 1) return true;

    int capitalized = 1;
    for (int i = 1; i < wordCount; i++) {
      String word = words.get(i);
      if (isCapitalizedWord(word)) {
        // check for abbreviations like 'C', 'SQL', 'I18n'
        if (word.length() == 1 || !Character.isLowerCase(word.charAt(1))) {
          continue;
        }
        capitalized++;
      }
    }

    // "Start service"
    if (capitalized == 1 && wordCount == 2) return true;

    final double ratio = ((double)capitalized - 1) / wordCount;
    return ratio <= 0.4; // allow reasonable amount of capitalized words
  }

  private static boolean isCapitalizedWord(String word) {
    return !word.isEmpty() && Character.isLetter(word.charAt(0)) && Character.isUpperCase(word.charAt(0));
  }

  @NotNull
  public static String fixValue(String string, Nls.Capitalization capitalization) {
    return capitalization == Nls.Capitalization.Title
           ? StringUtil.wordsToBeginFromUpperCase(string)
           : StringUtil.capitalize(StringUtil.wordsToBeginFromLowerCase(string));
  }

  @NotNull
  public static Nls.Capitalization getCapitalizationFromAnno(PsiModifierListOwner modifierListOwner) {
    PsiAnnotation nls = AnnotationUtil.findAnnotationInHierarchy(modifierListOwner, Collections.singleton(Nls.class.getName()));
    if (nls == null) return Nls.Capitalization.NotSpecified;
    PsiAnnotationMemberValue capitalization = nls.findAttributeValue("capitalization");
    Object cap = JavaPsiFacade
      .getInstance(modifierListOwner.getProject()).getConstantEvaluationHelper().computeConstantExpression(capitalization);
    return cap instanceof Nls.Capitalization ? (Nls.Capitalization)cap : Nls.Capitalization.NotSpecified;
  }
}
