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
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionPreview implements Disposable {
  private static final Key<CompletionPreview> COMPLETION_PREVIEW_KEY = Key.create("COMPLETION_PREVIEW_KEY");
  private final LookupImpl myLookup;
  private final MergingUpdateQueue myQueue;
  private Update myUpdate = new Update("update") {
    @Override
    public void run() {
      updatePreview();
    }
  };

  private CompletionPreview(LookupImpl lookup) {
    myLookup = lookup;
    myQueue = new MergingUpdateQueue("Lookup Preview", 50, true, getEditorImpl().getContentComponent(), this);
    myLookup.putUserData(COMPLETION_PREVIEW_KEY, this);

    Disposer.register(myLookup, this);

    myLookup.addLookupListener(new LookupListener() {
      @Override
      public void itemSelected(LookupEvent event) {
      }

      @Override
      public void lookupCanceled(LookupEvent event) {
      }

      @Override
      public void currentItemChanged(LookupEvent event) {
        myQueue.queue(myUpdate);
      }
    });
    myQueue.queue(myUpdate);
  }

  public static boolean hasPreview(LookupImpl lookup) {
    return COMPLETION_PREVIEW_KEY.get(lookup) != null;
  }

  private void updatePreview() {
    if (true) {
      return;
    }

    final EditorImpl editor = getEditorImpl();
    editor.setCustomImage(null);
    repaintCaretLine();

    LookupElement item = myLookup.getCurrentItem();
    if (item == null) {
      return;
    }

    if (editor.getSelectionModel().hasSelection() || editor.getSelectionModel().hasBlockSelection()) {
      return;
    }

    String text = getPreviewText(item);

    int prefixLength = myLookup.getPrefixLength(item);
    if (prefixLength > text.length()) {
      return;
    }
    FList<TextRange> fragments = LookupCellRenderer.getMatchingFragments(myLookup.itemPattern(item).substring(0, prefixLength), text);
    if (fragments == null) {
      return;
    }

    if (!fragments.isEmpty()) {
      ArrayList<TextRange> arrayList = new ArrayList<TextRange>(fragments);
      prefixLength = arrayList.get(arrayList.size() - 1).getEndOffset();
    }


    BufferedImage previewImage = createPreviewImage(text.substring(prefixLength));
    editor.setCustomImage(Pair.create(getCaretPoint(), previewImage));
    repaintCaretLine();
  }

  @Override
  public void dispose() {
    getEditorImpl().setCustomImage(null);
    repaintCaretLine();
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

  public static void installPreview(LookupImpl lookup) {
    if (ApplicationManager.getApplication().isUnitTestMode() || !(lookup.getEditor() instanceof EditorImpl)) {
      return;
    }
    new CompletionPreview(lookup);
  }

  private String getPreviewText(LookupElement item) {
    LookupElementPresentation presentation = LookupElementPresentation.renderElement(item);
    String text = presentation.getItemText();
    if (text == null) {
      text = item.getLookupString();
    }

    String tailText = presentation.getTailText();
    if (tailText != null && tailText.startsWith("(") && tailText.contains(")")) {
      Editor editor = getEditorImpl();
      CharSequence seq = editor.getDocument().getCharsSequence();
      int caret = editor.getCaretModel().getOffset();
      if (caret >= seq.length() || seq.charAt(caret) != '(') {
        return text + "()";
      }
    }
    return text;
  }

}
