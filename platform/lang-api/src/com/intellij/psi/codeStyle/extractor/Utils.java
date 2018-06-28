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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import com.intellij.psi.codeStyle.extractor.values.GenGeneration;
import com.intellij.psi.codeStyle.extractor.values.Gens;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Utils {
  public static final int CRITICAL_SYMBOL_WEIGHT = 100;

  public static void logError(String message) {
    System.out.println(message); //TODO reasonable implementation
  }

  @Contract(pure = true)
  public static int getTabSize(@Nullable CommonCodeStyleSettings.IndentOptions options) {
    return options == null ? 4 : options.TAB_SIZE;
  }

  public static int getNormalizedLength(int tabSize, @NotNull String text) {
    int scope = 0;
    for (int i = 0; i < text.length(); ++i) {
      final char at = text.charAt(i);
      switch (at) {
        case '\t': scope += tabSize; break;
        case '\n': scope += CRITICAL_SYMBOL_WEIGHT; break;
        default: ++scope; break;
      }
    }
    return scope;
  }

  @Contract(pure = true)
  public static boolean isWordSeparator(char c) {
    return !Character.isJavaIdentifierPart(c);
  }

  @Contract(pure = true)
  public static boolean isSpace(char c) {
    return " \t\n".indexOf(c) >= 0;
  }

  @SuppressWarnings("unused")
  @Contract(pure = true)
  public static boolean isBracket(char c) {
    return "(){}[]<>".indexOf(c) >= 0;
  }

  public static int getDiff(int oldTabSize, @NotNull String oldV,
                            int newTabSize, @NotNull String newV) {
    oldV = oldV.trim();
    newV = newV.trim();
    int diff = 0;
    int oPos = 0, nPos = 0;
    int oEnd = oldV.length(), nEnd = newV.length();
    if (oldV.equals(newV)) {
      return 0;
    }

    StringBuilder oSp = new StringBuilder();
    StringBuilder nSp = new StringBuilder();
    char ch;
    while (oPos < oEnd || nPos < nEnd) {
      int _oPos = oPos;
      int _nPos = nPos;
      oSp.setLength(0);
      nSp.setLength(0);
      while (oPos < oEnd && isWordSeparator(ch = oldV.charAt(oPos))) {
        ++oPos;
        if (isSpace(ch)) oSp.append(ch);
        else break;
      }
      while (nPos < nEnd && isWordSeparator(ch = newV.charAt(nPos))) {
        ++nPos;
        if (isSpace(ch)) nSp.append(ch);
        else break;
      }
      diff += Math.abs(getNormalizedLength(oldTabSize, oSp.toString())
              - getNormalizedLength(newTabSize, nSp.toString())); //each time

      while (oPos < oEnd && nPos < nEnd
             && (ch = oldV.charAt(oPos)) == newV.charAt(nPos)
             && !isWordSeparator(ch)) {
        ++oPos;
        ++nPos;
      }

      if ( (_oPos == oPos && _nPos == nPos)
           || (oPos < oEnd && nPos < nEnd && !isWordSeparator(oldV.charAt(oPos)) && !isWordSeparator(newV.charAt(nPos)))) {
        logError("AST changed!");
        return Differ.UGLY_FORMATTING;
      }
    }
    logError("diff:" + diff);
    return diff;
  }

  @Nullable
  public static CustomCodeStyleSettings getLanguageSettings(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    for (CodeStyleSettingsProvider provider : CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (language.equals(provider.getLanguage())) {
        CustomCodeStyleSettings modelSettings = provider.createCustomSettings(settings);
        if (modelSettings == null) continue;
        return settings.getCustomSettings(modelSettings.getClass());
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
        indicator.setFraction(0.5 + 0.5 * i / length);
      }

      Object bestValue = value.value;
      int bestScope = differ.getDifference(gens);
      int index = 0;
      final Object[] possibleValues = value.getPossibleValues();
      for (Object cnst : possibleValues) {
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
        updateState(indicator, 
                    String.format("Value: %s  Divergence: %d [%d/%d]", value.name, bestScope, index, possibleValues.length), 
                    false);
      }
      updateState(indicator, String.format("Value: %s  Divergence: %d", value.name, bestScope), true);
      value.value = bestValue;
      ++i;
    }
  }

  public static void adjustValuesGA(
    @NotNull Gens gens,
    @NotNull Differ differ,
    @Nullable ProgressIndicator indicator) {

    GenGeneration generation = GenGeneration.createZeroGeneration(gens);
    while (generation.tryAgain()) {
      final int age = generation.getAge();
      if (indicator != null) {
        indicator.setFraction(0.5 * age / GenGeneration.GENERATIONS_COUNT);
      }
      generation = GenGeneration.createNextGeneration(differ, generation);
      updateState(indicator,
                  String.format("Generation: %d  Divergence: %d", age, generation.getParentKind()),
                  true);
    }
    gens.copyFrom(generation.getBestGens(differ));
  }

  static long rseed = 0;
  static final long RAND_MAX_32 = ((1L << 31L) - 1L);
  static final long RAND_MAX = ((1L << 15L) - 1L);

  @SuppressWarnings("unused")
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

  public static void updateState(@Nullable ProgressIndicator indicator, @NotNull String status, boolean primaryStatus) {
    if (indicator != null) {
      indicator.checkCanceled();
      if (primaryStatus) {
        indicator.setText(status);
        indicator.setText2("");
      }
      else {
        indicator.setText2(status);
      }
    }
  }
}
