/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Yura Cangea
 */
package com.intellij.application.options.colors;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class ClickNavigator {
  private final JList myOptionsList;

  public ClickNavigator(JList optionsList) {
    myOptionsList = optionsList;
  }

  public void addClickNavigatorToGeneralView(final Editor view) {
    view.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        EditorUtil.setHandCursor(view);
      }
    });

    CaretListener listener = new CaretAdapter() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        setSelectedItem(HighlighterColors.TEXT.getExternalName(), true);
      }
    };
    view.getCaretModel().addCaretListener(listener);
  }

  private boolean setSelectedItem(String type, boolean select) {
    DefaultListModel model = (DefaultListModel)myOptionsList.getModel();

    for (int i = 0; i < model.size(); i++) {
      Object o = model.get(i);
      if (o instanceof EditorSchemeAttributeDescriptor) {
        if (type.equals(((EditorSchemeAttributeDescriptor)o).getType())) {
          if (select) {
            ScrollingUtil.selectItem(myOptionsList, i);
          }
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isWhiteSpace(int offset, CharSequence text) {
    return offset <= 0 || offset >= text.length() ||
           text.charAt(offset) == ' ' || text.charAt(offset) == '\t' ||
           text.charAt(offset) == '\n' || text.charAt(offset) == '\r';
  }

  public static boolean highlightDataContainsOffset(HighlightData data, int offset) {
    return offset >= data.getStartOffset() && offset <= data.getEndOffset();
  }

  public void addClickNavigator(final Editor view,
                                final SyntaxHighlighter highlighter,
                                final HighlightData[] data,
                                final boolean isBackgroundImportant) {
    addMouseMotionListener(view, highlighter, data, isBackgroundImportant);

    CaretListener listener = new CaretAdapter() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        navigate(view, true, e.getNewPosition(), highlighter, data, isBackgroundImportant);
      }
    };
    view.getCaretModel().addCaretListener(listener);
  }

  private boolean selectItem(boolean select, HighlighterIterator itr, SyntaxHighlighter highlighter) {

    IElementType tokenType = itr.getTokenType();
    if (tokenType == null) return false;
    String type = highlightingTypeFromTokenType(tokenType, highlighter);
    return setSelectedItem(type, select);
  }

  public static String highlightingTypeFromTokenType(IElementType tokenType, SyntaxHighlighter highlighter) {
    TextAttributesKey[] highlights = highlighter.getTokenHighlights(tokenType);
    String s = null;
    for (int i = highlights.length - 1; i >= 0; i--) {
      if (highlights[i] != HighlighterColors.TEXT) {
        s = highlights[i].getExternalName();
        break;
      }
    }
    return s == null ? HighlighterColors.TEXT.getExternalName() : s;
  }

  private void addMouseMotionListener(final Editor view,
                                      final SyntaxHighlighter highlighter,
                                      final HighlightData[] data, final boolean isBackgroundImportant) {
    view.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        LogicalPosition pos = view.xyToLogicalPosition(new Point(e.getX(), e.getY()));
        navigate(view, false, pos, highlighter, data, isBackgroundImportant);
      }
    });
  }

  private void navigate(final Editor editor, boolean select,
                        LogicalPosition pos,
                        final SyntaxHighlighter highlighter,
                        final HighlightData[] data, final boolean isBackgroundImportant) {
    int offset = editor.logicalPositionToOffset(pos);

    if (!isBackgroundImportant && editor.offsetToLogicalPosition(offset).column != pos.column) {
      if (!select) {
        setCursor(editor, Cursor.TEXT_CURSOR);
        return;
      }
    }

    if (data != null) {
      for (HighlightData highlightData : data) {
        if (highlightDataContainsOffset(highlightData, editor.logicalPositionToOffset(pos))) {
          if (!select) setCursor(editor, Cursor.HAND_CURSOR);
          setSelectedItem(highlightData.getHighlightType(), select);
          return;
        }
      }
    }

    if (highlighter != null) {
      HighlighterIterator itr = ((EditorEx)editor).getHighlighter().createIterator(offset);
      boolean selection = selectItem(select, itr, highlighter);
      if (!select && selection) {
        setCursor(editor, Cursor.HAND_CURSOR);
      }
      else {
        setCursor(editor, Cursor.TEXT_CURSOR);
      }
    }
  }

  public static void setCursor(final Editor view, @JdkConstants.CursorType int type) {
    final Cursor cursor = type == Cursor.TEXT_CURSOR && view instanceof EditorEx ?
                          UIUtil.getTextCursor(((EditorEx)view).getBackgroundColor()) : Cursor.getPredefinedCursor(type);
    view.getContentComponent().setCursor(cursor);
  }

}
