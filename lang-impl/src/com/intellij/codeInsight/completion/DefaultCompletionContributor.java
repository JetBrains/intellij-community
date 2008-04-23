/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.lang.LangBundle;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.codeInsight.documentation.actions.ShowJavaDocInfoAction;
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;
import java.util.Random;

/**
 * @author peter
 */
public class DefaultCompletionContributor extends CompletionContributor{

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(psiElement(), new CompletionAdvertiser() {
      public String advertise(@NotNull final CompletionParameters parameters, final ProcessingContext context) {
        if (shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_REPLACE)) {
          final String shortcut = getShortcut(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
          if (shortcut != null) {
            return LangBundle.message("completion.replace.ad", shortcut);
          }
        }

        final Random random = new Random();
        if (random.nextInt(5) < 2 && shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC)) {
          return LangBundle.message("completion.dot.etc.ad");
        }
        if (random.nextInt(5) < 2 && shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_SMART_ENTER)) {
          final String shortcut = getShortcut(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
          if (shortcut != null) {
            return LangBundle.message("completion.smart.enter.ad", shortcut);
          }
        }

        if (random.nextInt(5) < 2 &&
            (shouldShowFeature(parameters, ShowJavaDocInfoAction.CODEASSISTS_QUICKJAVADOC_FEATURE) || shouldShowFeature(parameters, ShowJavaDocInfoAction.CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE))) {
          final String shortcut = getShortcut(IdeActions.ACTION_QUICK_JAVADOC);
          if (shortcut != null) {
            return LangBundle.message("completion.quick.javadoc.ad", shortcut);
          }
        }

        if (shouldShowFeature(parameters, ShowImplementationsAction.CODEASSISTS_QUICKDEFINITION_FEATURE) ||
            shouldShowFeature(parameters, ShowImplementationsAction.CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE)) {
          final String shortcut = getShortcut(IdeActions.ACTION_QUICK_IMPLEMENTATIONS);
          if (shortcut != null) {
            return LangBundle.message("completion.quick.implementations.ad", shortcut);
          }
        }

        if (Calendar.getInstance().get(Calendar.MONTH) == 3 && Calendar.getInstance().get(Calendar.DATE) == 1 ||
            CompletionData.findPrefixStatic(parameters.getPosition(), parameters.getOffset()).length() > 42) {
          final int i = random.nextInt(data.length * 5);
          if (i < data.length) {
            return new String(data[i]);
          }
        }

        return null;
      }

      public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final ProcessingContext context) {
        return LangBundle.message("completion.no.suggestions");
      }
    });
  }

  // So. You've managed to find this and now you can rather easily figure out what's written here. But is it necessary? Won't it be
  // a bit more interesting not to see everything at once, but encounter these values incrementally? It's completely up to you.
  private static final byte[][] data = {{66, 114, 111, 117, 103, 104, 116, 32, 116, 111, 32, 121, 111, 117, 32, 98, 121, 32, 99, 114, 101, 97, 116, 111, 114, 115, 32, 111, 102, 32, 70, 97, 98, 114, 105, 113, 117, 101},
      {74, 117, 115, 116, 32, 114, 101, 108, 97, 120},
      {69, 110, 108, 97, 114, 103, 101, 32, 121, 111, 117, 114, 32, 112, 101, 114, 102, 111, 114, 109, 97, 110, 99, 101},
      {83, 111, 117, 110, 100, 116, 114, 97, 99, 107, 32, 97, 118, 97, 105, 108, 97, 98, 108, 101, 32, 111, 110, 32, 70, 68},
      {39, 73, 116, 39, 115, 32, 102, 97, 110, 116, 97, 115, 116, 105, 99, 33, 39, 32, 32, 78, 101, 119, 32, 89, 111, 114, 107, 32, 84, 105, 109, 101, 115},
      {65, 110, 100, 32, 119, 104, 121, 32, 100, 105, 100, 32, 121, 111, 117, 32, 100, 111, 32, 116, 104, 97, 116, 63},
      {74, 101, 116, 66, 114, 97, 105, 110, 115, 32, 65, 100, 87, 111, 114, 100, 115, 32, 45, 32, 118, 105, 115, 105, 116, 32, 111, 117, 114, 32, 115, 105, 116, 101, 32, 102, 111, 114, 32, 109, 111, 114, 101, 32, 100, 101, 116, 97, 105, 108, 115},
      {68, 111, 110, 39, 116, 32, 112, 97, 110, 105, 99, 33},
      {84, 104, 101, 32, 73, 68, 69, 65, 32, 105, 115, 32, 111, 117, 116, 32, 116, 104, 101, 114, 101},
      {77, 114, 46, 32, 87, 111, 108, 102, 32, 119, 105, 108, 108, 32, 115, 111, 108, 118, 101, 32, 97, 108, 108, 32, 121, 111, 117, 114, 32, 112, 114, 111, 98, 108, 101, 109, 115, 44, 32, 99, 97, 108, 108, 32, 78, 79, 87},
      {80, 108, 101, 97, 115, 101, 32, 116, 121, 112, 101, 32, 115, 108, 111, 119, 101, 114, 44, 32, 73, 32, 99, 97, 110, 39, 116, 32, 107, 101, 101, 112, 32, 117, 112, 32, 119, 105, 116, 104, 32, 121, 111, 117},
      {87, 104, 97, 116, 32, 100, 105, 100, 32, 121, 111, 117, 32, 101, 120, 112, 101, 99, 116, 32, 116, 111, 32, 115, 101, 101, 32, 104, 101, 114, 101, 63},
      {65, 114, 101, 32, 121, 111, 117, 32, 115, 117, 114, 101, 63},
      {67, 111, 100, 101, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 109, 105, 103, 104, 116, 32, 98, 101, 32, 105, 110, 115, 117, 105, 116, 97, 98, 108, 101, 32, 102, 111, 114, 32, 99, 104, 105, 108, 100, 114, 101, 110, 32, 117, 110, 100, 101, 114, 32, 55},
      {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 99, 97, 110, 39, 116, 32, 115, 111, 108, 118, 101, 32, 101, 118, 101, 114, 121, 32, 104, 117, 109, 97, 110, 32, 112, 114, 111, 98, 108, 101, 109, 44, 32, 105, 116, 39, 115, 32, 77, 114, 46, 32, 87, 111, 108, 102, 32, 119, 104, 111, 32, 100, 111, 101, 115},
      {66, 101, 119, 97, 114, 101, 32, 111, 102, 32, 116, 104, 101, 32, 76, 101, 111, 112, 97, 114, 100},
      {80, 108, 101, 97, 115, 101, 32, 100, 111, 110, 39, 116, 32, 100, 111, 32, 105, 116, 32, 97, 103, 97, 105, 110},
      {73, 102, 32, 121, 111, 117, 32, 99, 97, 110, 39, 116, 32, 114, 101, 97, 100, 32, 116, 104, 105, 115, 32, 116, 101, 120, 116, 44, 32, 100, 111, 110, 39, 116, 32, 104, 101, 115, 105, 116, 97, 116, 101, 32, 116, 111, 32, 97, 115, 107, 32, 116, 104, 101, 32, 115, 117, 112, 112, 111, 114, 116},
      {84, 104, 105, 110, 107, 105, 110, 103, 44, 32, 112, 108, 101, 97, 115, 101, 32, 100, 111, 110, 39, 116, 32, 105, 110, 116, 101, 114, 114, 117, 112, 116},
      {87, 104, 97, 116, 32, 103, 111, 111, 100, 32, 105, 115, 32, 115, 105, 116, 116, 105, 110, 103, 32, 104, 101, 114, 101, 32, 99, 111, 100, 105, 110, 103, 44, 32, 99, 111, 109, 101, 32, 101, 110, 106, 111, 121, 32, 89, 79, 85, 82, 32, 108, 105, 102, 101},
      {71, 101, 116, 32, 97, 32, 115, 99, 114, 101, 101, 110, 115, 104, 111, 116, 32, 110, 111, 119, 58, 32, 121, 111, 117, 39, 108, 108, 32, 115, 104, 111, 119, 32, 105, 116, 32, 116, 111, 32, 121, 111, 117, 114, 32, 103, 114, 97, 110, 100, 99, 104, 105, 108, 100, 114, 101, 110},
      {75, 101, 101, 112, 32, 99, 111, 109, 112, 108, 101, 116, 105, 110, 103, 32, 97, 110, 100, 32, 111, 110, 101, 32, 100, 97, 121, 32, 121, 111, 117, 39, 108, 108, 32, 119, 105, 110},
      {82, 111, 97, 100, 32, 116, 111, 32, 104, 101, 108, 108, 32, 105, 115, 32, 112, 97, 118, 101, 100, 32, 119, 105, 116, 104, 32, 103, 111, 111, 100, 32, 105, 110, 116, 101, 110, 116, 105, 111, 110, 115},
      {67, 111, 108, 108, 101, 99, 116, 32, 49, 48, 48, 32, 73, 68, 69, 65, 32, 108, 111, 103, 111, 115, 32, 97, 110, 100, 32, 103, 101, 116, 32, 97, 32, 109, 111, 117, 115, 101},
      {69, 118, 101, 114, 121, 32, 116, 105, 109, 101, 32, 121, 111, 117, 32, 99, 111, 109, 112, 108, 101, 116, 101, 32, 99, 111, 100, 101, 44, 32, 97, 32, 99, 104, 105, 108, 100, 32, 105, 115, 32, 98, 111, 114, 110},
      {73, 68, 69, 65, 58, 32, 76, 101, 118, 101, 108, 32, 49, 32, 99, 111, 109, 112, 108, 101, 116, 101},
      {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 39, 115, 32, 97, 108, 108, 32, 97, 114, 111, 117, 110, 100, 32, 121, 111, 117},
      {83, 111, 32, 119, 104, 97, 116, 63},
      {68, 111, 110, 39, 116, 32, 98, 101, 32, 108, 97, 122, 121, 44, 32, 116, 121, 112, 101, 32, 105, 116, 32, 121, 111, 117, 114, 115, 101, 108, 102},
      {68, 111, 32, 121, 111, 117, 32, 117, 110, 100, 101, 114, 115, 116, 97, 110, 100, 32, 104, 111, 119, 32, 104, 97, 114, 100, 32, 105, 116, 32, 105, 115, 32, 116, 111, 32, 102, 105, 110, 100, 32, 97, 108, 108, 32, 116, 104, 101, 115, 101, 32, 118, 97, 114, 105, 97, 110, 116, 115, 63},
      {84, 104, 105, 110, 107, 32, 116, 119, 105, 99, 101, 32, 98, 101, 102, 111, 114, 101, 32, 115, 101, 108, 101, 99, 116, 105, 110, 103, 32, 97, 110, 32, 105, 116, 101, 109, 44, 32, 121, 111, 117, 114, 32, 102, 117, 116, 117, 114, 101, 32, 100, 101, 112, 101, 110, 100, 115, 32, 111, 110, 32, 105, 116},
      {78, 111, 116, 32, 115, 111, 32, 102, 97, 115, 116, 33},
      {67, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 105, 115, 32, 115, 109, 97, 114, 116, 101, 114, 32, 116, 104, 97, 110, 32, 121, 111, 117, 32, 116, 104, 105, 110, 107},
      {89, 111, 117, 32, 116, 114, 121, 32, 109, 121, 32, 112, 97, 116, 105, 101, 110, 99, 101, 44, 32, 109, 97, 107, 101, 32, 121, 111, 117, 114, 32, 99, 104, 111, 105, 99, 101},
      {80, 108, 101, 97, 115, 101, 44, 32, 100, 111, 110, 39, 116, 32, 99, 108, 111, 115, 101, 32, 109, 101, 44, 32, 108, 105, 102, 101, 32, 105, 115, 32, 115, 111, 32, 115, 104, 111, 114, 116, 32, 97, 110, 100, 32, 116, 104, 101, 114, 101, 39, 115, 32, 115, 111, 32, 109, 117, 99, 104, 32, 121, 101, 116, 32, 116, 111, 32, 98, 101, 32, 100, 111, 110, 101, 33},
      {65, 112, 112, 108, 97, 117, 115, 101, 33},
      {89, 111, 117, 32, 109, 117, 115, 116, 32, 98, 101, 32, 109, 105, 115, 116, 97, 107, 101, 110, 44, 32, 112, 114, 101, 115, 115, 32, 69, 83, 67},
      {69, 108, 101, 99, 116, 114, 111, 110, 105, 99, 97, 108, 108, 121, 32, 116, 101, 115, 116, 101, 100},
      {84, 114, 105, 97, 108, 32, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 32, 118, 101, 114, 115, 105, 111, 110, 44, 32, 52, 50, 32, 105, 110, 118, 111, 99, 97, 116, 105, 111, 110, 115, 32, 114, 101, 109, 97, 105, 110, 105, 110, 103},
      {67, 111, 109, 46, 112, 108, 101, 116, 46, 105, 111, 110, 32, 50, 46, 48, 58, 32, 115, 104, 97, 114, 101, 32, 121, 111, 117, 114, 32, 102, 97, 118, 111, 117, 114, 105, 116, 101, 32, 105, 116, 101, 109, 115, 32, 119, 105, 116, 104, 32, 102, 114, 105, 101, 110, 100, 115},
      {83, 104, 97, 109, 101, 32, 111, 110, 32, 97, 110, 116, 105, 45, 99, 111, 109, 112, 108, 101, 116, 105, 111, 110, 105, 115, 116, 115}};
}