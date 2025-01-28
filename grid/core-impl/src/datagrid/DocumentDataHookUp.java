package com.intellij.database.datagrid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.connection.throwable.info.SimpleErrorInfo;
import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.datagrid.mutating.RowMutation;
import com.intellij.database.dbimport.TypeMerger;
import com.intellij.database.run.ui.grid.GridMutationModel;
import com.intellij.database.run.ui.grid.GridStorageAndModelUpdater;
import com.intellij.database.run.ui.grid.MoveColumnsRequestPlace;
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EdtInvocationManager;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.math.BigInteger;
import java.sql.Types;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

import static com.intellij.util.containers.ContainerUtil.emptyList;


public abstract class DocumentDataHookUp extends GridDataHookUpBase<GridRow, GridColumn> implements Disposable, HookUpVirtualFileProvider {

  private final Document myDocument;
  private TextRange myRange;

  private DataMarkup myCurrentMarkup;

  private final DataGridListModel myModel;
  private final GridStorageAndModelUpdater myModelUpdater;
  private final GridMutationModel myMutationModel;
  private final GridPagingModel<GridRow, GridColumn> myPageModel;
  private final DocumentDataLoader myLoader;
  private final DocumentDataMutator myMutator;
  private final CoroutineScope cs = com.intellij.platform.util.coroutines.CoroutineScopeKt
    .childScope(GlobalScope.INSTANCE, getClass().getName(), Dispatchers.getIO(), true);

  private final MyDocumentListener myDocumentListener;

  protected DocumentDataHookUp(@NotNull Project project, @NotNull Document document, @Nullable TextRange range) {
    super(project);

    myDocument = document;
    myRange = range;

    myModel = new DataGridListModel(GridCellEditorHelper::areValuesEqual);
    myMutationModel = new GridMutationModel(this);
    myModelUpdater = new GridStorageAndModelUpdater(myModel, myMutationModel, null);
    myPageModel = new GridPagingModelImpl.SinglePage<>(myMutationModel);
    myLoader = new DocumentDataLoader();
    Disposer.register(this, myLoader);
    myMutator = createDataMutator();

    myDocumentListener = new MyDocumentListener();
    myDocument.addDocumentListener(myDocumentListener, this);
  }

  protected @NotNull DocumentDataMutator createDataMutator() {
    return new DocumentDataMutator();
  }

