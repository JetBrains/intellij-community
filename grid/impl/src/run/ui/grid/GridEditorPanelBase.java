package com.intellij.database.run.ui.grid;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.database.DataGridBundle;
import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.datagrid.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.database.datagrid.GridUtil.getDataGrid;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD;
import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * @author Liudmila Kornilova
 **/
public abstract class GridEditorPanelBase extends JPanel
  implements GridEditorPanel, EditorColorsListener, UiDataProvider {

  protected final Project myProject;
  protected final DataGrid myGrid;
  protected final EditorEx myEditor;

  private String myPrefix;
  private String myDefaultText;

  GridEditorPanelBase(@NotNull Project project, @NotNull DataGrid grid, @NotNull String prefix, @NotNull String defaultText, @NotNull Document document) {
    setLayout(new BorderLayout());
    myProject = project;
    myGrid = grid;
    myEditor = createEditor(document);
    myPrefix = prefix;
    myDefaultText = defaultText;
    updateEditorPrefix();
    Disposer.register(myGrid, () -> {
      EditorFactory.getInstance().releaseEditor(myEditor);
    });

    GridHelper.get(myGrid).updateFilterSortPSI(myGrid);

    JBLabel clearFieldLabel = new JBLabel(AllIcons.Actions.Close);
    clearFieldLabel.setOpaque(false);
    clearFieldLabel.setVisible(document.getTextLength() != 0);
    clearFieldLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        clearText();
      }
    });
    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        int oldLength = event.getOldLength();
        int newLength = event.getNewLength();
        if (oldLength == 0 || newLength == 0 ||
            oldLength == myDefaultText.length() || newLength == myDefaultText.length()) {
          clearFieldLabel.setVisible(event.getDocument().getTextLength() != 0);
          updateEditorPrefix();
        }
      }

      @Override
      public void bulkUpdateFinished(@NotNull Document document) {
        clearFieldLabel.setVisible(document.getTextLength() != 0);
      }
    }, myGrid);

    add(clearFieldLabel, BorderLayout.EAST);
    add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  protected void updatePrefix(@NotNull String prefix, @NotNull String defaultText) {
    myPrefix = prefix;
    myDefaultText = defaultText;
    updateEditorPrefix();
  }

  @Override
  public @NotNull String getText() {
    return myEditor.getDocument().getText();
  }

  abstract void clearText();

  protected void setHighlighter() {
    GridHelper.get(myGrid).setFilterSortHighlighter(myGrid, myEditor);
  }

  private @NotNull EditorEx createEditor(@NotNull Document document) {
    EditorEx editor = (EditorEx)EditorFactory.getInstance().createEditor(document, myProject);
    editor.putUserData(EditorTextField.SUPPLEMENTARY_KEY, true);
    editor.setOneLineMode(true);
    editor.getSettings().setCaretRowShown(false);
    editor.getSettings().setShowIntentionBulb(false);
    EditorTextField.setupTextFieldEditor(editor);
    editor.getComponent().setFocusable(false);
    editor.getComponent().setOpaque(false);
    editor.getComponent().setBorder(JBUI.Borders.empty(0, 7));
    setScheme(editor, myGrid.getEditorColorsScheme());

    editor.setEmbeddedIntoDialogWrapper(true);
    editor.getContentComponent().setOpaque(false);

    return editor;
  }

  public void onError(@NotNull ErrorInfo errorInfo) {
    JComponent component = myEditor.getContentComponent();
    GridUtil.showErrorBalloon(errorInfo, component, getFilterEditorCaretPoint(myEditor));
  }

  @Override
  protected Graphics getComponentGraphics(Graphics g) {
    return IdeBackgroundUtil.withEditorBackground(g, this);
  }

  @Override
  public Color getBackground() {
    return myEditor == null ? super.getBackground() :
           myEditor.getBackgroundColor();
  }

  @Override
  public void requestFocus() {
    getGlobalInstance().doWhenFocusSettlesDown(
      () -> getGlobalInstance().requestFocus(myEditor.getComponent(), true));
  }

  @Override
  public boolean requestFocusInWindow() {
    boolean b = myEditor.getContentComponent().requestFocusInWindow();
    if (!myEditor.isDisposed()) {
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    return b;
  }

  @Override
  public boolean handleError(@NotNull GridRequestSource source, @NotNull ErrorInfo errorInfo) {
    GridEditorPanelRequestPlace place = ObjectUtils.tryCast(source.place, GridEditorPanelRequestPlace.class);
    if (place == null || place.myPanel != this) return false;
    onError(errorInfo);
    return true;
  }

  protected static @NotNull Point getFilterEditorCaretPoint(@NotNull EditorEx editor) {
    VisualPosition caretPosition = editor.getCaretModel().getVisualPosition();
    Point point = editor.visualPositionToXY(caretPosition);
    point.translate(0, editor.getContentComponent().getHeight());
    return point;
  }

  private void updateEditorPrefix() {
    EditorColorsScheme scheme = myEditor.getColorsScheme();
    TextAttributes attributes = scheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT).clone();
    TextAttributes keywordAttributes = scheme.getAttributes(KEYWORD).clone();
    DocumentEx document = myEditor.getDocument();
    if (document.getTextLength() == 0 || myDefaultText.equals(document.getText()) || isComment(document.getText())) {
      keywordAttributes.setForegroundColor(attributes.getForegroundColor());
    }
    myEditor.setPrefixTextAndAttributes(myPrefix + " ", keywordAttributes);
  }

  protected static boolean isComment(@NotNull String text) {
    return text.startsWith("--") || text.startsWith("#") ||
           text.startsWith("//") || text.startsWith("/*");
  }

  private static void setScheme(@NotNull EditorEx editor, @NotNull EditorColorsScheme scheme) {
    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(scheme));
    editor.setBackgroundColor(editor.getBackgroundColor()); // update scroll pane bg
  }

  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    setScheme(myEditor, myGrid.getEditorColorsScheme());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    // providing a file editor enables undo/redo actions (DBE-1757)
    sink.set(PlatformCoreDataKeys.FILE_EDITOR, TextEditorProvider.getInstance().getTextEditor(myEditor));
  }

  static @NotNull CustomShortcutSet getShowHistoryShortcut() {
    return new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK));
  }

  static @NotNull JBPopup createHistoryPopup(@NotNull List<String> history, @NotNull Project project, @NotNull Editor editor, @NotNull Runnable apply) {
    final JList<String> historyList = new JBList<>(ContainerUtil.filter(history, value -> !value.isEmpty()));
    Runnable itemChosenCallback = () -> {
      String selectedFilter = historyList.getSelectedValue();
      // selectedFilter can be null if user clicks on "No matches found"
      if (selectedFilter != null) {
        WriteCommandAction.writeCommandAction(project)
          .run(() -> {
            Document document = editor.getDocument();
            document.replaceString(0, document.getTextLength(), selectedFilter);
            CaretModel caretModel = editor.getCaretModel();
            if (caretModel.getOffset() >= document.getTextLength()) {
              caretModel.moveToOffset(document.getTextLength());
            }
          });
        apply.run();
      }
    };
    return JBPopupFactory.getInstance().createListPopupBuilder(historyList)
      .setMovable(false).setRequestFocus(true).setItemChosenCallback(itemChosenCallback).createPopup();
  }

  @Override
  public void apply() {
    GridFilteringModel model = myGrid.getDataHookup().getFilteringModel();
    if (model != null && myGrid.isSafeToReload()) {
      myGrid.getDataHookup().getLoader().applyFilterAndSorting(new GridRequestSource(new GridEditorPanelRequestPlace(this, myGrid)));
    }
  }

  @Override
  public @NotNull JComponent getGridPreferredFocusedComponent() {
    return myGrid.getPreferredFocusedComponent();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public @NotNull EditorEx getEditor() {
    return myEditor;
  }

  @SuppressWarnings("BoundedWildcard")
  protected abstract static class FilterFieldAction extends DumbAwareAction {
    private final @NotNull Function<@NotNull DataGrid, @Nullable GridEditorPanel> myGetPanel;

    FilterFieldAction(@Nullable @NlsActions.ActionText String text, @NotNull Function<@NotNull DataGrid, @Nullable GridEditorPanel> getPanel) {
      super(text);
      myGetPanel = getPanel;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      DataGrid grid = getDataGrid(e.getDataContext());
      GridEditorPanel panel = grid == null ? null : myGetPanel.fun(grid);
      e.getPresentation().setEnabled(panel != null && LookupManager.getActiveLookup(panel.getEditor()) == null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DataGrid grid = getDataGrid(e.getDataContext());
      GridEditorPanel panel = grid == null ? null : myGetPanel.fun(grid);
      if (panel == null) return;
      actionPerformed(panel, e.getProject());
    }

    protected abstract void actionPerformed(@NotNull GridEditorPanel panel, @Nullable Project project);
  }

  protected static class CancelAction extends FilterFieldAction {
    CancelAction(@NotNull Function<@NotNull DataGrid, @Nullable GridEditorPanel> getPanel) {
      super(DataGridBundle.message("action.CancelAction.text"), getPanel);
    }

    @Override
    protected void actionPerformed(@NotNull GridEditorPanel panel, @Nullable Project project) {
      JComponent component = panel.getGridPreferredFocusedComponent();
      IdeFocusManager.findInstanceByComponent(component).requestFocus(component, true);
    }
  }

  public static class ApplyAction extends FilterFieldAction {
    public ApplyAction(@NotNull Function<@NotNull DataGrid, @Nullable GridEditorPanel> getPanel) {
      super(DataGridBundle.message("action.ApplyAction.text"), getPanel);
    }

    @Override
    protected void actionPerformed(@NotNull GridEditorPanel panel, @Nullable Project project) {
      if (isValidTextEntered(panel.getEditor().getDocument(), project)) {
        panel.apply();
      }
      else {
        showInvalidFilterCriteriaBalloon(panel, panel.getInvalidTextErrorMessage());
      }
    }

    private static boolean isValidTextEntered(Document document, Project project) {
      String filter = document.getText();
      if (StringUtil.isEmptyOrSpaces(filter) || isComment(filter)) {
        return true;
      }
      PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(document);
      return psi == null || psi.getLanguage().getID().equals("GenericSQL") || !PsiTreeUtil.hasErrorElements(psi);
    }

    private static void showInvalidFilterCriteriaBalloon(@NotNull GridEditorPanel panel,
                                                         @NotNull @NlsContexts.PopupContent String message) {
      Balloon balloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(message, MessageType.WARNING, null)
        .setShowCallout(true).setHideOnAction(true).setHideOnClickOutside(true).createBalloon();

      Point point = getFilterEditorCaretPoint(panel.getEditor());
      balloon.show(new RelativePoint(panel.getEditor().getComponent(), point), Balloon.Position.below);
    }
  }

  static class ShowHistoryAction extends FilterFieldAction {
    ShowHistoryAction(@NotNull Function<@NotNull DataGrid, @Nullable GridEditorPanel> getPanel) {
      super(DataGridBundle.message("action.ShowHistoryAction.text"), getPanel);
    }

    @Override
    protected void actionPerformed(@NotNull GridEditorPanel panel, @Nullable Project project) {
      panel.showHistoryPopup();
    }
  }

  protected class HistoryIcon extends JBLabel {
    HistoryIcon(@NotNull Icon icon) {
      super(icon);
      setOpaque(false);
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(@NotNull MouseEvent e) {
          showHistoryPopup();
        }
      });
      new HelpTooltip().setTitle(DataGridBundle.message("action.ShowHistoryAction.tooltip"))
        .setShortcut(KeymapUtil.getFirstKeyboardShortcutText(getShowHistoryShortcut()))
        .setLocation(HelpTooltip.Alignment.BOTTOM)
        .installOn(this);
    }
  }

  private static class GridEditorPanelRequestPlace implements GridRequestSource.GridRequestPlace<GridRow, GridColumn> {
    final GridEditorPanel myPanel;
    final DataGrid myGrid;

    GridEditorPanelRequestPlace(@NotNull GridEditorPanel panel, @NotNull DataGrid grid) {
      myPanel = panel;
      myGrid = grid;
    }

    @Override
    public @NotNull DataGrid getGrid() {
      return myGrid;
    }
  }
}