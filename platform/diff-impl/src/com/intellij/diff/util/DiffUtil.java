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
package com.intellij.diff.util;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffTool;
import com.intellij.diff.SuppressiveDiffTool;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.LineFragmentCache;
import com.intellij.diff.tools.util.LineFragmentCache.PolicyData;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.Function;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public class DiffUtil {
  private static final Logger LOG = Logger.getInstance(DiffUtil.class);

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
    editor.getSettings().setCaretRowShown(false);
    editor.reinitSettings();
  }

  public static void setFoldingModelSupport(@NotNull EditorEx editor) {
    editor.getSettings().setFoldingOutlineShown(true);
    editor.getSettings().setAutoCodeFoldingEnabled(false);
    editor.getColorsScheme().setAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES, null);
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

    editor.getSettings().setLineNumbersShown(true);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
    editor.getGutterComponentEx().setShowDefaultGutterPopup(false);

    if (enableFolding) {
      setFoldingModelSupport(editor);
    } else {
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

  public static boolean isMirrored(@NotNull Editor editor) {
    if (editor instanceof EditorEx) {
      return ((EditorEx)editor).getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT;
    }
    return false;
  }

  //
  // Scrolling
  //

  public static void moveCaret(@Nullable final Editor editor, int line) {
    if (editor == null) return;
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, 0));
  }

  public static void scrollEditor(@Nullable final Editor editor, int line, boolean animated) {
    scrollEditor(editor, line, 0, animated);
  }

  public static void scrollEditor(@Nullable final Editor editor, int line, int column, boolean animated) {
    if (editor == null) return;
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, column));
    scrollToCaret(editor, animated);
  }

  public static void scrollToPoint(@Nullable Editor editor, @NotNull Point point) {
    scrollToPoint(editor, point, false);
  }

  public static void scrollToPoint(@Nullable Editor editor, @NotNull Point point, boolean animated) {
    if (editor == null) return;
    if (!animated) editor.getScrollingModel().disableAnimation();
    editor.getScrollingModel().scrollHorizontally(point.x);
    editor.getScrollingModel().scrollVertically(point.y);
    if (!animated) editor.getScrollingModel().enableAnimation();
  }

  public static void scrollToCaret(@Nullable Editor editor, boolean animated) {
    if (editor == null) return;
    if (!animated) editor.getScrollingModel().disableAnimation();
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    if (!animated) editor.getScrollingModel().enableAnimation();
  }

  @NotNull
  public static Point getScrollingPosition(@Nullable Editor editor) {
    if (editor == null) return new Point(0, 0);
    ScrollingModel model = editor.getScrollingModel();
    return new Point(model.getHorizontalScrollOffset(), model.getVerticalScrollOffset());
  }

  @NotNull
  public static LogicalPosition getCaretPosition(@Nullable Editor editor) {
    return editor != null ? editor.getCaretModel().getLogicalPosition() : new LogicalPosition(0, 0);
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
    JLabel label = new JLabel();
    label.setForeground(UIUtil.getInactiveTextColor());
    JPanel wrapper = createMessagePanel(label);
    return Pair.create(wrapper, label);
  }

  @NotNull
  public static JPanel createMessagePanel(@NotNull JComponent comp) {
    JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(comp, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBUI.insets(1), 0, 0));
    return wrapper;
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
    List<String> titles = request.getContentTitles();

    List<JComponent> components = new ArrayList<JComponent>(titles.size());
    for (String title : titles) {
      components.add(createTitle(title));
    }

    return components;
  }

  @NotNull
  public static List<JComponent> createTextTitles(@NotNull ContentDiffRequest request, @NotNull List<? extends Editor> editors) {
    List<DiffContent> contents = request.getContents();
    List<String> titles = request.getContentTitles();

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

    List<JComponent> result = new ArrayList<JComponent>(contents.size());

    if (equalCharsets && equalSeparators && ContainerUtil.find(titles, Condition.NOT_NULL) == null) {
      return Collections.nCopies(titles.size(), null);
    }

    for (int i = 0; i < contents.size(); i++) {
      result.add(createTitle(StringUtil.notNullize(titles.get(i)), contents.get(i), equalCharsets, equalSeparators, editors.get(i)));
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
    panel.setBorder(IdeBorderFactory.createEmptyBorder(0, 4, 0, 4));
    panel.add(createTitlePanel(title), BorderLayout.CENTER);
    if (charset != null && separator != null) {
      JPanel panel2 = new JPanel();
      panel2.setLayout(new BoxLayout(panel2, BoxLayout.X_AXIS));
      panel2.add(createCharsetPanel(charset));
      panel2.add(Box.createRigidArea(new Dimension(4, 0)));
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
    return CopyableLabel.create(title);
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

  public static boolean isFocusedComponent(@Nullable Component component) {
    return isFocusedComponent(null, component);
  }

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
  public static List<LineFragment> compareWithCache(@NotNull DiffRequest request,
                                                    @NotNull DocumentData data,
                                                    @NotNull DiffConfig config,
                                                    @NotNull ProgressIndicator indicator) {
    return compareWithCache(request, data.getText1(), data.getText2(), data.getStamp1(), data.getStamp2(), config, indicator);
  }

  @NotNull
  public static List<LineFragment> compareWithCache(@NotNull DiffRequest request,
                                                    @NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    long stamp1,
                                                    long stamp2,
                                                    @NotNull DiffConfig config,
                                                    @NotNull ProgressIndicator indicator) {
    List<LineFragment> fragments = doCompareWithCache(request, text1, text2, stamp1, stamp2, config, indicator);

    indicator.checkCanceled();
    return ComparisonManager.getInstance().processBlocks(fragments, text1, text2,
                                                         config.policy, config.squashFragments, config.trimFragments);
  }

  @NotNull
  private static List<LineFragment> doCompareWithCache(@NotNull DiffRequest request,
                                                       @NotNull CharSequence text1,
                                                       @NotNull CharSequence text2,
                                                       long stamp1,
                                                       long stamp2,
                                                       @NotNull DiffConfig config,
                                                       @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();
    PolicyData cachedData = getFromCache(request, config, stamp1, stamp2);

    List<LineFragment> newFragments;
    if (cachedData != null) {
      if (cachedData.getFragments().isEmpty()) return cachedData.getFragments();
      if (!config.innerFragments) return cachedData.getFragments();
      if (cachedData.isInnerFragments()) return cachedData.getFragments();
      newFragments = ComparisonManager.getInstance().compareLinesInner(text1, text2, cachedData.getFragments(), config.policy, indicator);
    }
    else {
      if (config.innerFragments) {
        newFragments = ComparisonManager.getInstance().compareLinesInner(text1, text2, config.policy, indicator);
      }
      else {
        newFragments = ComparisonManager.getInstance().compareLines(text1, text2, config.policy, indicator);
      }
    }

    indicator.checkCanceled();
    putToCache(request, config, stamp1, stamp2, newFragments, config.innerFragments);
    return newFragments;
  }

  @Nullable
  public static PolicyData getFromCache(@NotNull DiffRequest request, @NotNull DiffConfig config, long stamp1, long stamp2) {
    LineFragmentCache cache = request.getUserData(DiffUserDataKeysEx.LINE_FRAGMENT_CACHE);
    if (cache != null && cache.checkStamps(stamp1, stamp2)) {
      return cache.getData(config.policy);
    }
    return null;
  }

  public static void putToCache(@NotNull DiffRequest request, @NotNull DiffConfig config, long stamp1, long stamp2,
                                @NotNull List<LineFragment> fragments, boolean isInnerFragments) {
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

    cache.putData(config.policy, fragments, isInnerFragments);
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
    if (line1 == line2 && oLine1 == oLine2) return;
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
    return document.getImmutableCharSequence().subSequence(otherRange.getStartOffset(), otherRange.getEndOffset());
  }

  /**
   * Return affected range, without non-internal newlines
   * <p/>
   * we consider '\n' not as a part of line, but a separator between lines
   * ex: if last line is not empty, the last symbol will not be '\n'
   */
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

  public static int getOffset(@NotNull Document document, int line, int column) {
    if (line < 0) return 0;
    if (line >= getLineCount(document)) return document.getTextLength();

    int start = document.getLineStartOffset(line);
    int end = document.getLineEndOffset(line);
    return Math.min(start + column, end);
  }

  public static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }

  //
  // Updating ranges on change
  //

  public static int countLinesShift(@NotNull DocumentEvent e) {
    return StringUtil.countNewLines(e.getNewFragment()) - StringUtil.countNewLines(e.getOldFragment());
  }

  @NotNull
  public static UpdatedLineRange updateRangeOnModification(int start, int end, int changeStart, int changeEnd, int shift) {
    return updateRangeOnModification(start, end, changeStart, changeEnd, shift, false);
  }

  @NotNull
  public static UpdatedLineRange updateRangeOnModification(int start, int end, int changeStart, int changeEnd, int shift, boolean greedy) {
    if (end <= changeStart) { // change before
      return new UpdatedLineRange(start, end, false);
    }
    if (start >= changeEnd) { // change after
      return new UpdatedLineRange(start + shift, end + shift, false);
    }

    if (start <= changeStart && end >= changeEnd) { // change inside
      return new UpdatedLineRange(start, end + shift, false);
    }

    // range is damaged. We don't know new boundaries.
    // But we can try to return approximate new position
    int newChangeEnd = changeEnd + shift;

    if (start >= changeStart && end <= changeEnd) { // fully inside change
      return greedy ? new UpdatedLineRange(changeStart, newChangeEnd, true) :
                      new UpdatedLineRange(newChangeEnd, newChangeEnd, true);
    }

    if (start < changeStart) { // bottom boundary damaged
      return greedy ? new UpdatedLineRange(start, newChangeEnd, true) :
                      new UpdatedLineRange(start, changeStart, true);
    } else { // top boundary damaged
      return greedy ? new UpdatedLineRange(changeStart, end + shift, true) :
                      new UpdatedLineRange(newChangeEnd, end + shift, true);
    }
  }

  public static class UpdatedLineRange {
    public final int startLine;
    public final int endLine;
    public final boolean damaged;

    public UpdatedLineRange(int startLine, int endLine, boolean damaged) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.damaged = damaged;
    }
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
    if (document.isWritable()) return true;
    if (project == null) return false;
    return ReadonlyStatusHandler.ensureDocumentWritable(project, document);
  }

  //
  // Windows
  //

  @NotNull
  public static Dimension getDefaultDiffPanelSize() {
    return new Dimension(400, 200);
  }

  @NotNull
  public static Dimension getDefaultDiffWindowSize() {
    Rectangle screenBounds = ScreenUtil.getMainScreenBounds();
    int width = (int)(screenBounds.width * 0.8);
    int height = (int)(screenBounds.height * 0.8);
    return new Dimension(width, height);
  }

  @NotNull
  public static WindowWrapper.Mode getWindowMode(@NotNull DiffDialogHints hints) {
    WindowWrapper.Mode mode = hints.getMode();
    if (mode == null) {
      boolean isUnderDialog = LaterInvocator.isInModalContext();
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

  public static <T> UserDataHolderBase createUserDataHolder(@NotNull Key<T> key, @Nullable T value) {
    UserDataHolderBase holder = new UserDataHolderBase();
    holder.putUserData(key, value);
    return holder;
  }

  public static <T> UserDataHolderBase createUserDataHolder(@NotNull Key<T> key1, @Nullable T value1,
                                                            @NotNull Key<T> key2, @Nullable T value2) {
    UserDataHolderBase holder = new UserDataHolderBase();
    holder.putUserData(key1, value1);
    holder.putUserData(key2, value2);
    return holder;
  }

  public static boolean isUserDataFlagSet(@NotNull Key<Boolean> key, UserDataHolder... holders) {
    for (UserDataHolder holder : holders) {
      if (holder == null) continue;
      Boolean data = holder.getUserData(key);
      if (data != null) return data;
    }
    return false;
  }

  public static <T> T getUserData(@Nullable DiffRequest request, @Nullable DiffContext context, @NotNull Key<T> key) {
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

  public static <T> T getUserData(@Nullable DiffContext context, @Nullable DiffRequest request, @NotNull Key<T> key) {
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
  // DataProvider
  //

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull ContentDiffRequest request, @NotNull Side currentSide) {
    List<DiffContent> contents = request.getContents();
    DiffContent content1 = currentSide.select(contents);
    DiffContent content2 = currentSide.other().select(contents);

    if (content1 instanceof FileContent) return ((FileContent)content1).getFile();
    if (content2 instanceof FileContent) return ((FileContent)content2).getFile();
    return null;
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull ContentDiffRequest request, @NotNull ThreeSide currentSide) {
    List<DiffContent> contents = request.getContents();
    DiffContent content1 = currentSide.select(contents);
    DiffContent content2 = ThreeSide.BASE.select(contents);

    if (content1 instanceof FileContent) return ((FileContent)content1).getFile();
    if (content2 instanceof FileContent) return ((FileContent)content2).getFile();
    return null;
  }

  @Nullable
  public static Object getData(@Nullable DataProvider provider, @Nullable DataProvider fallbackProvider, @NonNls String dataId) {
    if (provider != null) {
      Object data = provider.getData(dataId);
      if (data != null) return data;
    }
    if (fallbackProvider != null) {
      Object data = fallbackProvider.getData(dataId);
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

    final List<Class<? extends DiffTool>> suppressedTools = new ArrayList<Class<? extends DiffTool>>();
    for (T tool : tools) {
      try {
        if (tool instanceof SuppressiveDiffTool) suppressedTools.addAll(((SuppressiveDiffTool)tool).getSuppressedTools());
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (suppressedTools.isEmpty()) return tools;

    List<T> filteredTools = ContainerUtil.filter(tools, new Condition<T>() {
      @Override
      public boolean value(T tool) {
        return !suppressedTools.contains(tool.getClass());
      }
    });

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
    public final boolean innerFragments;
    public final boolean squashFragments;
    public final boolean trimFragments;

    public DiffConfig(@NotNull ComparisonPolicy policy, boolean innerFragments, boolean squashFragments, boolean trimFragments) {
      this.policy = policy;
      this.innerFragments = innerFragments;
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
