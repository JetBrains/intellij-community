package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.text.StringUtil;

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
        final String prefix = lookup.getPrefix();
        ListModel model = lookup.getList().getModel();
        for(int i = 0; i < model.getSize(); i++){
          LookupItem item = (LookupItem)model.getElementAt(i);
          if (StringUtil.startsWithIgnoreCase(item.getLookupString(), prefix)){
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
