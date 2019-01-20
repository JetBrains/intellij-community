// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.actions.RecentLocationsAction.RecentLocationItem;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

class RecentLocationsRenderer extends ColoredListCellRenderer<RecentLocationItem> {
  private static final JBColor BACKGROUND_COLOR = JBColor.namedColor("Table.lightSelectionBackground", new JBColor(0xE9EEF5, 0x464A4D));
  private static final Color TITLE_FOREGROUND_COLOR = UIUtil.getLabelForeground().darker();

  @NotNull private final Project myProject;
  @NotNull private final SpeedSearch mySpeedSearch;

  RecentLocationsRenderer(@NotNull Project project, @NotNull SpeedSearch speedSearch) {
    myProject = project;
    mySpeedSearch = speedSearch;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends RecentLocationItem> list,
                                                RecentLocationItem value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {
    EditorEx editor = value.getEditor();
    if (myProject.isDisposed() || editor.isDisposed()) {
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    Color background = selected ? BACKGROUND_COLOR : editor.getColorsScheme().getDefaultBackground();
    if (index % 2 == 1) {
      background = adjustBackgroundColor(background);
    }

    IdeDocumentHistoryImpl.PlaceInfo placeInfo = value.getInfo();
    String breadcrumb = RecentLocationsAction.getBreadcrumbs(myProject, placeInfo);

    JComponent title = JBUI.Panels
      .simplePanel()
      .addToLeft(createBreadcrumbsComponent(list, mySpeedSearch, breadcrumb, background, selected))
      .addToCenter(createTitledSeparator(background))
      .addToRight(createFileNameComponent(list, mySpeedSearch, breadcrumb, placeInfo, selected))
      .withBackground(background);

    JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
    panel.add(title);

    String text = editor.getDocument().getText();
    if (!StringUtil.isEmpty(text)) {
      addEditorComponent(panel, editor, text, background, mySpeedSearch);
    }

    return panel;
  }

  @NotNull
  private static JComponent createTitledSeparator(@NotNull Color background) {
    JComponent titledSeparator = new TitledSeparator();
    titledSeparator.setBackground(background);
    return titledSeparator;
  }

  private static void addEditorComponent(@NotNull JPanel panel,
                                         @NotNull EditorEx editor,
                                         @NotNull String text,
                                         @NotNull Color background,
                                         @NotNull SpeedSearch speedSearch) {
    editor.setBackgroundColor(background);
    Iterable<TextRange> ranges = speedSearch.matchingFragments(text);

    if (ranges != null) {
      selectSearchResultsInEditor(editor, ranges.iterator());
    }
    else {
      RecentLocationsAction.clearSelectionInEditor(editor);
    }

    JComponent editorComponent = editor.getComponent();
    editorComponent.setBorder(BorderFactory.createEmptyBorder());
    panel.add(editorComponent);

    editor.setBorder(BorderFactory.createEmptyBorder());
  }

  @NotNull
  private static SimpleColoredComponent createBreadcrumbsComponent(@NotNull JList<? extends RecentLocationItem> list,
                                                                   @NotNull SpeedSearch speedSearch,
                                                                   @NotNull String breadcrumb,
                                                                   @NotNull Color background,
                                                                   boolean selected) {
    SimpleColoredComponent breadcrumbTextComponent = new SimpleColoredComponent();
    breadcrumbTextComponent.setForeground(TITLE_FOREGROUND_COLOR);
    breadcrumbTextComponent.setBackground(background);
    breadcrumbTextComponent.append(breadcrumb);
    Iterable<TextRange> breadCrumbRanges = speedSearch.matchingFragments(breadcrumb);
    if (breadCrumbRanges != null) {
      SpeedSearchUtil.applySpeedSearchHighlighting(list, breadcrumbTextComponent, true, selected);
    }

    return breadcrumbTextComponent;
  }

  @NotNull
  private static SimpleColoredComponent createFileNameComponent(@NotNull JList<? extends RecentLocationItem> list,
                                                                @NotNull SpeedSearch speedSearch,
                                                                @NotNull String breadcrumb,
                                                                @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                                boolean selected) {
    SimpleColoredComponent fileNameComponent = new SimpleColoredComponent();
    fileNameComponent.setForeground(TITLE_FOREGROUND_COLOR);
    if (!StringUtil.equals(breadcrumb, placeInfo.getFile().getName())) {
      fileNameComponent.append(placeInfo.getFile().getName());
      fileNameComponent.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 2));
      Iterable<TextRange> fileNameRanges = speedSearch.matchingFragments(placeInfo.getFile().getName());
      if (fileNameRanges != null) {
        SpeedSearchUtil.applySpeedSearchHighlighting(list, fileNameComponent, true, selected);
      }
    }

    return fileNameComponent;
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends RecentLocationItem> list,
                                       RecentLocationItem value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
  }

  @NotNull
  private static Color adjustBackgroundColor(@NotNull Color background) {
    Color brighterColor = ColorUtil.brighter(background, 1);
    if (!background.equals(brighterColor)) {
      background = brighterColor;
    }
    else {
      background = ColorUtil.hackBrightness(background, 1, 1 / 1.03F);
    }
    return background;
  }

  private static void selectSearchResultsInEditor(@NotNull Editor editor, @NotNull Iterator<TextRange> resultIterator) {
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
    }
    if (caretStates.isEmpty()) {
      return;
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }
}
