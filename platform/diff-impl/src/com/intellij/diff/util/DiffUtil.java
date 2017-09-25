/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.diff.*;
import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.ComparisonUtil;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.text.*;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.GenericDataProvider;
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
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.Equality;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

public class DiffUtil {
  private static final Logger LOG = Logger.getInstance(DiffUtil.class);

  public static final Key<Boolean> TEMP_FILE_KEY = Key.create("Diff.TempFile");
  @NotNull public static final String DIFF_CONFIG = "diff.xml";
  public static final int TITLE_GAP = JBUI.scale(2);

  public static final List<Image> DIFF_FRAME_ICONS = loadDiffFrameImages();


  @NotNull
  private static List<Image> loadDiffFrameImages() {
    return ContainerUtil.list(
      ImageLoader.loadFromResource("/diff_frame32.png"),
      ImageLoader.loadFromResource("/diff_frame64.png"),
      ImageLoader.loadFromResource("/diff_frame128.png")
    );
  }

  //
  // Editor
  //

  public static boolean isDiffEditor(@NotNull Editor editor) {
    return editor.getEditorKind() == EditorKind.DIFF;
  }

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
  public static EditorHighlighter initEmptyEditorHighlighter(@NotNull CharSequence text) {
    EditorHighlighter highlighter = createEmptyEditorHighlighter();
    highlighter.setText(text);
    return highlighter;
  }

