/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.application.options.colors;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.application.options.colors.highlighting.HighlightsExtractor;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.EditorHighlightingProvidingColorSettingsPage;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimpleEditorPreview implements PreviewPanel {
  private final ColorSettingsPage myPage;

  private final EditorEx myEditor;
  private final Alarm myBlinkingAlarm;
  private final List<HighlightData> myHighlightData = new ArrayList<>();

  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);
  private final HighlightsExtractor myHighlightsExtractor;
  private boolean myTextIsChanging = false;

  public SimpleEditorPreview(final ColorAndFontOptions options, final ColorSettingsPage page) {
    this(options, page, true);
  }

  @NotNull
  public List<HighlightData> getHighlightDataForExtension() {
    return myHighlightData;
  }

  public SimpleEditorPreview(final ColorAndFontOptions options, final ColorSettingsPage page, final boolean navigatable) {
    myOptions = options;
    myPage = page;

    myHighlightsExtractor = new HighlightsExtractor(page.getAdditionalHighlightingTagToDescriptorMap());
    myEditor = (EditorEx)FontEditorPreview.createPreviewEditor(
      myHighlightsExtractor.extractHighlights(page.getDemoText(), myHighlightData), // text without tags
      10, 3, -1, myOptions, false);

    FontEditorPreview.installTrafficLights(myEditor);
    myBlinkingAlarm = new Alarm().setActivationComponent(myEditor.getComponent());
    if (navigatable) {
      myEditor.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          LogicalPosition pos = myEditor.xyToLogicalPosition(new Point(e.getX(), e.getY()));
          navigate(myEditor, false, pos, page.getHighlighter(), false);
        }
      });

      myEditor.getCaretModel().addCaretListener(new CaretAdapter() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          if (!myTextIsChanging) {
            navigate(myEditor, true, e.getNewPosition(), page.getHighlighter(), false);
          }
        }
      });
    }
  }

  public EditorEx getEditor() {
    return myEditor;
  }

  public void setDemoText(final String text) {
    try {
      myTextIsChanging = true;
      myEditor.getSelectionModel().removeSelection();
      myHighlightData.clear();
      myEditor.getDocument().setText(myHighlightsExtractor.extractHighlights(text, myHighlightData));
    }
    finally {
      myTextIsChanging = false;
    }
  }

  private void navigate(final Editor editor, boolean select,
                        LogicalPosition pos,
                        final SyntaxHighlighter highlighter,
                        final boolean isBackgroundImportant) {
    int offset = editor.logicalPositionToOffset(pos);

    if (!isBackgroundImportant && editor.offsetToLogicalPosition(offset).column != pos.column) {
      if (!select) {
        ClickNavigator.setCursor(editor, Cursor.TEXT_CURSOR);
        return;
      }
    }

    for (HighlightData highlightData : myHighlightData) {
      if (ClickNavigator.highlightDataContainsOffset(highlightData, editor.logicalPositionToOffset(pos))) {
        if (!select) {
          ClickNavigator.setCursor(editor, Cursor.HAND_CURSOR);
        }
        else {
          myDispatcher.getMulticaster().selectionInPreviewChanged(
            RainbowHighlighter.isRainbowTempKey(highlightData.getHighlightKey())
            ? RainbowHighlighter.RAINBOW_TYPE
            : highlightData.getHighlightType());
        }
        return;
      }
    }

    if (highlighter != null) {
      HighlighterIterator itr = ((EditorEx)editor).getHighlighter().createIterator(offset);
      selectItem(itr, highlighter, select);
      ClickNavigator.setCursor(editor, select ? Cursor.TEXT_CURSOR : Cursor.HAND_CURSOR);
    }
  }

  private void selectItem(HighlighterIterator itr, SyntaxHighlighter highlighter, final boolean select) {
    IElementType tokenType = itr.getTokenType();
    if (tokenType == null) return;
    String type = ClickNavigator.highlightingTypeFromTokenType(tokenType, highlighter);
    if (select) {
      myDispatcher.getMulticaster().selectionInPreviewChanged(type);
    }
  }

  @Override
  public JComponent getPanel() {
    return myEditor.getComponent();
  }

  @Override
  public void updateView() {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    myEditor.setColorsScheme(scheme);

    EditorHighlighter highlighter = null;
    if (myPage instanceof EditorHighlightingProvidingColorSettingsPage) {

      highlighter = ((EditorHighlightingProvidingColorSettingsPage)myPage).createEditorHighlighter(scheme);
    }
    if (highlighter == null) {
      final SyntaxHighlighter pageHighlighter = myPage.getHighlighter();
      highlighter = HighlighterFactory.createHighlighter(pageHighlighter, scheme);
    }
    myEditor.setHighlighter(highlighter);
    updateHighlighters();

    myEditor.reinitSettings();
  }

  private void updateHighlighters() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myEditor.isDisposed()) return;
      myEditor.getMarkupModel().removeAllHighlighters();
      final Map<TextAttributesKey, String> displayText = ColorSettingsUtil.keyToDisplayTextMap(myPage);
      for (final HighlightData data : myHighlightData) {
        data.addHighlToView(myEditor, myOptions.getSelectedScheme(), displayText);
      }
    });
  }

  private static final int BLINK_COUNT = 3 * 2;

  @Override
  public void blinkSelectedHighlightType(Object description) {
    if (description instanceof EditorSchemeAttributeDescriptor) {
      String type = ((EditorSchemeAttributeDescriptor)description).getType();

      List<HighlightData> highlights = startBlinkingHighlights(myEditor,
                                                               type,
                                                               myPage.getHighlighter(), true,
                                                               myBlinkingAlarm, BLINK_COUNT, myPage);

      scrollHighlightInView(highlights);
    }
  }

  void scrollHighlightInView(@Nullable final List<HighlightData> highlightDatas) {
    if (highlightDatas == null) return;

    boolean needScroll = true;
    int minOffset = Integer.MAX_VALUE;
    for (HighlightData data : highlightDatas) {
      if (isOffsetVisible(data.getStartOffset())) {
        needScroll = false;
        break;
      }
      minOffset = Math.min(minOffset, data.getStartOffset());
    }
    if (needScroll && minOffset != Integer.MAX_VALUE) {
      LogicalPosition pos = myEditor.offsetToLogicalPosition(minOffset);
      myEditor.getScrollingModel().scrollTo(pos, ScrollType.MAKE_VISIBLE);
    }
  }

  private boolean isOffsetVisible(final int startOffset) {
    return myEditor
      .getScrollingModel()
      .getVisibleArea()
      .contains(myEditor.logicalPositionToXY(myEditor.offsetToLogicalPosition(startOffset)));
  }

  public void stopBlinking() {
    myBlinkingAlarm.cancelAllRequests();
  }

  private List<HighlightData> startBlinkingHighlights(final EditorEx editor,
                                                      final String attrKey,
                                                      final SyntaxHighlighter highlighter,
                                                      final boolean show,
                                                      final Alarm alarm,
                                                      final int count,
                                                      final ColorSettingsPage page) {
    if (show && count <= 0) return Collections.emptyList();
    editor.getMarkupModel().removeAllHighlighters();
    boolean found = false;
    List<HighlightData> highlights = new ArrayList<>();
    List<HighlightData> matchingHighlights = new ArrayList<>();
    for (HighlightData highlightData : myHighlightData) {
      String type = highlightData.getHighlightType();
      highlights.add(highlightData);
      if (show && type.equals(attrKey)) {
        highlightData =
          new HighlightData(highlightData.getStartOffset(), highlightData.getEndOffset(),
                            CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
        highlights.add(highlightData);
        matchingHighlights.add(highlightData);
        found = true;
      }
    }
    if (!found && highlighter != null) {
      HighlighterIterator iterator = editor.getHighlighter().createIterator(0);
      do {
        IElementType tokenType = iterator.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);
        for (final TextAttributesKey tokenHighlight : tokenHighlights) {
          String type = tokenHighlight.getExternalName();
          if (show && type != null && type.equals(attrKey)) {
            HighlightData highlightData = new HighlightData(iterator.getStart(), iterator.getEnd(),
                                                            CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
            highlights.add(highlightData);
            matchingHighlights.add(highlightData);
          }
        }
        iterator.advance();
      }
      while (!iterator.atEnd());
    }

    final Map<TextAttributesKey, String> displayText = ColorSettingsUtil.keyToDisplayTextMap(page);

    // sort highlights to avoid overlappings
    Collections.sort(highlights, (highlightData1, highlightData2) -> highlightData1.getStartOffset() - highlightData2.getStartOffset());
    for (int i = highlights.size() - 1; i >= 0; i--) {
      HighlightData highlightData = highlights.get(i);
      int startOffset = highlightData.getStartOffset();
      HighlightData prevHighlightData = i == 0 ? null : highlights.get(i - 1);
      if (prevHighlightData != null
          && startOffset <= prevHighlightData.getEndOffset()
          && highlightData.getHighlightType().equals(prevHighlightData.getHighlightType())) {
        prevHighlightData.setEndOffset(highlightData.getEndOffset());
      }
      else {
        highlightData.addHighlToView(editor, myOptions.getSelectedScheme(), displayText);
      }
    }
    alarm.cancelAllRequests();
    alarm.addComponentRequest(() -> startBlinkingHighlights(editor, attrKey, highlighter, !show, alarm, count - 1, page), 400);
    return matchingHighlights;
  }


  @Override
  public void addListener(@NotNull final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void disposeUIResources() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);
    stopBlinking();
  }
}
