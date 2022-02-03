// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.highlighter.LightHighlighterClient;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.FontUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;

class RecentLocationsRenderer extends EditorTextFieldCellRenderer.SimpleWithGutterRendererComponent implements ListCellRenderer<RecentLocationItem> {
  private final Project myProject;
  private final RecentLocationsDataModel myModel;
  private final JBCheckBox myCheckBox;

  private final SimpleColoredComponent myTitle = new SimpleColoredComponent();
  private final ConcurrentLinkedDeque<RecentLocationItem> myItemsDeque = new ConcurrentLinkedDeque<>();
  private final Map<RecentLocationItem, Couple<Highlight[]>> myItemHighlights = new ConcurrentHashMap<>();
  private Future<?> myHighlightingFuture;

  private RecentLocationItem myCurrentValueForPainting;
  private boolean myCurrentSelectedForPainting;

  RecentLocationsRenderer(@NotNull Project project,
                          @NotNull RecentLocationsDataModel model,
                          @NotNull JBCheckBox checkBox) {
    super(project, null, false);
    myProject = project;
    myModel = model;
    myCheckBox = checkBox;
    myTitle.setBorder(JBUI.Borders.empty(8, 6, 5, 0));
    getEditor().setBorder(JBUI.Borders.empty(0, 4, 6, 0));
    setupEditor(getEditor());

    setLayout(new BorderLayout());
    add(getEditor().getComponent(), BorderLayout.CENTER);
    add(myTitle, BorderLayout.NORTH);
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myHighlightingFuture != null) {
      myHighlightingFuture.cancel(true);
      myHighlightingFuture = null;
    }
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends RecentLocationItem> list,
                                                RecentLocationItem value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {
    myTitle.clear();
    if (myProject.isDisposed() || getEditor().isDisposed()) return myTitle;
    myCurrentValueForPainting = value;
    myCurrentSelectedForPainting = selected;

    EditorColorsScheme colorsScheme = getEditor().getColorsScheme();
    Color backgroundColor = getBackgroundColor(colorsScheme, selected);
    myTitle.setForeground(colorsScheme.getDefaultForeground());
    setForcedBackground(backgroundColor);
    getEditor().setBackgroundColor(backgroundColor);

    customizeTitleComponentText(value.info);
    customizeEditorComponent(value);

    setBorder(index == 0 ? JBUI.Borders.empty() :
              JBUI.Borders.customLine(getSeparatorLineColor(colorsScheme), 1, 0, 0, 0));
    return this;
  }

  @NotNull String getSpeedSearchText(@NotNull RecentLocationItem item) {
    String breadcrumb = myModel.getBreadcrumbsMap(myCheckBox.isSelected()).get(item.info);
    return breadcrumb + " " + item.info.getFile().getName() + " " + item.text;
  }

  private static @NotNull Color getBackgroundColor(@NotNull EditorColorsScheme colorsScheme, boolean selected) {
    return selected ? HintUtil.getRecentLocationsSelectionColor(colorsScheme) : colorsScheme.getDefaultBackground();
  }

  private static @NotNull Color getSeparatorLineColor(@NotNull EditorColorsScheme colorsScheme) {
    Color color = colorsScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    return color == null ? JBColor.namedColor("Group.separatorColor", new JBColor(Gray.xCD, Gray.x51)) : color;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Objects.requireNonNull(myCurrentValueForPainting);
    JList<?> component = Objects.requireNonNull(UIUtil.getParentOfType(JList.class, this));
    scheduleHighlightingIfNeeded(component);
    applyEditorHighlighting(myCurrentValueForPainting);
    applyEditorSpeedSearchHighlighting(SpeedSearchSupply.getSupply(component));
    SpeedSearchUtil.applySpeedSearchHighlighting(component, myTitle, true, myCurrentSelectedForPainting);
    super.paintComponent(g);
  }

  private void customizeEditorComponent(RecentLocationItem value) {
    getEditor().getCaretModel().removeSecondaryCarets();
    getEditor().getSelectionModel().removeSelection(true);
    getEditor().getMarkupModel().removeAllHighlighters();
    getEditor().getGutterComponentEx().setLineNumberConverter(
      value.linesShift == 0 ? LineNumberConverter.DEFAULT : new LineNumberConverter.Increasing() {
        @Override
        public Integer convert(@NotNull Editor editor, int lineNumber) {
          return lineNumber + value.linesShift;
        }
      });
    setText(value.text);
    getEditor().getGutterComponentEx().updateUI();
  }

