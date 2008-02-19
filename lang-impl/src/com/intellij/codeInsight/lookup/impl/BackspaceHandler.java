package com.intellij.codeInsight.lookup.impl;

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
    LookupImpl lookup = (LookupImpl)editor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    if (lookup.getPrefix().length() > lookup.getInitialPrefix().length()){
      lookup.setPrefix(lookup.getPrefix().substring(0, lookup.getPrefix().length() - 1));
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
