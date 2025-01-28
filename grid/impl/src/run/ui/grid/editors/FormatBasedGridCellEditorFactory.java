package com.intellij.database.run.ui.grid.editors;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.editors.UnparsedValue.ParsingError;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.textCompletion.TextCompletionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.EventObject;
import java.util.Objects;
import java.util.function.BiFunction;

import static com.intellij.database.datagrid.GridUtil.createFormatterConfig;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public abstract class FormatBasedGridCellEditorFactory implements GridCellEditorFactory {
  private static final Logger LOG = Logger.getInstance(FormatBasedGridCellEditorFactory.class);

  private final boolean myMultiline;

  protected FormatBasedGridCellEditorFactory() {
    this(false);
  }

  protected FormatBasedGridCellEditorFactory(boolean multiline) {
    myMultiline = multiline;
  }

  @Override
  public @NotNull GridCellEditor createEditor(@NotNull DataGrid grid,
                                              @NotNull ModelIndex<GridRow> row,
                                              @NotNull ModelIndex<GridColumn> column,
                                              @Nullable Object object,
                                              EventObject initiator) {
    Project project = grid.getProject();
    Formatter formatter = getFormat(grid, row, column);
    TextCompletionProvider provider = GridUtil.createCompletionProvider(grid, row, column);
    GridCellEditorHelper helper = GridCellEditorHelper.get(grid);
    ReservedCellValue nullValue = helper.getDefaultNullValue(grid, column);
    TextCompletionProvider resultProvider = new NullCompletionProvider(helper.isNullable(grid, column), provider);
    ValueParser valueParser = getValueParser(grid, row, column);
    ValueFormatter valueFormatter = getValueFormatter(grid, row, column, object);
    return createEditorImpl(project, grid, formatter, nullValue, initiator, resultProvider, row, column, valueParser, valueFormatter);
  }

  private @NotNull Formatter getFormat(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    Formatter baseFormatter = getFormatInner(grid, row, column);
    return makeFormatterLenient(grid) ? new LenientFormatter(baseFormatter) : baseFormatter;
  }

  protected abstract @NotNull Formatter getFormatInner(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  @Override
  public @NotNull IsEditableChecker getIsEditableChecker() {
    return (value, grid, column) -> true;
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx) {
    Object databaseValue = grid.getDataModel(DataAccessType.DATABASE_DATA).getValueAt(rowIdx, columnIdx);
    String databaseValueText = getValueFormatter(grid, rowIdx, columnIdx, databaseValue).format().text;
    Formatter format = getFormat(grid, rowIdx, columnIdx);
    return getValueParser(format, grid, databaseValue, databaseValueText, columnIdx,
                          (text, e) -> GridCellEditorHelper.get(grid).createUnparsedValue(text, e, grid, rowIdx, columnIdx));
  }

  protected static @NotNull ValueParser getValueParser(@NotNull Formatter format,
                                                       @NotNull DataGrid grid,
                                                       @Nullable Object databaseValue,
                                                       @Nullable String databaseValueText,
                                                       @Nullable ModelIndex<GridColumn> columnIdx,
                                                       @NotNull BiFunction<? super String, ? super ParsingError, UnparsedValue> unparsedValueCreator) {
    ReservedCellValue nullValue = GridCellEditorHelper.get(grid).getDefaultNullValue(grid, columnIdx);
    Formatter parser = new Formatter.Wrapper(format) {
      @Override
      public Object parse(@NotNull String value) throws ParseException {
        if (databaseValueText != null && databaseValueText.equals(value) && databaseValue != null) return databaseValue;
        return super.parse(value);
      }

      @Override
      public Object parse(@NotNull String value, ParsePosition position) {
        if (databaseValueText != null && databaseValueText.equals(value) && databaseValue != null) {
          position.setIndex(value.length());
          return databaseValue;
        }
        return super.parse(value, position);
      }
    };
    return new ValueParserWrapper(parser, columnIdx != null && GridCellEditorHelper.get(grid).isNullable(grid, columnIdx), nullValue, unparsedValueCreator::apply);
  }

  @Override
  public @NotNull ValueFormatter getValueFormatter(@NotNull DataGrid grid,
                                                   @NotNull ModelIndex<GridRow> rowIdx,
                                                   @NotNull ModelIndex<GridColumn> columnIdx,
                                                   @Nullable Object value) {
    Formatter format;
    if (value instanceof UnparsedValue) {
      return new DefaultValueToText(grid, columnIdx, value);
    }
    format = getFormat(grid, rowIdx, columnIdx);
    return () -> {
      try {
        return new ValueFormatterResult(value instanceof ReservedCellValue || value == null ? "" : format.format(value));
      }
      catch (IllegalArgumentException iae) {
        if (LOG.isDebugEnabled()) {
          String message = "Failed to format object " + value + " of class " + (value == null ? "null" : value.getClass().getName()) +
                           " using format " + format + "\n";
          LOG.debug(message, iae);
        }
        GridColumn column = grid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
        String text;
        if (column != null) {
          text = grid.getObjectFormatter().objectToString(value, column, createFormatterConfig(grid, columnIdx));
          text = Objects.requireNonNullElse(text, "null");
        }
        else {
          text = value instanceof String ? (String)value : "";
        }
        return new ValueFormatterResult(text);
      }
    };
  }

  protected boolean makeFormatterLenient(@NotNull DataGrid grid) {
    return false;
  }

  protected @NotNull FormatBasedGridCellEditor createEditorImpl(@NotNull Project project,
                                                                @NotNull DataGrid grid,
                                                                @NotNull Formatter format,
                                                                @Nullable ReservedCellValue nullValue,
                                                                EventObject initiator,
                                                                @Nullable TextCompletionProvider provider,
                                                                @NotNull ModelIndex<GridRow> row,
                                                                @NotNull ModelIndex<GridColumn> column,
                                                                @NotNull ValueParser valueParser,
                                                                @NotNull ValueFormatter valueFormatter) {
    return new FormatBasedGridCellEditor(project, grid, format, column, row, nullValue, initiator, provider, valueParser, valueFormatter, myMultiline);
  }

  private static class LenientFormatter extends Formatter.Wrapper {
    LenientFormatter(@NotNull Formatter formatter) {
      super(formatter);
    }

    @Override
    public Object parse(@NotNull String value) {
      try {
        return super.parse(value);
      }
      catch (ParseException ignore) {
      }
      return value;
    }

    @Override
    public Object parse(@NotNull String value, ParsePosition position) {
      Object parsed = super.parse(value, position);
      if (parsed == null || position.getErrorIndex() != -1) {
        position.setErrorIndex(-1);
        return value;
      }
      return parsed;
    }

    @Override
    public String format(Object value) {
      try {
        return super.format(value);
      }
      catch (IllegalArgumentException ignore) {
      }
      return value == null ? null : value.toString();
    }
  }

  private static class NullCompletionProvider implements TextCompletionProvider {
    private final boolean myIsNullable;
    private final TextCompletionProvider myDelegate;

    NullCompletionProvider(boolean isNullable, @Nullable TextCompletionProvider delegate) {
      myIsNullable = isNullable;
      myDelegate = delegate;
    }

    @Override
    public @Nullable String getAdvertisement() {
      return myDelegate == null ? null : myDelegate.getAdvertisement();
    }

    @Override
    public @Nullable String getPrefix(@NotNull String text, int offset) {
      return myDelegate == null ? null : myDelegate.getPrefix(text, offset);
    }

    @Override
    public @NotNull CompletionResultSet applyPrefixMatcher(@NotNull CompletionResultSet result, @NotNull String prefix) {
      return myDelegate == null ? result : myDelegate.applyPrefixMatcher(result, prefix);
    }

    @Override
    public @Nullable CharFilter.Result acceptChar(char c) {
      return myDelegate == null ? null : myDelegate.acceptChar(c);
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull String prefix,
                                       @NotNull CompletionResultSet result) {
      if (parameters.getInvocationCount() == 0 && !isNullPrefix(prefix)) return;
      if (myDelegate != null) myDelegate.fillCompletionVariants(parameters, prefix, result);
      if (!myIsNullable) return;

      String fileText = parameters.getOriginalFile().getText();
      if (fileText == null || !isNullPrefix(StringUtil.trim(fileText))) return;
      result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create("null")
                                                                .withCaseSensitivity(false)
                                                                .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE), 1));
    }

    private static boolean isNullPrefix(@NotNull String trim) {
      return StringUtil.startsWithIgnoreCase("nul", trim);
    }
  }
}
