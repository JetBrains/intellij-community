// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.diff.*;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.ComparisonUtil;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.editor.DiffEditorTabFilesManager;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.impl.DiffToolSubstitutor;
import com.intellij.diff.merge.ConflictType;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.text.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.*;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.SingleComponentCenteringLayout;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import icons.PlatformDiffImplIcons;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import static com.intellij.util.ArrayUtilRt.EMPTY_BYTE_ARRAY;
import static com.intellij.util.ObjectUtils.notNull;

public final class DiffUtil {
  private static final Logger LOG = Logger.getInstance(DiffUtil.class);

  public static final Key<Boolean> TEMP_FILE_KEY = Key.create("Diff.TempFile");
  public static final @NotNull @NonNls String DIFF_CONFIG = "diff.xml";
  public static final JBValue TITLE_GAP = new JBValue.Float(2);

  public static final NotNullLazyValue<@Unmodifiable List<Image>> DIFF_FRAME_ICONS = NotNullLazyValue.createValue(() -> {
    return ContainerUtil.skipNulls(
      Arrays.asList(
        iconToImage(PlatformDiffImplIcons.Diff_frame32),
        iconToImage(PlatformDiffImplIcons.Diff_frame64),
        iconToImage(PlatformDiffImplIcons.Diff_frame128)
      )
    );
  });

  private static @Nullable Image iconToImage(@NotNull Icon icon) {
    return IconLoader.toImage(icon, null);
  }

  private static CharSequence getDocumentCharSequence(DocumentContent documentContent) {
    return ReadAction.compute(() -> {
      return documentContent.getDocument().getImmutableCharSequence();
    });
  }

  //
  // Editor
  //

  public static boolean isDiffEditor(@NotNull Editor editor) {
    return editor.getEditorKind() == EditorKind.DIFF;
  }