  @Nullable
  private static EditorHighlighter createEditorHighlighter(@Nullable Project project, @NotNull DocumentContent content) {
    FileType type = content.getContentType();
    VirtualFile file = content.getHighlightFile();
    Language language = content.getUserData(DiffUserDataKeys.LANGUAGE);

    EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
    if (language != null) {
      SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file);
      return highlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme());
    }
    if (file != null && file.isValid()) {
      if ((type == null || type == PlainTextFileType.INSTANCE) || file.getFileType() == type || file instanceof LightVirtualFile) {
        return highlighterFactory.createEditorHighlighter(project, file);
      }
    }
    if (type != null) {
      return highlighterFactory.createEditorHighlighter(project, type);
    }
    return null;
  }

  @NotNull
  private static EditorHighlighter createEmptyEditorHighlighter() {
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
    EditorKind kind = EditorKind.DIFF;
    EditorEx editor = (EditorEx)(isViewer ? factory.createViewer(document, project, kind) : factory.createEditor(document, project, kind));

    editor.getSettings().setShowIntentionBulb(false);
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

    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(content.getDocument());
    if (virtualFile != null && Registry.is("diff.enable.psi.highlighting")) {
      editor.setFile(virtualFile);
    }
  }

  public static boolean isMirrored(@NotNull Editor editor) {
    if (editor instanceof EditorEx) {
      return ((EditorEx)editor).getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT;
    }
    return false;
  }

  @Contract("null, _ -> false; _, null -> false")
  public static boolean canNavigateToFile(@Nullable Project project, @Nullable VirtualFile file) {
    if (project == null || project.isDefault()) return false;
    if (file == null || !file.isValid()) return false;
    if (OutsidersPsiFileSupport.isDiffFile(file)) return false;
    if (file.getUserData(TEMP_FILE_KEY) == Boolean.TRUE) return false;
    return true;
  }

  //
  // Scrolling
  //

  public static void disableBlitting(@NotNull EditorEx editor) {
    if (Registry.is("diff.divider.repainting.disable.blitting")) {
      editor.getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
    }
  }

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

  public static void moveCaretToLineRangeIfNeeded(@NotNull Editor editor, int startLine, int endLine) {
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    if (!isSelectedByLine(caretLine, startLine, endLine)) {
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(startLine, 0));
    }
  }

  //
  // Icons
  //

  @NotNull
  public static Icon getArrowIcon(@NotNull Side sourceSide) {
    return sourceSide.select(AllIcons.Diff.ArrowRight, AllIcons.Diff.Arrow);
  }

  @NotNull
  public static Icon getArrowDownIcon(@NotNull Side sourceSide) {
    return sourceSide.select(AllIcons.Diff.ArrowRightDown, AllIcons.Diff.ArrowLeftDown);
  }

  //
  // UI
  //

  public static void registerAction(@NotNull AnAction action, @NotNull JComponent component) {
    action.registerCustomShortcutSet(action.getShortcutSet(), component);
  }

  @NotNull
  public static JPanel createMessagePanel(@NotNull String message) {
    String text = StringUtil.replace(message, "\n", "<br>");
    JLabel label = new JBLabel(text) {
      @Override
      public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        size.width = Math.min(size.width, 200);
        size.height = Math.min(size.height, 100);
        return size;
      }
    }.setCopyable(true);
    label.setForeground(UIUtil.getInactiveTextColor());

    return new CenteredPanel(label, JBUI.Borders.empty(5));
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group, AnAction... actions) {
    addActionBlock(group, Arrays.asList(actions));
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group, @Nullable List<? extends AnAction> actions) {
    if (actions == null || actions.isEmpty()) return;
    group.addSeparator();

    AnAction[] children = group.getChildren(null);
    for (AnAction action : actions) {
      if (action instanceof Separator ||
          !ArrayUtil.contains(action, children)) {
        group.add(action);
      }
    }
  }

  @NotNull
  public static String getSettingsConfigurablePath() {
    return "Settings | Tools | Diff";
  }

  @NotNull
  public static String createTooltipText(@NotNull String text, @Nullable String appendix) {
    StringBuilder result = new StringBuilder();
    result.append("<html><body>");
    result.append(text);
    if (appendix != null) {
      result.append("<br><div style='margin-top: 5px'><font size='2'>");
      result.append(appendix);
      result.append("</font></div>");
    }
    result.append("</body></html>");
    return result.toString();
  }

  @NotNull
  public static String createNotificationText(@NotNull String text, @Nullable String appendix) {
    StringBuilder result = new StringBuilder();
    result.append("<html><body>");
    result.append(text);
    if (appendix != null) {
      result.append("<br><span style='color:#").append(ColorUtil.toHex(JBColor.gray)).append("'><small>");
      result.append(appendix);
      result.append("</small></span>");
    }
    result.append("</body></html>");
    return result.toString();
  }

  public static void showSuccessPopup(@NotNull String message,
                                      @NotNull RelativePoint point,
                                      @NotNull Disposable disposable,
                                      @Nullable Runnable hyperlinkHandler) {
    HyperlinkListener listener = null;
    if (hyperlinkHandler != null) {
      listener = new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          hyperlinkHandler.run();
        }
      };
    }

    Color bgColor = MessageType.INFO.getPopupBackground();

    Balloon balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message, null, bgColor, listener)
      .setAnimationCycle(200)
      .createBalloon();
    balloon.show(point, Balloon.Position.below);
    Disposer.register(disposable, balloon);
  }

  //
  // Titles
  //

  @NotNull
  public static List<JComponent> createSimpleTitles(@NotNull ContentDiffRequest request) {
    List<DiffContent> contents = request.getContents();
    List<String> titles = request.getContentTitles();

    if (!ContainerUtil.exists(titles, Condition.NOT_NULL)) {
      return Collections.nCopies(titles.size(), null);
    }

    List<JComponent> components = new ArrayList<>(titles.size());
    for (int i = 0; i < contents.size(); i++) {
      JComponent title = createTitle(StringUtil.notNullize(titles.get(i)));
      title = createTitleWithNotifications(title, contents.get(i));
      components.add(title);
    }

    return components;
  }

  @NotNull
  public static List<JComponent> createTextTitles(@NotNull ContentDiffRequest request, @NotNull List<? extends Editor> editors) {
    List<DiffContent> contents = request.getContents();
    List<String> titles = request.getContentTitles();

    boolean equalCharsets = TextDiffViewerUtil.areEqualCharsets(contents);
    boolean equalSeparators = TextDiffViewerUtil.areEqualLineSeparators(contents);

    List<JComponent> result = new ArrayList<>(contents.size());

    if (equalCharsets && equalSeparators && !ContainerUtil.exists(titles, Condition.NOT_NULL)) {
      return Collections.nCopies(titles.size(), null);
    }

    for (int i = 0; i < contents.size(); i++) {
      JComponent title = createTitle(StringUtil.notNullize(titles.get(i)), contents.get(i), equalCharsets, equalSeparators, editors.get(i));
      title = createTitleWithNotifications(title, contents.get(i));
      result.add(title);
    }

    return result;
  }

  @Nullable
  private static JComponent createTitleWithNotifications(@Nullable JComponent title,
                                                         @NotNull DiffContent content) {
    List<JComponent> notifications = new ArrayList<>();
    notifications.addAll(getCustomNotifications(content));

    if (content instanceof DocumentContent) {
      Document document = ((DocumentContent)content).getDocument();
      if (FileDocumentManager.getInstance().isPartialPreviewOfALargeFile(document)) {
        notifications.add(DiffNotifications.createNotification("File is too large. Only preview is loaded."));
      }
    }

    if (notifications.isEmpty()) return title;

    JPanel panel = new JPanel(new BorderLayout(0, TITLE_GAP));
    if (title != null) panel.add(title, BorderLayout.NORTH);
    panel.add(createStackedComponents(notifications, TITLE_GAP), BorderLayout.SOUTH);
    return panel;
  }

  @Nullable
  private static JComponent createTitle(@NotNull String title,
                                        @NotNull DiffContent content,
                                        boolean equalCharsets,
                                        boolean equalSeparators,
                                        @Nullable Editor editor) {
    if (content instanceof EmptyContent) return null;
    DocumentContent documentContent = (DocumentContent)content;

    Charset charset = equalCharsets ? null : documentContent.getCharset();
    Boolean bom = equalCharsets ? null : documentContent.hasBom();
    LineSeparator separator = equalSeparators ? null : documentContent.getLineSeparator();
    boolean isReadOnly = editor == null || editor.isViewer() || !canMakeWritable(editor.getDocument());

    return createTitle(title, separator, charset, bom, isReadOnly);
  }

  @NotNull
  public static JComponent createTitle(@NotNull String title) {
    return createTitle(title, null, null, null, false);
  }

  @NotNull
  public static JComponent createTitle(@NotNull String title,
                                       @Nullable LineSeparator separator,
                                       @Nullable Charset charset,
                                       @Nullable Boolean bom,
                                       boolean readOnly) {
    if (readOnly) title += " " + DiffBundle.message("diff.content.read.only.content.title.suffix");

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(0, 4));
    panel.add(new JBLabel(title).setCopyable(true), BorderLayout.CENTER);
    if (charset != null && separator != null) {
      JPanel panel2 = new JPanel();
      panel2.setLayout(new BoxLayout(panel2, BoxLayout.X_AXIS));
      panel2.add(createCharsetPanel(charset, bom));
      panel2.add(Box.createRigidArea(JBUI.size(4, 0)));
      panel2.add(createSeparatorPanel(separator));
      panel.add(panel2, BorderLayout.EAST);
    }
    else if (charset != null) {
      panel.add(createCharsetPanel(charset, bom), BorderLayout.EAST);
    }
    else if (separator != null) {
      panel.add(createSeparatorPanel(separator), BorderLayout.EAST);
    }
    return panel;
  }

  @NotNull
  private static JComponent createCharsetPanel(@NotNull Charset charset, @Nullable Boolean bom) {
    String text = charset.displayName();
    if (bom != null && bom) {
      text += " BOM";
    }

    JLabel label = new JLabel(text);
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

  @NotNull
  public static List<JComponent> createSyncHeightComponents(@NotNull final List<JComponent> components) {
    if (!ContainerUtil.exists(components, Condition.NOT_NULL)) return components;
    List<JComponent> result = new ArrayList<>();
    for (int i = 0; i < components.size(); i++) {
      result.add(new SyncHeightComponent(components, i));
    }
    return result;
  }

  @NotNull
  public static JComponent createStackedComponents(@NotNull List<JComponent> components, int gap) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    for (int i = 0; i < components.size(); i++) {
      if (i != 0) panel.add(Box.createVerticalStrut(JBUI.scale(gap)));
      panel.add(components.get(i));
    }

    return panel;
  }

  //
  // Focus
  //

  public static boolean isFocusedComponent(@Nullable Component component) {
    return isFocusedComponent(null, component);
  }

  public static boolean isFocusedComponent(@Nullable Project project, @Nullable Component component) {
    if (component == null) return false;
    Component ideFocusOwner = IdeFocusManager.getInstance(project).getFocusOwner();
    if (ideFocusOwner != null && SwingUtilities.isDescendingFrom(ideFocusOwner, component)) return true;

    Component jdkFocusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (jdkFocusOwner != null && SwingUtilities.isDescendingFrom(jdkFocusOwner, component)) return true;

    return false;
  }

  public static void requestFocus(@Nullable Project project, @Nullable Component component) {
    if (component == null) return;
    IdeFocusManager.getInstance(project).requestFocus(component, true);
  }

  public static void runPreservingFocus(@NotNull FocusableContext context, @NotNull Runnable task) {
    boolean hadFocus = context.isFocused();
    if (hadFocus) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
    task.run();
    if (hadFocus) context.requestFocus();
  }

  //
  // Compare
  //

  @NotNull
  public static TwosideTextDiffProvider createTextDiffProvider(@Nullable Project project,
                                                               @NotNull ContentDiffRequest request,
                                                               @NotNull TextDiffSettings settings,
                                                               @NotNull Runnable rediff,
                                                               @NotNull Disposable disposable) {
    DiffUserDataKeysEx.DiffComputer diffComputer = request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER);
    if (diffComputer != null) return new SimpleTextDiffProvider(settings, rediff, disposable, diffComputer);

    TwosideTextDiffProvider smartProvider = SmartTextDiffProvider.create(project, request, settings, rediff, disposable);
    if (smartProvider != null) return smartProvider;

    return new SimpleTextDiffProvider(settings, rediff, disposable);
  }

  @NotNull
  public static TwosideTextDiffProvider.NoIgnore createNoIgnoreTextDiffProvider(@Nullable Project project,
                                                                                @NotNull ContentDiffRequest request,
                                                                                @NotNull TextDiffSettings settings,
                                                                                @NotNull Runnable rediff,
                                                                                @NotNull Disposable disposable) {
    DiffUserDataKeysEx.DiffComputer diffComputer = request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER);
    if (diffComputer != null) return new SimpleTextDiffProvider.NoIgnore(settings, rediff, disposable, diffComputer);

    TwosideTextDiffProvider.NoIgnore smartProvider = SmartTextDiffProvider.createNoIgnore(project, request, settings, rediff, disposable);
    if (smartProvider != null) return smartProvider;

    return new SimpleTextDiffProvider.NoIgnore(settings, rediff, disposable);
  }

  @Nullable
  public static MergeInnerDifferences compareThreesideInner(@NotNull List<CharSequence> chunks,
                                                            @NotNull ComparisonPolicy comparisonPolicy,
                                                            @NotNull ProgressIndicator indicator) {
    if (chunks.get(0) == null && chunks.get(1) == null && chunks.get(2) == null) return null; // ---

    if (comparisonPolicy == ComparisonPolicy.IGNORE_WHITESPACES) {
      if (isChunksEquals(chunks.get(0), chunks.get(1), comparisonPolicy) &&
          isChunksEquals(chunks.get(0), chunks.get(2), comparisonPolicy)) {
        // whitespace-only changes, ex: empty lines added/removed
        return new MergeInnerDifferences(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
      }
    }

    if (chunks.get(0) == null && chunks.get(1) == null ||
        chunks.get(0) == null && chunks.get(2) == null ||
        chunks.get(1) == null && chunks.get(2) == null) { // =--, -=-, --=
      return null;
    }

    if (chunks.get(0) != null && chunks.get(1) != null && chunks.get(2) != null) { // ===
      List<DiffFragment> fragments1 = ByWord.compare(chunks.get(1), chunks.get(0), comparisonPolicy, indicator);
      List<DiffFragment> fragments2 = ByWord.compare(chunks.get(1), chunks.get(2), comparisonPolicy, indicator);

      List<TextRange> left = new ArrayList<>();
      List<TextRange> base = new ArrayList<>();
      List<TextRange> right = new ArrayList<>();

      for (DiffFragment wordFragment : fragments1) {
        base.add(new TextRange(wordFragment.getStartOffset1(), wordFragment.getEndOffset1()));
        left.add(new TextRange(wordFragment.getStartOffset2(), wordFragment.getEndOffset2()));
      }

      for (DiffFragment wordFragment : fragments2) {
        base.add(new TextRange(wordFragment.getStartOffset1(), wordFragment.getEndOffset1()));
        right.add(new TextRange(wordFragment.getStartOffset2(), wordFragment.getEndOffset2()));
      }

      return new MergeInnerDifferences(left, base, right);
    }

    // ==-, =-=, -==
    final ThreeSide side1 = chunks.get(0) != null ? ThreeSide.LEFT : ThreeSide.BASE;
    final ThreeSide side2 = chunks.get(2) != null ? ThreeSide.RIGHT : ThreeSide.BASE;
    CharSequence chunk1 = side1.select(chunks);
    CharSequence chunk2 = side2.select(chunks);

    List<DiffFragment> wordConflicts = ByWord.compare(chunk1, chunk2, comparisonPolicy, indicator);

    List<List<TextRange>> textRanges = ThreeSide.map(side -> {
      if (side == side1) {
        return ContainerUtil.map(wordConflicts, fragment -> new TextRange(fragment.getStartOffset1(), fragment.getEndOffset1()));
      }
      if (side == side2) {
        return ContainerUtil.map(wordConflicts, fragment -> new TextRange(fragment.getStartOffset2(), fragment.getEndOffset2()));
      }
      return null;
    });

    return new MergeInnerDifferences(textRanges.get(0), textRanges.get(1), textRanges.get(2));
  }

  private static boolean isChunksEquals(@Nullable CharSequence chunk1,
                                        @Nullable CharSequence chunk2,
                                        @NotNull ComparisonPolicy comparisonPolicy) {
    if (chunk1 == null) chunk1 = "";
    if (chunk2 == null) chunk2 = "";
    return ComparisonUtil.isEquals(chunk1, chunk2, comparisonPolicy);
  }

  @NotNull
  public static <T> int[] getSortedIndexes(@NotNull List<T> values, @NotNull Comparator<T> comparator) {
    final List<Integer> indexes = new ArrayList<>(values.size());
    for (int i = 0; i < values.size(); i++) {
      indexes.add(i);
    }

    ContainerUtil.sort(indexes, (i1, i2) -> {
      T val1 = values.get(i1);
      T val2 = values.get(i2);
      return comparator.compare(val1, val2);
    });

    return ArrayUtil.toIntArray(indexes);
  }

  @NotNull
  public static int[] invertIndexes(@NotNull int[] indexes) {
    int[] inverted = new int[indexes.length];
    for (int i = 0; i < indexes.length; i++) {
      inverted[indexes[i]] = i;
    }
    return inverted;
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

  private static void deleteLines(@NotNull Document document, int line1, int line2) {
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

  private static void insertLines(@NotNull Document document, int line, @NotNull CharSequence text) {
    if (line == getLineCount(document)) {
      document.insertString(document.getTextLength(), "\n" + text);
    }
    else {
      document.insertString(document.getLineStartOffset(line), text + "\n");
    }
  }

  private static void replaceLines(@NotNull Document document, int line1, int line2, @NotNull CharSequence text) {
    TextRange currentTextRange = getLinesRange(document, line1, line2);
    int offset1 = currentTextRange.getStartOffset();
    int offset2 = currentTextRange.getEndOffset();

    document.replaceString(offset1, offset2, text);
  }

  public static void applyModification(@NotNull Document document,
                                       int line1,
                                       int line2,
                                       @NotNull List<? extends CharSequence> newLines) {
    if (line1 == line2 && newLines.isEmpty()) return;
    if (line1 == line2) {
      insertLines(document, line1, StringUtil.join(newLines, "\n"));
    }
    else if (newLines.isEmpty()) {
      deleteLines(document, line1, line2);
    }
    else {
      replaceLines(document, line1, line2, StringUtil.join(newLines, "\n"));
    }
  }

  public static void applyModification(@NotNull Document document1,
                                       int line1,
                                       int line2,
                                       @NotNull Document document2,
                                       int oLine1,
                                       int oLine2) {
    if (line1 == line2 && oLine1 == oLine2) return;
    if (line1 == line2) {
      insertLines(document1, line1, getLinesContent(document2, oLine1, oLine2));
    }
    else if (oLine1 == oLine2) {
      deleteLines(document1, line1, line2);
    }
    else {
      replaceLines(document1, line1, line2, getLinesContent(document2, oLine1, oLine2));
    }
  }

  @NotNull
  public static CharSequence getLinesContent(@NotNull Document document, int line1, int line2) {
    return getLinesRange(document, line1, line2).subSequence(document.getImmutableCharSequence());
  }

  @NotNull
  public static CharSequence getLinesContent(@NotNull CharSequence sequence, @NotNull LineOffsets lineOffsets, int line1, int line2) {
    assert sequence.length() == lineOffsets.getTextLength();
    return getLinesRange(lineOffsets, line1, line2, false).subSequence(sequence);
  }

  /**
   * Return affected range, without non-internal newlines
   * <p/>
   * we consider '\n' not as a part of line, but a separator between lines
   * ex: if last line is not empty, the last symbol will not be '\n'
   */
  public static TextRange getLinesRange(@NotNull Document document, int line1, int line2) {
    return getLinesRange(document, line1, line2, false);
  }

  @NotNull
  public static TextRange getLinesRange(@NotNull Document document, int line1, int line2, boolean includeNewline) {
    return getLinesRange(LineOffsetsUtil.create(document), line1, line2, includeNewline);
  }

  @NotNull
  public static TextRange getLinesRange(@NotNull LineOffsets lineOffsets, int line1, int line2, boolean includeNewline) {
    if (line1 == line2) {
      int lineStartOffset = line1 < lineOffsets.getLineCount() ? lineOffsets.getLineStart(line1) : lineOffsets.getTextLength();
      return new TextRange(lineStartOffset, lineStartOffset);
    }
    else {
      int startOffset = lineOffsets.getLineStart(line1);
      int endOffset = lineOffsets.getLineEnd(line2 - 1);
      if (includeNewline && endOffset < lineOffsets.getTextLength()) endOffset++;
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

  /**
   * Document.getLineCount() returns 0 for empty text.
   * <p>
   * This breaks an assumption "getLineCount() == StringUtil.countNewLines(text) + 1"
   * and adds unnecessary corner case into line ranges logic.
   */
  public static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }

  @NotNull
  public static List<String> getLines(@NotNull Document document) {
    return getLines(document, 0, getLineCount(document));
  }

  @NotNull
  public static List<String> getLines(@NotNull Document document, int startLine, int endLine) {
    if (startLine < 0 || startLine > endLine || endLine > getLineCount(document)) {
      throw new IndexOutOfBoundsException(String.format("Wrong line range: [%d, %d); lineCount: '%d'",
                                                        startLine, endLine, document.getLineCount()));
    }

    List<String> result = new ArrayList<>();
    for (int i = startLine; i < endLine; i++) {
      int start = document.getLineStartOffset(i);
      int end = document.getLineEndOffset(i);
      result.add(document.getText(new TextRange(start, end)));
    }
    return result;
  }

  public static int bound(int value, int lowerBound, int upperBound) {
    assert lowerBound <= upperBound : String.format("%s - [%s, %s]", value, lowerBound, upperBound);
    return Math.max(Math.min(value, upperBound), lowerBound);
  }

  //
  // Updating ranges on change
  //

  @NotNull
  public static LineRange getAffectedLineRange(@NotNull DocumentEvent e) {
    int line1 = e.getDocument().getLineNumber(e.getOffset());
    int line2 = e.getDocument().getLineNumber(e.getOffset() + e.getOldLength()) + 1;
    return new LineRange(line1, line2);
  }

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
    boolean left = fragment.getStartLine1() != fragment.getEndLine1();
    boolean right = fragment.getStartLine2() != fragment.getEndLine2();
    return getDiffType(left, right);
  }

  @NotNull
  public static TextDiffType getDiffType(@NotNull DiffFragment fragment) {
    boolean left = fragment.getEndOffset1() != fragment.getStartOffset1();
    boolean right = fragment.getEndOffset2() != fragment.getStartOffset2();
    return getDiffType(left, right);
  }

  @NotNull
  public static TextDiffType getDiffType(boolean hasDeleted, boolean hasInserted) {
    if (hasDeleted && hasInserted) {
      return TextDiffType.MODIFIED;
    }
    else if (hasDeleted) {
      return TextDiffType.DELETED;
    }
    else if (hasInserted) {
      return TextDiffType.INSERTED;
    }
    else {
      LOG.error("Diff fragment should not be empty");
      return TextDiffType.MODIFIED;
    }
  }

  @NotNull
  public static MergeConflictType getMergeType(@NotNull Condition<ThreeSide> emptiness,
                                               @NotNull Equality<ThreeSide> equality,
                                               @NotNull BooleanGetter conflictResolver) {
    boolean isLeftEmpty = emptiness.value(ThreeSide.LEFT);
    boolean isBaseEmpty = emptiness.value(ThreeSide.BASE);
    boolean isRightEmpty = emptiness.value(ThreeSide.RIGHT);
    assert !isLeftEmpty || !isBaseEmpty || !isRightEmpty;

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return new MergeConflictType(TextDiffType.INSERTED, false, true);
      }
      else if (isRightEmpty) { // =--
        return new MergeConflictType(TextDiffType.INSERTED, true, false);
      }
      else { // =-=
        boolean equalModifications = equality.equals(ThreeSide.LEFT, ThreeSide.RIGHT);
        if (equalModifications) {
          return new MergeConflictType(TextDiffType.INSERTED, true, true);
        }
        else {
          return new MergeConflictType(TextDiffType.CONFLICT, true, true, false);
        }
      }
    }
    else {
      if (isLeftEmpty && isRightEmpty) { // -=-
        return new MergeConflictType(TextDiffType.DELETED, true, true);
      }
      else { // -==, ==-, ===
        boolean unchangedLeft = equality.equals(ThreeSide.BASE, ThreeSide.LEFT);
        boolean unchangedRight = equality.equals(ThreeSide.BASE, ThreeSide.RIGHT);
        assert !unchangedLeft || !unchangedRight;

        if (unchangedLeft) return new MergeConflictType(isRightEmpty ? TextDiffType.DELETED : TextDiffType.MODIFIED, false, true);
        if (unchangedRight) return new MergeConflictType(isLeftEmpty ? TextDiffType.DELETED : TextDiffType.MODIFIED, true, false);

        boolean equalModifications = equality.equals(ThreeSide.LEFT, ThreeSide.RIGHT);
        if (equalModifications) {
          return new MergeConflictType(TextDiffType.MODIFIED, true, true);
        }
        else {
          boolean canBeResolved = !isLeftEmpty && !isRightEmpty && conflictResolver.get();
          return new MergeConflictType(TextDiffType.CONFLICT, true, true, canBeResolved);
        }
      }
    }
  }

  @NotNull
  public static MergeConflictType getLineMergeType(@NotNull MergeLineFragment fragment,
                                                   @NotNull List<? extends CharSequence> sequences,
                                                   @NotNull List<LineOffsets> lineOffsets,
                                                   @NotNull ComparisonPolicy policy) {
    return getMergeType((side) -> isLineMergeIntervalEmpty(fragment, side),
                        (side1, side2) -> compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2),
                        () -> canResolveLineConflict(fragment, sequences, lineOffsets));
  }

  private static boolean canResolveLineConflict(@NotNull MergeLineFragment fragment,
                                                @NotNull List<? extends CharSequence> sequences,
                                                @NotNull List<LineOffsets> lineOffsets) {
    List<? extends CharSequence> contents = ThreeSide.map(side -> {
      return getLinesContent(side.select(sequences), side.select(lineOffsets), fragment.getStartLine(side), fragment.getEndLine(side));
    });
    return ComparisonMergeUtil.tryResolveConflict(contents.get(0), contents.get(1), contents.get(2)) != null;
  }

  private static boolean compareLineMergeContents(@NotNull MergeLineFragment fragment,
                                                  @NotNull List<? extends CharSequence> sequences,
                                                  @NotNull List<LineOffsets> lineOffsets,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ThreeSide side1,
                                                  @NotNull ThreeSide side2) {
    int start1 = fragment.getStartLine(side1);
    int end1 = fragment.getEndLine(side1);
    int start2 = fragment.getStartLine(side2);
    int end2 = fragment.getEndLine(side2);

    if (end2 - start2 != end1 - start1) return false;

    CharSequence sequence1 = side1.select(sequences);
    CharSequence sequence2 = side2.select(sequences);
    LineOffsets offsets1 = side1.select(lineOffsets);
    LineOffsets offsets2 = side2.select(lineOffsets);

    for (int i = 0; i < end1 - start1; i++) {
      int line1 = start1 + i;
      int line2 = start2 + i;

      CharSequence content1 = getLinesContent(sequence1, offsets1, line1, line1 + 1);
      CharSequence content2 = getLinesContent(sequence2, offsets2, line2, line2 + 1);
      if (!ComparisonUtil.isEquals(content1, content2, policy)) return false;
    }

    return true;
  }

  private static boolean isLineMergeIntervalEmpty(@NotNull MergeLineFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartLine(side) == fragment.getEndLine(side);
  }

  @NotNull
  public static MergeConflictType getWordMergeType(@NotNull MergeWordFragment fragment,
                                                   @NotNull List<? extends CharSequence> texts,
                                                   @NotNull ComparisonPolicy policy) {
    return getMergeType((side) -> isWordMergeIntervalEmpty(fragment, side),
                        (side1, side2) -> compareWordMergeContents(fragment, texts, policy, side1, side2),
                        BooleanGetter.FALSE);
  }

  private static boolean compareWordMergeContents(@NotNull MergeWordFragment fragment,
                                                  @NotNull List<? extends CharSequence> texts,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ThreeSide side1,
                                                  @NotNull ThreeSide side2) {
    int start1 = fragment.getStartOffset(side1);
    int end1 = fragment.getEndOffset(side1);
    int start2 = fragment.getStartOffset(side2);
    int end2 = fragment.getEndOffset(side2);

    CharSequence document1 = side1.select(texts);
    CharSequence document2 = side2.select(texts);

    CharSequence content1 = document1.subSequence(start1, end1);
    CharSequence content2 = document2.subSequence(start2, end2);
    return ComparisonUtil.isEquals(content1, content2, policy);
  }

  private static boolean isWordMergeIntervalEmpty(@NotNull MergeWordFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartOffset(side) == fragment.getEndOffset(side);
  }

  //
  // Writable
  //

  @CalledInAwt
  public static boolean executeWriteCommand(@Nullable Project project,
                                            @NotNull Document document,
                                            @Nullable String commandName,
                                            @Nullable String commandGroupId,
                                            @NotNull UndoConfirmationPolicy confirmationPolicy,
                                            boolean underBulkUpdate,
                                            @NotNull Runnable task) {
    if (!makeWritable(project, document)) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      LOG.warn("Document is read-only" + (file != null ? ": " + file.getPresentableName() : ""));
      return false;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      CommandProcessor.getInstance().executeCommand(project, () -> {
        if (underBulkUpdate) {
          DocumentUtil.executeInBulk(document, true, task);
        }
        else {
          task.run();
        }
      }, commandName, commandGroupId, confirmationPolicy, document);
    });
    return true;
  }

  @CalledInAwt
  public static boolean executeWriteCommand(@NotNull final Document document,
                                            @Nullable final Project project,
                                            @Nullable final String commandName,
                                            @NotNull final Runnable task) {
    return executeWriteCommand(project, document, commandName, null, UndoConfirmationPolicy.DEFAULT, false, task);
  }

  public static boolean isEditable(@NotNull Editor editor) {
    return !editor.isViewer() && canMakeWritable(editor.getDocument());
  }

  public static boolean canMakeWritable(@NotNull Document document) {
    if (document.isWritable()) {
      return true;
    }
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && file.isValid() && file.isInLocalFileSystem()) {
      if (file.getUserData(TEMP_FILE_KEY) == Boolean.TRUE) return false;
      // decompiled file can be writable, but Document with decompiled content is still read-only
      return !file.isWritable();
    }
    return false;
  }

  @CalledInAwt
  public static boolean makeWritable(@Nullable Project project, @NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return document.isWritable();
    if (!file.isValid()) return false;
    return makeWritable(project, file) && document.isWritable();
  }

  @CalledInAwt
  public static boolean makeWritable(@Nullable Project project, @NotNull VirtualFile file) {
    if (project == null) project = ProjectManager.getInstance().getDefaultProject();
    return !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(file).hasReadonlyFiles();
  }

  public static void putNonundoableOperation(@Nullable Project project, @NotNull Document document) {
    UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    if (undoManager != null) {
      DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
      undoManager.nonundoableActionPerformed(ref, false);
    }
  }

  /**
   * Difference with {@link VfsUtil#markDirtyAndRefresh} is that refresh from VfsUtil will be performed with ModalityState.NON_MODAL.
   */
  public static void markDirtyAndRefresh(boolean async, boolean recursive, boolean reloadChildren, @NotNull VirtualFile... files) {
    ModalityState modalityState = ApplicationManager.getApplication().getDefaultModalityState();
    VfsUtil.markDirty(recursive, reloadChildren, files);
    RefreshQueue.getInstance().refresh(async, recursive, null, modalityState, files);
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
      if (component instanceof Window) {
        boolean isClosed = closeWindow((Window)component, modalOnly);
        if (!isClosed) break;
      }

      component = recursive ? component.getParent() : null;
    }
  }

  /**
   * @return whether window was closed
   */
  private static boolean closeWindow(@NotNull Window window, boolean modalOnly) {
    if (window instanceof IdeFrameImpl) return false;
    if (modalOnly && window instanceof Frame) return false;

    if (window instanceof DialogWrapperDialog) {
      ((DialogWrapperDialog)window).getDialogWrapper().doCancelAction();
      return !window.isVisible();
    }

    window.setVisible(false);
    window.dispose();
    return true;
  }

  //
  // UserData
  //

  public static <T> UserDataHolderBase createUserDataHolder(@NotNull Key<T> key, @Nullable T value) {
    UserDataHolderBase holder = new UserDataHolderBase();
    holder.putUserData(key, value);
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

  public static <T> T getUserData(@Nullable UserDataHolder first, @Nullable UserDataHolder second, @NotNull Key<T> key) {
    if (first != null) {
      T data = first.getUserData(key);
      if (data != null) return data;
    }
    if (second != null) {
      T data = second.getUserData(key);
      if (data != null) return data;
    }
    return null;
  }

  public static void addNotification(@Nullable JComponent component, @NotNull UserDataHolder holder) {
    if (component == null) return;
    List<JComponent> oldComponents = ContainerUtil.notNullize(holder.getUserData(DiffUserDataKeys.NOTIFICATIONS));
    holder.putUserData(DiffUserDataKeys.NOTIFICATIONS, ContainerUtil.append(oldComponents, component));
  }

  @NotNull
  public static List<JComponent> getCustomNotifications(@NotNull UserDataHolder context, @NotNull UserDataHolder request) {
    List<JComponent> requestComponents = request.getUserData(DiffUserDataKeys.NOTIFICATIONS);
    List<JComponent> contextComponents = context.getUserData(DiffUserDataKeys.NOTIFICATIONS);
    return ContainerUtil.concat(ContainerUtil.notNullize(contextComponents), ContainerUtil.notNullize(requestComponents));
  }

  @NotNull
  public static List<JComponent> getCustomNotifications(@NotNull DiffContent content) {
    return ContainerUtil.notNullize(content.getUserData(DiffUserDataKeys.NOTIFICATIONS));
  }

  //
  // DataProvider
  //

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

  public static <T> void putDataKey(@NotNull UserDataHolder holder, @NotNull DataKey<T> key, @Nullable T value) {
    DataProvider dataProvider = holder.getUserData(DiffUserDataKeys.DATA_PROVIDER);
    if (!(dataProvider instanceof GenericDataProvider)) {
      dataProvider = new GenericDataProvider(dataProvider);
      holder.putUserData(DiffUserDataKeys.DATA_PROVIDER, dataProvider);
    }
    ((GenericDataProvider)dataProvider).putData(key, value);
  }

  @NotNull
  public static DiffSettings getDiffSettings(@NotNull DiffContext context) {
    DiffSettings settings = context.getUserData(DiffSettings.KEY);
    if (settings == null) {
      settings = DiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE));
      context.putUserData(DiffSettings.KEY, settings);
    }
    return settings;
  }

  @NotNull
  public static <K, V> TreeMap<K, V> trimDefaultValues(@NotNull TreeMap<K, V> map, @NotNull Convertor<K, V> defaultValue) {
    TreeMap<K, V> result = new TreeMap<>();
    for (Map.Entry<K, V> it : map.entrySet()) {
      K key = it.getKey();
      V value = it.getValue();
      if (!value.equals(defaultValue.convert(key))) result.put(key, value);
    }
    return result;
  }

  //
  // Tools
  //

  @NotNull
  public static <T extends DiffTool> List<T> filterSuppressedTools(@NotNull List<T> tools) {
    if (tools.size() < 2) return tools;

    final List<Class<? extends DiffTool>> suppressedTools = new ArrayList<>();
    for (T tool : tools) {
      try {
        if (tool instanceof SuppressiveDiffTool) suppressedTools.addAll(((SuppressiveDiffTool)tool).getSuppressedTools());
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (suppressedTools.isEmpty()) return tools;

    List<T> filteredTools = ContainerUtil.filter(tools, tool -> !suppressedTools.contains(tool.getClass()));
    return filteredTools.isEmpty() ? tools : filteredTools;
  }

  //
  // Helpers
  //


  private static class SyncHeightComponent extends JPanel {
    @NotNull private final List<JComponent> myComponents;

    public SyncHeightComponent(@NotNull List<JComponent> components, int index) {
      super(new BorderLayout());
      myComponents = components;
      JComponent delegate = components.get(index);
      if (delegate != null) add(delegate, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = getPreferredHeight();
      return size;
    }

    private int getPreferredHeight() {
      int height = 0;
      for (JComponent component : myComponents) {
        if (component == null) continue;
        height = Math.max(height, component.getPreferredSize().height);
      }
      return height;
    }
  }

  public static class CenteredPanel extends JPanel {
    private final JComponent myComponent;

    public CenteredPanel(@NotNull JComponent component) {
      myComponent = component;
      add(component);
    }

    public CenteredPanel(@NotNull JComponent component, @NotNull Border border) {
      this(component);
      setBorder(border);
    }

    @Override
    public void doLayout() {
      final Dimension size = getSize();
      final Dimension preferredSize = myComponent.getPreferredSize();

      Insets insets = getInsets();
      JBInsets.removeFrom(size, insets);

      int width = Math.min(size.width, preferredSize.width);
      int height = Math.min(size.height, preferredSize.height);
      int x = Math.max(0, (size.width - preferredSize.width) / 2);
      int y = Math.max(0, (size.height - preferredSize.height) / 2);

      myComponent.setBounds(insets.left + x, insets.top + y, width, height);
    }

    @Override
    public Dimension getPreferredSize() {
      return addInsets(myComponent.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
      return addInsets(myComponent.getMinimumSize());
    }

    @Override
    public Dimension getMaximumSize() {
      return addInsets(myComponent.getMaximumSize());
    }

    private Dimension addInsets(Dimension dimension) {
      JBInsets.addTo(dimension, getInsets());
      return dimension;
    }
  }
}
