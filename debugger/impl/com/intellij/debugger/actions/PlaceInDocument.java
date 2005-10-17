package com.intellij.debugger.actions;

import com.intellij.openapi.editor.Document;

/**
 * User: lex
 * Date: Oct 7, 2003
 * Time: 3:12:54 PM
 */
interface PlaceInDocument {
  public Document getDocument();
  public int      getOffset();
}
