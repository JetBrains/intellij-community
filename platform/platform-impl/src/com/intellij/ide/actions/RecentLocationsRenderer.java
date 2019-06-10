// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.FontUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

import static com.intellij.ide.actions.RecentLocationsAction.EMPTY_FILE_TEXT;

class RecentLocationsRenderer extends ColoredListCellRenderer<RecentLocationItem> {
  @NotNull private final Project myProject;
  @NotNull private final SpeedSearch mySpeedSearch;
  @NotNull private final RecentLocationsDataModel myData;
  @NotNull private final JBCheckBox myCheckBox;

  RecentLocationsRenderer(@NotNull Project project,
                          @NotNull SpeedSearch speedSearch,
                          @NotNull RecentLocationsDataModel data,
                          @NotNull JBCheckBox checkBox) {
    myProject = project;
    mySpeedSearch = speedSearch;
    myData = data;
    myCheckBox = checkBox;
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

    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    String breadcrumbs = myData.getBreadcrumbsMap(myCheckBox.isSelected()).get(value.getInfo());
    JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
    if (index != 0) {
      panel.add(createSeparatorLine(colorsScheme));
    }
    panel.add(createTitleComponent(myProject, list, mySpeedSearch, breadcrumbs, value.getInfo(), colorsScheme, selected));
    panel.add(setupEditorComponent(editor, editor.getDocument().getText(), mySpeedSearch, colorsScheme, selected));

    return panel;
  }

  @NotNull
  private static Color getBackgroundColor(@NotNull EditorColorsScheme colorsScheme, boolean selected) {
    return selected ? HintUtil.getRecentLocationsSelectionColor(colorsScheme) : colorsScheme.getDefaultBackground();
  }

  @NotNull
  private static JComponent createTitleComponent(@NotNull Project project,
                                                 @NotNull JList<? extends RecentLocationItem> list,
                                                 @NotNull SpeedSearch speedSearch,
                                                 @Nullable String breadcrumb,
                                                 @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                 @NotNull EditorColorsScheme colorsScheme,
                                                 boolean selected) {
    JComponent title = JBUI.Panels
      .simplePanel()
      .withBorder(JBUI.Borders.empty())
      .addToLeft(createTitleTextComponent(project, list, speedSearch, placeInfo, colorsScheme, breadcrumb, selected));

    title.setBorder(JBUI.Borders.empty(8, 6, 5, 0));
    title.setBackground(getBackgroundColor(colorsScheme, selected));

    return title;
  }

  @NotNull
  private static JPanel createSeparatorLine(@NotNull EditorColorsScheme colorsScheme) {
    Color color = colorsScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    if (color == null) {
      color = JBColor.namedColor("Group.separatorColor", new JBColor(Gray.xCD, Gray.x51));
    }

    return JBUI.Panels.simplePanel().withBorder(JBUI.Borders.customLine(color, 1, 0, 0, 0));
  }

  @NotNull
  private static JComponent setupEditorComponent(@NotNull EditorEx editor,
                                                 @NotNull String text,
                                                 @NotNull SpeedSearch speedSearch,
                                                 @NotNull EditorColorsScheme colorsScheme,
                                                 boolean selected) {
    Iterable<TextRange> ranges = speedSearch.matchingFragments(text);
    if (ranges != null) {
      selectSearchResultsInEditor(editor, ranges.iterator());
    }
    else {
      RecentLocationsAction.clearSelectionInEditor(editor);
    }

    editor.setBackgroundColor(getBackgroundColor(colorsScheme, selected));
    editor.setBorder(JBUI.Borders.empty(0, 4, 6, 0));

    if (EMPTY_FILE_TEXT.equals(editor.getDocument().getText())) {
      editor.getMarkupModel().addRangeHighlighter(0,
                                                  EMPTY_FILE_TEXT.length(),
                                                  HighlighterLayer.SYNTAX,
                                                  createEmptyTextForegroundTextAttributes(colorsScheme),
                                                  HighlighterTargetArea.EXACT_RANGE);
    }

    return editor.getComponent();
  }

