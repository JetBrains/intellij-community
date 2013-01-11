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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionPreview {
  private final LookupImpl myLookup;
  private Disposable myUninstaller;

  private CompletionPreview(LookupImpl lookup, final String text, final int prefixLength) {
    myLookup = lookup;

    final EditorImpl editor = (EditorImpl)myLookup.getEditor();

    myLookup.performGuardedChange(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            AccessToken token = WriteAction.start();
            try {
              String preview = text.substring(prefixLength);
              int caret = editor.getCaretModel().getOffset();
              int previewEnd = caret + preview.length();

              editor.getDocument().insertString(caret, preview);
              final RangeHighlighter highlighter = editor.getMarkupModel()
                .addRangeHighlighter(caret, previewEnd, HighlighterLayer.LAST,
                                     new TextAttributes(JBColor.GRAY, null, null, null, Font.PLAIN),
                                     HighlighterTargetArea.EXACT_RANGE);

              editor.startDumb();

              editor.getMarkupModel().removeHighlighter(highlighter);
              editor.getDocument().deleteString(caret, previewEnd);
            }
            finally {
              token.finish();
            }
          }
        });
      }
    }, "preview");


    myUninstaller = new Disposable() {
      @Override
      public void dispose() {
        myLookup.setPreview(null);
        myUninstaller = null;
        editor.stopDumb();
        editor.getContentComponent().repaintEditorComponent();
      }
    };
    myLookup.setPreview(this);
    Disposer.register(myLookup, myUninstaller);
  }

  public static void reinstallPreview(@Nullable CompletionPreview oldPreview) {
    if (oldPreview != null && !oldPreview.myLookup.isLookupDisposed()) {
      installPreview(oldPreview.myLookup);
    }
  }
  
  public static void installPreview(LookupImpl lookup) {
    LookupElement item = lookup.getCurrentItem();
    if (item == null) {
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
    new CompletionPreview(lookup, text, prefixLength);
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