  @TestOnly
  public void awaitParsingFinished() {
    myLoader.awaitParsingFinished();
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @Nullable VirtualFile getVirtualFile() {
    return FileDocumentManager.getInstance().getFile(myDocument);
  }

  @Override
  public @NotNull GridModel<GridRow, GridColumn> getDataModel() {
    return myModel;
  }

  @Override
  public @NotNull GridModel<GridRow, GridColumn> getMutationModel() {
    return myMutationModel;
  }

  @Override
  public @NotNull GridPagingModel<GridRow, GridColumn> getPageModel() {
    return myPageModel;
  }

  @Override
  public @NotNull GridLoader getLoader() {
    return myLoader;
  }

  @Override
  public @Nullable GridMutator<GridRow, GridColumn> getMutator() {
    return myMutator;
  }

  @Override
  public boolean isReadOnly() {
    return !myDocument.isWritable();
  }

  @Override
  public void dispose() {
    CoroutineScopeKt.cancel(cs, "Disposed", null);
  }

  public @Nullable TextRange getRange() {
    return myRange;
  }

  protected abstract @Nullable DataMarkup buildMarkup(@NotNull CharSequence sequence, @NotNull GridRequestSource myRequestSource);

  public static class UpdateSession {
    private final Document myDocument;
    private int myRightShift;

    protected UpdateSession(@NotNull Document document, int rightShift) {
      myDocument = document;
      myRightShift = rightShift;
    }

    public void insert(@NotNull CharSequence sequence, int atOffset) {
      myDocument.insertString(atOffset + myRightShift, sequence);
      myRightShift += sequence.length();
    }

    public void replace(@NotNull TextRange range, @NotNull CharSequence sequence) {
      TextRange shifted = range.shiftRight(myRightShift);
      myDocument.replaceString(shifted.getStartOffset(), shifted.getEndOffset(), sequence);
      myRightShift += sequence.length() - shifted.getLength();
    }

    public void delete(@NotNull TextRange range) {
      TextRange shifted = range.shiftRight(myRightShift);
      myDocument.deleteString(shifted.getStartOffset(), shifted.getEndOffset());
      myRightShift -= range.getLength();
    }

    public char charAt(int offset) {
      return myDocument.getCharsSequence().charAt(offset + myRightShift);
    }

    public boolean isValidOffset(int offset) {
      int shifted = offset + myRightShift;
      return shifted >= 0 && shifted < myDocument.getTextLength();
    }

    public @NotNull String getText() {
      return myDocument.getText();
    }
  }

  public abstract static class DataMarkup {
    public static final TypeMerger STRING_MERGER = new TypeMerger.StringMerger("TEXT");
    public static final TypeMerger INTEGER_MERGER = new TypeMerger.IntegerMerger("INT");
    public static final TypeMerger BIG_INTEGER_MERGER = new TypeMerger.BigIntegerMerger("BIGINT");
    public static final TypeMerger DOUBLE_MERGER = new TypeMerger.DoubleMerger("DOUBLE");
    public static final TypeMerger BOOLEAN_MERGER = new TypeMerger.BooleanMerger("BOOLEAN");

    public final List<GridColumn> columns;
    public final List<GridRow> rows;

    public DataMarkup(@NotNull List<GridColumn> columns, @NotNull List<GridRow> rows) {
      this.columns = columns;
      this.rows = rows;
    }

    public static int getType(@NotNull TypeMerger merger) {
      return merger == INTEGER_MERGER ? Types.INTEGER :
             merger == DOUBLE_MERGER ? Types.DOUBLE :
             merger == BIG_INTEGER_MERGER ? Types.BIGINT :
             merger == BOOLEAN_MERGER ? Types.BOOLEAN :
             Types.VARCHAR;
    }

    public static String getClassName(@NotNull TypeMerger merger) {
      return merger == INTEGER_MERGER ? CommonClassNames.JAVA_LANG_INTEGER :
             merger == DOUBLE_MERGER ? CommonClassNames.JAVA_LANG_DOUBLE :
             merger == BIG_INTEGER_MERGER ? BigInteger.class.getName() :
             merger == BOOLEAN_MERGER ? Boolean.class.getName() :
             CommonClassNames.JAVA_LANG_STRING;
    }

    protected abstract boolean deleteRows(@NotNull UpdateSession session, @NotNull List<GridRow> sortedRows);

    protected abstract boolean insertRow(@NotNull UpdateSession session);

    protected abstract boolean cloneRow(@NotNull UpdateSession session, @NotNull GridRow row);

    protected abstract boolean deleteColumns(@NotNull UpdateSession session, @NotNull List<GridColumn> sortedColumns);

    protected abstract boolean insertColumn(@NotNull UpdateSession session, @Nullable String name);

    protected abstract boolean cloneColumn(@NotNull UpdateSession session, @NotNull GridColumn column);

    protected abstract boolean update(@NotNull UpdateSession session, @NotNull List<RowMutation> infos);

    protected abstract boolean renameColumn(@NotNull UpdateSession session, @NotNull ModelIndex<GridColumn> column, @NotNull String name);

    protected String prepareMoveColumn(@NotNull GridColumn fromColumn, @NotNull ModelIndex<GridColumn> toColumn) {
      return "";
    }
  }


  protected class DocumentDataMutator
    implements GridMutator.RowsMutator<GridRow, GridColumn>, GridMutator.ColumnsMutator<GridRow, GridColumn> {

    @Override
    public boolean hasUnparsedValues() {
      return false;
    }

    @Override
    public void deleteRows(@NotNull GridRequestSource source, @NotNull ModelIndexSet<GridRow> rows) {
      final List<GridRow> rowsToDelete = sortedRows(myModel.getRows(rows));
      if (isReadOnly() || rowsToDelete.isEmpty()) {
        notifyRequestFinished(source, !isReadOnly());
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull UpdateSession session) {
          return myCurrentMarkup.deleteRows(session, rowsToDelete);
        }
      });
    }

    @Override
    public void insertRows(@NotNull GridRequestSource source, int amount) {
      if (isReadOnly()) {
        notifyRequestFinished(source, false);
        return;
      }

      for (int i = 0; i < amount; i++) {
        updateDocument(source, new UpdateAction() {
          @Override
          public boolean perform(@NotNull UpdateSession session) {
            return myCurrentMarkup.insertRow(session);
          }
        });
      }
    }

