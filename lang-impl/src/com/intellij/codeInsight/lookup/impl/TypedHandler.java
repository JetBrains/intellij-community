package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.extensions.Extensions;

import java.awt.*;
import java.util.Arrays;

class TypedHandler implements TypedActionHandler {
  private final TypedActionHandler myOriginalHandler;

  public TypedHandler(TypedActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(final Editor editor, final char charTyped, DataContext dataContext){
    final LookupImpl lookup = editor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY);
    if (lookup == null){
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    final String prefix = lookup.getPrefix();
    final LookupItem<?> currentItem = lookup.getCurrentItem();
    final CharFilter.Result result = getLookupAction(charTyped, prefix, currentItem, lookup);

    CommandProcessor.getInstance().executeCommand(PlatformDataKeys.PROJECT.getData(dataContext), new Runnable() {
      public void run() {
        EditorModificationUtil.deleteSelectedText(editor);
        if (result == CharFilter.Result.ADD_TO_PREFIX) {
          lookup.setPrefix(lookup.getPrefix() + charTyped);
          EditorModificationUtil.insertStringAtCaret(editor, String.valueOf(charTyped));
        }
      }
    }, "", null);

    if (result == CharFilter.Result.ADD_TO_PREFIX){
      lookup.updateList();
      Point point=lookup.calculatePosition();
      Dimension preferredSize = lookup.getComponent().getPreferredSize();
      lookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);

      lookup.getList().repaint();
    }
    else{
      if (result == CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP){
        LookupItem item = lookup.getCurrentItem();
        if (item != null){
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.finishByDotEtc");
          lookup.finishLookup(charTyped);
          return;
        }
      }

      lookup.hide();
      myOriginalHandler.execute(editor, charTyped, dataContext);
    }
  }

  private static CharFilter.Result getLookupAction(final char charTyped, final String prefix, final LookupItem<?> currentItem, final Lookup lookup) {
    if (currentItem != null) {
      for (String lookupString : currentItem.getAllLookupStrings()) {
        if (lookupString.startsWith(prefix + charTyped)) return CharFilter.Result.ADD_TO_PREFIX;
      }
    }
    final CharFilter[] filters = Extensions.getExtensions(CharFilter.EP_NAME);
    for (final CharFilter extension : filters) {
      final CharFilter.Result result = extension.acceptChar(charTyped, prefix, lookup);
      if (result != null) {
        return result;
      }
    }
    throw new AssertionError("c=" + charTyped + "; prefix=" + currentItem + "; filters=" + Arrays.toString(filters));
  }
}
