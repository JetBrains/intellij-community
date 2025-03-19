package com.intellij.database.datagrid;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatter;
import com.intellij.database.csv.CsvRecord;
import com.intellij.database.csv.ValueRange;
import com.intellij.database.datagrid.mutating.ColumnQueryData;
import com.intellij.database.datagrid.mutating.RowMutation;
import com.intellij.database.dbimport.CsvImportUtil;
import com.intellij.database.dbimport.TypeMerger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.*;

public class CsvDocumentDataHookUp extends DocumentDataHookUp {
  private CsvFormat myFormat;

  public CsvDocumentDataHookUp(@NotNull Project project, @NotNull CsvFormat format, @NotNull Document document, @Nullable TextRange range) {
    super(project, document, range);
    myFormat = format;
  }

  @Override
  protected @NotNull DocumentDataMutator createDataMutator() {
    return new CsvDocumentDataMutator();
  }

  public void setFormat(@NotNull CsvFormat format, @NotNull GridRequestSource source) {
    myFormat = format;
    getLoader().reloadCurrentPage(source);
  }

  public @NotNull CsvFormat getFormat() {
    return myFormat;
  }

  @Override
  protected @Nullable CsvMarkup buildMarkup(@NotNull CharSequence sequence, @NotNull GridRequestSource source) {
    //TODO use a sliding window if copying whole document is unacceptable
    // using CharsSequence returned by myDocument.getCharsSequence is slow, use string instead
    String string = sequence.toString();
    CsvParserResult result = new CsvFormatParser(myFormat).parse(string);
    return result == null ? oneLineMarkup(string) : new CsvMarkup(result);
  }

  private @NotNull CsvMarkup oneLineMarkup(@NotNull String string) {
    CsvFormatter formatter = new CsvFormatter(myFormat);
    int length = string.length();
    TextRange range = new TextRange(0, length);
    CsvRecord record = new CsvRecord(range, Collections.singletonList(new ValueRange(0, length)), false);
    return new CsvMarkup(formatter, string, Collections.singletonList(record), null, false, record.values.size());
  }

  private class CsvDocumentDataMutator extends DocumentDataMutator {
    @Override
    protected @NotNull UpdateSession createSession() {
      TextRange range = getRange();
      return new CsvUpdateSession(getDocument(), range != null ? range.getStartOffset() : 0);
    }

    @Override
    protected void finishSession(@NotNull UpdateSession session, boolean success) {
      if (success && ((CsvUpdateSession)session).myNewFormat != null) {
         myFormat = ((CsvUpdateSession)session).myNewFormat;
      }
    }
  }

  protected static class CsvUpdateSession extends UpdateSession {
    private CsvFormat myNewFormat;

    private CsvUpdateSession(@NotNull Document document, int rightShift) {
      super(document, rightShift);
    }
  }

  public static @NotNull List<GridColumn> columnsFrom(@NotNull CharSequence sequence,
                                                      @NotNull List<CsvRecord> records,
                                                      @Nullable CsvRecord header,
                                                      boolean rowNameColumn,
                                                      int columnsCount,
                                                      @NotNull CsvFormat format) {
    if (columnsCount > 0 && rowNameColumn) columnsCount--;
    List<String> columnNames;
    if (header != null && rowNameColumn) {
      columnNames = CsvFormatParser.values(null, sequence, header.values.subList(1, header.values.size()));
    }
    else if (header != null) {
      columnNames = CsvFormatParser.values(null, sequence, header.values);
    }
    else {
      columnNames = null;
    }
    List<GridColumn> columns = new ArrayList<>(columnsCount);
    for (int i = 0; i < columnsCount; i++) {
      String name = columnNames == null || i >= columnNames.size() ? "C" + (i + 1) : columnNames.get(i);
      TypeMerger merger = determineColumnType(records, i, sequence, format);
      columns.add(new DataConsumer.Column(i, name, getType(merger), merger.getName(), getClassName(merger)));
    }
    return columns;
  }

  private static @NotNull TypeMerger determineColumnType(@NotNull List<CsvRecord> records,
                                                         int i,
                                                         @NotNull CharSequence sequence,
                                                         @NotNull CsvFormat format) {
    JBIterable<@Nullable String> values = JBIterable.from(records)
      .map(row -> i < row.values.size() ? nullize(format.dataRecord.nullText, row.values.get(i).value(sequence).toString()) : null)
      .take(200);
    return CsvImportUtil.getPreferredTypeMergerBasedOnContent(values, STRING_MERGER, INTEGER_MERGER, BIG_INTEGER_MERGER, DOUBLE_MERGER, BOOLEAN_MERGER);
  }

