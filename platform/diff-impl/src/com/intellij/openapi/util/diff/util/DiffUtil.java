/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.util;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.diff.DiffDialogHints;
import com.intellij.openapi.util.diff.api.DiffTool;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.api.SuppressiveDiffTool;
import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.util.diff.comparison.ComparisonUtil;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.contents.DocumentContent;
import com.intellij.openapi.util.diff.contents.EmptyContent;
import com.intellij.openapi.util.diff.fragments.DiffFragment;
import com.intellij.openapi.util.diff.fragments.FineLineFragment;
import com.intellij.openapi.util.diff.fragments.LineFragment;
import com.intellij.openapi.util.diff.fragments.LineFragments;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.util.LineFragmentCache;
import com.intellij.openapi.util.diff.tools.util.base.HighlightPolicy;
import com.intellij.openapi.util.diff.tools.util.base.IgnorePolicy;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class DiffUtil {
  @NotNull public static final String DIFF_CONFIG = StoragePathMacros.APP_CONFIG + "/diff.xml";

  //
  // Editor
  //

  @Nullable
  public static EditorHighlighter initEditorHighlighter(@Nullable Project project,
                                                        @NotNull DocumentContent content,
                                                        @NotNull CharSequence text) {
    EditorHighlighter highlighter = createEditorHighlighter(project, content);
    if (highlighter == null) return null;
    highlighter.setText(text);
    return highlighter;
  }

  @NotNull
  public static EditorHighlighter initEmptyEditorHighlighter(@Nullable Project project, @NotNull CharSequence text) {
    EditorHighlighter highlighter = createEmptyEditorHighlighter();
    highlighter.setText(text);
    return highlighter;
  }

  @Nullable
  public static EditorHighlighter createEditorHighlighter(@Nullable Project project, @NotNull DocumentContent content) {
    FileType type = content.getContentType();
    VirtualFile file = content.getHighlightFile();

    if ((file != null && file.getFileType() == type) || file instanceof LightVirtualFile) {
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file);
    }
    if (type != null) {
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, type);
    }

    return null;
  }

  @NotNull
  public static EditorHighlighter createEmptyEditorHighlighter() {
    return new EmptyEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(HighlighterColors.TEXT));
  }

  public static void setEditorHighlighter(@Nullable Project project, @NotNull EditorEx editor, @NotNull DocumentContent content) {
    EditorHighlighter highlighter = createEditorHighlighter(project, content);
    if (highlighter != null) editor.setHighlighter(highlighter);
  }

  public static void setEditorCodeStyle(@Nullable Project project, @NotNull EditorEx editor, @Nullable FileType fileType) {
    if (project != null && fileType != null) {
      CodeStyleFacade codeStyleFacade = CodeStyleFacade.getInstance(project);
      editor.getSettings().setTabSize(codeStyleFacade.getTabSize(fileType));
      editor.getSettings().setUseTabCharacter(codeStyleFacade.useTabCharacter(fileType));
    }
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);
    editor.reinitSettings();
  }

  @NotNull
  public static EditorEx createEditor(@NotNull Document document, @Nullable Project project, boolean isViewer) {
    return createEditor(document, project, isViewer, false);
  }

  @NotNull
  public static EditorEx createEditor(@NotNull Document document, @Nullable Project project, boolean isViewer, boolean enableFolding) {
    EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)(isViewer ? factory.createViewer(document, project) : factory.createEditor(document, project));

    editor.putUserData(DiffManagerImpl.EDITOR_IS_DIFF_KEY, Boolean.TRUE);
    editor.setSoftWrapAppliancePlace(SoftWrapAppliancePlaces.VCS_DIFF);

    editor.getSettings().setLineNumbersShown(true);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
    editor.getGutterComponentEx().setShowDefaultGutterPopup(false);

    if (enableFolding) {
      editor.getSettings().setFoldingOutlineShown(true);
      editor.getSettings().setCodeFoldingEnabled(false);
    }
    else {
      editor.getSettings().setFoldingOutlineShown(false);
      editor.getFoldingModel().setFoldingEnabled(false);
    }

    UIUtil.removeScrollBorder(editor.getComponent());

    return editor;
  }

  public static void configureEditor(@NotNull EditorEx editor, @NotNull DocumentContent content, @Nullable Project project) {
    setEditorHighlighter(project, editor, content);
    setEditorCodeStyle(project, editor, content.getContentType());
    editor.reinitSettings();
  }

  public static void scrollEditor(@Nullable final Editor editor, int line) {
    scrollEditor(editor, line, 0);
  }

  public static void scrollEditor(@Nullable final Editor editor, int line, int column) {
    scrollEditor(editor, new LogicalPosition(line, column));
  }

  public static void scrollEditor(@Nullable final Editor editor, @NotNull LogicalPosition position) {
    if (editor == null) return;
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().moveToLogicalPosition(position);
    ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollToCaret(ScrollType.CENTER);
    scrollingModel.enableAnimation();
  }

  public static void scrollToLineAnimated(@Nullable final Editor editor, int line) {
    if (editor == null) return;
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, 0));
    ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.scrollToCaret(ScrollType.CENTER);
  }

  public static void scrollToPoint(@Nullable Editor editor, @NotNull Point point) {
    if (editor == null) return;
    editor.getScrollingModel().disableAnimation();
    editor.getScrollingModel().scrollHorizontally(point.x);
    editor.getScrollingModel().scrollVertically(point.y);
    editor.getScrollingModel().enableAnimation();
  }

  @NotNull
  public static Point getScrollingPoint(@Nullable Editor editor) {
    if (editor == null) return new Point(0, 0);
    ScrollingModel model = editor.getScrollingModel();
    return new Point(model.getHorizontalScrollOffset(), model.getVerticalScrollOffset());
  }

  //
  // UI
  //

  @NotNull
  public static JPanel createMessagePanel(@NotNull String message) {
    Pair<JPanel, JLabel> pair = createMessagePanel();
    pair.getSecond().setText(message);
    return pair.getFirst();
  }

  @NotNull
  public static Pair<JPanel, JLabel> createMessagePanel() {
    final JLabel label = new JLabel();
    label.setForeground(UIUtil.getInactiveTextColor());
    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(label,
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0));
    return Pair.create(wrapper, label);
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group, AnAction... actions) {
    if (actions.length == 0) return;
    if (group.getChildrenCount() != 0) group.addSeparator();

    for (AnAction action : actions) {
      if (action != null) group.add(action);
    }
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group, @Nullable List<? extends AnAction> actions) {
    if (actions == null || actions.isEmpty()) return;
    if (group.getChildrenCount() != 0) group.addSeparator();
    group.addAll(actions);
  }

  // Titles

  @NotNull
  public static List<JComponent> createSimpleTitles(@NotNull ContentDiffRequest request) {
    String[] titles = request.getContentTitles();

    List<JComponent> components = new ArrayList<JComponent>(titles.length);
    for (String title : titles) {
      components.add(createTitle(title));
    }

    return components;
  }

  @NotNull
  public static List<JComponent> createTextTitles(@NotNull ContentDiffRequest request, @NotNull List<? extends Editor> editors) {
    DiffContent[] contents = request.getContents();
    String[] titles = request.getContentTitles();

    List<Charset> charsets = ContainerUtil.map(contents, new Function<DiffContent, Charset>() {
      @Override
      public Charset fun(DiffContent content) {
        if (content instanceof EmptyContent) return null;
        return ((DocumentContent)content).getCharset();
      }
    });
    List<LineSeparator> separators = ContainerUtil.map(contents, new Function<DiffContent, LineSeparator>() {
      @Override
      public LineSeparator fun(DiffContent content) {
        if (content instanceof EmptyContent) return null;
        return ((DocumentContent)content).getLineSeparator();
      }
    });

    boolean equalCharsets = isEqualElements(charsets);
    boolean equalSeparators = isEqualElements(separators);

    List<JComponent> result = new ArrayList<JComponent>(contents.length);

    for (int i = 0; i < contents.length; i++) {
      result.add(createTitle(titles[i], contents[i], equalCharsets, equalSeparators, editors.get(i)));
    }

    return result;
  }

  private static boolean isEqualElements(@NotNull List elements) {
    for (int i = 0; i < elements.size(); i++) {
      for (int j = i + 1; j < elements.size(); j++) {
        if (!isEqualElements(elements.get(i), elements.get(j))) return false;
      }
    }
    return true;
  }

  private static boolean isEqualElements(@Nullable Object element1, @Nullable Object element2) {
    if (element1 == null || element2 == null) return true;
    return element1.equals(element2);
  }

  @Nullable
  private static JComponent createTitle(@NotNull String title,
                                        @NotNull DiffContent content,
                                        boolean equalCharsets,
                                        boolean equalSeparators,
                                        @Nullable Editor editor) {
    if (content instanceof EmptyContent) return null;

    Charset charset = equalCharsets ? null : ((DocumentContent)content).getCharset();
    LineSeparator separator = equalSeparators ? null : ((DocumentContent)content).getLineSeparator();
    boolean isReadOnly = editor == null || editor.isViewer() || !canMakeWritable(editor.getDocument());

    return createTitle(title, charset, separator, isReadOnly);
  }

  @NotNull
  public static JComponent createTitle(@NotNull String title) {
    return createTitle(title, null, null, true);
  }

  @NotNull
  public static JComponent createTitle(@NotNull String title,
                                       @Nullable Charset charset,
                                       @Nullable LineSeparator separator,
                                       boolean readOnly) {
    if (readOnly) title += " " + DiffBundle.message("diff.content.read.only.content.title.suffix");

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    panel.add(createTitlePanel(title), BorderLayout.WEST);
    if (charset != null && separator != null) {
      JPanel panel2 = new JPanel();
      panel2.add(createCharsetPanel(charset));
      panel2.add(createSeparatorPanel(separator));
      panel.add(panel2, BorderLayout.EAST);
    }
    else if (charset != null) {
      panel.add(createCharsetPanel(charset), BorderLayout.EAST);
    }
    else if (separator != null) {
      panel.add(createSeparatorPanel(separator), BorderLayout.EAST);
    }
    return panel;
  }

  @NotNull
  private static JComponent createTitlePanel(@NotNull String title) {
    if (title.isEmpty()) title = " "; // do not collapse
    JTextField field = new JTextField(title);
    field.setEditable(false);
    field.setBorder(null);
    field.setFont(UIUtil.getLabelFont());
    field.setBackground(UIUtil.TRANSPARENT_COLOR);
    field.setOpaque(false);
    return field;
  }

  @NotNull
  private static JComponent createCharsetPanel(@NotNull Charset charset) {
    JLabel label = new JLabel(charset.displayName());
    // TODO: specific colors for other charsets
    if (charset.equals(Charset.forName("UTF-8"))) {
      label.setForeground(JBColor.BLUE);
    }
    else if (charset.equals(Charset.forName("ISO-8859-1"))) {
      label.setForeground(JBColor.RED);
    }
    else {
      label.setForeground(JBColor.BLACK);
    }
    return label;
  }

  @NotNull
  private static JComponent createSeparatorPanel(@NotNull LineSeparator separator) {
    JLabel label = new JLabel(separator.name());
    Color color;
    if (separator == LineSeparator.CRLF) {
      color = JBColor.RED;
    }
    else if (separator == LineSeparator.LF) {
      color = JBColor.BLUE;
    }
    else if (separator == LineSeparator.CR) {
      color = JBColor.MAGENTA;
    }
    else {
      color = JBColor.BLACK;
    }
    label.setForeground(color);
    return label;
  }

  //
  // Focus
  //

  public static boolean isFocusedComponent(@Nullable Project project, @Nullable Component component) {
    if (component == null) return false;
    return IdeFocusManager.getInstance(project).getFocusedDescendantFor(component) != null;
  }

  public static void requestFocus(@Nullable Project project, @Nullable Component component) {
    if (component == null) return;
    IdeFocusManager.getInstance(project).requestFocus(component, true);
  }

  //
  // Compare
  //

  @NotNull
  public static LineFragments compareWithCache(@NotNull DiffRequest request,
                                               @NotNull DocumentData data,
                                               @NotNull DiffConfig config,
                                               @NotNull ProgressIndicator indicator) {
    return compareWithCache(request, data.getText1(), data.getText2(), data.getStamp1(), data.getStamp2(), config, indicator);
  }

  @NotNull
  public static LineFragments compareWithCache(@NotNull DiffRequest request,
                                               @NotNull CharSequence text1,
                                               @NotNull CharSequence text2,
                                               long stamp1,
                                               long stamp2,
                                               @NotNull DiffConfig config,
                                               @NotNull ProgressIndicator indicator) {
    // TODO: check instanceOf LineFragments, keep some additional data inside ?
    LineFragments lineFragments = doCompareWithCache(request, text1, text2, stamp1, stamp2, config, indicator);

    indicator.checkCanceled();
    if (lineFragments.isFine()) {
      List<? extends FineLineFragment> fragments = lineFragments.getFineFragments();
      fragments = ComparisonUtil.processBlocksFine(fragments, text1, text2, config.policy, config.squashFragments, config.trimFragments);
      return LineFragments.createFine(fragments);
    }
    else {
      List<? extends LineFragment> fragments = lineFragments.getFragments();
      fragments = ComparisonUtil.processBlocks(fragments, text1, text2, config.policy, config.squashFragments, config.trimFragments);
      return LineFragments.create(fragments);
    }
  }

  @NotNull
  private static LineFragments doCompareWithCache(@NotNull DiffRequest request,
                                                  @NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  long stamp1,
                                                  long stamp2,
                                                  @NotNull DiffConfig config,
                                                  @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();
    LineFragments lineFragments = getFromCache(request, config, stamp1, stamp2);

    LineFragments newLineFragments;
    if (lineFragments != null) {
      if (lineFragments.getFragments().isEmpty()) return lineFragments;
      if (!config.fineFragments) return lineFragments;
      if (lineFragments.isFine()) return lineFragments;
      List<FineLineFragment> result = ComparisonUtil.compareFineLines(text1, text2, lineFragments.getFragments(), config.policy, indicator);
      newLineFragments = LineFragments.createFine(result);
    }
    else {
      if (config.fineFragments) {
        List<FineLineFragment> result = ComparisonUtil.compareFineLines(text1, text2, config.policy, indicator);
        newLineFragments = LineFragments.createFine(result);
      }
      else {
        List<LineFragment> result = ComparisonUtil.compareLines(text1, text2, config.policy, indicator);
        newLineFragments = LineFragments.create(result);
      }
    }

    indicator.checkCanceled();
    putToCache(request, config, stamp1, stamp2, newLineFragments);
    return newLineFragments;
  }

  @Nullable
  public static LineFragments getFromCache(@NotNull DiffRequest request, @NotNull DiffConfig config, long stamp1, long stamp2) {
    LineFragmentCache cache = request.getUserData(DiffUserDataKeysEx.LINE_FRAGMENT_CACHE);
    if (cache != null && cache.checkStamps(stamp1, stamp2)) {
      return cache.getFragments(config.policy);
    }
    return null;
  }

  public static void putToCache(@NotNull DiffRequest request, @NotNull DiffConfig config, long stamp1, long stamp2,
                                @NotNull LineFragments fragments) {
    // We can't rely on monotonicity on modificationStamps, so we can't check if we actually compared freshest versions of documents
    // Possible data races also could make cache outdated.
    // But these cases shouldn't be often and won't break anything.

    LineFragmentCache oldCache = request.getUserData(DiffUserDataKeysEx.LINE_FRAGMENT_CACHE);
    LineFragmentCache cache;
    if (oldCache == null || !oldCache.checkStamps(stamp1, stamp2)) {
      cache = new LineFragmentCache(stamp1, stamp2);
    }
    else {
      cache = new LineFragmentCache(oldCache);
    }

    cache.putFragments(config.policy, fragments);
    request.putUserData(DiffUserDataKeysEx.LINE_FRAGMENT_CACHE, cache);
  }

  //
  // Document modification
  //

  @NotNull
  public static BitSet getSelectedLines(@NotNull Editor editor) {
    Document document = editor.getDocument();
    int totalLines = getLineCount(document);
    BitSet lines = new BitSet(totalLines + 1);

    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      if (caret.hasSelection()) {
        int line1 = editor.offsetToLogicalPosition(caret.getSelectionStart()).line;
        int line2 = editor.offsetToLogicalPosition(caret.getSelectionEnd()).line;
        lines.set(line1, line2 + 1);
        if (caret.getSelectionEnd() == document.getTextLength()) lines.set(totalLines);
      }
      else {
        lines.set(caret.getLogicalPosition().line);
        if (caret.getOffset() == document.getTextLength()) lines.set(totalLines);
      }
    }

    return lines;
  }

  public static boolean isSelectedByLine(int line, int line1, int line2) {
    if (line1 == line2 && line == line1) {
      return true;
    }
    if (line >= line1 && line < line2) {
      return true;
    }
    return false;
  }

  public static boolean isSelectedByLine(@NotNull BitSet selected, int line1, int line2) {
    if (line1 == line2) {
      return selected.get(line1);
    }
    else {
      int next = selected.nextSetBit(line1);
      return next != -1 && next < line2;
    }
  }

  public static void deleteLines(@NotNull Document document, int line1, int line2) {
    TextRange range = getLinesRange(document, line1, line2);
    int offset1 = range.getStartOffset();
    int offset2 = range.getEndOffset();

    if (offset1 > 0) {
      offset1--;
    }
    else if (offset2 < document.getTextLength()) {
      offset2++;
    }
    document.deleteString(offset1, offset2);
  }

  public static void insertLines(@NotNull Document document, int line, @NotNull CharSequence text) {
    if (line == getLineCount(document)) {
      document.insertString(document.getTextLength(), "\n" + text);
    }
    else {
      document.insertString(document.getLineStartOffset(line), text + "\n");
    }
  }

  public static void replaceLines(@NotNull Document document, int line1, int line2, @NotNull CharSequence text) {
    TextRange currentTextRange = getLinesRange(document, line1, line2);
    int offset1 = currentTextRange.getStartOffset();
    int offset2 = currentTextRange.getEndOffset();

    document.replaceString(offset1, offset2, text);
  }

  public static void insertLines(@NotNull Document document1, int line, @NotNull Document document2, int otherLine1, int otherLine2) {
    insertLines(document1, line, getLinesContent(document2, otherLine1, otherLine2));
  }

  public static void replaceLines(@NotNull Document document1, int line1, int line2, @NotNull Document document2, int oLine1, int oLine2) {
    replaceLines(document1, line1, line2, getLinesContent(document2, oLine1, oLine2));
  }

  public static void applyModification(@NotNull Document document1,
                                       int line1,
                                       int line2,
                                       @NotNull Document document2,
                                       int oLine1,
                                       int oLine2) {
    if (line1 == line2) {
      insertLines(document1, line1, document2, oLine1, oLine2);
    }
    else if (oLine1 == oLine2) {
      deleteLines(document1, line1, line2);
    }
    else {
      replaceLines(document1, line1, line2, document2, oLine1, oLine2);
    }
  }

  @NotNull
  public static CharSequence getLinesContent(@NotNull Document document, int line1, int line2) {
    TextRange otherRange = getLinesRange(document, line1, line2);
    return document.getCharsSequence().subSequence(otherRange.getStartOffset(), otherRange.getEndOffset());
  }

  @NotNull
  public static TextRange getLinesRange(@NotNull Document document, int line1, int line2) {
    if (line1 == line2) {
      int lineStartOffset = line1 < getLineCount(document) ? document.getLineStartOffset(line1) : document.getTextLength();
      return new TextRange(lineStartOffset, lineStartOffset);
    }
    else {
      int startOffset = document.getLineStartOffset(line1);
      int endOffset = document.getLineEndOffset(line2 - 1);
      return new TextRange(startOffset, endOffset);
    }
  }

  public static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }

  //
  // Types
  //

  @NotNull
  public static TextDiffType getLineDiffType(@NotNull LineFragment fragment) {
    boolean left = fragment.getEndOffset1() != fragment.getStartOffset1() || fragment.getStartLine1() != fragment.getEndLine1();
    boolean right = fragment.getEndOffset2() != fragment.getStartOffset2() || fragment.getStartLine2() != fragment.getEndLine2();
    return getType(left, right);
  }

  @NotNull
  public static TextDiffType getDiffType(@NotNull DiffFragment fragment) {
    boolean left = fragment.getEndOffset1() != fragment.getStartOffset1();
    boolean right = fragment.getEndOffset2() != fragment.getStartOffset2();
    return getType(left, right);
  }

  private static TextDiffType getType(boolean left, boolean right) {
    if (left && right) {
      return TextDiffType.MODIFIED;
    }
    else if (left) {
      return TextDiffType.DELETED;
    }
    else if (right) {
      return TextDiffType.INSERTED;
    }
    else {
      throw new IllegalArgumentException();
    }
  }

  //
  // Writable
  //

  @CalledInAwt
  public static void executeWriteCommand(@NotNull final Document document,
                                         @Nullable final Project project,
                                         @Nullable final String name,
                                         @NotNull final Runnable task) {
    if (!makeWritable(project, document)) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, task, name, null);
      }
    });
  }

  public static boolean isEditable(@NotNull Editor editor) {
    return !editor.isViewer() && canMakeWritable(editor.getDocument());
  }

  public static boolean canMakeWritable(@NotNull Document document) {
    if (document.isWritable()) {
      return true;
    }
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && file.isInLocalFileSystem()) {
      return true;
    }
    return false;
  }

  @CalledInAwt
  public static boolean makeWritable(@Nullable Project project, @NotNull Document document) {
    if (project == null) return false;
    return ReadonlyStatusHandler.ensureDocumentWritable(project, document);
  }

  //
  // Windows
  //

  @NotNull
  public static WindowWrapper.Mode getWindowMode(@NotNull DiffDialogHints hints) {
    WindowWrapper.Mode mode = hints.getMode();
    if (mode == null) {
      boolean isUnderDialog = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof JDialog;
      mode = isUnderDialog ? WindowWrapper.Mode.MODAL : WindowWrapper.Mode.FRAME;
    }
    return mode;
  }

  public static void closeWindow(@Nullable Window window, boolean modalOnly, boolean recursive) {
    if (window == null) return;

    Component component = window;
    while (component != null) {
      if (component instanceof Window) closeWindow((Window)component, modalOnly);

      component = recursive ? component.getParent() : null;
    }
  }

  public static void closeWindow(@NotNull Window window, boolean modalOnly) {
    if (window instanceof IdeFrameImpl) return;
    if (modalOnly && window instanceof Frame) return;

    if (window instanceof DialogWrapperDialog) {
      ((DialogWrapperDialog)window).getDialogWrapper().doCancelAction();
      return;
    }

    window.setVisible(false);
    window.dispose();
  }

  //
  // UserData
  //

  public static <T> T getUserData(@Nullable DiffRequest request, @Nullable FrameDiffTool.DiffContext context, @NotNull Key<T> key) {
    if (request != null) {
      T data = request.getUserData(key);
      if (data != null) return data;
    }
    if (context != null) {
      T data = context.getUserData(key);
      if (data != null) return data;
    }
    return null;
  }

  public static <T> T getUserData(@Nullable FrameDiffTool.DiffContext context, @Nullable DiffRequest request, @NotNull Key<T> key) {
    if (context != null) {
      T data = context.getUserData(key);
      if (data != null) return data;
    }
    if (request != null) {
      T data = request.getUserData(key);
      if (data != null) return data;
    }
    return null;
  }

  //
  // Tools
  //

  @NotNull
  public static <T extends DiffTool> List<T> filterSuppressedTools(@NotNull List<T> tools) {
    if (tools.size() < 2) return tools;

    List<Class<? extends DiffTool>> suppressedTools = new ArrayList<Class<? extends DiffTool>>();
    for (T tool : tools) {
      if (tool instanceof SuppressiveDiffTool) suppressedTools.addAll(((SuppressiveDiffTool)tool).getSuppressedTools());
    }

    if (suppressedTools.isEmpty()) return tools;

    List<T> filteredTools = new ArrayList<T>();
    for (T tool : tools) {
      if (suppressedTools.contains(tool.getClass())) continue;
      filteredTools.add(tool);
    }

    return filteredTools.isEmpty() ? tools : filteredTools;
  }

  //
  // Helpers
  //

  public static class DocumentData {
    @NotNull private final CharSequence myText1;
    @NotNull private final CharSequence myText2;
    private final long myStamp1;
    private final long myStamp2;

    public DocumentData(@NotNull CharSequence text1, @NotNull CharSequence text2, long stamp1, long stamp2) {
      myText1 = text1;
      myText2 = text2;
      myStamp1 = stamp1;
      myStamp2 = stamp2;
    }

    @NotNull
    public CharSequence getText1() {
      return myText1;
    }

    @NotNull
    public CharSequence getText2() {
      return myText2;
    }

    public long getStamp1() {
      return myStamp1;
    }

    public long getStamp2() {
      return myStamp2;
    }
  }

  public static class DiffConfig {
    @NotNull public final ComparisonPolicy policy;
    public final boolean fineFragments;
    public final boolean squashFragments;
    public final boolean trimFragments;

    public DiffConfig(@NotNull ComparisonPolicy policy, boolean fineFragments, boolean squashFragments, boolean trimFragments) {
      this.policy = policy;
      this.fineFragments = fineFragments;
      this.squashFragments = squashFragments;
      this.trimFragments = trimFragments;
    }

    public DiffConfig(@NotNull IgnorePolicy ignorePolicy, @NotNull HighlightPolicy highlightPolicy) {
      this(ignorePolicy.getComparisonPolicy(), highlightPolicy.isFineFragments(), highlightPolicy.isShouldSquash(),
           ignorePolicy.isShouldTrimChunks());
    }

    public DiffConfig() {
      this(IgnorePolicy.DEFAULT, HighlightPolicy.BY_LINE);
    }
  }
}
