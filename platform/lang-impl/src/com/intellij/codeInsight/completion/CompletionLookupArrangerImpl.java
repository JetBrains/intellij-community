// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.AsyncRendering;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.ide.ui.UISettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class CompletionLookupArrangerImpl extends BaseCompletionLookupArranger {
  public CompletionLookupArrangerImpl(CompletionProcessEx process) {
    super(process);
  }

  @Override
  public void addElement(@NotNull LookupElement element, @NotNull LookupElementPresentation presentation) {
    StatisticsWeigher.clearBaseStatisticsInfo(element);
    super.addElement(element, presentation);
  }

  @Override
  protected boolean isAlphaSorted() {
    return UISettings.getInstance().getSortLookupElementsLexicographically();
  }

  @Override
  protected @NotNull List<LookupElement> getExactMatches(List<? extends LookupElement> items) {
    String selectedText =
      InjectedLanguageEditorUtil.getTopLevelEditor(myProcess.getParameters().getEditor()).getSelectionModel().getSelectedText();
    List<LookupElement> exactMatches = new SmartList<>();
    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      if (isCustomElements(item)) continue;
      boolean isSuddenLiveTemplate = isSuddenLiveTemplate(item);
      if (isPrefixItem(item, true) && !isSuddenLiveTemplate || item.getLookupString().equals(selectedText)) {
        if (item instanceof LiveTemplateLookupElement) {
          // prefer most recent live template lookup item
          return Collections.singletonList(item);
        }
        exactMatches.add(item);
      }
      else if (i == 0 && isSuddenLiveTemplate && items.size() > 1 && !CompletionService.isStartMatch(items.get(1), this)) {
        return Collections.singletonList(item);
      }
    }
    return exactMatches;
  }

  @Override
  protected void removeItem(@NotNull LookupElement element, @NotNull ProcessingContext context) {
    super.removeItem(element, context);
    AsyncRendering.Companion.cancelRendering(element);
  }

  private static boolean isSuddenLiveTemplate(LookupElement element) {
    return element instanceof LiveTemplateLookupElement && ((LiveTemplateLookupElement)element).sudden;
  }
}
