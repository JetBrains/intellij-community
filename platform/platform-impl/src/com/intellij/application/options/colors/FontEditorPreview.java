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

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import com.intellij.openapi.editor.markup.AnalyzerStatus;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.UIController;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class FontEditorPreview implements PreviewPanel{
  private static final String PREVIEW_TEXT_KEY = "FontPreviewText";

  private static final Key<PreviewTextModel> TEXT_MODEL_KEY = Key.create("RestorePreviewTextAction.textModel");

  private final EditorEx myEditor;
  private final JPanel myTopPanel;

  private final PreviewTextModel myTextModel;

  private final Supplier<? extends EditorColorsScheme> mySchemeSupplier;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private final static ShortcutSet TOGGLE_BOLD_SHORTCUT = CustomShortcutSet.fromString("control B");

  public FontEditorPreview(final Supplier<? extends EditorColorsScheme> schemeSupplier, boolean editable) {
    mySchemeSupplier = schemeSupplier;

    @Nls String text = PropertiesComponent.getInstance().getValue(PREVIEW_TEXT_KEY, getIDEDemoText());

    myTextModel = new PreviewTextModel(text);
    myEditor = (EditorEx)createPreviewEditor(myTextModel.getText(), mySchemeSupplier.get(), editable);
    myEditor.setBorder(JBUI.Borders.empty());
    myEditor.setHighlighter(new PreviewHighlighter(myTextModel, myEditor.getDocument()));
    myEditor.getDocument().addDocumentListener(myTextModel);

    myTopPanel = new JPanel(new BorderLayout());
    myTopPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    if (editable) {
      JLabel previewLabel = new JLabel(ApplicationBundle.message("settings.editor.font.preview.hint"));
      previewLabel.setFont(JBUI.Fonts.smallFont());
      previewLabel.setForeground(UIUtil.getContextHelpForeground());
      previewLabel.setBorder(JBUI.Borders.empty(10, 15, 10, 0));
      previewLabel.setBackground(myEditor.getBackgroundColor());
      myTopPanel.add(previewLabel, BorderLayout.SOUTH);
    }
    myTopPanel.setBackground(myEditor.getBackgroundColor());
    myTopPanel.setBorder(getBorder());

    registerActions(myEditor);
    installTrafficLights(myEditor);
  }

  protected Border getBorder() {
    return JBUI.Borders.customLine(JBColor.border());
  }

  private void registerActions(EditorEx editor) {
    editor.putUserData(TEXT_MODEL_KEY, myTextModel);
    AnAction restoreAction = ActionManager.getInstance().getAction(IdeActions.ACTION_RESTORE_FONT_PREVIEW_TEXT);
    AnAction toggleBoldFontAction = ActionManager.getInstance().getAction("fontEditorPreview.ToggleBoldFont");
    if (restoreAction != null || toggleBoldFontAction != null) {
      String originalGroupId = editor.getContextMenuGroupId();
      AnAction originalGroup = originalGroupId == null ? null : ActionManager.getInstance().getAction(originalGroupId);
      DefaultActionGroup group = new DefaultActionGroup();
      if (originalGroup instanceof ActionGroup) {
        group.addAll(((ActionGroup)originalGroup).getChildren(null));
      }
      if (restoreAction != null) {
        group.add(restoreAction);
      }
      if (toggleBoldFontAction != null) {
        group.add(toggleBoldFontAction);
        DumbAwareAction.create(event -> toggleBoldFont(editor)).registerCustomShortcutSet(TOGGLE_BOLD_SHORTCUT, editor.getComponent());
      }
      editor.installPopupHandler(new ContextMenuPopupHandler.Simple(group));
    }
  }

  private static String getIDEDemoText() {
    return
      ApplicationNamesInfo.getInstance().getFullProductName() +
      " is an <bold>Integrated \n" +
      "Development Environment (IDE)</bold> designed\n" +
      "to maximize productivity. It provides\n" +
      "<bold>clever code completion, static code\n" +
      "analysis, and refactorings,</bold> and lets\n" +
      "you focus on the bright side of\n" +
      "software development making\n" +
      "it an enjoyable experience.\n" +
      "\n" +
      "Default:\n" +
      "abcdefghijklmnopqrstuvwxyz\n" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
      " 0123456789 (){}[]\n" +
      " +-*/= .,;:!? #&$%@|^\n" +
      "\n" +
      "<bold>Bold:\n" +
      "abcdefghijklmnopqrstuvwxyz\n" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
      " 0123456789 (){}[]\n" +
      " +-*/= .,;:!? #&$%@|^</bold>\n" +
      "\n" +
      "<!-- -- != := === >= >- >=> |-> -> <$>\n" +
      "</> #[ |||> |= ~@\n" +
      "\n";
  }

  static void installTrafficLights(@NotNull EditorEx editor) {
    EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
    markupModel.setErrorStripeRenderer(new DumbTrafficLightRenderer());
    markupModel.setErrorStripeVisible(true);
  }

  static Editor createPreviewEditor(String text, EditorColorsScheme scheme, boolean editable) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    // enable editor popup toolbar
    FileDocumentManagerBase.registerDocument(editorDocument, new LightVirtualFile());
    EditorEx editor = (EditorEx) (editable ? editorFactory.createEditor(editorDocument) : editorFactory.createViewer(editorDocument));
    editor.setColorsScheme(scheme);
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setWhitespacesShown(true);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setRightMarginShown(true);
    settings.setRightMargin(60);
    settings.setGutterIconsShown(false);
    settings.setIndentGuidesShown(false);
    ((EditorGutterComponentEx)editor.getGutter()).setPaintBackground(false);
    return editor;
  }

  @Override
  public JComponent getPanel() {
    return myTopPanel;
  }

  @Override
  public void updateView() {
    EditorColorsScheme scheme = updateOptionsScheme(mySchemeSupplier.get());

    myEditor.setColorsScheme(scheme);
    myEditor.reinitSettings();

  }

  protected EditorColorsScheme updateOptionsScheme(EditorColorsScheme selectedScheme) {
    return selectedScheme;
  }

  @Override
  public void blinkSelectedHighlightType(Object description) {
  }

  @Override
  public void addListener(@NotNull final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void disposeUIResources() {
    if (myTextModel.isDefault() || myTextModel.getRawText().isEmpty()) {
      PropertiesComponent.getInstance().unsetValue(PREVIEW_TEXT_KEY);
    }
    else {
      PropertiesComponent.getInstance().setValue(PREVIEW_TEXT_KEY, myTextModel.getRawText());
    }
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  private static class DumbTrafficLightRenderer implements ErrorStripeRenderer {
    @Override
    public @NotNull AnalyzerStatus getStatus() {
      return new AnalyzerStatus(AllIcons.General.InspectionsOK, "", "", () -> UIController.EMPTY);
    }
  }

  public static class RestorePreviewTextAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      PreviewTextModel textModel = ObjectUtils.doIfNotNull(editor, it -> it.getUserData(TEXT_MODEL_KEY));
      e.getPresentation().setEnabledAndVisible(editor != null &&
                                               textModel != null &&
                                               !textModel.isDefault());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        PreviewTextModel textModel = editor.getUserData(TEXT_MODEL_KEY);
        if (textModel != null) {
          textModel.resetToDefault();
          WriteCommandAction.runWriteCommandAction(editor.getProject(), null, null, () -> {
            editor.getDocument().setText(textModel.getText());
            ((EditorEx)editor).reinitSettings();
          });
        }
      }
    }
  }

  public static class ToggleBoldFontAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      PreviewTextModel textModel = ObjectUtils.doIfNotNull(editor, it -> it.getUserData(TEXT_MODEL_KEY));
      e.getPresentation().setEnabledAndVisible(textModel != null &&
                                               editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        toggleBoldFont((EditorEx)editor);
      }
    }
  }

  private static void toggleBoldFont(@NotNull EditorEx editor) {
    PreviewTextModel textModel = ObjectUtils.doIfNotNull(editor, it->it.getUserData(TEXT_MODEL_KEY));
    if (textModel != null) {
      SelectionModel selectionModel = editor.getSelectionModel();
      textModel.toggleBoldFont(TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
      editor.reinitSettings();
    }
  }

  private static class PreviewTextModel implements DocumentListener {
    private final static String BOLD_START_MARKER = "<bold>";
    private final static String BOLD_END_MARKER = "</bold>";

    private String myText;
    private final List<RangeHighlightingData> myRanges = new ArrayList<>();

    private PreviewTextModel(String rawPreviewText) {
      extractMarkersAndText(rawPreviewText);
    }

    private void extractMarkersAndText(@NotNull String rawPreviewText) {
      myRanges.clear();
      StringBuilder output = new StringBuilder();
      int shift = 0;
      int searchOffset = 0;
      while (searchOffset < rawPreviewText.length()) {
        int rawBoldStart = rawPreviewText.indexOf(BOLD_START_MARKER, searchOffset);
        if (rawBoldStart >= 0) {
          output.append(rawPreviewText, searchOffset, rawBoldStart);
          int boldStart = rawBoldStart - shift;
          myRanges.add(new RangeHighlightingData(searchOffset - shift, boldStart, false));
          searchOffset = rawBoldStart + BOLD_START_MARKER.length();
          shift += BOLD_START_MARKER.length();

          int rawBoldEnd = rawPreviewText.indexOf(BOLD_END_MARKER, searchOffset);
          if (rawBoldEnd < 0) rawBoldEnd = rawPreviewText.length();
          output.append(rawPreviewText, searchOffset, rawBoldEnd);
          int boldEnd = rawBoldEnd - shift;
          searchOffset = rawBoldEnd + BOLD_END_MARKER.length();
          shift += BOLD_END_MARKER.length();
          myRanges.add(new RangeHighlightingData(boldStart, boldEnd, true));
        }
        else {
          myRanges.add(new RangeHighlightingData(searchOffset - shift, rawPreviewText.length() - shift, false));
          output.append(rawPreviewText, searchOffset, rawPreviewText.length());
          break;
        }
      }
      myText = output.toString();
    }

    String getText() {
      return myText;
    }

    String getRawText() {
      StringBuilder builder = new StringBuilder();
      for (RangeHighlightingData data : myRanges) {
        if (data.isBold) {
          builder.append(BOLD_START_MARKER);
        }
        builder.append(myText, data.textRange.getStartOffset(), data.textRange.getEndOffset());
        if (data.isBold) {
          builder.append(BOLD_END_MARKER);
        }
      }
      return builder.toString();
    }

    int getRangeCount() {
      return myRanges.size();
    }

    @Nullable
    RangeHighlightingData getRangeDataAt(int index) {
      return myRanges.isEmpty() ? null :  myRanges.get(index);
    }

    boolean isDefault() {
      return getIDEDemoText().equals(getRawText());
    }

    void resetToDefault() {
      extractMarkersAndText(getIDEDemoText());
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      String docText = event.getDocument().getText();
      if (myText.equals(docText)) return;
      if (event.isWholeTextReplaced()) {
        myRanges.clear();
        myRanges.add(new RangeHighlightingData(0, docText.length(), false));
      }
      else {
        int offset = event.getOffset();
        if (event.getNewLength() >= event.getOldLength()) {
          if (myRanges.isEmpty()) {
            myRanges.add(new RangeHighlightingData(0, 0, false));
          }
          int insertedLen = event.getNewLength() - event.getOldLength();
          for (RangeHighlightingData data : myRanges) {
            if (data.textRange.contains(offset) || data == myRanges.get(myRanges.size() - 1) && data.textRange.getEndOffset() <= offset) {
              data.updateRange(data.textRange.grown(insertedLen));
            }
            else if (data.textRange.getStartOffset() > offset) {
              data.updateRange(data.textRange.shiftRight(insertedLen));
            }
          }
        }
        else {
          int deletedLen = event.getOldLength() - event.getNewLength();
          TextRange deletedRange = new TextRange(offset, offset + deletedLen);
          int delta = 0;
          Iterator<RangeHighlightingData> rangeIterator = myRanges.iterator();
          while(rangeIterator.hasNext()) {
            RangeHighlightingData data =rangeIterator.next();
            int cutoutStart = Math.max(deletedRange.getStartOffset(), data.textRange.getStartOffset());
            int cutoutEnd = Math.min(deletedRange.getEndOffset(), data.textRange.getEndOffset());
            if (cutoutStart < cutoutEnd) {
              int shrinkSize = cutoutEnd - cutoutStart;
              if (shrinkSize == data.textRange.getLength()) {
                rangeIterator.remove();
              }
              else {
                data.updateRange(data.textRange.grown(-shrinkSize));
              }
              data.updateRange(data.textRange.shiftLeft(delta));
              delta += shrinkSize;
            }
            else {
              data.updateRange(data.textRange.shiftLeft(delta));
            }
          }
        }
      }
      myText = docText;
    }

    int getIndexAtOffset(int offset) {
      for (int i = 0; i < myRanges.size(); i ++) {
        if (myRanges.get(i).textRange.contains(offset)) return i;
      }
      return -1;
    }

    void toggleBoldFont(@NotNull TextRange toggleRange) {
      List<RangeHighlightingData> updatedRanges = new ArrayList<>();
      myRanges.forEach(data -> {
        int toggleStart = Math.max(toggleRange.getStartOffset(), data.textRange.getStartOffset());
        int toggleEnd = Math.min(toggleRange.getEndOffset(), data.textRange.getEndOffset());
        if (toggleStart < toggleEnd) {
          glueRange(updatedRanges, TextRange.create(data.textRange.getStartOffset(), toggleStart), data.isBold);
          glueRange(updatedRanges, TextRange.create(toggleStart, toggleEnd), !data.isBold);
          glueRange(updatedRanges, TextRange.create(toggleEnd, data.textRange.getEndOffset()), data.isBold);
        }
        else {
          glueRange(updatedRanges, data.textRange, data.isBold);
        }
      });
      myRanges.clear();
      myRanges.addAll(updatedRanges);
    }

    private static void glueRange(@NotNull List<RangeHighlightingData> ranges, @NotNull TextRange range, boolean isBold) {
      if (!range.isEmpty()) {
        RangeHighlightingData lastRange = ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
        if (lastRange != null && lastRange.isBold == isBold) {
          lastRange.updateRange(lastRange.textRange.grown(range.getLength()));
        }
        else {
          ranges.add(new RangeHighlightingData(range.getStartOffset(), range.getEndOffset(), isBold));
        }
      }
    }
  }

  private static class RangeHighlightingData {
    private TextRange textRange;
    private final boolean isBold;

    private RangeHighlightingData(int startOffset, int endOffset, boolean isBold) {
      textRange = TextRange.create(startOffset, endOffset);
      this.isBold = isBold;
    }

    void updateRange(@NotNull TextRange newRange) {
      this.textRange = newRange;
    }

    @Override
    public String toString() {
      return "RangeHighlightingData{" +
             "textRange=" + textRange +
             ", isBold=" + isBold +
             '}';
    }
  }

  private static class PreviewHighlighter implements EditorHighlighter {
    private final PreviewTextModel myTextModelModel;
    private final Document myDocument;

    private PreviewHighlighter(@NotNull PreviewTextModel model, @NotNull Document document) {
      myTextModelModel = model;
      myDocument = document;
    }

    @Override
    public @NotNull HighlighterIterator createIterator(int startOffset) {
      return new PreviewHighlighterIterator(myTextModelModel, myDocument, startOffset);
    }

    @Override
    public void setEditor(@NotNull HighlighterClient editor) {
    }
  }

  private static class PreviewHighlighterIterator implements HighlighterIterator {
    private final PreviewTextModel myTextModel;
    private final Document myDocument;

    private final static TextAttributes PLAIN_ATTRIBUTES =
      new TextAttributes(null, null, null, null, Font.PLAIN);
    private final static TextAttributes BOLD_ATTRIBUTES =
      new TextAttributes(null, null, null, null, Font.BOLD);

    private final static RangeHighlightingData EMPTY_RANGE_DATA = new RangeHighlightingData(0, 0, false);

    private int myCurrIndex;

    private PreviewHighlighterIterator(@NotNull PreviewTextModel model, @NotNull Document document, int startOffset) {
      myTextModel = model;
      myDocument = document;
      myCurrIndex = Math.max(myTextModel.getIndexAtOffset(startOffset), 0);
    }

    @NotNull
    private RangeHighlightingData getData() {
      return ObjectUtils.notNull(myTextModel.getRangeDataAt(myCurrIndex), EMPTY_RANGE_DATA);
    }

    @Override
    public TextAttributes getTextAttributes() {
      return getData().isBold ? BOLD_ATTRIBUTES : PLAIN_ATTRIBUTES;
    }

    @Override
    public int getStart() {
      return getData().textRange.getStartOffset();
    }

    @Override
    public int getEnd() {
      return getData().textRange.getEndOffset();
    }

    @Override
    public IElementType getTokenType() {
      return null;
    }

    @Override
    public void advance() {
      if (myCurrIndex < myTextModel.getRangeCount() - 1) myCurrIndex++;
    }

    @Override
    public void retreat() {
      if (myCurrIndex > 0) myCurrIndex--;
    }

    @Override
    public boolean atEnd() {
      return myCurrIndex >= myTextModel.getRangeCount() - 1;
    }

    @Override
    public Document getDocument() {
      return myDocument;
    }
  }
}
