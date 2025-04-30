package com.intellij.database.run.ui.grid.editors;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.GridEditGuard;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory.ValueFormatterResult;
import com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextCompletionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EventObject;

public class GridCellEditorTextField extends EditorTextField implements Disposable {
  private final DataGrid myGrid;

  public GridCellEditorTextField(Project project,
                                 @NotNull DataGrid grid,
                                 @NotNull ModelIndex<GridRow> row,
                                 @NotNull ModelIndex<GridColumn> column,
                                 boolean multiline,
                                 EventObject initiator,
                                 TextCompletionProvider provider,
                                 boolean autoPopup,
                                 @NotNull GridCellEditorFactory.ValueFormatter valueFormatter) {
    // Not passing multiline flag allows to reuse oneLineMode's initialization logic.
    // The editor is turned into a multiline editor via SettingsProvider, if needed.
    // We also set "JBListTable.isTableCellEditor" to Boolean.TRUE: see EditorTextField.updateBorder()
    super(createDocument(DefaultTextRendererFactory.getLanguage(grid, row, column)), project, FileTypes.PLAIN_TEXT);
    boolean clear = initiator instanceof KeyEvent && grid.isEditable();
    if (!clear) setText(valueFormatter, grid, row, column);
    putClientProperty("JBListTable.isTableCellEditor", Boolean.TRUE);
    myGrid = grid;
    installEditorSettingsProvider(multiline);
    installCompletion(project, getDocument(), provider, autoPopup);
    myGrid.addDataGridListener(new DataGridListener() {
      @Override
      public void onCellLanguageChanged(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) {
        if (!columnIdx.equals(column)) return;
        LightVirtualFile file = ObjectUtils.tryCast(FileDocumentManager.getInstance().getFile(getDocument()), LightVirtualFile.class);
        if (file == null) return;
        file.setLanguage(language);
        EditorEx editor = ObjectUtils.tryCast(getEditor(), EditorEx.class);
        if (editor != null) editor.setHighlighter(HighlighterFactory.createHighlighter(project, file));
        FileContentUtilCore.reparseFiles(file);
      }
    }, this);
  }

