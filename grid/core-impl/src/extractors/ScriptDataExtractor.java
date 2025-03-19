package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ExtractorHierarchicalGridColumn;
import com.intellij.database.extensions.*;
import com.intellij.database.util.Out;
import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.intellij.database.extensions.DataExtractorBindings.*;

public abstract class ScriptDataExtractor implements DataExtractor {
  private final Project myProject;
  private final Path myScriptFile;
  private final IdeScriptEngine engine;
  private final boolean isAggregator;

  private final boolean supportsText;

  public ScriptDataExtractor(@Nullable Project project,
                             @NotNull Path scriptFile,
                             @NotNull IdeScriptEngine engine,
                             @NotNull ObjectFormatter objectFormatter,
                             boolean isAggregator,
                             boolean supportsText) {
    myProject = project;
    myScriptFile = scriptFile;
    this.isAggregator = isAggregator;
    this.supportsText = supportsText;
    this.engine = engine;
    ExtensionScriptsUtil.setBindings(engine)
      .bind(PROJECT, project)
      .bind(FORMATTER, createFormatter(objectFormatter));
  }

  @Override
  public @NotNull String getFileExtension() {
    return getFileExtension(myScriptFile);
  }

  @Override
  public boolean supportsText() {
    return supportsText;
  }

  private static @NotNull String getFileExtension(@NotNull Path scriptFile) {
    return ExtractorScripts.getOutputFileExtension(scriptFile.getFileName().toString());
  }