  private static @Nullable String nullize(@Nullable String nullText, @NotNull String string) {
    return StringUtil.equals(nullText, string) ? null : string;
  }

  public static @NotNull List<GridRow> rowsFrom(@NotNull CsvFormat format,
                                                @NotNull CharSequence sequence,
                                                @NotNull List<CsvRecord> records,
                                                boolean named) {
    List<GridRow> rows = new ArrayList<>(records.size());
    for (int i = 0; i < records.size(); i++) {
      List<ValueRange> valuesList = records.get(i).values;
      GridRow row;
      if (named) {
        Object[] values = CsvFormatParser.values(format.dataRecord, sequence, valuesList.subList(1, valuesList.size())).toArray();
        String name = valuesList.get(0).value(sequence).toString();
        row = NamedRow.create(i, name, values);
      }
      else {
        row = DataConsumer.Row.create(i, CsvFormatParser.values(format.dataRecord, sequence, valuesList).toArray());
      }
      rows.add(row);
    }
    return rows;
  }

  public static class CsvMarkup extends DocumentDataHookUp.DataMarkup {
    private final List<CsvRecord> myRecords;
    private final CsvRecord myHeader;
    private final CsvFormatter myFormatter;

    public CsvMarkup(@NotNull CsvParserResult result) {
      super(
        columnsFrom(result.getSequence(), result.getRecords(), result.getHeader(), result.getFormat().rowNumbers, result.getColumnsCount(),
                    result.getFormat()),
        rowsFrom(result.getFormat(), result.getSequence(), result.getRecords(), result.getFormat().rowNumbers));
      myFormatter = new CsvFormatter(result.getFormat());
      myHeader = result.getHeader();
      myRecords = result.getRecords();
    }

    CsvMarkup(@NotNull CsvFormatter formatter,
              @NotNull CharSequence sequence,
              @NotNull List<CsvRecord> records,
              @Nullable CsvRecord header,
              boolean firstRowIsHeader,
              int columnsCount) {
      super(columnsFrom(sequence, records, header, firstRowIsHeader, columnsCount, formatter.getFormat()),
            rowsFrom(formatter.getFormat(), sequence, records, firstRowIsHeader));
      myFormatter = formatter;
      myHeader = header;
      myRecords = records;
    }

    @Override
    protected boolean deleteRows(@NotNull UpdateSession session, @NotNull List<GridRow> sortedRows) {
      for (GridRow row : sortedRows) {
        session.delete(myRecords.get(GridRow.toRealIdx(row)).range);
      }
      return true;
    }

    @Override
    protected boolean insertRow(@NotNull UpdateSession session) {
      return insertRow(session, Arrays.asList(new Object[columns.size()]));
    }

    @Override
    protected boolean cloneRow(@NotNull UpdateSession session, @NotNull GridRow row) {
      return insertRow(session, ContainerUtil.newArrayList(row));
    }

    @Override
    protected boolean deleteColumns(@NotNull UpdateSession session, @NotNull List<GridColumn> sortedColumns) {
      List<GridColumn> columnsToLeave = getColumnsToLeave(sortedColumns);
      if (myHeader != null) {
        leaveColumns(session, columnsToLeave, myHeader);
      }
      for (int i = 0; i < rows.size(); i++) {
        leaveColumns(session, columnsToLeave, myRecords.get(i), rows.get(i));
      }
      return true;
    }

    @Override
    protected boolean renameColumn(@NotNull UpdateSession session,
                                   @NotNull ModelIndex<GridColumn> column,
                                   @NotNull String name) {
      if (column.asInteger() >= columns.size()) return false;
      String columnName = myFormatter.formatHeaderValue(name);
      if (myHeader == null) {
        generateHeaderRow(session, column, columnName);
        return true;
      }
      if (column.asInteger() >= myHeader.values.size()) {
        generateMissingPartOfHeaderRow(session, column, columnName);
        return true;
      }
      ValueRange range = myHeader.values.get(column.asInteger());
      session.replace(range, columnName);
      return true;
    }

