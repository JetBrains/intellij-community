
package com.intellij.codeHighlighting;

/** @fabrique */
public interface Pass {
  int ALL = 0xFF;

  int UPDATE_FOLDING = 0x01;
  int UPDATE_VISIBLE = 0x02;
  int POPUP_HINTS = 0x04;
  int UPDATE_ALL = 0x08;
  int POST_UPDATE_ALL = 0x10;
  int UPDATE_OVERRIDEN_MARKERS = 0x20;
  int LOCAL_INSPECTIONS = 0x40;
  int POPUP_HINTS2 = 0x80;
  int EXTERNAL_TOOLS = 0x100;
}