  private void applyEditorHighlighting(RecentLocationItem value) {
    Couple<Highlight[]> highlights = myItemHighlights.get(value);
    if (highlights == null) {
      myItemsDeque.addFirst(value);
    }
    else {
      MarkupModelEx markupModel = getEditor().getMarkupModel();
      for (Highlight highlight : highlights.first) {
        markupModel.addRangeHighlighter(highlight.start, highlight.end, HighlighterLayer.SYNTAX - 1,
                                  highlight.attrs, HighlighterTargetArea.EXACT_RANGE);
      }
      for (Highlight highlight : highlights.second) {
        markupModel.addRangeHighlighter(highlight.start, highlight.end, HighlighterLayer.SYNTAX,
                                  highlight.attrs, HighlighterTargetArea.EXACT_RANGE);
      }
    }
  }

  private static void setupEditor(@NotNull EditorEx editor) {
    editor.getGutterComponentEx().setPaintBackground(false);
    editor.getScrollPane().setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(true);
    settings.setUseSoftWraps(false);
  }

  private void customizeTitleComponentText(@NotNull IdeDocumentHistoryImpl.PlaceInfo place) {
    String breadcrumbs = myModel.getBreadcrumbsMap(myCheckBox.isSelected()).get(place);
    String fileName = place.getFile().getName();
    myTitle.append(fileName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    if (StringUtil.isNotEmpty(breadcrumbs) && !StringUtil.equals(breadcrumbs, fileName)) {
      myTitle.append("  ");
      myTitle.append(breadcrumbs, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    Icon icon = IconUtil.getIcon(place.getFile(), Iconable.ICON_FLAG_READ_STATUS, myProject);
    myTitle.setIcon(icon);
    myTitle.setIconTextGap(4);
    if (!SystemInfo.isWindows) {
      myTitle.setFont(FontUtil.minusOne(StartupUiUtil.getLabelFont()));
    }
    long timeStamp = place.getTimeStamp();
    if (UISettings.getInstance().getShowInplaceComments() && Registry.is("show.last.visited.timestamps") && timeStamp != -1) {
      myTitle.append(" " + DateFormatUtil.formatPrettyDateTime(timeStamp), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES, false);
    }
  }

  private void applyEditorSpeedSearchHighlighting(@Nullable SpeedSearchSupply speedSearch) {
    String text = getEditor().getDocument().getText();
    Iterable<TextRange> ranges = speedSearch == null ? null : speedSearch.matchingFragments(text);
    if (ranges != null) {
      selectSearchResultsInEditor(getEditor(), ranges.iterator());
    }
    if (RecentLocationsAction.getEmptyFileText().equals(text)) {
      getEditor().getMarkupModel().addRangeHighlighter(
        0, RecentLocationsAction.getEmptyFileText().length(), HighlighterLayer.SYNTAX,
        SimpleTextAttributes.GRAYED_ATTRIBUTES.toTextAttributes(), HighlighterTargetArea.EXACT_RANGE);
    }
  }

  private static void selectSearchResultsInEditor(@NotNull Editor editor, @NotNull Iterator<? extends TextRange> resultIterator) {
    if (!editor.getCaretModel().supportsMultipleCarets()) {
      return;
    }
    ArrayList<CaretState> caretStates = new ArrayList<>();
    while (resultIterator.hasNext()) {
      TextRange findResult = resultIterator.next();

      int caretOffset = findResult.getEndOffset();

      int selectionStartOffset = findResult.getStartOffset();
      int selectionEndOffset = findResult.getEndOffset();
      EditorActionUtil.makePositionVisible(editor, caretOffset);
      EditorActionUtil.makePositionVisible(editor, selectionStartOffset);
      EditorActionUtil.makePositionVisible(editor, selectionEndOffset);
      caretStates.add(new CaretState(editor.offsetToLogicalPosition(caretOffset),
                                     editor.offsetToLogicalPosition(selectionStartOffset),
                                     editor.offsetToLogicalPosition(selectionEndOffset)));
      if (caretStates.size() >= editor.getCaretModel().getMaxCaretCount()) break;
    }
    if (caretStates.isEmpty()) {
      return;
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }

  private void scheduleHighlightingIfNeeded(@NotNull JList<?> component) {
    if (getEditor().isDisposed()) return;
    if (myHighlightingFuture != null && !myHighlightingFuture.isDone()) return;
    if (myItemsDeque.isEmpty()) return;
    myHighlightingFuture = ReadAction.nonBlocking(() -> {
        while (!myItemsDeque.isEmpty()) {
          ProgressManager.checkCanceled();
          RecentLocationItem item = myItemsDeque.removeFirst();
          try {
            myItemHighlights.put(item, Couple.of(
              calcItemHighlights(item, true),
              calcItemHighlights(item, false)));
          }
          catch (ProcessCanceledException e) {
            myItemsDeque.addFirst(item);
            throw e;
          }
        }
      })
      .expireWith(this)
      .finishOnUiThread(ModalityState.stateForComponent(this), __ -> {
        component.repaint();
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private Highlight @NotNull [] calcItemHighlights(@NotNull RecentLocationItem item, boolean lexerHighlights) {
    TextRange[] ranges = item.ranges;
    IdeDocumentHistoryImpl.PlaceInfo info = item.info;
    EditorColorsScheme colorsScheme = getEditor().getColorsScheme();

    RangeMarker caretPosition = item.info.getCaretPosition();
    if (caretPosition == null || !caretPosition.isValid()) {
      return Highlight.EMPTY_ARRAY;
    }

    Document fileDocument = caretPosition.getDocument();
    ArrayList<Highlight> result = new ArrayList<>();
    if (lexerHighlights) {
      EditorHighlighter editorHighlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
        info.getFile(), colorsScheme, myProject);
      editorHighlighter.setEditor(new LightHighlighterClient(fileDocument, myProject));
      editorHighlighter.setText(fileDocument.getText());
      int rangeIdx = 0;
      int rangeOffset = 0;
      HighlighterIterator iterator = editorHighlighter.createIterator(ranges[0].getStartOffset());
      while (!iterator.atEnd() && rangeIdx < ranges.length) {
        TextRange range = ranges[rangeIdx];
        if (range.intersects(iterator.getStart(), iterator.getEnd())) {
          result.add(new Highlight(
            Math.max(iterator.getStart(), range.getStartOffset()) - range.getStartOffset() + rangeOffset,
            Math.min(iterator.getEnd(), range.getEndOffset()) - range.getStartOffset() + rangeOffset,
            iterator.getTextAttributes()));
        }
        if (iterator.getEnd() < range.getEndOffset()) {
          iterator.advance();
        }
        else {
          rangeOffset += range.getLength() + 1;
          rangeIdx++;
        }
      }
    }
    else {
      int totalStartOffset = ranges[0].getStartOffset();
      int totalEndOffset = ranges[ranges.length - 1].getEndOffset();
      DaemonCodeAnalyzerEx.processHighlights(fileDocument, myProject, null, totalStartOffset, totalEndOffset, o -> {
          if (o.getSeverity() != HighlightSeverity.INFORMATION ||
              o.getEndOffset() <= totalStartOffset ||
              o.getStartOffset() >= totalEndOffset) {
            return true;
          }
          for (int rangeIdx = 0, rangeOffset = 0; rangeIdx < ranges.length; rangeOffset += ranges[rangeIdx].getLength() + 1, rangeIdx++) {
            TextRange range = ranges[rangeIdx];
            if (range.intersects(o.getStartOffset(), o.getEndOffset())) {
              TextAttributes textAttributes = o.forcedTextAttributes != null ? o.forcedTextAttributes :
                                              colorsScheme.getAttributes(o.forcedTextAttributesKey);
              result.add(new Highlight(Math.max(o.getActualStartOffset(), range.getStartOffset()) - range.getStartOffset() + rangeOffset,
                                       Math.min(o.getActualEndOffset(), range.getEndOffset()) - range.getStartOffset() + rangeOffset,
                                       textAttributes));
            }
          }
          return true;
        });
    }
    return result.toArray(Highlight.EMPTY_ARRAY);
  }

  private static class Highlight {
    static final Highlight[] EMPTY_ARRAY = new Highlight[0];

    final int start;
    final int end;
    final TextAttributes attrs;

    Highlight(int start, int end, TextAttributes attrs) {
      this.start = start;
      this.end = end;
      this.attrs = attrs;
    }
  }
}
