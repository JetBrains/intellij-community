// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.*;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffType;

public class LineStatusMarkerPopupPanel extends JPanel {
  private static final JBColor TOOLBAR_BACKGROUND_COLOR =
    JBColor.namedColor("VersionControl.MarkerPopup.Toolbar.background", UIUtil.getPanelBackground());

  @Nullable private final JComponent myEditorComponent;
  @NotNull private final Editor myEditor;

  private LineStatusMarkerPopupPanel(@NotNull Editor editor,
                                     @NotNull ActionToolbar toolbar,
                                     @Nullable JComponent editorComponent,
                                     @Nullable JComponent additionalInfo) {
    super(new BorderLayout());
    setOpaque(false);

    myEditor = editor;
    myEditorComponent = editorComponent;
    boolean isEditorVisible = myEditorComponent != null;

    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(null);
    toolbarComponent.setBackground(TOOLBAR_BACKGROUND_COLOR);

    JComponent toolbarPanel = JBUI.Panels.simplePanel(toolbarComponent);
    Border outsideToolbarBorder = JBUI.Borders.customLine(getBorderColor(), 1, 1, isEditorVisible ? 0 : 1, 1);
    JBInsets insets = JBUI.insets("VersionControl.MarkerPopup.borderInsets", JBInsets.create(1, 5));
    Border insideToolbarBorder = JBUI.Borders.empty(insets);
    toolbarPanel.setBorder(BorderFactory.createCompoundBorder(outsideToolbarBorder, insideToolbarBorder));
    toolbarPanel.setBackground(TOOLBAR_BACKGROUND_COLOR);

    if (additionalInfo != null) {
      toolbarPanel.add(additionalInfo, BorderLayout.EAST);
    }

    // 'empty space' to the right of toolbar
    JPanel emptyPanel = new JPanel();
    emptyPanel.setOpaque(false);
    emptyPanel.setPreferredSize(new Dimension());

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(toolbarPanel, BorderLayout.WEST);
    topPanel.add(emptyPanel, BorderLayout.CENTER);

    add(topPanel, BorderLayout.NORTH);
    if (myEditorComponent != null) add(myEditorComponent, BorderLayout.CENTER);

    // transfer clicks into editor
    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        transferEvent(e, editor);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        transferEvent(e, editor);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        transferEvent(e, editor);
      }
    };
    emptyPanel.addMouseListener(listener);
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  private static void transferEvent(MouseEvent e, Editor editor) {
    editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
  }

  int getEditorTextOffset() {
    return EditorFragmentComponent.createEditorFragmentBorder(myEditor).getBorderInsets(myEditorComponent).left;
  }

  @Override
  public Dimension getPreferredSize() {
    int gap = JBUI.scale(10);
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(myEditor.getComponent());
    Rectangle maxSize = new Rectangle(screenRectangle.width - gap, screenRectangle.height - gap);

    Dimension size = super.getPreferredSize();
    if (size.width > maxSize.width) {
      size.width = maxSize.width;
      // Space for horizontal scrollbar
      size.height += JBUI.scale(20);
    }
    if (size.height > maxSize.height) {
      size.height = maxSize.height;
    }
    return size;
  }


  public static void showPopupAt(@NotNull Editor editor,
                                 @NotNull ActionToolbar toolbar,
                                 @Nullable JComponent editorComponent,
                                 @Nullable JComponent additionalInfoPanel,
                                 @Nullable Point mousePosition,
                                 @NotNull Disposable childDisposable,
                                 @Nullable DataProvider dataProvider) {
    LineStatusMarkerPopupPanel popupPanel = new LineStatusMarkerPopupPanel(editor, toolbar, editorComponent, additionalInfoPanel);

    if (dataProvider != null) DataManager.registerDataProvider(popupPanel, dataProvider);
    toolbar.setTargetComponent(popupPanel);

    LightweightHint hint = new LightweightHint(popupPanel);
    HintListener closeListener = __ -> Disposer.dispose(childDisposable);
    hint.addHintListener(closeListener);

    int line = editor.getCaretModel().getLogicalPosition().line;
    Point point = HintManagerImpl.getHintPosition(hint, editor, new LogicalPosition(line, 0), HintManager.UNDER);
    if (mousePosition != null) { // show right after the nearest line
      int lineHeight = editor.getLineHeight();
      int delta = (point.y - mousePosition.y) % lineHeight;
      if (delta < 0) delta += lineHeight;
      point.y = mousePosition.y + delta;
    }
    point.x -= popupPanel.getEditorTextOffset(); // align main editor with the one in popup

    int flags = HintManager.HIDE_BY_CARET_MOVE | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, flags, -1, false, new HintHint(editor, point));

    ApplicationManager.getApplication().getMessageBus().connect(childDisposable)
      .subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
        @Override
        public void hintShown(@NotNull Editor newEditor, @NotNull LightweightHint newHint, int flags, @NotNull HintHint hintInfo) {
          // Ex: if popup re-shown by ToggleByWordDiffAction
          if (newHint.getComponent() instanceof LineStatusMarkerPopupPanel) {
            LineStatusMarkerPopupPanel newPopupPanel = (LineStatusMarkerPopupPanel)newHint.getComponent();
            if (newPopupPanel.getEditor().equals(newEditor)) {
              hint.hide();
            }
          }
        }
      });

    if (!hint.isVisible()) {
      closeListener.hintHidden(new EventObject(hint));
    }
  }

  @NotNull
  public static EditorTextField createTextField(@NotNull Editor editor, @NotNull String content) {
    EditorTextField field = new EditorTextField(content);
    field.setBorder(null);
    field.setOneLineMode(false);
    field.ensureWillComputePreferredSize();
    field.setFontInheritedFromLAF(false);

    field.addSettingsProvider(uEditor -> {
      uEditor.setVerticalScrollbarVisible(true);
      uEditor.setHorizontalScrollbarVisible(true);

      uEditor.setRendererMode(true);
      uEditor.setBorder(null);

      uEditor.setColorsScheme(editor.getColorsScheme());
      uEditor.setBackgroundColor(getEditorBackgroundColor(editor));
      uEditor.getSettings().setCaretRowShown(false);

      uEditor.getSettings().setTabSize(editor.getSettings().getTabSize(editor.getProject()));
      uEditor.getSettings().setUseTabCharacter(editor.getSettings().isUseTabCharacter(editor.getProject()));
    });

    return field;
  }

  @NotNull
  public static JComponent createEditorComponent(@NotNull Editor editor, @NotNull EditorTextField textField) {
    JPanel editorComponent = JBUI.Panels.simplePanel(textField);
    editorComponent.setBorder(createEditorFragmentBorder());
    editorComponent.setBackground(getEditorBackgroundColor(editor));
    return editorComponent;
  }

  @NotNull
  public static Border createEditorFragmentBorder() {
    Border outsideEditorBorder = JBUI.Borders.customLine(getBorderColor(), 1);
    Border insideEditorBorder = JBUI.Borders.empty(2);
    return BorderFactory.createCompoundBorder(outsideEditorBorder, insideEditorBorder);
  }

  public static Color getEditorBackgroundColor(@NotNull Editor editor) {
    Color color = editor.getColorsScheme().getColor(EditorColors.CHANGED_LINES_POPUP);
    return color != null ? color : EditorFragmentComponent.getBackgroundColor(editor);
  }

  @NotNull
  public static Color getBorderColor() {
    return JBColor.namedColor("VersionControl.MarkerPopup.borderColor", new JBColor(Gray._206, Gray._75));
  }

  @NotNull
  public static ActionToolbar buildToolbar(@NotNull Editor editor,
                                           @NotNull List<? extends AnAction> actions,
                                           @NotNull Disposable parentDisposable) {
    JComponent editorComponent = editor.getComponent();
    for (AnAction action : actions) {
      DiffUtil.registerAction(action, editorComponent);
    }

    Disposer.register(parentDisposable, () -> ActionUtil.getActions(editorComponent).removeAll(actions));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, new DefaultActionGroup(actions), true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    return toolbar;
  }

  public static void installBaseEditorSyntaxHighlighters(@Nullable Project project,
                                                         @NotNull EditorTextField textField,
                                                         @NotNull Document vcsDocument,
                                                         TextRange vcsTextRange,
                                                         @NotNull FileType fileType) {
    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType);
    highlighter.setText(vcsDocument.getImmutableCharSequence());
    FragmentedEditorHighlighter fragmentedHighlighter = new FragmentedEditorHighlighter(highlighter, vcsTextRange);
    textField.addSettingsProvider(uEditor -> uEditor.setHighlighter(fragmentedHighlighter));
  }

  public static void installPopupEditorWordHighlighters(@NotNull EditorTextField textField,
                                                        @Nullable List<? extends DiffFragment> wordDiff) {
    if (wordDiff == null) return;
    textField.addSettingsProvider(uEditor -> {
      for (DiffFragment fragment : wordDiff) {
        int vcsStart = fragment.getStartOffset1();
        int vcsEnd = fragment.getEndOffset1();
        TextDiffType type = getDiffType(fragment);

        DiffDrawUtil.createInlineHighlighter(uEditor, vcsStart, vcsEnd, type);
      }
    });
  }

  public static void installMasterEditorWordHighlighters(@NotNull Editor editor,
                                                         int startLine,
                                                         int endLine,
                                                         @NotNull List<? extends DiffFragment> wordDiff,
                                                         @NotNull Disposable parentDisposable) {
    TextRange currentTextRange = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine);

    DiffDrawUtil.setupLayeredRendering(editor, startLine, endLine,
                                       DiffDrawUtil.LAYER_PRIORITY_LST, parentDisposable);

    int currentStartOffset = currentTextRange.getStartOffset();
    List<RangeHighlighter> highlighters = new ArrayList<>();

    highlighters.addAll(
      new DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, TextDiffType.MODIFIED)
        .withLayerPriority(DiffDrawUtil.LAYER_PRIORITY_LST)
        .withIgnored(true)
        .withHideStripeMarkers(true)
        .withHideGutterMarkers(true)
        .done());

    for (DiffFragment fragment : wordDiff) {
      int currentStart = currentStartOffset + fragment.getStartOffset2();
      int currentEnd = currentStartOffset + fragment.getEndOffset2();
      TextDiffType type = getDiffType(fragment);

      highlighters.addAll(new DiffDrawUtil.InlineHighlighterBuilder(editor, currentStart, currentEnd, type)
                            .withLayerPriority(DiffDrawUtil.LAYER_PRIORITY_LST)
                            .done());
    }

    Disposer.register(parentDisposable, () -> highlighters.forEach(RangeMarker::dispose));
  }
}
