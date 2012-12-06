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
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author peter
 */
public class CompletionPreview {
  private final LookupImpl myLookup;
  private Disposable myUninstaller;

  private CompletionPreview(LookupImpl lookup, final String text, final String prefix) {
    myLookup = lookup;

    final Editor editor = myLookup.getEditor();
    final int caret = editor.getCaretModel().getOffset();
    int previewStart = caret - prefix.length();
    final int previewEnd = previewStart + text.length();

    myLookup.performGuardedChange(new Runnable() {
      @Override
      public void run() {
        Runnable runnable = new Runnable() {
          public void run() {
            AccessToken token = WriteAction.start();
            try {
              editor.getDocument().insertString(caret, text.substring(prefix.length()));
            }
            finally {
              token.finish();
            }
          }
        };
        CommandProcessor.getInstance().runUndoTransparentAction(runnable);
      }
    }, "preview");
    final RangeHighlighter highlighter = myLookup.getEditor().getMarkupModel()
      .addRangeHighlighter(caret, previewEnd, HighlighterLayer.LAST,
                           new TextAttributes(Color.GRAY, null, null, null, Font.PLAIN),
                           HighlighterTargetArea.EXACT_RANGE);
    
    myUninstaller = new Disposable() {
      @Override
      public void dispose() {
        myLookup.setPreview(null);
        myUninstaller = null;

        if (editor.isDisposed() || !highlighter.isValid()) {
          return;
        }

        myLookup.performGuardedChange(new Runnable() {
          @Override
          public void run() {
            Runnable runnable = new Runnable() {
              public void run() {
                AccessToken token = WriteAction.start();
                try {
                  editor.getDocument().deleteString(highlighter.getStartOffset(), highlighter.getEndOffset());
                }
                finally {
                  token.finish();
                }
              }
            };
            CommandProcessor.getInstance().runUndoTransparentAction(runnable);
          }
        }, "remove preview");
        editor.getMarkupModel().removeHighlighter(highlighter);
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
    
    LookupElementPresentation presentation = LookupElementPresentation.renderElement(item);
    String text = presentation.getItemText();
    if (text == null) {
      text = item.getLookupString();
    } else {
      String tailText = presentation.getTailText();
      if (tailText != null && tailText.startsWith("(") && tailText.contains(")")) {
        Editor editor = lookup.getEditor();
        CharSequence seq = editor.getDocument().getCharsSequence();
        int caret = editor.getCaretModel().getOffset();
        if (caret < seq.length() && seq.charAt(caret) != '(') {
          text += "()";
        }
      }
    }

    new CompletionPreview(lookup, text, lookup.itemPattern(item));
  }

  public void uninstallPreview() {
    if (myUninstaller != null) {
      Disposer.dispose(myUninstaller);
      assert myUninstaller == null;
    }
  }

}