    private void generateMissingPartOfHeaderRow(@NotNull UpdateSession session,
                                                @NotNull ModelIndex<GridColumn> column,
                                                @NotNull String columnName) {
      int offset = myHeader.values.isEmpty() ? 0 : ContainerUtil.getLastItem(myHeader.values).getEndOffset();
      for (int i = myHeader.values.size(); i < column.asInteger(); i++) {
        session.insert(myFormatter.valueSeparator(), offset);
        session.insert(columns.get(i).getName(), offset);
      }
      session.insert(myFormatter.valueSeparator(), offset);
      session.insert(columnName, offset);
    }

    private void generateHeaderRow(@NotNull UpdateSession session,
                                   @NotNull ModelIndex<GridColumn> column,
                                   @NotNull String columnName) {
      ((CsvDocumentDataHookUp.CsvUpdateSession)session).myNewFormat =
        CsvFormatter.setFirstRowIsHeader(myFormatter.getFormat(), true);
      boolean emptyDoc = session.getText().isEmpty();
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (GridColumn c : columns) {
        if (first) first = false;
        else sb.append(myFormatter.valueSeparator());

        if (c.getColumnNumber() == column.asInteger()) sb.append(columnName);
        else sb.append(myFormatter.formatHeaderValue(c.getName()));
      }
      if (emptyDoc) sb.append(myFormatter.recordSeparator());
      session.insert(sb.append(myFormatter.recordSeparator()).toString(), 0);
    }

    @Override
    protected boolean insertColumn(@NotNull UpdateSession session, @Nullable String name) {
      return insertColumn(session, null, name);
    }

    @Override
    protected String prepareMoveColumn(@NotNull GridColumn fromColumn, @NotNull ModelIndex<GridColumn> toColumn) {
      var to = toColumn.value;
      var from = fromColumn.getColumnNumber();
      boolean fromBeforeTo = from < to; // i.e. moving to the right
      var left = Math.min(from, to);
      var right = Math.max(from, to);

      var text = new StringBuilder();

      if (myHeader != null) {
        for (int i = 0; i < columns.size(); i++) {
          int newPos = (i < left || i > right) ? i :
                       i == to ? from :
                       fromBeforeTo ? i + 1 : i - 1;
          text.append(myFormatter.formatHeaderValue(columns.get(newPos).getName()));
          text.append(i < columns.size() - 1 ? myFormatter.headerValueSeparator() : myFormatter.recordSeparator());
        }
      }

      for (var row : rows) {
        for (int i = 0; i < columns.size(); i++) {
          int newPos = (i < left || i > right) ? i :
                       i == to ? from :
                       fromBeforeTo ? i + 1 : i - 1;
          text.append(myFormatter.formatValue(columns.get(newPos).getValue(row)));
          text.append(i < columns.size() - 1 ? myFormatter.valueSeparator() : myFormatter.recordSeparator());
        }
      }

      return text.toString();
    }
    @Override
    protected boolean cloneColumn(@NotNull UpdateSession session, @NotNull GridColumn column) {
      return insertColumn(session, column, null);
    }

    @Override
    protected boolean update(@NotNull UpdateSession session, @NotNull List<RowMutation> mutations) {
      for (RowMutation mutation : mutations) {
        GridRow row = mutation.getRow();
        List<ValueRange> valueRanges = myRecords.get(GridRow.toRealIdx(row)).values;
        for (ColumnQueryData data : mutation.getData()) {
          String newValueText = myFormatter.formatValue(data.getObject());
          GridColumn column = data.getColumn();
          int rangeIdx = column.getColumnNumber() + (row instanceof NamedRow ? 1 : 0);
          int missingRanges = rangeIdx - valueRanges.size() + 1;

          ValueRange range = missingRanges > 0
                             ? insertMissingRanges(session, ContainerUtil.getLastItem(valueRanges).getEndOffset(), missingRanges)
                             : valueRanges.get(rangeIdx);
          session.replace(range, newValueText);
        }
      }
      return true;
    }

    private ValueRange insertMissingRanges(@NotNull UpdateSession session, int endOffset, int missingRanges) {
      String nullValue = myFormatter.formatValue(null);
      for (int i = 0; i < missingRanges; i++) {
        session.insert(myFormatter.valueSeparator(), endOffset);
        session.insert(nullValue, endOffset);
      }
      return new ValueRange(endOffset - nullValue.length(), endOffset, false);
    }

    private boolean insertColumn(@NotNull UpdateSession session, @Nullable GridColumn column, @Nullable String name) {
      String columnName = name != null ? name :
                          column != null ? column.getName() :
                          "column" + (columns.size() + 1);
      return doInsertColumn(session, column, columnName);
    }

