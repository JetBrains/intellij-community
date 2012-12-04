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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class CompletionPreview {
  private final LookupImpl myLookup;
  private Disposable myUninstaller;
  private int myPreviewStart;

  public CompletionPreview(LookupImpl lookup) {
    myLookup = lookup;
  }

  public void installPreview() {
    List<LookupElement> items = myLookup.getItems();
    if (items.isEmpty()) {
      return;
    }

    LookupElement first = items.get(0);
    final String text = getPreviewText(first);
    if (text == null) {
      return;
    }

    final String prefix = myLookup.itemPattern(first);
    final Iterable<TextRange> fragments = LookupCellRenderer.getMatchingFragments(prefix, text);
    if (fragments == null) {
      return;
    }

    ArrayList<TextRange> ranges = ContainerUtil.newArrayList(fragments);
    if (ranges.isEmpty()) {
      return;
    }

    final int lastMatch = ranges.get(ranges.size() - 1).getEndOffset();
    
    final Editor editor = myLookup.getEditor();
    final int caret = editor.getCaretModel().getOffset();
    myPreviewStart = caret - prefix.length();
    final int previewCaret = myPreviewStart + lastMatch;
    final int previewEnd = myPreviewStart + text.length();

    final List<RangeHighlighter> highlighters = ContainerUtil.newArrayList();
    myLookup.performGuardedChange(new Runnable() {
      @Override
      public void run() {
        Runnable runnable = new Runnable() {
          public void run() {
            AccessToken token = WriteAction.start();
            try {
              editor.getDocument().insertString(caret, text.substring(lastMatch));
              editor.getDocument().replaceString(myPreviewStart, caret, text.substring(0, lastMatch));
              editor.getCaretModel().moveToOffset(previewCaret);
            }
            finally {
              token.finish();
            }
          }
        };
        CommandProcessor.getInstance().runUndoTransparentAction(runnable);

        int lastOffset = 0;
        for (TextRange range : fragments) {
          if (range.getStartOffset() > lastOffset) {
            highlighters.add(createRange(lastOffset, range.getStartOffset(), true));
          }
          highlighters.add(createRange(range.getStartOffset(), range.getEndOffset(), false));
          lastOffset = range.getEndOffset();
        }
        if (lastOffset < text.length()) {
          highlighters.add(createRange(lastOffset, text.length(), true));        
        }
        
      }
    }, "preview");
    myLookup.setPreview(this);
    
    myUninstaller = new Disposable() {
      @Override
      public void dispose() {
        myLookup.setPreview(null);
        myUninstaller = null;

        if (editor.isDisposed()) {
          return;
        }
        
        for (RangeHighlighter highlighter : highlighters) {
          editor.getMarkupModel().removeHighlighter(highlighter);
        }
        
        myLookup.performGuardedChange(new Runnable() {
          @Override
          public void run() {
            Runnable runnable = new Runnable() {
              public void run() {
                AccessToken token = WriteAction.start();
                try {
                  editor.getDocument().replaceString(myPreviewStart, previewEnd, prefix);
                  editor.getCaretModel().moveToOffset(myPreviewStart + prefix.length());
                }
                finally {
                  token.finish();
                }
              }
            };
            CommandProcessor.getInstance().runUndoTransparentAction(runnable);
          }
        }, "remove preview");
      }
    };
    Disposer.register(myLookup, myUninstaller);
  }

  private RangeHighlighter createRange(final int start, final int end, boolean generated) {
    return myLookup.getEditor().getMarkupModel().addRangeHighlighter(myPreviewStart + start, myPreviewStart + end, HighlighterLayer.LAST, 
                                                       new TextAttributes(generated ? Color.LIGHT_GRAY : Color.BLACK, null, null, null, Font.PLAIN),
                                                                 HighlighterTargetArea.EXACT_RANGE);
  }

  public void uninstallPreview() {
    if (myUninstaller != null) {
      Disposer.dispose(myUninstaller);
      assert myUninstaller == null;
    }
  }

  @Nullable 
  private static String getPreviewText(LookupElement item) {
    LookupElementPresentation presentation = LookupElementPresentation.renderElement(item);
    String text = presentation.getItemText();
    if (text == null) {
      return null;
    }
    String tailText = presentation.getTailText();
    if (tailText != null && tailText.startsWith("(") && tailText.contains(")")) {
      text += "()";
    }
    return text;
  }
}
