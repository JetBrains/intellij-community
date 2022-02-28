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
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
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

    JComponent toolbarPanel = JBUI.Panels.simplePanel(toolbarComponent);
    Border outsideToolbarBorder = JBUI.Borders.customLine(getBorderColor(), 1, 1, isEditorVisible ? 0 : 1, 1);
    Border insideToolbarBorder = JBUI.Borders.empty(1, 5);
    toolbarPanel.setBorder(BorderFactory.createCompoundBorder(outsideToolbarBorder, insideToolbarBorder));

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
        public void hintShown(Project project, @NotNull LightweightHint newHint, int flags) {
          // Ex: if popup re-shown by ToggleByWordDiffAction
          if (newHint.getComponent() instanceof LineStatusMarkerPopupPanel) {
            LineStatusMarkerPopupPanel newPopupPanel = (LineStatusMarkerPopupPanel)newHint.getComponent();
            if (newPopupPanel.getEditor().equals(editor)) {
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

    DataManager.registerDataProvider(field, data -> {
      if (CommonDataKeys.HOST_EDITOR.is(data)) {
        return field.getEditor();
      }
      return null;
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
    return EditorFragmentComponent.getBackgroundColor(editor, true);
  }

  @NotNull
  public static Color getBorderColor() {
    return new JBColor(Gray._206, Gray._75);
  }

  @NotNull
  public static ActionToolbar buildToolbar(@NotNull Editor editor,
                                           @NotNull List<AnAction> actions,
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
                                                         int currentStartOffset,
                                                         @NotNull List<? extends DiffFragment> wordDiff,
                                                         @NotNull Disposable parentDisposable) {
    final List<RangeHighlighter> highlighters = new ArrayList<>();
    for (DiffFragment fragment : wordDiff) {
      int currentStart = currentStartOffset + fragment.getStartOffset2();
      int currentEnd = currentStartOffset + fragment.getEndOffset2();
      TextDiffType type = getDiffType(fragment);

      highlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, currentStart, currentEnd, type));
    }

    Disposer.register(parentDisposable, () -> highlighters.forEach(RangeMarker::dispose));
  }
}