  @NotNull
  private static SimpleColoredComponent createTitleTextComponent(@NotNull Project project,
                                                                 @NotNull JList<? extends RecentLocationItem> list,
                                                                 @NotNull SpeedSearch speedSearch,
                                                                 @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo,
                                                                 @NotNull EditorColorsScheme colorsScheme,
                                                                 @Nullable String breadcrumbText,
                                                                 boolean selected) {
    SimpleColoredComponent titleTextComponent = new SimpleColoredComponent();

    String fileName = placeInfo.getFile().getName();
    String text = fileName;
    titleTextComponent.append(fileName, createFileNameTextAttributes(colorsScheme, selected));

    if (StringUtil.isNotEmpty(breadcrumbText) && !StringUtil.equals(breadcrumbText, fileName)) {
      text += " " + breadcrumbText;
      titleTextComponent.append("  ");
      titleTextComponent.append(breadcrumbText, createBreadcrumbsTextAttributes(colorsScheme, selected));
    }

    Icon icon = fetchIcon(project, placeInfo);

    if (icon != null) {
      titleTextComponent.setIcon(icon);
      titleTextComponent.setIconTextGap(4);
    }

    titleTextComponent.setBorder(JBUI.Borders.empty());

    if (!SystemInfo.isWindows) {
      titleTextComponent.setFont(FontUtil.minusOne(UIUtil.getLabelFont()));
    }

    if (speedSearch.matchingFragments(text) != null) {
      SpeedSearchUtil.applySpeedSearchHighlighting(list, titleTextComponent, false, selected);
    }

    return titleTextComponent;
  }

  @Nullable
  private static Icon fetchIcon(@NotNull Project project, @NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    return IconUtil.getIcon(placeInfo.getFile(), Iconable.ICON_FLAG_READ_STATUS, project);
  }

  @NotNull
  private static SimpleTextAttributes createFileNameTextAttributes(@NotNull EditorColorsScheme colorsScheme, boolean selected) {
    TextAttributes textAttributes = createDefaultTextAttributesWithBackground(colorsScheme, getBackgroundColor(colorsScheme, selected));
    textAttributes.setFontType(Font.BOLD);

    return SimpleTextAttributes.fromTextAttributes(textAttributes);
  }

  @NotNull
  private static SimpleTextAttributes createBreadcrumbsTextAttributes(@NotNull EditorColorsScheme colorsScheme, boolean selected) {
    Color backgroundColor = getBackgroundColor(colorsScheme, selected);
    TextAttributes attributes = colorsScheme.getAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    if (attributes != null) {
      Color unusedForeground = attributes.getForegroundColor();
      if (unusedForeground != null) {
        return SimpleTextAttributes.fromTextAttributes(new TextAttributes(unusedForeground, backgroundColor, null, null, Font.PLAIN));
      }
    }

    return SimpleTextAttributes.fromTextAttributes(createDefaultTextAttributesWithBackground(colorsScheme, backgroundColor));
  }

  @NotNull
  private static TextAttributes createDefaultTextAttributesWithBackground(@NotNull EditorColorsScheme colorsScheme,
                                                                          @NotNull Color backgroundColor) {
    TextAttributes defaultTextAttributes = new TextAttributes();
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    if (textAttributes != null) {
      defaultTextAttributes = textAttributes.clone();
      defaultTextAttributes.setBackgroundColor(backgroundColor);
    }

    return defaultTextAttributes;
  }

  @NotNull
  private static TextAttributes createEmptyTextForegroundTextAttributes(@NotNull EditorColorsScheme colorsScheme) {
    TextAttributes unusedAttributes = colorsScheme.getAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    return unusedAttributes != null ? unusedAttributes : SimpleTextAttributes.GRAYED_ATTRIBUTES.toTextAttributes();
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends RecentLocationItem> list,
                                       RecentLocationItem value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
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
    }
    if (caretStates.isEmpty()) {
      return;
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }
}