    @Override
    public void cloneRow(@NotNull GridRequestSource source, final @NotNull ModelIndex<GridRow> toClone) {
      final GridRow row = myModel.getRow(toClone);
      if (isReadOnly() || row == null) {
        notifyRequestFinished(source, false);
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull UpdateSession session) {
          return myCurrentMarkup.cloneRow(session, row);
        }
      });
    }

    @Override
    public boolean isDeletedRow(@NotNull ModelIndex<GridRow> row) {
      return false;
    }

    @Override
    public boolean isDeletedRows(@NotNull ModelIndexSet<GridRow> rows) {
      return false;
    }

    @Override
    public boolean isInsertedRow(@NotNull ModelIndex<GridRow> row) {
      return false;
    }

    @Override
    public boolean isDeletedColumn(@NotNull ModelIndex<GridColumn> idx) {
      return false;
    }

    @Override
    public int getInsertedRowsCount() {
      return 0;
    }

    @Override
    public int getInsertedColumnsCount() {
      return 0;
    }

    @Override
    public void deleteColumns(@NotNull GridRequestSource source, @NotNull ModelIndexSet<GridColumn> columns) {
      final List<GridColumn> columnsToDelete = sortedColumns(myModel.getColumns(columns));
      if (isReadOnly() || columnsToDelete.isEmpty()) {
        notifyRequestFinished(source, !isReadOnly());
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull UpdateSession session) {
          return myCurrentMarkup.deleteColumns(session, columnsToDelete);
        }
      });
    }

    @Override
    public void insertColumn(@NotNull GridRequestSource source, @Nullable String name) {
      if (isReadOnly()) {
        notifyRequestFinished(source, false);
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull UpdateSession session) {
          return myCurrentMarkup.insertColumn(session, name);
        }
      });
    }

    @Override
    public void moveColumn(
      @NotNull GridRequestSource source,
      @NotNull ModelIndex<GridColumn> from,
      @NotNull ModelIndex<GridColumn> to
    ) {
      final GridColumn fromColumn = myModel.getColumn(from);
      if (isReadOnly() || fromColumn == null) {
        notifyRequestFinished(source, false);
        return;
      }

      updateDocumentWithComputeOnBG(source, new UpdateActionWithComputeOnBG<String>() {
        @Override
        public boolean performUpdate(@NotNull DocumentDataHookUp.UpdateSession session, String data) {
          myDocument.setText(data);
          return true;
        }

        @Override
        public String prepareData() {
          return myCurrentMarkup.prepareMoveColumn(fromColumn, to);
        }
      });
    }

    @Override
    public void cloneColumn(@NotNull GridRequestSource source, @NotNull ModelIndex<GridColumn> toClone) {
      final GridColumn column = myModel.getColumn(toClone);
      if (isReadOnly() || column == null) {
        notifyRequestFinished(source, false);
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull UpdateSession session) {
          return myCurrentMarkup.cloneColumn(session, column);
        }
      });
    }

    @Override
    public @NotNull ModelIndex<GridRow> getLastInsertedRow() {
      return ModelIndex.forRow(myModel, -1);
    }

    @Override
    public @NotNull ModelIndexSet<GridRow> getAffectedRows() {
      return ModelIndexSet.forRows(myModel, -1);
    }

    @Override
    public @NotNull JBIterable<ModelIndex<GridRow>> getInsertedRows() {
      return JBIterable.empty();
    }

    @Override
    public @NotNull JBIterable<ModelIndex<GridColumn>> getInsertedColumns() {
      return JBIterable.empty();
    }

    @Override
    public boolean isInsertedColumn(@NotNull ModelIndex<GridColumn> idx) {
      return false;
    }

    @Override
    public @Nullable GridColumn getInsertedColumn(@NotNull ModelIndex<GridColumn> idx) {
      return null;
    }

    @Override
    public void renameColumn(@NotNull GridRequestSource source,
                             @NotNull ModelIndex<GridColumn> idx,
                             @NotNull String newName) {
      final GridColumn column = myModel.getColumn(idx);
      if (isReadOnly() || column == null) {
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull UpdateSession session) {
          return myCurrentMarkup.renameColumn(session, idx, newName);
        }
      });
    }

    @Override
    public void mutate(@NotNull GridRequestSource source,
                       @NotNull ModelIndexSet<GridRow> rows,
                       @NotNull ModelIndexSet<GridColumn> columns,
                       Object newValue,
                       boolean allowImmediateUpdate) {
      mutate(source, GridUtilCore.createMutations(rows, columns, newValue), allowImmediateUpdate);
    }

    @Override
    public void mutate(@NotNull GridRequestSource source, @NotNull List<CellMutation> mutations, boolean allowImmediateUpdate) {
      List<RowMutation> rowMutations = GridUtilCore.mergeAll(mutations, myModel);
      if (isReadOnly() || mutations.isEmpty() || rowMutations.isEmpty() || myModel.allValuesEqualTo(mutations)) {
        notifyRequestFinished(source, !isReadOnly());
        return;
      }

      updateDocument(source, new UpdateAction() {
        @Override
        public boolean perform(@NotNull DocumentDataHookUp.UpdateSession session) {
          return myCurrentMarkup.update(session, rowMutations);
        }
      });
    }

    @Override
    public boolean isUpdateSafe(@NotNull ModelIndexSet<GridRow> rowIndices,
                                @NotNull ModelIndexSet<GridColumn> columnIndices,
                                @Nullable Object newValue) {
      return true;
    }

    @Override
    public boolean hasPendingChanges() {
      return false;
    }

    @Override
    public boolean isUpdateImmediately() {
      return true;
    }

    private <T> void updateDocumentWithComputeOnBG(@NotNull GridRequestSource source, @NotNull UpdateActionWithComputeOnBG<T> action) {
      new DocumentUpdaterWithComputeOnBG().run(
        cs, source, action, myDocument, myDocumentListener, DocumentDataHookUp.this,
        () -> {
          return new UpdateSessionCloser(createSession(), (session, success) -> { finishSession(session, success); return Unit.INSTANCE; });
        }
      );
    }

    private void updateDocument(final @NotNull GridRequestSource source, final @NotNull UpdateAction action) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
        if (!ReadonlyStatusHandler.ensureDocumentWritable(getProject(), myDocument)) {
          notifyRequestError(source, SimpleErrorInfo.create(DataGridBundle.message("cannot.update.document")));
          notifyRequestFinished(source, false);
          return;
        }

        UpdateSession session = createSession();
        final ThrowableComputable<Boolean, Exception> wrappedAction = () -> {
          myDocumentListener.muteChangeEvents();
          var result = Ref.create(false);
          try {
            DocumentUtil.executeInBulk(myDocument, () -> {
              try {
                var actionResult = action.perform(session);
                result.set(actionResult);
              }
              catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
            return result.get();
          }
          finally {
            myDocumentListener.unmuteChangeEvents(source);
          }
        };
        CommandProcessor.getInstance().executeCommand(getProject(), () -> {
          boolean success = false;
          try {
            success = ApplicationManager.getApplication().runWriteAction(wrappedAction);
            finishSession(session, success);
          }
          catch (Exception e) {
            notifyRequestError(source, SimpleErrorInfo.create(e));
          }
          notifyRequestFinished(source, success);
        }, DataGridBundle.message("command.name.update.values"), null);
      });
    }

    protected void finishSession(@NotNull UpdateSession session, boolean success) {
    }

    protected @NotNull UpdateSession createSession() {
      return new UpdateSession(myDocument, myRange != null ? myRange.getStartOffset() : 0);
    }

    private static List<GridColumn> sortedColumns(@NotNull List<GridColumn> columns) {
      return ContainerUtil.sorted(columns, Comparator.comparingInt(column -> column.getColumnNumber()));
    }

    private static List<GridRow> sortedRows(@NotNull List<GridRow> rows) {
      return ContainerUtil.sorted(rows, Comparator.comparingInt(row -> row.getRowNum()));
    }
  }


  public class MyDocumentListener implements DocumentListener {
    private boolean myMuteChangeEvents;
    private boolean myChangesOccurredWhileMuted;

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      adjustRange(event);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (myMuteChangeEvents) {
        myChangesOccurredWhileMuted = true;
      }
      else {
        reload(new GridRequestSource(null));
      }
    }

    public void muteChangeEvents() {
      myMuteChangeEvents = true;
      myChangesOccurredWhileMuted = false;
    }

    public void unmuteChangeEvents(@NotNull GridRequestSource source) {
      myMuteChangeEvents = false;
      if (myChangesOccurredWhileMuted) {
        reload(source);
      }
    }

    private void reload(@NotNull GridRequestSource source) {
      EdtInvocationManager.invokeLaterIfNeeded(() -> {
          if (!myMutationModel.hasListeners()) return;
          myLoader.doLoadData(source);
        });
    }

    private void adjustRange(DocumentEvent e) {
      if (myRange == null) return;

      int lengthDelta = e.getNewLength() - e.getOldLength();
      if (myRange.containsRange(e.getOffset(), e.getOffset() + e.getOldLength())) {
        myRange = new TextRange(myRange.getStartOffset(), myRange.getEndOffset() + lengthDelta);
      }
      else if (myRange.getStartOffset() > e.getOffset() + e.getOldLength()) {
        myRange = myRange.shiftRight(lengthDelta);
      }
      else if (myRange.intersects(e.getOffset(), e.getOffset() + e.getOldLength())) {
        // expand our range to cover both previous range and appended/prepended region, or contract accordingly
        int startOffset = Math.min(e.getOffset(), myRange.getStartOffset());
        int endOffset = Math.max(e.getOffset() + e.getNewLength(), myRange.getEndOffset() + lengthDelta);
        myRange = new TextRange(startOffset, endOffset);
      }
    }
  }

  private class DocumentDataLoader implements GridLoader, Disposable {
    private final Logger LOG = Logger.getInstance(DocumentDataLoader.class);
    private final ThreadPoolExecutor myExecutor = ConcurrencyUtil.newSingleThreadExecutor(getClass().getSimpleName());
    private Future<?> myLastExtractorTask = null;

    @Override
    public void reloadCurrentPage(@NotNull GridRequestSource source) {
      load(source, 0);
    }

    @Override
    public void loadNextPage(@NotNull GridRequestSource source) {
      load(source, 0);
    }

    @Override
    public void loadPreviousPage(@NotNull GridRequestSource source) {
      load(source, 0);
    }

    @Override
    public void loadLastPage(@NotNull GridRequestSource source) {
      load(source, 0);
    }

    @Override
    public void loadFirstPage(@NotNull GridRequestSource source) {
      load(source, 0);
    }

    @Override
    public void load(@NotNull GridRequestSource source, int offset) {
      try {
        doLoadData(source);
      }
      catch (Exception e) {
        notifyRequestError(source, SimpleErrorInfo.create(e));
      }
    }

    @Override
    public void updateTotalRowCount(@NotNull GridRequestSource source) {
      notifyRequestFinished(source, false);
    }

    @Override
    public void applyFilterAndSorting(@NotNull GridRequestSource source) {
      notifyRequestFinished(source, false);
    }

    @Override
    public void updateIsTotalRowCountUpdateable() {
    }

    private void doLoadData(@NotNull GridRequestSource source) {
      CharSequence sequence = myRange != null ? myRange.subSequence(myDocument.getCharsSequence()) :
                              myDocument.getCharsSequence();
      if (myLastExtractorTask != null) myLastExtractorTask.cancel(false);
      ModalityState state = ModalityState.current();
      myLastExtractorTask = myExecutor.submit(() -> {
        DataMarkup markup = buildMarkup(sequence, source);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (markup == null) {
            myModelUpdater.removeRows(0, myModel.getRowCount());
            myModelUpdater.setColumns(emptyList());
            myCurrentMarkup = null;
            notifyRequestFinished(source, false);
            return;
          }

          List<GridColumn> columns = markup.columns;
          List<GridRow> rows = markup.rows;

          if (!sameColumns(columns, myModel.getColumns()) || source.place instanceof MoveColumnsRequestPlace) {
            myModelUpdater.removeRows(0, myModel.getRowCount());
            myModelUpdater.setColumns(columns);
            myModelUpdater.addRows(rows);

            if (source.place instanceof MoveColumnsRequestPlace info) {
              info.adjustColumnsUI();
            }
          }
          else {
            int oldRowCount = myModel.getRowCount();
            int newRowCount = rows.size();
            myModelUpdater.setRows(0, rows, source);
            if (oldRowCount > newRowCount) {
              myModelUpdater.removeRows(newRowCount, oldRowCount - newRowCount);
            }
          }

          myCurrentMarkup = markup;
          notifyRequestFinished(source, true);
        }, state);
      });
    }

    private static boolean sameColumns(List<GridColumn> columns1, List<GridColumn> columns2) {
      if (columns1.size() != columns2.size()) return false;
      for (int i = 0; i < columns1.size(); i++) {
        if (!Comparing.equal(columns1.get(i), columns2.get(i))) return false;
      }
      return true;
    }

    public void awaitParsingFinished() {
      if (myLastExtractorTask == null) return;
      try {
        myLastExtractorTask.get(10, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        LOG.error("DocumentDataLoader task termination interrupted", e);
      }
    }

    @Override
    public void dispose() {
      myExecutor.shutdown();
    }
  }

  public interface UpdateAction {
    boolean perform(@NotNull UpdateSession session) throws Exception;
  }
}