    private boolean doInsertColumn(@NotNull UpdateSession session, @Nullable GridColumn column, @NotNull String name) {
      if (myHeader != null) {
        ValueRange lastColumnRange = Objects.requireNonNull(ContainerUtil.getLastItem(myHeader.values));
        String columnName = myFormatter.formatHeaderValue(name);
        session.insert(myFormatter.headerValueSeparator() + columnName, lastColumnRange.getEndOffset());
      }
      for (int i = 0; i < rows.size(); i++) {
        ValueRange lastValueRange = Objects.requireNonNull(ContainerUtil.getLastItem(myRecords.get(i).values));
        String valueText = myFormatter.formatValue(column != null ? column.getValue(rows.get(i)) : null);
        session.insert(myFormatter.valueSeparator() + valueText, lastValueRange.getEndOffset());
      }
      return true;
    }

    private boolean insertRow(@NotNull UpdateSession session, @NotNull List<?> values) {
      CsvRecord lastRecord = ObjectUtils.chooseNotNull(ContainerUtil.getLastItem(myRecords), myHeader);
      int offset = lastRecord != null ? lastRecord.range.getEndOffset() : 0;
      if (lastRecord != null && !lastRecord.hasRecordSeparator) {
        session.insert(myFormatter.recordSeparator(), offset);
      }
      if (myFormatter.requiresRowNumbers()) {
        String newRecordName = null;
        GridRow lastRow = ContainerUtil.getLastItem(rows);
        if (lastRow instanceof NamedRow) {
          try {
            newRecordName = String.valueOf(Long.parseLong(((NamedRow)lastRow).name) + 1);
          }
          catch (NumberFormatException ignore) {
          }
        }
        if (newRecordName == null) {
          newRecordName = String.valueOf(lastRow != null ? lastRow.getRowNum() + 1 : 1);
        }
        values = ContainerUtil.prepend(values, newRecordName);
      }
      String newRecord = myFormatter.formatRecord(values);
      session.insert(newRecord, offset);
      if (newRecord.isEmpty()) {
        session.insert(myFormatter.recordSeparator(), offset);
      }
      return true;
    }

    private void leaveColumns(@NotNull UpdateSession session,
                              @NotNull List<GridColumn> columns,
                              final @NotNull CsvRecord record,
                              final @NotNull GridRow row) {
      if (columns.isEmpty()) {
        session.delete(record.range);
        return;
      }

      List<Object> values = ContainerUtil.map(columns, column -> column.getValue(row));
      if (row instanceof NamedRow) {
        values = ContainerUtil.prepend(values, ((NamedRow)row).name);
      }
      String recordText = myFormatter.formatRecord(values);

      session.replace(record.range, recordText);
      if (record.hasRecordSeparator) {
        session.insert(myFormatter.recordSeparator(), record.range.getEndOffset());
      }
    }

    private void leaveColumns(@NotNull UpdateSession session,
                              @NotNull List<GridColumn> columns,
                              @NotNull CsvRecord headerRecord) {
      List<ValueRange> values = headerRecord.values;
      int valuesStart = values.get(myFormatter.requiresRowNumbers() ? 1 : 0).getStartOffset();
      int valuesEnd = values.get(values.size() - 1).getEndOffset();

      StringBuilder sb = new StringBuilder();
      for (GridColumn column : columns) {
        sb.append(myFormatter.formatHeaderValue(column.getName()))
          .append(myFormatter.headerValueSeparator());
      }
      sb.setLength(!sb.isEmpty() ? sb.length() - myFormatter.headerValueSeparator().length() : 0);

      session.replace(TextRange.create(valuesStart, valuesEnd), sb.toString());
    }

    private @NotNull List<GridColumn> getColumnsToLeave(@NotNull List<GridColumn> orderedColumnsToDelete) {
      List<GridColumn> columnsToLeave = new ArrayList<>(columns.size() - orderedColumnsToDelete.size());

      Iterator<GridColumn> toDeleteIterator = orderedColumnsToDelete.iterator();
      Iterator<GridColumn> allColumnsIterator = columns.iterator();

      while (allColumnsIterator.hasNext()) {
        GridColumn toDelete = toDeleteIterator.hasNext() ? toDeleteIterator.next() : null;
        do {
          GridColumn column = allColumnsIterator.next();
          if (column.equals(toDelete)) break;

          columnsToLeave.add(column);
        }
        while (allColumnsIterator.hasNext());
      }

      return columnsToLeave;
    }
  }
}
