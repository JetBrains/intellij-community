package com.intellij.database.dump;

import com.intellij.concurrency.AsyncFutureFactory;
import com.intellij.concurrency.AsyncFutureResult;
import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.*;
import com.intellij.database.run.actions.DumpSourceNameProvider;
import com.intellij.database.util.ErrorHandler;
import com.intellij.database.util.Out;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.NotNullFunction;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.JBIterable;
import kotlin.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class DumpHandler<T> {
  private static final Logger LOG = Logger.getInstance(DumpHandler.class);

  protected final Project myProject;
  protected final JBIterable<? extends T> mySources;
  protected final DumpSourceNameProvider<T> myNameProvider;
  protected final @NlsContexts.ProgressTitle String myTitle;
  private final DataExtractorFactory myFactory;
  protected final ExtractionConfig myConfig;
  protected final ExtractionHelper myManager;

  private final ErrorHandler myHandler;

  private long myLength;
  protected int myRowsCount;
  private int myProcessedCount;

  protected DumpHandler(@NotNull Project project,
                        @NotNull JBIterable<? extends T> sources,
                        @NotNull DumpSourceNameProvider<T> nameProvider,
                        @NotNull ExtractionHelper manager,
                        @NotNull String displayName,
                        @NotNull DataExtractorFactory factory,
                        @NotNull ExtractionConfig config) {
    myProject = project;
    myManager = manager;
    mySources = sources;
    myNameProvider = nameProvider;
    myTitle = myManager.getTitle(displayName);
    myFactory = factory;
    myConfig = config;
    myHandler = new ErrorHandler();
  }

  protected abstract @NotNull ExtractorConfig createExtractorConfig(@NotNull T source, @Nullable Project project);

  // TODO (anya) [api]: make it abstract later
  protected int getSubQueryIndex(@NotNull T source) {
    return 0;
  }

  protected abstract int getResultSetIndex(@NotNull T source);

  protected abstract @Nullable ModelIndexSet<GridColumn> getSelectedColumns(@NotNull T source);

  protected abstract @Nullable AsyncPromise<Void> run(@NotNull T source,
                                                      @NotNull DataExtractor extractor,
                                                      @NotNull Out out,
                                                      @NotNull DumpHandlerParameters dumpParameters);

  protected abstract @NlsSafe @Nullable String getDatabaseSystemName();

  protected abstract @Nullable String getProducerName();

  protected abstract @Nullable String getSourceName(int count);

  protected @Nullable ObjectFormatter getFormatter(@NotNull T source) {
    return null;
  }

  protected @NotNull NotNullFunction<DataExtractor, GridDataRequest> newSimpleRunner(final @NotNull DataProducer producer,
                                                                                     final GridDataRequest.GridDataRequestOwner owner,
                                                                                     @NotNull Out out,
                                                                                     @NotNull DumpHandlerParameters dumpParameters) {
    return extractor -> {
      GridDataRequest request = createDumpRequest(owner, extractor, out, null, dumpParameters);
      producer.processRequest(request);
      return request;
    };
  }

  protected final boolean isSingleSource() {
    return mySources.skip(1).isEmpty();
  }

  private void processError(@Nullable Throwable th) {
    myHandler.addError(null, th);
  }

  public void performDump(@Nullable Project project) {
    Task.Backgroundable task = buildTask(myFactory, project);
    if (task == null) return;
    ProgressManager.getInstance().run(task);
  }

  public @Nullable Task.Backgroundable buildTask(@NotNull DataExtractorFactory factory, @Nullable Project project) {
    ThreadingAssertions.assertEventDispatchThread();
    List<Triple<T, DataExtractor, DumpHandlerParameters>> pairs = mySources
      .map(s -> {
        int subQueryIndex = getSubQueryIndex(s);
        int resultSetIndex = getResultSetIndex(s);
        ModelIndexSet<GridColumn> selectedColumns = getSelectedColumns(s);
        String queryText = myNameProvider.getQueryText(s);
        String name = myNameProvider.getName(s);
        DataExtractor extractor = factory.createExtractor(createExtractorConfig(s, project));
        return extractor != null && queryText != null ?
               new Triple<>((T)s, extractor, new DumpHandlerParameters(selectedColumns, queryText, subQueryIndex, resultSetIndex, name)) :
               null;
      })
      .filter(p -> p != null)
      .toList();
    if (pairs.isEmpty()) return null;
    return new Task.Backgroundable(myProject, myTitle) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(DataGridBundle.message("progress.text.initializing.output"));
        indicator.setIndeterminate(true);
        Out previousOutput = null;
        try {
          for (Triple<T, DataExtractor, DumpHandlerParameters> p : pairs) {
            indicator.checkCanceled();
            if (p.getSecond() == null) continue;
            previousOutput = processSource(p.getFirst(), p.getSecond(), indicator, p.getThird(), previousOutput);
          }
        }
        catch (ProcessCanceledException ignore) {
        }
        finally {
          dumpFinished(previousOutput);
        }
      }
    };
  }

  private void dumpFinished(@Nullable Out lastOutput) {
    try {
      String systemName = getDatabaseSystemName();
      DumpInfo info = new DumpInfo(
        systemName != null ? systemName : DataGridBundle.message("notification.title.data.dump"),
        myHandler.getSummary(),
        getSourceName(myProcessedCount),
        getProducerName(),
        myRowsCount,
        myProcessedCount
      );
      if (lastOutput != null && myManager.isSingleFileMode()) lastOutput.close();
      myManager.after(myProject, info);
    }
    catch (Exception e) {
      String message = e.getMessage();
      if (message == null) {
        LOG.warn(e);
        return;
      }
      DataGridNotifications.EXTRACTORS_GROUP
        .createNotification(DataGridBundle.message("notification.title.data.dump"), message, NotificationType.ERROR)
        .setDisplayId("DumpHandler.error")
        .notify(myProject);
    }
  }

  private @Nullable Out processSource
    (@NotNull T source,
     @NotNull DataExtractor extractor,
     @NotNull ProgressIndicator indicator,
     @NotNull DumpHandlerParameters dumpParameters,
     @Nullable Out previousOutput
    ) {
    AsyncFutureResult<Object> result = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    Out output;
    try {
      output = refreshOut(dumpParameters.name, extractor, previousOutput);
    }
    catch (Exception e) {
      processError(e);
      return null;
    }
    indicator.setText(DataGridBundle.message("progress.text.running.query"));
    AsyncPromise<Void> promise = run(source, extractor, output, dumpParameters);
    if (promise == null) return output;
    promise.onProcessed(o -> result.set(true));
    try {
      result.get();
    }
    catch (Exception e) {
      processError(e);
    }
    finally {
      myProcessedCount++;
      sourceDumped(extractor, output);
    }
    return output;
  }

  private @NotNull Out refreshOut(
    @Nullable String name,
    @NotNull DataExtractor extractor,
    @Nullable Out previousOutput
  ) throws Exception {
    if (myManager.isSingleFileMode() && previousOutput != null) {
      if (previousOutput instanceof ByteArrayOutputStream arrayOutput && extractor.supportsText()) {
        if (arrayOutput.size() > myLength) arrayOutput.write("\n".getBytes(StandardCharsets.UTF_8));
        myLength = arrayOutput.size();
      }
      return previousOutput;
    }
    return myManager.createOut(name, extractor);
  }

  private void sourceDumped(@NotNull DataExtractor extractor, @NotNull Out out) {
    if (myManager.isSingleFileMode()) return;

    try {
      myManager.sourceDumped(extractor, out);
      out.close();
    }
    catch (Exception ex) {
      processError(ex);
    }
  }

  protected abstract @NotNull GridDataRequest createDumpRequest(@NotNull GridDataRequest.GridDataRequestOwner owner,
                                                                DataExtractor extractor,
                                                                @NotNull Out out,
                                                                @Nullable String name,
                                                                @NotNull DumpHandlerParameters dumpParameters);

  public static class DumpHandlerParameters {
    public final ModelIndexSet<GridColumn> selectedColumns;
    public final String queryText;
    public final int subQueryIndex;
    public final int resultSetIndex;
    private final String name;

    private DumpHandlerParameters(@Nullable ModelIndexSet<GridColumn> selectedColumns,
                                  @NotNull String queryText,
                                  int subQueryIndex,
                                  int resultSetIndex,
                                  @Nullable String name) {
      this.selectedColumns = selectedColumns;
      this.queryText = queryText;
      this.subQueryIndex = subQueryIndex;
      this.resultSetIndex = resultSetIndex;
      this.name = name;
    }
  }
}