  public void setText(@NotNull GridCellEditorFactory.ValueFormatter valueFormatter,
                      @NotNull DataGrid grid,
                      @NotNull ModelIndex<GridRow> row,
                      @NotNull ModelIndex<GridColumn> column) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ValueFormatterResult result = valueFormatter.format();
      VirtualFile file = FileDocumentManager.getInstance().getFile(getDocument());
      if (file != null) {
        file.setCharset(result.charset);
        file.setBOM(result.bom);
      }
      setText(result.text);
      PsiCodeFragment fragment = GridHelper.get(grid).createCellCodeFragment(getDocument().getText(), getProject(), grid, row, column);
      if (fragment != null) {
        GridUtilCore.associatePsiSafe(getDocument(), fragment);
      }
    });
  }

  @Override
  protected boolean shouldHaveBorder() {
    return false;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    Editor editor = getEditor();
    if (editor instanceof EditorEx && !editor.isOneLineMode()) {
      JScrollPane scrollPane = ((EditorEx)editor).getScrollPane();
      JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
      if (verticalScrollBar != null) {
        size.width += verticalScrollBar.getWidth();
      }
      JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
      if (horizontalScrollBar != null) {
        size.height += horizontalScrollBar.getHeight();
      }
    }
    return size;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(CommonDataKeys.VIRTUAL_FILE, FileDocumentManager.getInstance().getFile(getDocument()));
    Editor editor = getEditor();
    sink.set(CommonDataKeys.EDITOR, editor);
    sink.set(CommonDataKeys.HOST_EDITOR, editor);
  }

  @Override
  public void dispose() {
  }

  protected boolean isEditable() {
    return myGrid.isEditable();
  }

  private void registerEnterAction(@NotNull Editor editor, final boolean multiline) {
    CustomShortcutSet enterAndControlEnter = new CustomShortcutSet(
      KeyboardShortcut.fromString("ENTER"),
      KeyboardShortcut.fromString("control ENTER")
    );
    AnAction action = new DumbAwareAction("insertNewLineOrStopEditing1") {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(LookupManager.getActiveLookup(getEditor()) == null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        InputEvent inputEvent = e.getInputEvent();
        boolean isCtrlEnter = inputEvent instanceof KeyEvent && inputEvent.isControlDown();
        if (multiline && isCtrlEnter) {
          performEditorEnter(e);
        }
        else {
          myGrid.stopEditing();
        }
      }

      private static void performEditorEnter(@NotNull AnActionEvent e) {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ENTER);
        if (action == null) return;
        ActionUtil.performAction(action, e);
      }
    };
    registerAction(editor, action, enterAndControlEnter);
  }

  private void registerTabAction(final @NotNull EditorEx editor) {
    CustomShortcutSet tabAndShiftTab = new CustomShortcutSet(KeyboardShortcut.fromString("TAB"), KeyboardShortcut.fromString("shift TAB"));
    AnAction action = new DumbAwareAction("insertTabOrStopEditing") {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
      @Override
      public void update(@NotNull AnActionEvent e) {
        KeyEvent keyEvent = ObjectUtils.tryCast(e.getInputEvent(), KeyEvent.class);
        e.getPresentation().setEnabledAndVisible(keyEvent != null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        JComponent gridComponent = myGrid.getPreferredFocusedComponent();

        KeyEvent keyEvent = (KeyEvent)e.getInputEvent();
        KeyStroke stroke = KeyStroke.getKeyStroke(keyEvent.getKeyCode(), keyEvent.getModifiers());
        Object actionKey = gridComponent.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
        Action tabAction = gridComponent.getActionMap().get(actionKey);
        if (tabAction != null) {
          tabAction.actionPerformed(new ActionEvent(gridComponent, keyEvent.getID(), keyEvent.toString(), keyEvent.getWhen(), keyEvent.getModifiers()));
        }
      }
    };
    registerAction(editor, action, tabAndShiftTab);
  }

  private void registerArrowAction(final @NotNull EditorEx editor) {
    KeyboardShortcut up = KeyboardShortcut.fromString("UP");
    KeyboardShortcut down = KeyboardShortcut.fromString("DOWN");
    AnAction action = new DumbAwareAction("goToPreviousOrNextLineOrStopEditing") {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        if (editor.getDocument().getLineCount() > 1) {
          e.getPresentation().setEnabledAndVisible(false);
          return;
        }
        KeyEvent keyEvent = ObjectUtils.tryCast(e.getInputEvent(), KeyEvent.class);
        e.getPresentation().setEnabledAndVisible(keyEvent != null && LookupManager.getActiveLookup(getEditor()) == null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        KeyEvent keyEvent = (KeyEvent)e.getInputEvent();
        JComponent gridComponent = myGrid.getPreferredFocusedComponent();
        ActionMap actionMap = gridComponent.getActionMap();
        Action action = up.getFirstKeyStroke().getKeyCode() == keyEvent.getKeyCode()
                        ? actionMap.get("selectPreviousRow")
                        : actionMap.get("selectNextRow");
        if (action == null) return;
        action.actionPerformed(new ActionEvent(gridComponent, keyEvent.getID(), keyEvent.toString(), keyEvent.getWhen(), keyEvent.getModifiers()));
      }
    };
    registerAction(editor, action, new CustomShortcutSet(up, down));
  }

  private static void registerAction(@NotNull Editor editor, @NotNull AnAction action, @NotNull ShortcutSet shortcutSet) {
    action.registerCustomShortcutSet(shortcutSet, editor.getComponent());
  }

  private void installEditorSettingsProvider(final boolean multiline) {
    addSettingsProvider(editor -> {
      editor.setRendererMode(!isEditable());
      EditorColorsScheme scheme = editor.createBoundColorSchemeDelegate(myGrid.getColorsScheme());
      if (UISettings.getInstance().getPresentationMode()) scheme.setEditorFontSize(UISettingsUtils.getInstance().getPresentationModeFontSize());
      editor.setColorsScheme(scheme);
      editor.setBackgroundColor(scheme.getDefaultBackground());
      editor.setOneLineMode(false);
      editor.setVerticalScrollbarVisible(multiline);
      editor.setHorizontalScrollbarVisible(multiline);
      editor.getSettings().setAdditionalColumnsCount(2);
      editor.getCaretModel().moveToOffset(0);

      if (isEditable() && (!multiline || getDocument().getLineCount() == 1)) {
        editor.getSelectionModel().setSelection(0, getDocument().getTextLength());
      }
      putReadOnlyText(editor);
      editor.installPopupHandler(new ContextMenuPopupHandler.Simple("Console.TableResult.CellEditor.Popup"));

      registerEnterAction(editor, multiline);
      registerTabAction(editor);
      if (multiline) registerArrowAction(editor);
    });
  }

  private void putReadOnlyText(@NotNull EditorEx editor) {
    GridEditGuard guard = GridEditGuard.get(myGrid);
    if (guard == null) return;
    EditorModificationUtil.setReadOnlyHint(editor, guard.getReasonText(myGrid));
  }

  private static @NotNull Document createDocument(@NotNull Language language) {
    Language lang = language == Language.ANY ? PlainTextLanguage.INSTANCE : language;
    VirtualFile file = new LightVirtualFile("GridCellEditorTextField", lang, "");
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    return doc != null ? doc : EditorFactory.getInstance().createDocument("");
  }

  private static void installCompletion(@NotNull Project project,
                                        @Nullable Document document,
                                        @Nullable TextCompletionProvider provider,
                                        boolean autoPopup) {
    if (document == null || provider == null) return;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) TextCompletionUtil.installProvider(psiFile, provider, autoPopup);
  }
}
