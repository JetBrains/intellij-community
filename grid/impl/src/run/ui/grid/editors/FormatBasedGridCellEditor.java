package com.intellij.database.run.ui.grid.editors;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.editor.DataGridColors;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory.ValueFormatter;
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory.ValueParser;
import com.intellij.database.run.ui.grid.editors.UnparsedValue.ParsingError;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.Outline;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.ObjectUtils;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.ParsePosition;
import java.util.EventObject;
import java.util.Objects;

public class FormatBasedGridCellEditor extends GridCellEditor.Adapter implements GridCellEditor.EditorBased {
  private final Formatter myFormat;
  private final ValueParser myValueParser;
  protected final ModelIndex<GridColumn> myColumn;
  protected final ModelIndex<GridRow> myRow;
  private final ReservedCellValue myNullValue;
  private final GridCellEditorTextField myTextField;
  private final DataGrid myGrid;

  public FormatBasedGridCellEditor(@NotNull Project project,
                                   @NotNull DataGrid grid,
                                   @NotNull Formatter format,
                                   @NotNull ModelIndex<GridColumn> column,
                                   @NotNull ModelIndex<GridRow> row,
                                   @Nullable ReservedCellValue nullValue,
                                   @Nullable EventObject initiator,
                                   @Nullable TextCompletionProvider provider,
                                   @NotNull ValueParser valueParser,
                                   @NotNull ValueFormatter valueFormatter,
                                   boolean multiline) {
    myFormat = format;
    myColumn = column;
    myRow = row;
    myNullValue = nullValue;
    myGrid = grid;
    myValueParser = valueParser;

    myTextField = new GridCellEditorTextField(project, grid, row, column, multiline, initiator, provider, true, valueFormatter);
    myTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        String editorText = myTextField.getDocument().getText();
        setError(null, 0);
        fireEditing(StringUtil.isEmpty(editorText) ? myNullValue : editorText);
      }
    });
    if (nullValue != null) {
      myTextField.addSettingsProvider(editor -> {
        editor.setPlaceholder(myNullValue.getDisplayName());
        editor.setShowPlaceholderWhenFocused(true);
      });
    }
    Disposer.register(this, myTextField);
  }

  @Override
  public @NotNull String getText() {
    return myTextField.getText();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myTextField;
  }

  @Override
  public @NotNull Object getValue() {
    return myValueParser.parse(myTextField.getText(), myTextField.getDocument());
  }

  @Override
  public boolean stop() {
    String text = myTextField.getText();
    Object parsed = myValueParser.parse(text, myTextField.getDocument());
    if (!(parsed instanceof UnparsedValue e)) return true;
    ParsingError error = e.getError();
    setError(error == null ? DataGridBundle.message("failed.to.parse.data") : error.message(), error == null ? 0 : error.offset());
    return false;
  }

  private void setError(@Nullable @Nls String error, int offset) {
    setEditorOutline(error == null ? null : Outline.error);
    Editor editor = getEditor();
    MarkupModel markupModel = DocumentMarkupModel.forDocument(myTextField.getDocument(), editor == null ? null : editor.getProject(), true);
    setHighlighting(markupModel, error == null ? null: createErrorInfo(offset, myTextField.getDocument().getTextLength(), error));
  }

  private void setEditorOutline(@Nullable Outline outline) {
    Editor editor = myTextField.getEditor();
    JComponent pane = UIUtil.uiParents(editor == null ? null : editor.getContentComponent(), false)
      .filter(JComponent.class)
      .find(c -> c.getBorder() instanceof ErrorBorderCapable);
    if (pane != null) {
      pane.putClientProperty("JComponent.outline", outline == null ? null : outline.name());
    }
  }

  private static @NotNull HighlightInfo createErrorInfo(int startOffset, int endOffset, @NlsContexts.DetailedDescription String tooltip) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .textAttributes(DataGridColors.GRID_ERROR_VALUE)
      .range(TextRange.create(startOffset, endOffset))
      .descriptionAndTooltip(tooltip)
      .createUnconditionally();
  }

  private static void setHighlighting(@NotNull MarkupModel markupModel, @Nullable HighlightInfo info) {
    markupModel.removeAllHighlighters();
    if (info != null) {
      RangeHighlighterEx highlighter = ((MarkupModelEx)markupModel).addRangeHighlighterAndChangeAttributes(
        info.forcedTextAttributesKey, info.startOffset, info.endOffset,
        HighlighterLayer.ERROR, HighlighterTargetArea.EXACT_RANGE, false,
        rh -> {
          rh.setErrorStripeTooltip(info);
        });
      info.setHighlighter(highlighter);
    }
  }

  protected @NotNull GridCellEditorTextField getTextField() {
    return myTextField;
  }

  protected @Nullable ReservedCellValue getNullValue() {
    return myNullValue;
  }

  protected final DataGrid getGrid() {
    return myGrid;
  }

  protected final Formatter getFormat() {
    return myFormat;
  }

  @Override
  public @Nullable Editor getEditor() {
    return myTextField.getEditor();
  }

  public abstract static class BoundedFormatBasedGridCellEditor<T> extends FormatBasedGridCellEditor {
    private boolean adjusting;


    public BoundedFormatBasedGridCellEditor(Project project,
                                            @NotNull DataGrid grid,
                                            @NotNull Formatter format,
                                            @Nullable ReservedCellValue nullValue,
                                            EventObject initiator,
                                            @NotNull ModelIndex<GridRow> row,
                                            @NotNull ModelIndex<GridColumn> columnModelIndex,
                                            @Nullable TextCompletionProvider provider,
                                            @NotNull ValueParser valueParser,
                                            @NotNull ValueFormatter valueFormatter) {
      super(project, grid, format, columnModelIndex, row, nullValue, initiator, provider, valueParser, valueFormatter, false);
    }

    @Override
    public boolean stop() {
      return super.stop() && checkInRange();
    }

    private boolean checkInRange() {
      if (adjusting || getTextField().getText().isEmpty()) return true;
      T value = getInternalValue();
      return value == null || !outOfRange(value, null, null);
    }

    protected void showPopup(@NotNull JBPopup popup) {
      Dimension size = popup.getContent().getPreferredSize();
      JComponent button = getComponent();
      popup.show(new RelativePoint(button, new Point(button.getWidth() - size.width, button.getHeight())));
    }

    protected boolean outOfRange(@NotNull T internalValue, @Nullable Runnable onAccept, @Nullable Runnable onDecline) {
      String boundary = getInfinityString(internalValue);
      if (boundary != null && !isSameValueInEditor(internalValue, boundary)) {
        onOutOfRange(boundary, internalValue, onAccept, onDecline);
        return true;
      }
      return false;
    }

    private void onOutOfRange(@NotNull String boundary,
                              @NotNull T internalValue,
                              @Nullable Runnable onAccept,
                              @Nullable Runnable onDecline) {
      showPopup(JBPopupFactory.getInstance().createConfirmation(DataGridBundle.message("popup.title.value.out.range.set", boundary),
                                                                CommonBundle.getYesButtonText(),
                                                                CommonBundle.getNoButtonText(),
                                                                () -> acceptValue(internalValue, onAccept),
                                                                ObjectUtils.notNull(onDecline, () -> {}),
                                                                1));
    }

    protected void acceptValue(@NotNull T internalValue, @Nullable Runnable onAccept) {
      try {
        adjusting = true;
        beforeStopEditing(internalValue);
        if (onAccept != null) onAccept.run();
        getGrid().stopEditing();
      } finally {
        adjusting = false;
      }
    }

    protected void beforeStopEditing(@NotNull T internalValue) {
    }

    protected @NotNull Object convertInternalValue(@NotNull T internalValue) {
      return internalValue;
    }
    protected abstract boolean isSameValueInEditor(@NotNull T internalValue, @NotNull String boundaryString);
    protected abstract @Nullable T getInternalValue();
    protected abstract @Nullable String getInfinityString(@NotNull T internalValue);
  }

  public abstract static class WithBrowseButton<T extends JComponent, V> extends BoundedFormatBasedGridCellEditor<V>
    implements ActionListener {
    private final ComponentWithBrowseButton<GridCellEditorTextField> myComponent;
    private final GridCellEditorFactory myFactory;
    private final Class<V> myClazz;
    private JBPopup myPopup;
    private T myPopupComponent;

    public WithBrowseButton(@NotNull Project project,
                            @NotNull DataGrid grid,
                            @NotNull Formatter format,
                            @Nullable ReservedCellValue nullValue,
                            EventObject initiator,
                            @NotNull ModelIndex<GridRow> row,
                            @NotNull ModelIndex<GridColumn> columnModelIndex,
                            @NotNull Class<V> clazz,
                            @Nullable TextCompletionProvider provider,
                            @NotNull ValueParser valueParser,
                            @NotNull ValueFormatter valueFormatter,
                            @NotNull GridCellEditorFactory factory) {
      super(project, grid, format, nullValue, initiator, row, columnModelIndex, provider, valueParser, valueFormatter);
      myClazz = clazz;
      myFactory = factory;

      myComponent = new ComponentWithBrowseButton<>(getTextField(), this) {
        @Override
        public void addNotify() {
          super.addNotify();
          if (initiator instanceof KeyEvent event) {
            int code = event.getKeyCode();
            if (code != KeyEvent.VK_ENTER && code != KeyEvent.VK_SPACE) return;
          }
          Objects.requireNonNull(getEditor()).getContentComponent().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (myPopup != null) closePopup();
            }
          });
          ApplicationManager.getApplication().invokeLater(() -> actionPerformed(null));
        }
      };
      myComponent.getButton().setBorder(UIManager.getBorder("Button.border"));
      myComponent.setRequestFocusEnabled(true);

      // having an empty selection allows to cancel editing in 2 Esc hits instead of 3
      getTextField().addSettingsProvider(editor -> editor.getSelectionModel().removeSelection());
    }

    @Override
    public @NotNull ComponentWithBrowseButton<GridCellEditorTextField> getComponent() {
      return myComponent;
    }

    @Override
    public @NotNull Object getValue() {
      if (myPopup instanceof AbstractPopup && myPopup.isVisible()) {
        Object value = getValue(myPopupComponent);
        if (value != null) return value;
      }
      return super.getValue();
    }

    protected abstract V getValue(T component);

    protected final void processDate(@NotNull T component, @Nullable Runnable onAccept, @Nullable Runnable onDecline) {
      V internalValue = getValue(component);
      if (outOfRange(internalValue, onAccept, onDecline)) return;
      acceptValue(internalValue, onAccept);
    }

    protected final Runnable onAccept(@NotNull JBPopup popup) {
      return () -> {
        popup.closeOk(null);
        // editor -> wrapper -> table
        getComponent().getParent().getParent().requestFocus();
      };
    }

    @Override
    public final void actionPerformed(ActionEvent e) {
      if (myPopup != null) {
        closePopup();
      }
      else {
        JBPopup popup = createPopup();
        showPopup(popup);
      }
    }

    private void closePopup() {
      JBPopup popup = myPopup;
      myPopup = null;
      myPopupComponent = null;
      popup.cancel();
      Disposer.dispose(popup);
    }

    private @NotNull JBPopup createPopup() {
      myPopupComponent = getPopupComponent();
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myPopupComponent, getPreferredFocusedComponent(myPopupComponent))
        .setModalContext(false)
        .setFocusable(true)
        .setCancelOnClickOutside(false)
        .setRequestFocus(true)
        .setResizable(false)
        .createPopup();
      configurePopup(myPopup, myPopupComponent);
      Disposer.register(this, myPopup);
      return myPopup;
    }

    @Override
    protected void showPopup(@NotNull JBPopup popup) {
      Dimension size = popup.getContent().getPreferredSize();
      FixedSizeButton button = getComponent().getButton();
      popup.show(new RelativePoint(button, new Point(button.getWidth() - size.width, button.getHeight())));
    }

    @Override
    protected @Nullable V getInternalValue() {
      String text = getTextField().getText();
      return ObjectUtils.tryCast(getFormat().parse(text, new ParsePosition(0)), myClazz);
    }

    @Override
    protected String getInfinityString(@NotNull V internalValue) {
      BoundaryValueResolver resolver = GridCellEditorHelper.get(getGrid()).getResolver(getGrid(), myColumn);
      return resolver.getInfinityString(internalValue);
    }

    @Override
    protected void beforeStopEditing(@NotNull V internalValue) {
      Object object = convertInternalValue(internalValue);
      getTextField().setText(myFactory.getValueFormatter(getGrid(), myRow, myColumn, object), getGrid(), myRow, myColumn);
    }

    @Override
    protected boolean isSameValueInEditor(@NotNull V internalValue, @NotNull String boundaryString) {
      return StringUtil.equalsIgnoreWhitespaces(boundaryString, getTextField().getText());
    }

    protected abstract @NotNull T getPopupComponent();

    protected abstract @NotNull JComponent getPreferredFocusedComponent(@NotNull T popupComponent);

    protected abstract void configurePopup(@NotNull JBPopup popup, @NotNull T component);
  }
}
