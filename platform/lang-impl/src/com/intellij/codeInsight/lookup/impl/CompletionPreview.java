/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionPreview {
  private final LookupImpl myLookup;
  private Disposable myUninstaller;

  private CompletionPreview(LookupImpl lookup, final String text) {
    myLookup = lookup;

    final EditorImpl editor = getEditorImpl();
    editor.setCustomImage(Pair.create(getCaretPoint(), createPreviewImage(text)));
    repaintCaretLine();

    myUninstaller = new Disposable() {
      @Override
      public void dispose() {
        myLookup.setPreview(null);
        myUninstaller = null;
        editor.setCustomImage(null);
        repaintCaretLine();
      }
    };
    myLookup.setPreview(this);
    Disposer.register(myLookup, myUninstaller);
  }

  private EditorImpl getEditorImpl() {
    return (EditorImpl)myLookup.getEditor();
  }

  private void repaintCaretLine() {
    EditorImpl editor = getEditorImpl();
    int caretTop = getCaretPoint().y;
    editor.getContentComponent().repaintEditorComponent(0, caretTop, editor.getContentComponent().getWidth(), caretTop + editor.getLineHeight());
  }

  private Point getCaretPoint() {
    final Editor editor = getEditorImpl();
    return editor.logicalPositionToXY(editor.getCaretModel().getLogicalPosition());
  }

  private BufferedImage createPreviewImage(final String previewText) {
    Point caretTop = getCaretPoint();
    EditorImpl editor = getEditorImpl();
    TextAttributes attributes = getPreviewTextAttributes();
    Font font = EditorUtil.fontForChar('W', attributes.getFontType(), editor).getFont();

    int previewWidth = editor.getComponent().getFontMetrics(font).stringWidth(previewText);
    int restLineWidth = editor.getContentComponent().getWidth() - caretTop.x;
    int lineHeight = editor.getLineHeight();

    BufferedImage textImage = UIUtil.createImage(previewWidth + restLineWidth, lineHeight, BufferedImage.TYPE_INT_RGB);
    Graphics g = textImage.getGraphics();
    UISettings.setupAntialiasing(g);

    g.setColor(attributes.getBackgroundColor());
    g.setFont(font);
    g.fillRect(0, 0, previewWidth, lineHeight);

    g.setColor(JBColor.gray);
    g.drawString(previewText, 0, editor.getAscent());

    g.translate(-caretTop.x + previewWidth, -caretTop.y);
    g.setClip(caretTop.x, caretTop.y, restLineWidth, lineHeight);
    editor.setRendererMode(true);
    editor.getContentComponent().paint(g);
    editor.setRendererMode(false);

    return textImage;
  }

  private TextAttributes getPreviewTextAttributes() {
    EditorEx editor = getEditorImpl();
    int caret = editor.getCaretModel().getOffset();
    IterationState state = new IterationState(editor, caret, caret, false);
    TextAttributes attributes = state.getMergedAttributes();
    state.dispose();
    return attributes;
  }

  public static void reinstallPreview(@Nullable CompletionPreview oldPreview) {
    if (oldPreview != null && !oldPreview.myLookup.isLookupDisposed()) {
      installPreview(oldPreview.myLookup);
    }
  }
  
  public static void installPreview(LookupImpl lookup) {
    LookupElement item = lookup.getCurrentItem();
    if (item == null || !(lookup.getEditor() instanceof EditorImpl)) {
      return;
    }

    String text = getPreviewText(lookup, item);

    int prefixLength = lookup.getPrefixLength(item);
    if (prefixLength > text.length()) {
      return;
    }
    FList<TextRange> fragments = LookupCellRenderer.getMatchingFragments(lookup.itemPattern(item).substring(0, prefixLength), text);
    if (fragments == null) {
      return;
    }

    if (!fragments.isEmpty()) {
      ArrayList<TextRange> arrayList = new ArrayList<TextRange>(fragments);
      prefixLength = arrayList.get(arrayList.size() - 1).getEndOffset();
    }
    new CompletionPreview(lookup, text.substring(prefixLength));
  }

  private static String getPreviewText(LookupImpl lookup, LookupElement item) {
    LookupElementPresentation presentation = LookupElementPresentation.renderElement(item);
    String text = presentation.getItemText();
    if (text == null) {
      text = item.getLookupString();
    }

    String tailText = presentation.getTailText();
    if (tailText != null && tailText.startsWith("(") && tailText.contains(")")) {
      Editor editor = lookup.getEditor();
      CharSequence seq = editor.getDocument().getCharsSequence();
      int caret = editor.getCaretModel().getOffset();
      if (caret >= seq.length() || seq.charAt(caret) != '(') {
        return text + "()";
      }
    }
    return text;
  }

  public void uninstallPreview() {
    if (myUninstaller != null) {
      Disposer.dispose(myUninstaller);
      assert myUninstaller == null;
    }
  }

}
