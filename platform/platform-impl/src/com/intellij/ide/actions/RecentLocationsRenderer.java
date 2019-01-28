// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.actions.RecentLocationsAction.RecentLocationItem;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
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

import static com.intellij.ide.actions.RecentLocationsAction.getBreadcrumbs;

class RecentLocationsRenderer extends ColoredListCellRenderer<RecentLocationItem> {
  private static final JBColor BACKGROUND_COLOR = JBColor.namedColor("Table.lightSelectionBackground", new JBColor(0xE9EEF5, 0x464A4D));

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

    Color defaultBackground = editor.getColorsScheme().getDefaultBackground();
    String breadcrumbs = getBreadcrumbs(myProject, value.getInfo());
    JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
    panel.add(createTitleComponent(list, mySpeedSearch, breadcrumbs, value.getInfo(), defaultBackground, selected));

    String text = editor.getDocument().getText();
    if (!StringUtil.isEmpty(text)) {
      panel.add(setupEditorComponent(editor, text, mySpeedSearch, selected ? BACKGROUND_COLOR : defaultBackground));
    }

    return panel;
  }

  @NotNull
  private static JComponent createTitleComponent(@NotNull JList<? extends RecentLocationItem> list,
                                                 @NotNull SpeedSearch speedSearch,
                                                 @NotNull String breadcrumb,
                                                 @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                 @NotNull Color background,
                                                 boolean selected) {
    JComponent title = JBUI.Panels
      .simplePanel()
      .addToLeft(createTitleTextComponent(list, speedSearch, placeInfo, breadcrumb, selected))
      .addToCenter(createTitledSeparator(background));

    title.setBorder(BorderFactory.createEmptyBorder(2, 0, 1, 0));
    title.setBackground(background);

    return title;
  }

  @NotNull
  private static TitledSeparator createTitledSeparator(@NotNull Color background) {
    TitledSeparator titledSeparator = new TitledSeparator();
    titledSeparator.setBorder(BorderFactory.createEmptyBorder());
    titledSeparator.setBackground(background);
    return titledSeparator;
  }

  @NotNull
  private static JComponent setupEditorComponent(@NotNull EditorEx editor,
                                                 @NotNull String text,
                                                 @NotNull SpeedSearch speedSearch,
                                                 @NotNull Color backgroundColor) {
    Iterable<TextRange> ranges = speedSearch.matchingFragments(text);
    if (ranges != null) {
      selectSearchResultsInEditor(editor, ranges.iterator());
    }
    else {
      RecentLocationsAction.clearSelectionInEditor(editor);
    }

    editor.setBackgroundColor(backgroundColor);
    editor.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

    return editor.getComponent();
  }

  @NotNull
  private static SimpleColoredComponent createTitleTextComponent(@NotNull JList<? extends RecentLocationItem> list,
                                                                 @NotNull SpeedSearch speedSearch,
                                                                 @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                                 @NotNull String breadcrumbText,
                                                                 boolean selected) {
    SimpleColoredComponent titleTextComponent = new SimpleColoredComponent();
    titleTextComponent.append(breadcrumbText);

    String text = breadcrumbText;
    String fileName = placeInfo.getFile().getName();
    if (!StringUtil.equals(breadcrumbText, fileName)) {
      text += " " + fileName;
      titleTextComponent.append(" ");
      titleTextComponent.append(fileName, createLabelDisabledForegroundAttributes());
    }

    if (speedSearch.matchingFragments(text) != null) {
      SpeedSearchUtil.applySpeedSearchHighlighting(list, titleTextComponent, false, selected);
    }

    titleTextComponent.setBorder(BorderFactory.createEmptyBorder());

    return titleTextComponent;
  }

  @NotNull
  private static SimpleTextAttributes createLabelDisabledForegroundAttributes() {
    TextAttributes textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes();
    textAttributes.setForegroundColor(UIUtil.getLabelDisabledForeground());
    return SimpleTextAttributes.fromTextAttributes(textAttributes);
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends RecentLocationItem> list,
                                       RecentLocationItem value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
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
