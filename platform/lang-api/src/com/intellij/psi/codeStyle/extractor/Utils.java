/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.extractor;

import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import com.intellij.psi.codeStyle.extractor.values.Generation;
import com.intellij.psi.codeStyle.extractor.values.Gens;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Utils {
  public static int CRITICAL_SYMBOL_WEIGHT = 100;

  public static void logError(String message) {
    System.out.println(message); //TODO reasonable implementation
  }

  public static int getTabSize(@Nullable CommonCodeStyleSettings.IndentOptions options) {
    return options == null ? 4 : options.TAB_SIZE;
  }

  public static int getNormalizedLength(@Nullable CommonCodeStyleSettings.IndentOptions options, @NotNull String text) {
    return text
      .replaceAll(
        "\t",
        StringUtil.repeatSymbol(' ', getTabSize(options)))
      .replaceAll(
        "\n",
        StringUtil.repeatSymbol(' ', CRITICAL_SYMBOL_WEIGHT))
      .length();
  }

  public static boolean isSpace(char c) {
    return " \t\n".indexOf(c) >= 0;
  }

  public static boolean isBracket(char c) {
    return "(){}[]<>".indexOf(c) >= 0;
  }

  public static int getDiff(@Nullable CommonCodeStyleSettings.IndentOptions options, @NotNull String oldV, @NotNull String newV) {
    oldV = oldV.trim();
    newV = newV.trim();
    int diff = 0;
    int oPos = 0, nPos = 0;
    int oEnd = oldV.length(), nEnd = newV.length();

    StringBuilder oSp = new StringBuilder();
    StringBuilder nSp = new StringBuilder();
    char ch;
    while (oPos < oEnd || nPos < nEnd) {
      oSp.setLength(0);
      nSp.setLength(0);
      while (oPos < oEnd && isSpace(ch = oldV.charAt(oPos))) {
        oSp.append(ch);
        ++oPos;
      }
      while (nPos < nEnd && isSpace(ch = newV.charAt(nPos))) {
        nSp.append(ch);
        ++nPos;
      }
      diff += Math.abs(getNormalizedLength(options, oSp.toString())
              - getNormalizedLength(options, nSp.toString())); //each time

      while (oPos < oEnd && nPos < nEnd
             && (ch = oldV.charAt(oPos)) == newV.charAt(nPos)
             && !isSpace(ch)) {
        ++oPos;
        ++nPos;
      }

      if (oPos < oEnd && nPos < nEnd && !isSpace(oldV.charAt(oPos)) && !isSpace(newV.charAt(nPos))) {
        logError("AST changed!");
        return Differ.UGLY_FORMATTING;
      }
    }
    return diff;
  }

  @Nullable
  public static CustomCodeStyleSettings getLanguageSettings(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    for (CodeStyleSettingsProvider provider : CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (language.equals(provider.getLanguage())) {
        CustomCodeStyleSettings modelSettings = provider.createCustomSettings(settings);
        if (modelSettings == null) continue;
        CustomCodeStyleSettings customSettings = settings.getCustomSettings(modelSettings.getClass());
        if (customSettings != null) {
          return customSettings;
        }
      }
    }
    logError("Failed to load CustomCodeStyleSettings for " + language);
    return null;
  }

  public static void adjustValuesMin(
    @NotNull ValuesExtractionResult gens,
    @NotNull Differ differ,
    @Nullable ProgressIndicator indicator) {

    final List<Value> values = gens.getValues();
    final int length = values.size();
    int i = 0;
    for (Value value : values) {
      if (indicator != null) {
        indicator.checkCanceled();
        indicator.setText2("Value:" + value.name);
        indicator.setFraction(0.5 + 0.5 * i / length);
      }

      Object bestValue = value.value;
      int bestScope = differ.getDifference(gens);
      for (Object cnst : value.getPossibleValues()) {
        if (cnst.equals(value.value)) {
          continue;
        }
        value.value = cnst;
        int diff = differ.getDifference(gens);
        if (diff < bestScope) {
          bestValue = cnst;
          bestScope = diff;
          value.state = Value.STATE.SELECTED;
        }
        else if (diff > bestScope) {
          value.state = Value.STATE.SELECTED;
        }
      }
      value.value = bestValue;
      ++i;
    }
  }

  public static void adjustValuesGA(
    @NotNull Gens gens,
    @NotNull Differ differ,
    @Nullable ProgressIndicator indicator) {

    Generation generation = Generation.createZeroGeneration(gens);
    while (generation.tryAgain()) {
      if (indicator != null) {
        indicator.checkCanceled();
      }
      final int age = generation.getAge();
      if (indicator != null) {
        indicator.setText2(" age:" + age + "/" + generation.getParentKind());
        indicator.setFraction(0.5 * age / Generation.GEN_COUNT);
      }
      generation = Generation.createNextGeneration(differ, generation);
    }
    gens.copyFrom(generation.getBestGens(differ));
  }

  static long rseed = 0;
  static final long RAND_MAX_32 = ((1L << 31L) - 1L);
  static final long RAND_MAX = ((1L << 15L) - 1L);

  public static void resetRandom() {
    rseed = 0;
  }

  public static long getRandom() {
    rseed = (rseed * 214013 + 2531011) & RAND_MAX_32;
    return rseed >> 16;
  }

  public static int getRandomLess(int count) {
    final int ret = (int)(getRandom() * count / RAND_MAX);
    if (ret >= count) return count - 1;
    if (ret < 0) return 0;
    return ret;
  }
}
