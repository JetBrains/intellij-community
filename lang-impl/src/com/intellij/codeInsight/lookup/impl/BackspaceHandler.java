package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

import java.awt.*;

class BackspaceHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    if (lookup.getMinPrefixLength() > lookup.getInitialMinPrefixLength()) {
      for (final LookupElement item : lookup.getItems()) {
        final PrefixMatcher oldMatcher = item.getPrefixMatcher();
        final String oldPrefix = oldMatcher.getPrefix();
        final String newPrefix = oldPrefix.substring(0, oldPrefix.length() - 1);
        item.setPrefixMatcher(oldMatcher.cloneWithPrefix(newPrefix));
      }
      lookup.updateList();
      Point point = lookup.calculatePosition();
      Dimension preferredSize = lookup.getComponent().getPreferredSize();
      lookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);
      lookup.getList().repaint();
    }
    else{
      lookup.hide();
    }

    myOriginalHandler.execute(editor, dataContext);
  }
}
