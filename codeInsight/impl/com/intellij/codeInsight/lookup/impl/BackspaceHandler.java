package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.actionSystem.DataContext;

import javax.swing.*;
import java.awt.*;

class BackspaceHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)editor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    if (lookup.getPrefix().length() > lookup.getInitialPrefix().length()){
      lookup.setPrefix(lookup.getPrefix().substring(0, lookup.getPrefix().length() - 1));
      if (LookupImpl.isNarrowDownMode()){
        lookup.updateList();
        Point point=lookup.calculatePosition();
        Dimension preferredSize = lookup.getComponent().getPreferredSize();
        lookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);
      }
      else{
        String prefix = lookup.getPrefix().toLowerCase();
        ListModel model = lookup.getList().getModel();
        for(int i = 0; i < model.getSize(); i++){
          LookupItem item = (LookupItem)model.getElementAt(i);
          String s = item.getLookupString();
          if (s.toLowerCase().startsWith(prefix)){
            lookup.getList().setSelectedIndex(i);
            lookup.getList().ensureIndexIsVisible(i);
            break;
          }
        }
      }
      lookup.getList().repaint();
    }
    else{
      lookup.hide();
    }

    myOriginalHandler.execute(editor, dataContext);
  }
}
