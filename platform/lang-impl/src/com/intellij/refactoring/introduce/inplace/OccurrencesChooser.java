/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.introduce.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 10/14/10
 */
// Please do not make this class concrete<PsiElement>.
// This prevents languages with polyadic expressions or sequences
// from reusing it, use simpleChooser instead.
public abstract class OccurrencesChooser<T> {
  public static enum ReplaceChoice {
    NO("Replace this occurrence only"), NO_WRITE("Replace all occurrences but write"), ALL("Replace all {0} occurrences");

    private final String myDescription;

    ReplaceChoice(String description) {
      myDescription = description;
    }

    public String getDescription() {
      return myDescription;
    }
  }

  public static <T extends PsiElement> OccurrencesChooser<T> simpleChooser(Editor editor) {
    return new OccurrencesChooser<T>(editor) {
      @Override
      protected TextRange getOccurrenceRange(T occurrence) {
        return occurrence.getTextRange();
      }
    };
  }

  private final Set<RangeHighlighter> myRangeHighlighters = new HashSet<RangeHighlighter>();
  private final Editor myEditor;
  private final TextAttributes myAttributes;

  public OccurrencesChooser(Editor editor) {
    myEditor = editor;
    myAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
  }

  public void showChooser(final T selectedOccurrence, final List<T> allOccurrences, final Pass<ReplaceChoice> callback) {
    if (allOccurrences.size() == 1) {
      callback.pass(ReplaceChoice.ALL);
    }
    else {
      Map<ReplaceChoice, List<T>> occurrencesMap = ContainerUtil.newLinkedHashMap();
      occurrencesMap.put(ReplaceChoice.NO, Collections.singletonList(selectedOccurrence));
      occurrencesMap.put(ReplaceChoice.ALL, allOccurrences);
      showChooser(callback, occurrencesMap);
    }
  }

  public void showChooser(final Pass<ReplaceChoice> callback, final Map<ReplaceChoice, List<T>> occurrencesMap) {
    if (occurrencesMap.size() == 1) {
      callback.pass(occurrencesMap.keySet().iterator().next());
      return;
    }
    final DefaultListModel model = new DefaultListModel();
    for (ReplaceChoice choice : occurrencesMap.keySet()) {
      model.addElement(choice);
    }
    final JList list = new JBList(model);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final ReplaceChoice choices = (ReplaceChoice)value;
        if (choices != null) {
          String text = choices.getDescription();
          if (choices == ReplaceChoice.ALL) {
            text = MessageFormat.format(text, occurrencesMap.get(choices).size());
          }
          setText(text);
        }
        return rendererComponent;
      }
    });
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        final ReplaceChoice value = (ReplaceChoice)list.getSelectedValue();
        if (value == null) return;
        dropHighlighters();
        final MarkupModel markupModel = myEditor.getMarkupModel();
        final List<T> occurrenceList = occurrencesMap.get(value);
        for (T occurrence : occurrenceList) {
          final TextRange textRange = getOccurrenceRange(occurrence);
          final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
            textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, myAttributes,
            HighlighterTargetArea.EXACT_RANGE);
          myRangeHighlighters.add(rangeHighlighter);
        }
      }
    });

    JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setTitle("Multiple occurrences found")
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          callback.pass((ReplaceChoice)list.getSelectedValue());
        }
      })
      .addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          dropHighlighters();
        }
      })
      .createPopup().showInBestPositionFor(myEditor);
  }

  protected abstract TextRange getOccurrenceRange(T occurrence);

  private void dropHighlighters() {
    for (RangeHighlighter highlight : myRangeHighlighters) {
      highlight.dispose();
    }
    myRangeHighlighters.clear();
  }
}
