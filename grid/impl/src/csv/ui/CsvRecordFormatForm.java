package com.intellij.database.csv.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvRecordFormat;
import com.intellij.database.view.editors.DataGridEditorUtil;
import com.intellij.database.view.editors.DataGridEditorUtil.EmbeddableEditorAdapter;
import com.intellij.database.view.editors.DataGridEditorUtil.JBTableRowEditorWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class CsvRecordFormatForm implements Disposable {
  public static class LazyPair<T> {
    public final Supplier<@Nls String> uiName;
    public final Supplier<@Nls String> uiNameLowercase;
    public final T value;

    LazyPair(@NotNull Supplier<@Nls String> uiName, @Nullable T value) {
      this(value, uiName, null);
    }

    LazyPair(@Nullable T value, @NotNull Supplier<@Nls String> uiName, @Nullable Supplier<@Nls String> uiNameLowercase) {
      this.value = value;
      this.uiName = uiName;
      this.uiNameLowercase = uiNameLowercase;
    }
  }

  public static final List<LazyPair<String>> DELIMITERS =
    List.of(new LazyPair<>("\n", DataGridBundle.messagePointer("csv.format.settings.delimiter.newline"), DataGridBundle.messagePointer("csv.format.settings.delimiter.newline.lowercase")),
            new LazyPair<>(" ", DataGridBundle.messagePointer("csv.format.settings.delimiter.space"), DataGridBundle.messagePointer("csv.format.settings.delimiter.space.lowercase")),
            new LazyPair<>("\t", DataGridBundle.messagePointer("csv.format.settings.delimiter.tab"), DataGridBundle.messagePointer("csv.format.settings.delimiter.tab.lowercase")),
            new LazyPair<>(",", DataGridBundle.messagePointer("csv.format.settings.delimiter.comma"), DataGridBundle.messagePointer("csv.format.settings.delimiter.comma.lowercase")),
            new LazyPair<>(";", DataGridBundle.messagePointer("csv.format.settings.delimiter.semicolon"), DataGridBundle.messagePointer("csv.format.settings.delimiter.semicolon.lowercase")),
            new LazyPair<>("|", DataGridBundle.messagePointer("csv.format.settings.delimiter.pipe"), DataGridBundle.messagePointer("csv.format.settings.delimiter.pipe.lowercase")));
  private static final List<LazyPair<String>> NULL_TEXT_VARIANTS =
    List.of(new LazyPair<>(DataGridBundle.messagePointer("csv.format.settings.null.text.undefined"), null),
            new LazyPair<>(DataGridBundle.messagePointer("csv.format.settings.null.text.empty.string"), ""),
            new LazyPair<>(() -> "\\N", "\\N"));
  private static final List<LazyPair<CsvRecordFormat.QuotationPolicy>> QUOTATION_POLICIES = List.of(
    new LazyPair<>(DataGridBundle.messagePointer("csv.format.settings.quotation.policy.never"), CsvRecordFormat.QuotationPolicy.NEVER),
    new LazyPair<>(DataGridBundle.messagePointer("csv.format.settings.quotation.policy.when.needed"),
                   CsvRecordFormat.QuotationPolicy.AS_NEEDED),
    new LazyPair<>(DataGridBundle.messagePointer("csv.format.settings.quotation.policy.always"), CsvRecordFormat.QuotationPolicy.ALWAYS));

  private JPanel myPanel;

  private JBCheckBox myTrimWhitespaceCheckBox;
  private JBTextField myRecordPrefixTextField;
  private JBTextField myRecordSuffixTextField;
  private ComboBox<String> myNullTextCombo;
  private ComboBox<String> myValueSeparatorComboBox;
  private ComboBox<String> myRecordSeparatorComboBox;
  private ComboBox<String> myQuotationPolicyComboBox;
  private JPanel myRowPrefixSuffixPanel;
  private ActionLink myAddRowPrefixSuffixActionLink;
  private JPanel myQuotesTablePanel;
  private JBLabel myQuotationLabel;

  private QuotesListTable myQuotesTable;

  private boolean myResetting;
  private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public CsvRecordFormatForm(@NotNull Disposable parent) {
    Disposer.register(parent, this);
    myQuotesTablePanel.add(DataGridEditorUtil.labeledDecorator(myQuotationLabel, myQuotesTable.getTable()));
    ItemListener fireFormatChangedOnItemUpdate = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        fireFormatChanged();
      }
    };
    DocumentListener documentAdapter = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        fireFormatChanged();
      }
    };

    prepareComboBox(myRecordSeparatorComboBox, DELIMITERS, documentAdapter);
    prepareComboBox(myValueSeparatorComboBox, DELIMITERS, documentAdapter);
    prepareComboBox(myQuotationPolicyComboBox, QUOTATION_POLICIES, documentAdapter);
    prepareComboBox(myNullTextCombo, NULL_TEXT_VARIANTS, documentAdapter);
    myTrimWhitespaceCheckBox.addItemListener(fireFormatChangedOnItemUpdate);

    DocumentListener fireFormatChangeOnTextUpdate = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        fireFormatChanged();
      }
    };

    myRecordPrefixTextField.getDocument().addDocumentListener(fireFormatChangeOnTextUpdate);
    myRecordSuffixTextField.getDocument().addDocumentListener(fireFormatChangeOnTextUpdate);

    setRowPrefixSuffixVisible(false);
    myQuotationPolicyComboBox.addItemListener(fireFormatChangedOnItemUpdate);
  }

  private void createUIComponents() {
    myQuotesTable = new QuotesListTable(this);
    myQuotesTable.getTable().getModel().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        fireFormatChanged();
      }
    });
    myAddRowPrefixSuffixActionLink = new ActionLink("", e -> {
      setRowPrefixSuffixVisible(true);
    });
    myAddRowPrefixSuffixActionLink.setBorder(JBUI.Borders.emptyTop(4));
  }

  public void reset(@NotNull CsvRecordFormat recordFormat) {
    myResetting = true;
    try {
      selectInCombo(myRecordSeparatorComboBox, DELIMITERS, recordFormat.recordSeparator);
      selectInCombo(myValueSeparatorComboBox, DELIMITERS, recordFormat.valueSeparator);
      selectInCombo(myNullTextCombo, NULL_TEXT_VARIANTS, recordFormat.nullText);
      myTrimWhitespaceCheckBox.setSelected(recordFormat.trimWhitespace);

      selectInCombo(myQuotationPolicyComboBox, QUOTATION_POLICIES, recordFormat.quotationPolicy);

      myQuotesTable.setQuotes(recordFormat.quotes);

      myRecordPrefixTextField.setText(recordFormat.prefix);
      myRecordSuffixTextField.setText(recordFormat.suffix);

      setRowPrefixSuffixVisible(StringUtil.isNotEmpty(recordFormat.prefix) || StringUtil.isNotEmpty(recordFormat.suffix));
    }
    finally {
      myResetting = false;
      fireFormatChanged();
    }
  }

  public @NotNull CsvRecordFormat getFormat() {
    String rowSeparator = Objects.requireNonNull(selectedInCombo(myRecordSeparatorComboBox, DELIMITERS));
    String valueSeparator = Objects.requireNonNull(selectedInCombo(myValueSeparatorComboBox, DELIMITERS));
    String nullText = selectedInCombo(myNullTextCombo, NULL_TEXT_VARIANTS);
    boolean trimWhitespace = myTrimWhitespaceCheckBox.isSelected();
    List<CsvRecordFormat.Quotes> quotes = myQuotesTable.getQuotes();
    CsvRecordFormat.QuotationPolicy quotationPolicy =
      Objects.requireNonNull(selectedInCombo(myQuotationPolicyComboBox, QUOTATION_POLICIES));
    String prefix = myRecordPrefixTextField.getText();
    String suffix = myRecordSuffixTextField.getText();

    return new CsvRecordFormat(
      prefix, suffix, nullText, quotes, quotationPolicy, valueSeparator, rowSeparator, trimWhitespace);
  }

  public @NotNull JPanel getMainPanel() {
    return myPanel;
  }

  public void addChangeListener(@NotNull ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void dispose() {
  }

  private void fireFormatChanged() {
    if (!myResetting) {
      myEventDispatcher.getMulticaster().recordFormatChanged(this);
    }
  }

  private void setRowPrefixSuffixVisible(boolean visible) {
    myAddRowPrefixSuffixActionLink.setVisible(!visible);
    myRowPrefixSuffixPanel.setVisible(visible);
  }


  private static @Nullable <V, P extends LazyPair<V>> V selectedInCombo(@NotNull ComboBox<String> combobox, @NotNull List<P> mappings) {
    final Object selected = combobox.isEditable() ? combobox.getEditor().getItem() : combobox.getSelectedItem();
    P mapping = ContainerUtil.find(mappings, pair -> pair.uiName.get().equals(selected));
    //noinspection unchecked
    return mapping == null ? (V)selected : mapping.value;
  }

  private static <V, P extends LazyPair<V>> void selectInCombo(@NotNull ComboBox<String> combo, @NotNull List<P> mappings, final @Nullable V what) {
    P mapping = ContainerUtil.find(mappings, pair -> Objects.equals(pair.value, what));
    if (mapping == null) {
      if (what != null) combo.setSelectedItem(what);
      return;
    }
    combo.setSelectedItem(mapping.uiName.get());
  }

  private static void prepareComboBox(@NotNull ComboBox<String> combo,
                                      @NotNull List<? extends LazyPair<?>> mappings,
                                      @NotNull DocumentListener documentListener) {
    combo.setModel(new CollectionComboBoxModel<>(new ArrayList<>(ContainerUtil.map(mappings, pair -> pair.uiName.get()))));
    Component component = combo.getEditor().getEditorComponent();
    if (component instanceof JTextField) ((JTextField)component).getDocument().addDocumentListener(documentListener);
  }


  public interface ChangeListener extends EventListener {
    void recordFormatChanged(@NotNull CsvRecordFormatForm source);
  }


  private static class QuotesListTable extends JBListTable {
    private final QuotesTable.QuotesRowRenderer myRenderer;
    private final QuotesTable.QuotesRowEditor myEditor;

    QuotesListTable(@NotNull Disposable parent) {
      super(new QuotesTable(), parent);

      myRenderer = new QuotesTable.QuotesRowRenderer(parent);
      myEditor = new QuotesTable.QuotesRowEditor();

      getTable().addPropertyChangeListener("tableCellEditor", new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          TableCellEditor cellEditor = ObjectUtils.tryCast(evt.getNewValue(), TableCellEditor.class);
          if (cellEditor != null) {
            myEditor.editingStarted(cellEditor);
          }
        }
      });

      JComponent c = myEditor.getComponent();
      int width = c.getPreferredSize().width + UIUtil.getScrollBarWidth();
      getTable().setPreferredScrollableViewportSize(JBUI.size(width, -1));
      getTable().setVisibleRowCount(5);
      getTable().setShowGrid(false);
    }

    public @NotNull List<CsvRecordFormat.Quotes> getQuotes() {
      return quotesTable().getQuotes();
    }

    public void setQuotes(@NotNull List<CsvRecordFormat.Quotes> quotes) {
      quotesTable().setQuotes(quotes);
    }

    @Override
    protected JBTableRowRenderer getRowRenderer(int row) {
      return myRenderer;
    }

    @Override
    protected JBTableRowEditor getRowEditor(int row) {
      myEditor.prepareEditor(myInternalTable, row);
      return new JBTableRowEditorWrapper<>(myEditor) {

        @Override
        public JBTableRow getValue() {
          return myEditor.getValue();
        }
      };
    }

    private @NotNull QuotesTable quotesTable() {
      return (QuotesTable)myInternalTable;
    }

    private static class QuotesTable extends JBTable {
      private static final Supplier<@Nls String> DUPLICATE_ESCAPE_METHOD =
        DataGridBundle.messagePointer("csv.format.settings.quotation.policy.escape.duplicate");

      static final MyColumnInfo LEFT_QUOTE_COLUMN = new MyColumnInfo(DataGridBundle.message("settings.column.left.quote"), 0);
      static final MyColumnInfo RIGHT_QUOTE_COLUMN = new MyColumnInfo(DataGridBundle.message("settings.column.right.quote"), 1);
      static final MyColumnInfo ESCAPE_METHOD_COLUMN = new MyColumnInfo(DataGridBundle.message("settings.escape.method"), 2);

      QuotesTable() {
        super(new ListTableModel<String[]>(LEFT_QUOTE_COLUMN, RIGHT_QUOTE_COLUMN, ESCAPE_METHOD_COLUMN) {
          @Override
          public void addRow() {
            addRow(new String[]{"'", "'", DUPLICATE_ESCAPE_METHOD.get()});
          }
        });
      }

      public void setQuotes(@NotNull List<CsvRecordFormat.Quotes> quotesList) {
        List<String[]> quotes = ContainerUtil.map(quotesList, quotes1 -> new String[]{quotes1.leftQuote, quotes1.rightQuote, escapeMethod(quotes1)});
        model(this).setItems(new ArrayList<>(quotes));
      }

      public @NotNull List<CsvRecordFormat.Quotes> getQuotes() {
        return ContainerUtil.mapNotNull(model(this).getItems(), aspects -> {
          String left = LEFT_QUOTE_COLUMN.valueOf(aspects);
          String right = RIGHT_QUOTE_COLUMN.valueOf(aspects);
          String escapeMethod = ESCAPE_METHOD_COLUMN.valueOf(aspects);

          if (StringUtil.isEmpty(left) || StringUtil.isEmpty(right) || StringUtil.isEmpty(escapeMethod)) {
            return null;
          }

          return DUPLICATE_ESCAPE_METHOD.get().equalsIgnoreCase(escapeMethod) ?
                 new CsvRecordFormat.Quotes(left, right, left + left, right + right) :
                 new CsvRecordFormat.Quotes(left, right, escapeMethod + left, escapeMethod + right);
        });
      }

      private static @NotNull String escapeMethod(@NotNull CsvRecordFormat.Quotes quotes) {
        return StringUtil.equals(quotes.leftQuote + quotes.leftQuote, quotes.leftQuoteEscaped) &&
               StringUtil.equals(quotes.rightQuote + quotes.rightQuote, quotes.rightQuoteEscaped) ? DUPLICATE_ESCAPE_METHOD.get() :
               StringUtil.trimEnd(quotes.leftQuoteEscaped, quotes.leftQuote);
      }

      private static @NotNull ListTableModel<String[]> model(@NotNull JTable t) {
        //noinspection unchecked
        return (ListTableModel<String[]>) t.getModel();
      }

      static class QuotesRowRenderer extends EditorTextFieldJBTableRowRenderer {
        protected QuotesRowRenderer(@NotNull Disposable parent) {
          super(null, parent);
        }

        @Override
        protected String getText(JTable table, int row) {
          String[] aspects = model(table).getItem(row);
          String escapeMethod = ESCAPE_METHOD_COLUMN.valueOf(aspects);
          String escapeMethodText = DUPLICATE_ESCAPE_METHOD.get().equals(escapeMethod) ?
                                    DataGridBundle.message("csv.format.settings.quotation.policy.escape.duplicate.renderer.text") :
                                    DataGridBundle.message("csv.format.settings.quotation.policy.escape.pattern",
                                                           escapeMethod == null ? "<unknown>" : escapeMethod);
          String leftQuoteText = LEFT_QUOTE_COLUMN.valueOf(aspects);
          String rightQuoteText = RIGHT_QUOTE_COLUMN.valueOf(aspects);
          return StringUtil.join(new String[]{leftQuoteText, rightQuoteText, escapeMethodText}, "  ");
        }
      }

      static class QuotesRowEditor extends EmbeddableEditorAdapter {
        private JPanel myPanel;
        private JBTextField myLeftQuoteTextField;
        private JBTextField myRightQuoteTextField;
        private ComboBox<String> myEscapeMethodComboBox;

        QuotesRowEditor() {
          myLeftQuoteTextField.setColumns(4);
          myRightQuoteTextField.setColumns(4);
          myEscapeMethodComboBox.setModel(new DefaultComboBoxModel<>(new String[]{DUPLICATE_ESCAPE_METHOD.get()}));
          myEscapeMethodComboBox.setEditable(true);
        }

        @Override
        public @NotNull JComponent getComponent() {
          return myPanel;
        }


        void prepareEditor(JTable table, int row) {
          String[] aspects = model(table).getItem(row);
          myLeftQuoteTextField.setText(LEFT_QUOTE_COLUMN.valueOf(aspects));
          myRightQuoteTextField.setText(RIGHT_QUOTE_COLUMN.valueOf(aspects));
          myEscapeMethodComboBox.setSelectedItem(ESCAPE_METHOD_COLUMN.valueOf(aspects));
        }

        JBTableRow getValue() {
          return new JBTableRow() {
            @Override
            public Object getValueAt(int column) {
              return switch (column) {
                case 0 -> myLeftQuoteTextField.getText();
                case 1 -> myRightQuoteTextField.getText();
                case 2 -> myEscapeMethodComboBox.getEditor().getItem();
                default -> throw new AssertionError();
              };
            }
          };
        }

        @Override
        public @NotNull JComponent getPreferredFocusedComponent() {
          return myLeftQuoteTextField;
        }

        @Override
        public JComponent @NotNull [] getFocusableComponents() {
          return new JComponent[] {myLeftQuoteTextField, myRightQuoteTextField, myEscapeMethodComboBox};
        }

        void editingStarted(@NotNull TableCellEditor editor) {
          ComboBox.registerTableCellEditor(myEscapeMethodComboBox, editor);
        }
      }

      private static class MyColumnInfo extends ColumnInfo<String[], String> {
        private final int myIndex;

        MyColumnInfo(@NlsContexts.ColumnName @NotNull String name, int index) {
          super(name);
          myIndex = index;
        }

        @Override
        public @Nls @Nullable String valueOf(String @Nls [] aspects) {
          return aspects[myIndex];
        }

        @Override
        public void setValue(String @Nls [] strings, @Nls String value) {
          strings[myIndex] = value;
        }
      }
    }
  }
}