  public static boolean isFileWithoutContent(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWithoutContent) return true;
    return false;
  }

  public static @Nullable EditorHighlighter initEditorHighlighter(@Nullable Project project,
                                                                  @NotNull DocumentContent content,
                                                                  @NotNull CharSequence text) {
    EditorHighlighter highlighter = createEditorHighlighter(project, content);
    if (highlighter == null) return null;
    highlighter.setText(text);
    return highlighter;
  }

  public static @NotNull EditorHighlighter initEmptyEditorHighlighter(@NotNull CharSequence text) {
    EditorHighlighter highlighter = createEmptyEditorHighlighter();
    highlighter.setText(text);
    return highlighter;
  }

  public static @Nullable EditorHighlighter createEditorHighlighter(@Nullable Project project, @NotNull DocumentContent content) {
    EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();

    VirtualFile file = FileDocumentManager.getInstance().getFile(content.getDocument());
    FileType contentType = content.getContentType();
    VirtualFile highlightFile = content.getHighlightFile();
    Language language = content.getUserData(DiffUserDataKeys.LANGUAGE);
    boolean hasContentType = contentType != null &&
                             contentType != PlainTextFileType.INSTANCE &&
                             contentType != UnknownFileType.INSTANCE;

    if (language != null) {
      SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, highlightFile);
      return highlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme());
    }

    if (highlightFile != null && highlightFile.isValid()) {
      if (!hasContentType ||
          FileTypeRegistry.getInstance().isFileOfType(highlightFile, contentType) ||
          highlightFile instanceof LightVirtualFile) {
        return highlighterFactory.createEditorHighlighter(project, highlightFile);
      }
    }

    if (file != null && file.isValid()) {
      FileType type = file.getFileType();
      boolean hasFileType = !type.isBinary() && type != PlainTextFileType.INSTANCE;
      if (!hasContentType || hasFileType) {
        return highlighterFactory.createEditorHighlighter(project, file);
      }
    }

    if (contentType != null) {
      return highlighterFactory.createEditorHighlighter(project, contentType);
    }
    return null;
  }

  public static @NotNull EditorHighlighter createEmptyEditorHighlighter() {
    return new EmptyEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(HighlighterColors.TEXT));
  }

  public static void setEditorHighlighter(@Nullable Project project, @NotNull EditorEx editor, @NotNull DocumentContent content) {
    Disposable disposable = ((EditorImpl)editor).getDisposable();
    if (project != null) {
      DiffEditorHighlighterUpdater updater = new DiffEditorHighlighterUpdater(project, disposable, editor, content);
      updater.updateHighlighters();
    }
    else {
      ReadAction
        .nonBlocking(() -> {
          CharSequence text = editor.getDocument().getImmutableCharSequence();
          return initEditorHighlighter(null, content, text);
        })
        .finishOnUiThread(ModalityState.any(), result -> {
          if (result != null) editor.setHighlighter(result);
        })
        .expireWith(disposable)
        .submit(NonUrgentExecutor.getInstance());
    }
  }

  public static void setEditorCodeStyle(@Nullable Project project, @NotNull EditorEx editor, @Nullable DocumentContent content) {
    if (project != null && content != null && editor.getVirtualFile() == null) {
      PsiFile psiFile;
      try (AccessToken ignore = SlowOperations.knownIssue("IJPL-162978")) {
        psiFile = PsiDocumentManager.getInstance(project).getPsiFile(content.getDocument());
      }
      CommonCodeStyleSettings.IndentOptions indentOptions = psiFile != null
                                                            ? CodeStyle.getSettings(psiFile).getIndentOptionsByFile(psiFile)
                                                            : CodeStyle.getSettings(project).getIndentOptions(content.getContentType());
      editor.getSettings().setTabSize(indentOptions.TAB_SIZE);
      editor.getSettings().setUseTabCharacter(indentOptions.USE_TAB_CHARACTER);
    }

    Language language = content != null ? content.getUserData(DiffUserDataKeys.LANGUAGE) : null;
    if (language != null) {
      editor.getSettings().setLanguageSupplier(() -> language);
    }
    else if (editor.getProject() != null) {
      editor.getSettings().setLanguageSupplier(() -> TextEditorImpl.Companion.getDocumentLanguage(editor));
    }

    editor.getSettings().setCaretRowShown(false);
    editor.reinitSettings();
  }

  public static void setFoldingModelSupport(@NotNull EditorEx editor) {
    editor.getSettings().setFoldingOutlineShown(true);
    editor.getSettings().setAutoCodeFoldingEnabled(false);
    editor.getColorsScheme().setAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES, null);
  }

  public static @NotNull EditorEx createEditor(@NotNull Document document, @Nullable Project project, boolean isViewer) {
    return createEditor(document, project, isViewer, false);
  }

  public static @NotNull EditorEx createEditor(@NotNull Document document, @Nullable Project project, boolean isViewer, boolean enableFolding) {
    EditorFactory factory = EditorFactory.getInstance();
    EditorKind kind = EditorKind.DIFF;
    EditorEx editor = (EditorEx)(isViewer ? factory.createViewer(document, project, kind) : factory.createEditor(document, project, kind));

    editor.getSettings().setShowIntentionBulb(false);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
    editor.getGutterComponentEx().setShowDefaultGutterPopup(false);

    if (enableFolding) {
      setFoldingModelSupport(editor);
    }
    else {
      editor.getSettings().setFoldingOutlineShown(false);
      editor.getFoldingModel().setFoldingEnabled(false);
    }

    UIUtil.removeScrollBorder(editor.getComponent());

    return editor;
  }

  public static void configureEditor(@NotNull EditorEx editor, @NotNull DocumentContent content, @Nullable Project project) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(content.getDocument());
    if (virtualFile != null) {
      editor.setFile(virtualFile);
    }

    setEditorHighlighter(project, editor, content);
    setEditorCodeStyle(project, editor, content);
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
    if (OutsidersPsiFileSupport.isOutsiderFile(file)) return false;
    if (file.getUserData(TEMP_FILE_KEY) == Boolean.TRUE) return false;
    return true;
  }


  public static void installLineConvertor(@NotNull EditorEx editor, @NotNull FoldingModelSupport foldingSupport) {
    assert foldingSupport.getCount() == 1;
    IntPredicate foldingLinePredicate = foldingSupport.hideLineNumberPredicate(0);
    editor.getGutter().setLineNumberConverter(new DiffLineNumberConverter(foldingLinePredicate, null));
  }

  public static void installLineConvertor(@NotNull EditorEx editor, @NotNull DocumentContent content) {
    IntUnaryOperator contentLineConvertor = getContentLineConvertor(content);
    if (contentLineConvertor == null) {
      editor.getGutter().setLineNumberConverter(null);
    }
    else {
      editor.getGutter().setLineNumberConverter(new DiffLineNumberConverter(null, contentLineConvertor));
    }
  }

  public static void installLineConvertor(@NotNull EditorEx editor, @Nullable DocumentContent content,
                                          @NotNull FoldingModelSupport foldingSupport, int editorIndex) {
    IntUnaryOperator contentLineConvertor = content != null ? getContentLineConvertor(content) : null;
    IntPredicate foldingLinePredicate = foldingSupport.hideLineNumberPredicate(editorIndex);
    editor.getGutter().setLineNumberConverter(new DiffLineNumberConverter(foldingLinePredicate, contentLineConvertor));
  }

  public static @Nullable IntUnaryOperator getContentLineConvertor(@NotNull DocumentContent content) {
    return content.getUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR);
  }

  public static @Nullable IntUnaryOperator mergeLineConverters(@Nullable IntUnaryOperator convertor1, @Nullable IntUnaryOperator convertor2) {
    if (convertor1 == null && convertor2 == null) return null;
    if (convertor1 == null) return convertor2;
    if (convertor2 == null) return convertor1;
    return value -> {
      int value2 = convertor2.applyAsInt(value);
      return value2 >= 0 ? convertor1.applyAsInt(value2) : value2;
    };
  }

  //
  // Scrolling
  //

  public static void disableBlitting(@NotNull EditorEx editor) {
    if (Registry.is("diff.divider.repainting.disable.blitting")) {
      editor.getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
    }
  }

  public static void moveCaret(final @Nullable Editor editor, int line) {
    if (editor == null) return;
    editor.getSelectionModel().removeSelection();
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, 0));
  }

  public static void scrollEditor(final @Nullable Editor editor, int line, boolean animated) {
    scrollEditor(editor, line, 0, animated);
  }

  public static void scrollEditor(final @Nullable Editor editor, int line, int column, boolean animated) {
    if (editor == null) return;
    editor.getSelectionModel().removeSelection();
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

  public static @NotNull Point getScrollingPosition(@Nullable Editor editor) {
    if (editor == null) return new Point(0, 0);
    ScrollingModel model = editor.getScrollingModel();
    return new Point(model.getHorizontalScrollOffset(), model.getVerticalScrollOffset());
  }

  public static @NotNull LogicalPosition getCaretPosition(@Nullable Editor editor) {
    return editor != null ? editor.getCaretModel().getLogicalPosition() : new LogicalPosition(0, 0);
  }

  public static void moveCaretToLineRangeIfNeeded(@NotNull Editor editor, int startLine, int endLine) {
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    if (!isSelectedByLine(caretLine, startLine, endLine)) {
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(startLine, 0));
    }
  }

  //
  // Icons
  //

  public static @NotNull Icon getArrowIcon(@NotNull Side sourceSide) {
    return sourceSide.select(AllIcons.Diff.ArrowRight, AllIcons.Diff.Arrow);
  }

  public static @NotNull Icon getArrowDownIcon(@NotNull Side sourceSide) {
    return sourceSide.select(AllIcons.Diff.ArrowRightDown, AllIcons.Diff.ArrowLeftDown);
  }

  //
  // UI
  //

  public static boolean isFromShortcut(@NotNull AnActionEvent e) {
    String place = e.getPlace();
    return ActionPlaces.KEYBOARD_SHORTCUT.equals(place) || ActionPlaces.MOUSE_SHORTCUT.equals(place);
  }

  public static void registerAction(@NotNull AnAction action, @NotNull JComponent component) {
    action.registerCustomShortcutSet(action.getShortcutSet(), component);
  }

  /** @deprecated Avoid explicit synchronous group expansion! */
  @Deprecated(forRemoval = true)
  public static void recursiveRegisterShortcutSet(@NotNull ActionGroup group,
                                                  @NotNull JComponent component,
                                                  @Nullable Disposable parentDisposable) {
    AnAction[] actions =
      group instanceof DefaultActionGroup o ? o.getChildren(ActionManager.getInstance()) :
      group instanceof CustomisedActionGroup o && o.getDelegate() instanceof DefaultActionGroup oo ? oo.getChildren(ActionManager.getInstance()) :
      group.getChildren(null);
    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        recursiveRegisterShortcutSet((ActionGroup)action, component, parentDisposable);
      }
      action.registerCustomShortcutSet(component, parentDisposable);
    }
  }

  public static @NotNull JPanel createMessagePanel(@NotNull @Nls String message) {
    String text = StringUtil.replace(message, "\n", UIUtil.BR);
    JLabel label = new JBLabel(text) {
      @Override
      public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        size.width = Math.min(size.width, 200);
        size.height = Math.min(size.height, 100);
        return size;
      }
    }.setCopyable(true);

    return createMessagePanel(label);
  }

  public static @NotNull JPanel createMessagePanel(@NotNull JComponent label) {
    Color commentFg = JBColor.lazy(() -> {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      TextAttributes commentAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT);
      Color commentAttributesForegroundColor = commentAttributes.getForegroundColor();
      if (commentAttributesForegroundColor != null && commentAttributes.getBackgroundColor() == null) {
        return commentAttributesForegroundColor;
      }
      return scheme.getDefaultForeground();
    });
    label.setForeground(commentFg);

    JPanel panel = new JPanel(new SingleComponentCenteringLayout());
    panel.setBorder(JBUI.Borders.empty(5));
    panel.setBackground(JBColor.lazy(() -> EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()));
    panel.add(label);
    return panel;
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group, AnAction... actions) {
    addActionBlock(group, Arrays.asList(actions));
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group, @Nullable List<? extends AnAction> actions) {
    addActionBlock(group, actions, true);
  }

  public static void addActionBlock(@NotNull DefaultActionGroup group,
                                    @Nullable List<? extends AnAction> actions,
                                    boolean prependSeparator) {
    if (actions == null || actions.isEmpty()) return;

    if (prependSeparator) {
      group.addSeparator();
    }

    ActionManager actionManager = ActionManager.getInstance();
    AnAction[] children = group.getChildren(actionManager);
    for (AnAction action : actions) {
      if (action instanceof Separator ||
          !ArrayUtil.contains(action, children)) {
        group.add(action);
      }
    }
  }

  public static @Nls @NotNull String getSettingsConfigurablePath() {
    return SystemInfo.isMac ? DiffBundle.message("label.diff.settings.path.macos")
                            : DiffBundle.message("label.diff.settings.path");
  }

  public static @NlsContexts.Tooltip @NotNull String createTooltipText(@NotNull @NlsContexts.Tooltip String text, @Nullable @Nls String appendix) {
    HtmlBuilder result = new HtmlBuilder();
    result.append(text);
    if (appendix != null) {
      result.br();
      result.append(HtmlChunk.div("margin-top:5px; font-size:small").addText(appendix));
    }
    return result.wrapWithHtmlBody().toString();
  }

  public static @Nls @NotNull String createNotificationText(@NotNull @Nls String text, @Nullable @Nls String appendix) {
    HtmlBuilder result = new HtmlBuilder();
    result.append(text);
    if (appendix != null) {
      result.br();
      result.append(HtmlChunk.span("color:#" + ColorUtil.toHex(JBColor.gray) + "; font-size:small").addText(appendix));
    }
    return result.wrapWithHtmlBody().toString();
  }

  /**
   * RemDev-friendly UI listener
   * <p>
   * {@link UIUtil#markAsShowing} is not firing the events, so the components with delayed intitialization will not function correctly.
   */
  public static void runWhenFirstShown(@NotNull JComponent component, @NotNull Runnable runnable) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      runnable.run();
    }
    else {
      UiNotifyConnector.doWhenFirstShown(component, runnable);
    }
  }

  public static void installShowNotifyListener(@NotNull JComponent component, @NotNull Runnable runnable) {
    installShowNotifyListener(component, new Activatable() {
      @Override
      public void showNotify() {
        runnable.run();
      }
    });
  }

  /**
   * RemDev-friendly UI listener
   * <p>
   * {@link UIUtil#markAsShowing} is not firing the events, so the components with delayed intitialization will not function correctly.
   */
  public static void installShowNotifyListener(@NotNull JComponent component, @NotNull Activatable activatable) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      activatable.showNotify();
    }
    else {
      UiNotifyConnector.installOn(component, activatable, false);
    }
  }

  //
  // Titles
  //

  public static @NotNull List<JComponent> createSimpleTitles(@NotNull DiffViewer viewer, @NotNull ContentDiffRequest request) {
    List<DiffContent> contents = request.getContents();
    List<@Nls String> titles = request.getContentTitles();

    List<JComponent> components = new ArrayList<>(titles.size());
    List<DiffEditorTitleCustomizer> diffTitleCustomizers = request.getUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER);
    boolean needCreateTitle = !isUserDataFlagSet(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, request);
    for (int i = 0; i < contents.size(); i++) {
      DiffEditorTitleCustomizer customizer = diffTitleCustomizers != null ? diffTitleCustomizers.get(i) : null;
      JComponent title = needCreateTitle ? createTitle(viewer, titles.get(i), false, customizer) : null;
      title = createTitleWithNotifications(viewer, title, contents.get(i));
      components.add(title);
    }

    return components;
  }

  public static @NotNull List<JComponent> createTextTitles(@NotNull DiffViewer viewer,
                                                           @NotNull ContentDiffRequest request,
                                                           @NotNull List<? extends Editor> editors) {
    List<DiffContent> contents = request.getContents();
    List<@Nls String> titles = request.getContentTitles();

    boolean equalCharsets = TextDiffViewerUtil.areEqualCharsets(contents);
    boolean equalSeparators = TextDiffViewerUtil.areEqualLineSeparators(contents);

    List<JComponent> result = new ArrayList<>(contents.size());

    List<DiffEditorTitleCustomizer> diffTitleCustomizers = request.getUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER);
    boolean needCreateTitle = !isUserDataFlagSet(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, request);
    for (int i = 0; i < contents.size(); i++) {
      JComponent title = needCreateTitle ? createTitle(viewer,
                                                       titles.get(i),
                                                       contents.get(i),
                                                       equalCharsets,
                                                       equalSeparators,
                                                       editors.get(i),
                                                       diffTitleCustomizers != null ? diffTitleCustomizers.get(i) : null) : null;
      title = createTitleWithNotifications(viewer, title, contents.get(i));
      result.add(title);
    }

    return result;
  }

  public static @NotNull List<JComponent> createPatchTextTitles(@NotNull DiffViewer viewer,
                                                                @NotNull DiffRequest request,
                                                                @NotNull List<@Nls @Nullable String> titles) {
    List<JComponent> result = new ArrayList<>(titles.size());

    List<DiffEditorTitleCustomizer> diffTitleCustomizers = request.getUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER);
    boolean needCreateTitle = !isUserDataFlagSet(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, request);
    for (int i = 0; i < titles.size(); i++) {
      JComponent title = null;
      if (needCreateTitle) {
        String titleText = titles.get(i);
        DiffEditorTitleCustomizer customizer = diffTitleCustomizers != null ? diffTitleCustomizers.get(i) : null;
        title = createTitle(viewer, titleText, true, customizer);
      }

      title = createTitleWithNotifications(viewer, title, null);
      result.add(title);
    }

    return result;
  }

  private static @Nullable JComponent createTitleWithNotifications(@NotNull DiffViewer viewer,
                                                                   @Nullable JComponent title,
                                                                   @Nullable DiffContent content) {
    List<JComponent> components = new ArrayList<>();
    if (title != null) components.add(title);

    if (content != null) {
      components.addAll(createCustomNotifications(viewer, content));
    }

    if (content instanceof DocumentContent documentContent) {
      Document document = documentContent.getDocument();
      if (FileDocumentManager.getInstance().isPartialPreviewOfALargeFile(document)) {
        components.add(wrapEditorNotificationComponent(
          DiffNotifications.createNotification(DiffBundle.message("error.file.is.too.large.only.preview.is.loaded"))));
      }
    }

    if (content instanceof FileContent fileContent) {
      VirtualFile file = fileContent.getFile();
      if (file.isInLocalFileSystem() && !file.isValid()) {
        components.add(wrapEditorNotificationComponent(
          DiffNotifications.createNotification(DiffBundle.message("error.file.is.not.valid"))));
      }
    }

    if (components.isEmpty()) return null;
    return createStackedComponents(components, TITLE_GAP);
  }

  private static @Nullable JComponent createTitle(@NotNull DiffViewer viewer,
                                                  @Nullable @NlsContexts.Label String title,
                                                  @NotNull DiffContent content,
                                                  boolean equalCharsets,
                                                  boolean equalSeparators,
                                                  @Nullable Editor editor,
                                                  @Nullable DiffEditorTitleCustomizer titleCustomizer) {
    if (content instanceof EmptyContent) return null;
    DocumentContent documentContent = (DocumentContent)content;

    Charset charset = equalCharsets ? null : documentContent.getCharset();
    Boolean bom = equalCharsets ? null : documentContent.hasBom();
    LineSeparator separator = equalSeparators ? null : documentContent.getLineSeparator();
    boolean isReadOnly = editor == null || editor.isViewer() || !canMakeWritable(editor.getDocument());

    return createTitle(viewer, title, separator, charset, bom, isReadOnly, titleCustomizer);
  }

  public static @NotNull JComponent createTitle(@Nullable @NlsContexts.Label String title) {
    return createTitle(null, title, null, null, null, false, null);
  }

  public static @NotNull JComponent createTitle(@Nullable @NlsContexts.Label String title, @Nullable DiffEditorTitleCustomizer titleCustomizer) {
    return createTitle(null, title, null, null, null, false, titleCustomizer);
  }

  private static @NotNull JComponent createTitle(@NotNull DiffViewer viewer,
                                                 @Nullable @NlsContexts.Label String title,
                                                 boolean readOnly,
                                                 @Nullable DiffEditorTitleCustomizer titleCustomizer) {
    return createTitle(viewer, title, null, null, null, readOnly, titleCustomizer);
  }


  private static @NotNull JComponent createTitle(@Nullable DiffViewer viewer,
                                                 @Nullable @NlsContexts.Label String title,
                                                 @Nullable LineSeparator separator,
                                                 @Nullable Charset charset,
                                                 @Nullable Boolean bom,
                                                 boolean readOnly,
                                                 @Nullable DiffEditorTitleCustomizer titleCustomizer) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(0, 4));
    BorderLayoutPanel labelWithIcon = new BorderLayoutPanel();
    JComponent titleLabel = titleCustomizer != null ? titleCustomizer.getLabel()
                                                    : new JBLabel(StringUtil.notNullize(title)).setCopyable(true);
    if (titleCustomizer != null && titleLabel instanceof Disposable disposableTitleLabel) {
      if (viewer != null) {
        Disposer.register(viewer, disposableTitleLabel);
      } else {
        LOG.error("Viewer is not provided while the title label is disposable", new Throwable());
      }
    }
    if (titleLabel != null) {
      labelWithIcon.addToCenter(titleLabel);
    }
    if (readOnly) {
      labelWithIcon.addToLeft(new JBLabel(AllIcons.Ide.Readonly));
    }
    panel.add(labelWithIcon, BorderLayout.CENTER);
    if (charset != null || separator != null) {
      JPanel panel2 = new JPanel();
      panel2.setLayout(new BoxLayout(panel2, BoxLayout.X_AXIS));
      if (charset != null) {
        panel2.add(Box.createRigidArea(JBUI.size(4, 0)));
        panel2.add(createCharsetPanel(charset, bom));
      }
      if (separator != null) {
        panel2.add(Box.createRigidArea(JBUI.size(4, 0)));
        panel2.add(createSeparatorPanel(separator));
      }
      panel.add(panel2, BorderLayout.EAST);
    }
    return panel;
  }

  private static @NotNull JComponent createCharsetPanel(@NotNull Charset charset, @Nullable Boolean bom) {
    String text = charset.displayName();
    if (bom != null && bom) {
      text = DiffBundle.message("diff.utf.charset.name.bom.suffix", text);
    }

    JLabel label = new JLabel(text);
    // TODO: specific colors for other charsets
    if (charset.equals(StandardCharsets.UTF_8)) {
      label.setForeground(JBColor.BLUE);
    }
    else if (charset.equals(StandardCharsets.ISO_8859_1)) {
      label.setForeground(JBColor.RED);
    }
    else {
      label.setForeground(JBColor.BLACK);
    }
    return label;
  }

  private static @NotNull JComponent createSeparatorPanel(@NotNull LineSeparator separator) {
    JLabel label = new JLabel(separator.toString());
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

  public static @NotNull List<JComponent> createSyncHeightComponents(final @NotNull List<JComponent> components) {
    return SyncHeightComponent.createSyncHeightComponents(components);
  }

  public static @NotNull JComponent createStackedComponents(@NotNull List<? extends JComponent> components, @NotNull JBValue vGap) {
    JPanel panel = new JBPanel<>(new VerticalLayout(vGap, VerticalLayout.FILL));
    for (JComponent component : components) {
      panel.add(component);
    }
    return panel;
  }

  public static @Nls @NotNull String getStatusText(int totalCount, int excludedCount, @NotNull ThreeState isContentsEqual) {
    if (totalCount == 0 && isContentsEqual == ThreeState.NO) {
      return DiffBundle.message("diff.all.differences.ignored.text");
    }
    String message = DiffBundle.message("diff.count.differences.status.text", totalCount - excludedCount);
    if (excludedCount > 0) message += " " + DiffBundle.message("diff.inactive.count.differences.status.text", excludedCount);
    return message;
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

  public static boolean isFocusedComponentInWindow(@Nullable Component component) {
    if (component == null) return false;
    Window window = ComponentUtil.getWindow(component);
    if (window == null) return false;
    Component windowFocusOwner = window.getMostRecentFocusOwner();
    return windowFocusOwner != null && SwingUtilities.isDescendingFrom(windowFocusOwner, component);
  }

  public static void requestFocusInWindow(@Nullable Component component) {
    if (component != null) component.requestFocusInWindow();
  }

  public static void runPreservingFocus(@NotNull FocusableContext context, @NotNull Runnable task) {
    boolean hadFocus = context.isFocusedInWindow();
    // if (hadFocus) KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
    task.run();
    if (hadFocus) context.requestFocusInWindow();
  }

  //
  // Compare
  //

  public static @NotNull TwosideTextDiffProvider createTextDiffProvider(@Nullable Project project,
                                                                        @NotNull ContentDiffRequest request,
                                                                        @NotNull TextDiffSettings settings,
                                                                        @NotNull Runnable rediff,
                                                                        @NotNull Disposable disposable) {
    DiffUserDataKeysEx.DiffComputer diffComputer = request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER);
    if (diffComputer != null) return new SimpleTextDiffProvider(settings, rediff, disposable, diffComputer);

    return SmartTextDiffProvider.create(project, request, settings, rediff, disposable);
  }

  public static @NotNull TwosideTextDiffProvider.NoIgnore createNoIgnoreTextDiffProvider(@Nullable Project project,
                                                                                         @NotNull ContentDiffRequest request,
                                                                                         @NotNull TextDiffSettings settings,
                                                                                         @NotNull Runnable rediff,
                                                                                         @NotNull Disposable disposable) {
    DiffUserDataKeysEx.DiffComputer diffComputer = request.getUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER);
    if (diffComputer != null) return new SimpleTextDiffProvider.NoIgnore(settings, rediff, disposable, diffComputer);

    return SmartTextDiffProvider.createNoIgnore(project, request, settings, rediff, disposable);
  }

  public static List<DocumentContent> getDocumentContentsForViewer(@Nullable Project project,
                                                                   @NotNull List<byte[]> byteContents,
                                                                   @NotNull FilePath filePath,
                                                                   @Nullable ConflictType conflictType) {
    return getDocumentContentsForViewer(project, byteContents, conflictType, new DiffContentFactoryEx.ContextProvider() {
      @Override
      public void passContext(@NotNull DiffContentFactoryEx.DocumentContentBuilder builder) {
        builder.contextByFilePath(filePath);
      }
    });
  }

  public static List<DocumentContent> getDocumentContentsForViewer(@Nullable Project project,
                                                                   @NotNull List<byte[]> byteContents,
                                                                   @NotNull VirtualFile file,
                                                                   @Nullable ConflictType conflictType) {
    return getDocumentContentsForViewer(project, byteContents, conflictType, new DiffContentFactoryEx.ContextProvider() {
      @Override
      public void passContext(@NotNull DiffContentFactoryEx.DocumentContentBuilder builder) {
        builder.contextByHighlightFile(file);
      }
    });
  }

  private static List<DocumentContent> getDocumentContentsForViewer(@Nullable Project project,
                                                                    @NotNull List<byte[]> byteContents,
                                                                    @Nullable ConflictType conflictType,
                                                                    @NotNull DiffContentFactoryEx.ContextProvider contextProvider) {
    DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

    DocumentContent current = contentFactory.documentContent(project, true)
      .contextByProvider(contextProvider)
      .buildFromBytes(notNull(byteContents.get(0), EMPTY_BYTE_ARRAY));
    DocumentContent last = contentFactory.documentContent(project, true)
      .contextByProvider(contextProvider)
      .buildFromBytes(notNull(byteContents.get(2), EMPTY_BYTE_ARRAY));

    DocumentContent original;
    if (conflictType == ConflictType.ADDED_ADDED) {
      ProgressIndicator indicator = EmptyProgressIndicator.notNullize(ProgressManager.getInstance().getProgressIndicator());

      CharSequence currentContent = getDocumentCharSequence(current);
      CharSequence lastContent = getDocumentCharSequence(last);
      String newContent =
        ComparisonManager.getInstance().mergeLinesAdditions(currentContent, lastContent, ComparisonPolicy.IGNORE_WHITESPACES, indicator);
      original = contentFactory.documentContent(project, true)
        .contextByProvider(contextProvider)
        .buildFromText(newContent, false);
    }
    else {
      original = contentFactory.documentContent(project, true)
        .contextByProvider(contextProvider)
        .buildFromBytes(notNull(byteContents.get(1), EMPTY_BYTE_ARRAY));
    }
    return Arrays.asList(current, original, last);
  }

  public static @Nullable MergeInnerDifferences compareThreesideInner(@NotNull List<? extends CharSequence> chunks,
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
    return ComparisonUtil.isEqualTexts(chunk1, chunk2, comparisonPolicy);
  }

  public static <T> int @NotNull [] getSortedIndexes(@NotNull List<? extends T> values, @NotNull Comparator<? super T> comparator) {
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

  public static int @NotNull [] invertIndexes(int @NotNull [] indexes) {
    int[] inverted = new int[indexes.length];
    for (int i = 0; i < indexes.length; i++) {
      inverted[indexes[i]] = i;
    }
    return inverted;
  }

  public static boolean compareStreams(@NotNull ThrowableComputable<? extends InputStream, ? extends IOException> stream1,
                                       @NotNull ThrowableComputable<? extends InputStream, ? extends IOException> stream2)
    throws IOException {
    int i = 0;
    try (InputStream s1 = stream1.compute()) {
      try (InputStream s2 = stream2.compute()) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;

        while (true) {
          int b1 = s1.read();
          int b2 = s2.read();
          if (b1 != b2) return false;
          if (b1 == -1) return true;

          if (i++ % 10000 == 0) ProgressManager.checkCanceled();
        }
      }
    }
  }

  public static @NotNull InputStream getFileInputStream(@NotNull VirtualFile file) throws IOException {
    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof FileSystemInterface) {
      return ((FileSystemInterface)fs).getInputStream(file);
    }
    // can't use VirtualFile.getInputStream here, as it will strip BOM
    byte[] content = ReadAction.compute(() -> file.contentsToByteArray());
    return new ByteArrayInputStream(content);
  }

  //
  // Document modification
  //

  public static boolean isSomeRangeSelected(@NotNull Editor editor, @NotNull Condition<? super BitSet> condition) {
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    if (carets.size() != 1) return true;
    Caret caret = carets.get(0);
    if (caret.hasSelection()) return true;

    return condition.value(getSelectedLines(editor));
  }

  public static @NotNull BitSet getSelectedLines(@NotNull Editor editor) {
    Document document = editor.getDocument();
    int totalLines = getLineCount(document);
    BitSet lines = new BitSet(totalLines + 1);

    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      appendSelectedLines(editor, lines, caret);
    }

    return lines;
  }

  private static void appendSelectedLines(@NotNull Editor editor, @NotNull BitSet lines, @NotNull Caret caret) {
    Document document = editor.getDocument();
    int totalLines = getLineCount(document);

    if (caret.hasSelection()) {
      int line1 = editor.offsetToLogicalPosition(caret.getSelectionStart()).line;
      int line2 = editor.offsetToLogicalPosition(caret.getSelectionEnd()).line;
      lines.set(line1, line2 + 1);
      if (caret.getSelectionEnd() == document.getTextLength()) lines.set(totalLines);
    }
    else {
      int offset = caret.getOffset();
      VisualPosition visualPosition = caret.getVisualPosition();

      Pair<LogicalPosition, LogicalPosition> pair = EditorUtil.calcSurroundingRange(editor, visualPosition, visualPosition);
      lines.set(pair.first.line, Math.max(pair.second.line, pair.first.line + 1));
      if (offset == document.getTextLength()) lines.set(totalLines);
    }
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

  public static String applyModification(@NotNull CharSequence text,
                                         @NotNull LineOffsets lineOffsets,
                                         @NotNull CharSequence otherText,
                                         @NotNull LineOffsets otherLineOffsets,
                                         @NotNull List<? extends Range> ranges) {
    return new Object() {
      private final StringBuilder stringBuilder = new StringBuilder();
      private boolean isEmpty = true;

      public @NotNull String execute() {
        int lastLine = 0;

        for (Range range : ranges) {
          CharSequence newChunkContent = DiffRangeUtil.getLinesContent(otherText, otherLineOffsets, range.start2, range.end2);

          appendOriginal(lastLine, range.start1);
          append(newChunkContent, range.end2 - range.start2);

          lastLine = range.end1;
        }

        appendOriginal(lastLine, lineOffsets.getLineCount());

        return stringBuilder.toString();
      }

      private void appendOriginal(int start, int end) {
        append(DiffRangeUtil.getLinesContent(text, lineOffsets, start, end), end - start);
      }

      private void append(CharSequence content, int lineCount) {
        if (lineCount > 0 && !isEmpty) {
          stringBuilder.append('\n');
        }
        stringBuilder.append(content);
        isEmpty &= lineCount == 0;
      }
    }.execute();
  }

  public static void clearLineModificationFlags(@NotNull Document document, int startLine, int endLine) {
    if (document.getTextLength() == 0) return;  // empty document has no lines
    if (startLine == endLine) return;
    ((DocumentImpl)document).clearLineModificationFlags(startLine, endLine);
  }

  public static @NotNull CharSequence getLinesContent(@NotNull Document document, int line1, int line2) {
    return getLinesRange(document, line1, line2).subSequence(document.getImmutableCharSequence());
  }

  public static @NotNull CharSequence getLinesContent(@NotNull Document document, int line1, int line2, boolean includeNewLine) {
    return getLinesRange(document, line1, line2, includeNewLine).subSequence(document.getImmutableCharSequence());
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

  public static @NotNull TextRange getLinesRange(@NotNull Document document, int line1, int line2, boolean includeNewline) {
    return DiffRangeUtil.getLinesRange(LineOffsetsUtil.create(document), line1, line2, includeNewline);
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

  public static @NotNull List<String> getLines(@NotNull Document document) {
    return getLines(document, 0, getLineCount(document));
  }

  public static @NotNull List<String> getLines(@NotNull Document document, int startLine, int endLine) {
    return DiffRangeUtil.getLines(document.getCharsSequence(), LineOffsetsUtil.create(document), startLine, endLine);
  }

  public static int bound(int value, int lowerBound, int upperBound) {
    assert lowerBound <= upperBound : String.format("%s - [%s, %s]", value, lowerBound, upperBound);
    return MathUtil.clamp(value, lowerBound, upperBound);
  }

  //
  // Updating ranges on change
  //

  public static @NotNull LineRange getAffectedLineRange(@NotNull DocumentEvent e) {
    int line1 = e.getDocument().getLineNumber(e.getOffset());
    int line2 = e.getDocument().getLineNumber(e.getOffset() + e.getOldLength()) + 1;
    return new LineRange(line1, line2);
  }

  public static int countLinesShift(@NotNull DocumentEvent e) {
    return StringUtil.countNewLines(e.getNewFragment()) - StringUtil.countNewLines(e.getOldFragment());
  }

  public static @NotNull UpdatedLineRange updateRangeOnModification(int start, int end, int changeStart, int changeEnd, int shift) {
    return updateRangeOnModification(start, end, changeStart, changeEnd, shift, false);
  }

  public static @NotNull UpdatedLineRange updateRangeOnModification(int start, int end, int changeStart, int changeEnd, int shift, boolean greedy) {
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
      return greedy ? new UpdatedLineRange(changeStart, newChangeEnd, true)
                    : new UpdatedLineRange(newChangeEnd, newChangeEnd, true);
    }

    if (start < changeStart) { // bottom boundary damaged
      return greedy ? new UpdatedLineRange(start, newChangeEnd, true)
                    : new UpdatedLineRange(start, changeStart, true);
    }
    else { // top boundary damaged
      return greedy ? new UpdatedLineRange(changeStart, end + shift, true)
                    : new UpdatedLineRange(newChangeEnd, end + shift, true);
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

  public static @NotNull TextDiffType getLineDiffType(@NotNull LineFragment fragment) {
    boolean left = fragment.getStartLine1() != fragment.getEndLine1();
    boolean right = fragment.getStartLine2() != fragment.getEndLine2();
    return getDiffType(left, right);
  }

  public static @NotNull TextDiffType getDiffType(@NotNull DiffFragment fragment) {
    boolean left = fragment.getEndOffset1() != fragment.getStartOffset1();
    boolean right = fragment.getEndOffset2() != fragment.getStartOffset2();
    return getDiffType(left, right);
  }

  public static @NotNull TextDiffType getDiffType(@NotNull Range range) {
    boolean left = range.start1 != range.end1;
    boolean right = range.start2 != range.end2;
    return getDiffType(left, right);
  }

  public static @NotNull TextDiffType getDiffType(boolean hasDeleted, boolean hasInserted) {
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

  public static @NotNull TextDiffType getDiffType(@NotNull MergeConflictType conflictType) {
    return switch (conflictType.getType()) {
      case INSERTED -> TextDiffType.INSERTED;
      case DELETED -> TextDiffType.DELETED;
      case MODIFIED -> TextDiffType.MODIFIED;
      case CONFLICT -> TextDiffType.CONFLICT;
    };
  }

  //
  // Writable
  //

  @RequiresEdt
  public static boolean executeWriteCommand(@Nullable Project project,
                                            @NotNull Document document,
                                            @Nullable @NlsContexts.Command String commandName,
                                            @Nullable String commandGroupId,
                                            @NotNull UndoConfirmationPolicy confirmationPolicy,
                                            boolean underBulkUpdate,
                                            @NotNull Runnable task) {
    return executeWriteCommand(project, document, commandName, commandGroupId, confirmationPolicy, underBulkUpdate, true, task);
  }

  @RequiresEdt
  public static boolean executeWriteCommand(@Nullable Project project,
                                            @NotNull Document document,
                                            @Nullable @NlsContexts.Command String commandName,
                                            @Nullable String commandGroupId,
                                            @NotNull UndoConfirmationPolicy confirmationPolicy,
                                            boolean underBulkUpdate,
                                            boolean shouldRecordCommandForActiveDocument,
                                            @NotNull Runnable task) {
    if (!makeWritable(project, document)) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      String warning = "Document is read-only";
      if (file != null) {
        warning += ": " + file.getPresentableName();
        if (!file.isValid()) warning += " (invalid)";
      }
      LOG.warn(warning);
      return false;
    }

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> {
      if (underBulkUpdate) {
        DocumentUtil.executeInBulk(document, task);
      }
      else {
        task.run();
      }
    }, commandName, commandGroupId, confirmationPolicy, shouldRecordCommandForActiveDocument, document));
    return true;
  }

  @RequiresEdt
  public static boolean executeWriteCommand(final @NotNull Document document,
                                            final @Nullable Project project,
                                            final @Nullable @Nls String commandName,
                                            final @NotNull Runnable task) {
    return executeWriteCommand(project, document, commandName, null, UndoConfirmationPolicy.DEFAULT, false, task);
  }

  public static boolean isEditable(@NotNull Editor editor) {
    return !editor.isViewer() && canMakeWritable(editor.getDocument());
  }

  public static boolean canMakeWritable(@NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);

    if (file != null && file.isInLocalFileSystem() && !file.isValid()) {
      // Deleted files have writable Document, but are not writable.
      // See 'com.intellij.openapi.editor.impl.EditorImpl.processKeyTyped(char)'
      return false;
    }
    if (document.isWritable()) {
      return true;
    }

    if (file != null && file.isValid() && file.isInLocalFileSystem()) {
      if (file.getUserData(TEMP_FILE_KEY) == Boolean.TRUE) return false;
      // decompiled file can be writable, but Document with decompiled content is still read-only
      return !file.isWritable();
    }
    return false;
  }

  @RequiresEdt
  public static boolean makeWritable(@Nullable Project project, @NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return document.isWritable();
    if (!file.isValid()) return false;
    return makeWritable(project, file) && document.isWritable();
  }

  @RequiresEdt
  public static boolean makeWritable(@Nullable Project project, @NotNull VirtualFile file) {
    Project projectOrDefault = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    return ReadAction.compute(() ->
                                !ReadonlyStatusHandler.getInstance(projectOrDefault)
                                  .ensureFilesWritable(Collections.singletonList(file))
                                  .hasReadonlyFiles());
  }

  public static void putNonundoableOperation(@Nullable Project project, @NotNull Document document) {
    UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    if (undoManager != null) {
      DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
      undoManager.nonundoableActionPerformed(ref, false);
    }
  }

  public static void refreshOnFrameActivation(VirtualFile @NotNull ... files) {
    if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      markDirtyAndRefresh(true, false, false, files);
    }
  }

  /**
   * Difference with {@link VfsUtil#markDirtyAndRefresh} is that refresh from VfsUtil will be performed with ModalityState.NON_MODAL.
   */
  public static void markDirtyAndRefresh(boolean async, boolean recursive, boolean reloadChildren, VirtualFile @NotNull ... files) {
    if (files.length == 0) return;
    ModalityState modalityState = ApplicationManager.getApplication().getDefaultModalityState();
    VfsUtil.markDirty(recursive, reloadChildren, files);
    RefreshQueue.getInstance().refresh(async, recursive, null, modalityState, files);
  }

  //
  // Windows
  //

  public static @NotNull Dimension getDefaultDiffPanelSize() {
    return new Dimension(400, 200);
  }

  public static @NotNull Dimension getDefaultDiffWindowSize() {
    Rectangle screenBounds = ScreenUtil.getMainScreenBounds();
    int width = (int)(screenBounds.width * 0.8);
    int height = (int)(screenBounds.height * 0.8);
    return new Dimension(width, height);
  }

  public static @NotNull WindowWrapper.Mode getWindowMode(@NotNull DiffDialogHints hints) {
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
    if (window instanceof IdeFrameImpl || (modalOnly && canBeHiddenBehind(window))) {
      return false;
    }

    if (window instanceof DialogWrapperDialog) {
      ((DialogWrapperDialog)window).getDialogWrapper().doCancelAction();
      return !window.isVisible();
    }

    window.setVisible(false);
    window.dispose();
    return true;
  }

  /**
   * MacOS hack. Try to minimize the window while we are navigating to sources from the window diff in full screen mode.
   */
  public static void minimizeDiffIfOpenedInWindow(@NotNull Component diffComponent) {
    if (!SystemInfo.isMac) return;
    EditorWindowHolder holder = UIUtil.getParentOfType(EditorWindowHolder.class, diffComponent);
    if (holder == null) return;

    EditorWindow editorWindow = holder.getEditorWindow();
    List<EditorComposite> composites = editorWindow.getAllComposites();
    if (composites.size() != 1) return;

    Project project = editorWindow.getManager().getProject();
    VirtualFile file = composites.get(0).getFile();

    if (DiffEditorTabFilesManager.getInstance(project).isDiffOpenedInWindow(file)) {
      Window window = UIUtil.getWindow(diffComponent);
      if (window != null && !canBeHiddenBehind(window)) {
        if (window instanceof Frame) {
          ((Frame)window).setState(Frame.ICONIFIED);
        }
      }
    }
  }

  private static boolean canBeHiddenBehind(@NotNull Window window) {
    if (!(window instanceof Frame)) return false;
    if (SystemInfo.isMac) {
      if (window instanceof IdeFrame) {
        // we can't move focus to full-screen main frame, as it will be hidden behind other frame windows
        Project project = ((IdeFrame)window).getProject();
        IdeFrame projectFrame = WindowManager.getInstance().getIdeFrame(project);
        if (projectFrame != null) {
          JComponent projectFrameComponent = projectFrame.getComponent();
          if (projectFrameComponent != null) {
            return !projectFrame.isInFullScreen() ||
                   window.getGraphicsConfiguration().getDevice() != projectFrameComponent.getGraphicsConfiguration().getDevice();
          }
        }
      }
    }
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

  public static void addNotification(@Nullable DiffNotificationProvider provider, @NotNull UserDataHolder holder) {
    if (provider == null) return;
    List<DiffNotificationProvider> newProviders = new ArrayList<>(getNotificationProviders(holder));
    newProviders.add(provider);
    holder.putUserData(DiffUserDataKeys.NOTIFICATION_PROVIDERS, newProviders);
  }

  public static @NotNull List<JComponent> createCustomNotifications(@Nullable DiffViewer viewer,
                                                                    @NotNull UserDataHolder context,
                                                                    @NotNull UserDataHolder request) {
    List<DiffNotificationProvider> contextProviders = getNotificationProviders(context);
    List<DiffNotificationProvider> requestProviders = getNotificationProviders(request);
    return createNotifications(viewer, ContainerUtil.concat(contextProviders, requestProviders));
  }

  public static @NotNull List<JComponent> createCustomNotifications(@Nullable DiffViewer viewer,
                                                                    @NotNull DiffContent content) {
    List<DiffNotificationProvider> providers = getNotificationProviders(content);
    return createNotifications(viewer, providers);
  }

  private static @NotNull @Unmodifiable List<DiffNotificationProvider> getNotificationProviders(@NotNull UserDataHolder holder) {
    return ContainerUtil.notNullize(holder.getUserData(DiffUserDataKeys.NOTIFICATION_PROVIDERS));
  }

  private static @NotNull List<JComponent> createNotifications(@Nullable DiffViewer viewer,
                                                               @NotNull List<? extends DiffNotificationProvider> providers) {
    List<JComponent> notifications = ContainerUtil.mapNotNull(providers, it -> it.createNotification(viewer));
    return wrapEditorNotificationBorders(notifications);
  }

  public static @NotNull @Unmodifiable List<JComponent> wrapEditorNotificationBorders(@NotNull List<? extends JComponent> notifications) {
    return ContainerUtil.map(notifications, component -> wrapEditorNotificationComponent(component));
  }

  private static @NotNull JComponent wrapEditorNotificationComponent(JComponent component) {
    Border border = ClientProperty.get(component, FileEditorManager.SEPARATOR_BORDER);
    if (border == null) return component;

    Wrapper wrapper = new InvisibleWrapper();
    wrapper.setContent(component);
    wrapper.setBorder(border);
    return wrapper;
  }

  public static @NotNull <T extends DiffRequest> T addTitleCustomizers(@NotNull T request, @NotNull List<DiffEditorTitleCustomizer> customizers) {
    tryTitleCustomizersNumber(request, customizers.size());
    request.putUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER, customizers);
    return request;
  }

  public static @NotNull <T extends DiffRequest> T addTitleCustomizers(@NotNull T request, DiffEditorTitleCustomizer @NotNull... customizers) {
    tryTitleCustomizersNumber(request, customizers.length);
    request.putUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER, List.of(customizers));
    return request;
  }

  public static @NotNull <T extends MergeRequest> T addTitleCustomizers(@NotNull T request, @NotNull List<DiffEditorTitleCustomizer> customizers) {
    request.putUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER, customizers);
    return request;
  }

  private static void tryTitleCustomizersNumber(@NotNull DiffRequest request, int customizersNumber) {
    assert customizersNumber != 0;

    if (request instanceof ContentDiffRequest contentDiffRequest && contentDiffRequest.getContentTitles().size() != customizersNumber) {
      LOG.error("Expected " + contentDiffRequest.getContentTitles().size() + " titles, but got " + customizersNumber);
    }
  }

  //
  // DataProvider
  //

  public static <T> void putDataKey(@NotNull UserDataHolder holder, @NotNull DataKey<T> key, @Nullable T value) {
    DataProvider dataProvider = holder.getUserData(DiffUserDataKeys.DATA_PROVIDER);
    if (!(dataProvider instanceof GenericDataProvider)) {
      dataProvider = new GenericDataProvider(dataProvider);
      holder.putUserData(DiffUserDataKeys.DATA_PROVIDER, dataProvider);
    }
    ((GenericDataProvider)dataProvider).putData(key, value);
  }

  public static @NotNull DiffSettings getDiffSettings(@NotNull DiffContext context) {
    DiffSettings settings = context.getUserData(DiffSettings.KEY);
    if (settings == null) {
      settings = DiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE));
      context.putUserData(DiffSettings.KEY, settings);
    }
    return settings;
  }

  public static @NotNull <K, V> TreeMap<K, V> trimDefaultValues(@NotNull TreeMap<K, V> map, @NotNull Convertor<? super K, V> defaultValue) {
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

  public static @NotNull <T extends DiffTool> List<T> filterSuppressedTools(@NotNull List<T> tools) {
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

  public static @Nullable DiffTool findToolSubstitutor(@NotNull DiffTool tool, @NotNull DiffContext context, @NotNull DiffRequest request) {
    for (DiffToolSubstitutor substitutor : DiffToolSubstitutor.EP_NAME.getExtensions()) {
      DiffTool replacement = substitutor.getReplacement(tool, context, request);
      if (replacement == null) continue;

      boolean canShow = replacement.canShow(context, request);
      if (!canShow) {
        LOG.error("DiffTool substitutor returns invalid tool");
        continue;
      }

      return replacement;
    }
    return null;
  }
}
