package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ListScrollingUtil;

import javax.swing.*;
import java.awt.*;

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

    CharFilter charFilter = lookup.getCharFilter();
    final String prefix = lookup.getPrefix();
    final LookupItem<?> currentItem = lookup.getCurrentItem();
    final int result = getLookupAction(charTyped, charFilter, prefix, currentItem);

    CommandProcessor.getInstance().executeCommand(DataKeys.PROJECT.getData(dataContext), new Runnable() {
      public void run() {
        EditorModificationUtil.deleteSelectedText(editor);
        if (result == CharFilter.ADD_TO_PREFIX) {
          lookup.setPrefix(lookup.getPrefix() + charTyped);
          EditorModificationUtil.insertStringAtCaret(editor, String.valueOf(charTyped));
        }
      }
    }, "", null);

    if (result == CharFilter.ADD_TO_PREFIX){
      if (LookupImpl.isNarrowDownMode()){
        lookup.updateList();
        Point point=lookup.calculatePosition();
        Dimension preferredSize = lookup.getComponent().getPreferredSize();
        lookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);
      }
      else{
        ListModel model = lookup.getList().getModel();
        for(int i = 0; i < model.getSize(); i++){
          LookupItem item = (LookupItem)model.getElementAt(i);
          String s = item.getLookupString();
          if (StringUtil.startsWithIgnoreCase(s, prefix)){
            ListScrollingUtil.selectItem(lookup.getList(), i);
            break;
          }
        }
      }

      lookup.getList().repaint();
    }
    else{
      if (result == CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP){
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

  private static int getLookupAction(final char charTyped, final CharFilter charFilter, final String prefix, final LookupItem<?> currentItem) {
    if (currentItem != null) {
      for (String lookupString : currentItem.getAllLookupStrings()) {
        if (lookupString.startsWith(prefix + charTyped)) return CharFilter.ADD_TO_PREFIX;
      }
    }
    return charFilter.accept(charTyped, prefix);
  }
}
