// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.documentation.actions.ShowQuickDocInfoAction;
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.completion.BaseCompletionService.LOOKUP_ELEMENT_CONTRIBUTOR;

public class DefaultCompletionContributor extends CompletionContributor implements DumbAware {

  static void addDefaultAdvertisements(LookupImpl lookup, boolean includePsiFeatures) {
    Project project = lookup.getProject();
    if (CompletionUtil.shouldShowFeature(project, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC)) {
      lookup.addAdvertisement(LangBundle.message("completion.dot.etc.ad"), null);
    }
    if (!includePsiFeatures) return;

    if (CompletionUtil.shouldShowFeature(project, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_SMART_ENTER)) {
      final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
      if (StringUtil.isNotEmpty(shortcut)) {
        lookup.addAdvertisement(LangBundle.message("completion.smart.enter.ad", shortcut), null);
      }
    }

    if ((CompletionUtil.shouldShowFeature(project, ShowQuickDocInfoAction.CODEASSISTS_QUICKJAVADOC_FEATURE) ||
         CompletionUtil.shouldShowFeature(project, ShowQuickDocInfoAction.CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE))) {
      final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_QUICK_JAVADOC);
      if (StringUtil.isNotEmpty(shortcut)) {
        lookup.addAdvertisement(LangBundle.message("completion.quick.javadoc.ad", shortcut), null);
      }
    }

    if (CompletionUtil.shouldShowFeature(project, ShowImplementationsAction.CODEASSISTS_QUICKDEFINITION_FEATURE) ||
        CompletionUtil.shouldShowFeature(project, ShowImplementationsAction.CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE)) {
      final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_QUICK_IMPLEMENTATIONS);
      if (StringUtil.isNotEmpty(shortcut)) {
        lookup.addAdvertisement(LangBundle.message("completion.quick.implementations.ad", shortcut), null);
      }
    }
  }

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    LookupElement[] items = context.getItems();
    if (items.length == 0) return null;
    LookupElement first = items[0];
    String firstPattern = context.getLookup().itemPattern(first);
    if (StringUtil.isEmpty(firstPattern) &&
        context.getParameters().getCompletionType() != CompletionType.SMART) return null;
    for (int i = 1; i < items.length; i++) {
      LookupElement item = items[i];
      // same strings with different decorators produce different results
      // word completion is known to be very simple
      if (!(item.getUserData(LOOKUP_ELEMENT_CONTRIBUTOR) instanceof WordCompletionContributor)) {
        return null;
      }
      String itemPattern = context.getLookup().itemPattern(item);
      // check if the word item has the same text
      String firstSuffix = StringUtil.trimStart(first.getLookupString(), firstPattern);
      String itemSuffix = StringUtil.trimStart(item.getLookupString(), itemPattern);
      if (!firstSuffix.equals(itemSuffix)) return null;
    }
    return AutoCompletionDecision.insertItem(first);
  }

}