  @Override
  public @NotNull Extraction startExtraction(@NotNull Out out,
                                             @NotNull List<? extends GridColumn> allColumns,
                                             @NotNull String query,
                                             @NotNull ExtractionConfig config,
                                             int... selectedColumns) {
    ExtractionDataConveyor conveyor = new ExtractionDataConveyor(selectedColumns, allColumns.size());
    final ExtractionDataConveyor.DataStreamImpl stream = conveyor.outputStream;
    prepareEngine(out, stream, allColumns, config.isTransposed(), selectedColumns);
    conveyor.setBinder(() -> ExtensionScriptsUtil.setBindings(engine));
    var errorMessagesDisabled = config.getSilent();

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ExtensionScriptsUtil.prepareScript(myScriptFile);
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Throwable exception = null;
      try {
        ExtensionScriptsUtil.evalScript(myProject, engine, myScriptFile, !isAggregator && !errorMessagesDisabled);
      }
      catch (Throwable th) {
        exception = th;
      }
      finally {
        stream.close(exception);
      }
    });
    return conveyor.extractionInput;
  }

  private void prepareEngine(@NotNull Out out,
                             @NotNull DataStream<DataRowImpl> stream,
                             @NotNull List<? extends GridColumn> allColumns,
                             boolean transposed,
                             int... selectedColumns) {
    List<? extends DataColumn> dataColumns = createDataColumns(allColumns, selectedColumns);
    ExtensionScriptsUtil.setBindings(engine)
      .bind(ALL_COLUMNS, dataColumns)
      .bind(COLUMNS, getSelectedDataColumns(dataColumns, selectedColumns))
      .bind(OUT, createOutput(out))
      .bind(ROWS, stream)
      .bind(TRANSPOSED, transposed);
  }

  private @NotNull ValueFormatter createFormatter(@NotNull ObjectFormatter objectFormatter) {
    ObjectFormatterConfig config = StringUtil.equalsIgnoreCase("sql", getFileExtension())
                                   ? new DatabaseObjectFormatterConfig(ObjectFormatterMode.SQL_SCRIPT)
                                   : StringUtil.equalsIgnoreCase("json", getFileExtension())
                                     ? new DatabaseObjectFormatterConfig(ObjectFormatterMode.JSON)
                                     : new DatabaseObjectFormatterConfig(ObjectFormatterMode.DEFAULT);
    return new ValueFormatter() {
      @Override
      public @NotNull String format(@NotNull DataRow row, @NotNull DataColumn column) {
        return formatValue(row.value(column), column);
      }

      @Override
      public @NotNull String formatValue(@Nullable Object o, @NotNull DataColumn column) {
        String str = objectFormatter.objectToString(o, ((DataColumnImpl)column).getColumn(), config);
        return StringUtil.notNullize(str, "null");
      }

      @Override
      public @NotNull String getTypeName(@Nullable Object o, @NotNull DataColumn column) {
        return ScriptDataExtractor.this.getTypeName(o, (DataColumnImpl)column);
      }

      @Override
      public boolean isStringLiteral(@Nullable Object o, @NotNull DataColumn column) {
        return objectFormatter.isStringLiteral(((DataColumnImpl)column).getColumn(), o, config.getMode());
      }
    };
  }

  protected @NotNull String getTypeName(@Nullable Object o, DataColumnImpl column) {
    return ObjectUtils.notNull(StringUtil.nullize(column.getColumn().getTypeName()), "unknown");
  }

  private static List<? extends DataColumn> getSelectedDataColumns(List<? extends DataColumn> allDataColumns, int[] selectedColumns) {
    if (selectedColumns.length == 0) {
      return allDataColumns;
    }

    List<DataColumn> selectedDataColumns = new ArrayList<>(selectedColumns.length);
    Set<Integer> indicesSet = GridExtractorsUtilCore.intArrayToSet(selectedColumns);

    for (int i = 0; i < allDataColumns.size(); ++i) {
      DataColumn dataColumn = allDataColumns.get(i);
      if (dataColumn instanceof HierarchicalDataColumnImpl hierarchical) {
        if (hierarchical.isMatchesSelection()) selectedDataColumns.add(dataColumn);
        continue;
      }
      if (indicesSet.contains(i)) selectedDataColumns.add(dataColumn);
    }

    return selectedDataColumns;
  }

  private static @NotNull List<? extends DataColumn> createDataColumns(List<? extends GridColumn> allColumns, int[] selectedColumns) {
    List<DataColumn> dataColumns = new ArrayList<>();
    for (GridColumn column : allColumns) {
      if (column instanceof ExtractorHierarchicalGridColumn hierarchical) {
        dataColumns.add(new HierarchicalDataColumnImpl(hierarchical));
        continue;
      }
      dataColumns.add(new DataColumnImpl(column));
    }

    return dataColumns;
  }

  private static @NotNull Appendable createOutput(final Out out) {
    return new Appendable() {
      @Override
      public Appendable append(CharSequence csq) {
        return append(csq, 0, csq.length());
      }

      @Override
      public Appendable append(CharSequence csq, int start, int end) {
        out.appendText(csq.subSequence(start, end));
        return this;
      }

      @Override
      public Appendable append(char c) {
        return append(String.valueOf(c));
      }
    };
  }


  @SuppressWarnings("SynchronizeOnThis")
  private static class ExtractionDataConveyor {
    public final ScriptExtraction extractionInput = new ScriptExtraction();
    public final DataStreamImpl outputStream = new DataStreamImpl();
    final int[] mySelectedColumns;
    private int myAllColumnsCount;
    private List<? extends DataColumn> myAllColumnsUpdate = null;
    Supplier<ExtensionScriptsUtil.Binder> myBinder;

    ExtractionDataConveyor(int[] selectedColumns, int allColumnsCount) {
      mySelectedColumns = selectedColumns;
      myAllColumnsCount = allColumnsCount;
    }

    public synchronized void setBinder(@NotNull Supplier<ExtensionScriptsUtil.Binder> binder) {
      myBinder = binder;
    }

    private class ScriptExtraction implements DataExtractor.Extraction {
      @Override
      public void updateColumns(GridColumn @NotNull [] columns) {
        ExtractionDataConveyor.this.updateColumns(columns);
      }

      @Override
      public void addData(List<? extends GridRow> rows) {
        enqueue(rows);
      }

      @Override
      public void complete() {
        allRowsEnqueued();
      }
    }

    final class DataStreamImpl extends DataStream<DataRowImpl> {
      final AtomicReference<Iterator<DataRowImpl>> iterator = new AtomicReference<>(new Iterator<>() {
        @Override
        public boolean hasNext() {
          return !isDepleted();
        }

        @Override
        public DataRowImpl next() {
          return dequeue();
        }
      });

      @Override
      public @NotNull Iterator<DataRowImpl> iterator() {
        Iterator<DataRowImpl> result = iterator.getAndSet(null);
        if (result == null) {
          throw new AssertionError("This data stream can only be iterated once");
        }
        return result;
      }

      void close(@Nullable Throwable error) {
        stop(error);
      }
    }

    private final Queue<RowUpdate> myRowsQueue = new ArrayDeque<>();
    private boolean myRowsDequeued;
    private boolean myAllRowsLoaded;

    private boolean myStopped;
    private Throwable myStopReason;

    public synchronized void enqueue(List<? extends GridRow> rows) {
      myRowsQueue.addAll(ContainerUtil.map(rows, row -> createUpdate(row)));

      // wake up those waiting for enqueue
      notifyAll();

      waitUntilRowsAreDequeued();
    }

    private RowUpdate createUpdate(GridRow row) {
      RowUpdate update = new RowUpdate(row, myAllColumnsUpdate);
      myAllColumnsUpdate = null;
      return update;
    }

    private synchronized void updateColumns(GridColumn[] columns) {
      if (columns.length == myAllColumnsCount) return; // existing columns do not change. Only number of columns may increase
      myAllColumnsCount = columns.length;
      myAllColumnsUpdate = createDataColumns(Arrays.asList(columns), mySelectedColumns);
    }

    public synchronized void allRowsEnqueued() {
      myAllRowsLoaded = true;
      notifyAll();
      waitUntilStopped();
    }

    public synchronized DataRowImpl dequeue() {
      waitUntilRowsAreEnqueued();

      if (myRowsQueue.isEmpty()) {
        throw new AssertionError();
      }

      RowUpdate update = myRowsQueue.remove();
      if (update.myAllColumnsUpdate != null) {
        myBinder.get()
          .bind(ALL_COLUMNS, update.myAllColumnsUpdate)
          .bind(COLUMNS, getSelectedDataColumns(update.myAllColumnsUpdate, mySelectedColumns));
      }

      DataRowImpl dataRow = new DataRowImpl(update.myRow, !myRowsDequeued, myAllRowsLoaded && myRowsQueue.isEmpty());
      myRowsDequeued = true;
      // wake up those waiting for dequeue
      notifyAll();

      return dataRow;
    }

    public synchronized boolean isDepleted() {
      return myAllRowsLoaded && myRowsQueue.isEmpty();
    }

    public synchronized void stop(@Nullable Throwable error) {
      myStopped = true;
      myStopReason = error;
      notifyAll();
    }

    private synchronized void waitUntilRowsAreEnqueued() {
      while (!myAllRowsLoaded && myRowsQueue.size() < 2) {
        checkStopped();
        doWait();
      }
    }

    private synchronized void waitUntilRowsAreDequeued() {
      while (!myAllRowsLoaded && myRowsQueue.size() > 1) {
        if (checkStopped()) {
          // An extractor script execution has completed without errors, yet not all rows are consumed.
          // We'll consume the remaining rows instead of the extractor script.
          myRowsQueue.clear();
          continue;
        }
        doWait();
      }
    }

    private synchronized void waitUntilStopped() {
      while (!checkStopped()) {
        doWait();
      }
    }

    private synchronized boolean checkStopped() {
      if (myStopReason != null) {
        throw myStopReason instanceof ProcessCanceledException
              ? (ProcessCanceledException)myStopReason
              : new ProcessCanceledException(myStopReason);
      }
      return myStopped;
    }

    private synchronized void doWait() {
      try {
        //noinspection WaitNotInLoop
        wait(300);
      }
      catch (InterruptedException ignore) {
      }
    }

    static final class RowUpdate {
      final GridRow myRow;
      final List<? extends DataColumn> myAllColumnsUpdate;

      RowUpdate(GridRow row, List<? extends DataColumn> allColumnsUpdate) {
        myRow = row;
        myAllColumnsUpdate = allColumnsUpdate;
      }
    }
  }
}
