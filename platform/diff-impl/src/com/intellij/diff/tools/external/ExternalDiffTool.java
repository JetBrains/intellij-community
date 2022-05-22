// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.chains.*;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.execution.ExecutionException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExternalDiffTool {
  private static final Logger LOG = Logger.getInstance(ExternalDiffTool.class);

  public static boolean isEnabled() {
    return ExternalDiffSettings.getInstance().isExternalToolsEnabled();
  }

  public static boolean isDefault() {
    return isEnabled() && ExternalDiffSettings.isNotBuiltinDiffTool();
  }

  public static boolean wantShowExternalToolFor(@NotNull List<? extends DiffRequestProducer> diffProducers) {
    if (isDefault()) return true;

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    return diffProducers.stream()
      .map(DiffRequestProducer::getName)
      .filter(filePath -> !FileUtilRt.getExtension(filePath).equals("tmp"))
      .map(filePath -> fileTypeManager.getFileTypeByFileName(filePath))
      .distinct()
      .anyMatch(fileType -> ExternalDiffSettings.findDiffTool(fileType) != null);
  }

  public static void show(@Nullable Project project,
                          @NotNull DiffRequestChain chain,
                          @NotNull DiffDialogHints hints) {
    show(project, hints, indicator -> {
      List<DiffRequestProducer> producers = loadProducersFromChain(project, chain);
      return collectRequests(project, producers, indicator);
    });
  }

  public static void show(@Nullable Project project,
                          @NotNull List<DiffRequestProducer> requestProducers,
                          @NotNull DiffDialogHints hints) {
    show(project, hints, indicator -> {
      return collectRequests(project, requestProducers, indicator);
    });
  }

  private static void show(@Nullable Project project,
                           @NotNull DiffDialogHints hints,
                           @NotNull ThrowableConvertor<? super ProgressIndicator, List<DiffRequest>, ? extends Exception> requestsProducer) {
    try {
      List<DiffRequest> requests = computeWithModalProgress(project,
                                                            DiffBundle.message("progress.title.loading.requests"),
                                                            requestsProducer);
      if (requests == null) return;

      showRequests(project, requests, hints);
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (Throwable e) {
      LOG.warn(e);
      Messages.showErrorDialog(project, e.getMessage(), DiffBundle.message("can.t.show.diff.in.external.tool"));
    }
  }

  @RequiresEdt
  private static void showRequests(@Nullable Project project,
                                   @NotNull List<DiffRequest> requests,
                                   @NotNull DiffDialogHints hints) throws IOException, ExecutionException {
    List<DiffRequest> showInBuiltin = new ArrayList<>();
    for (DiffRequest request : requests) {
      boolean success = tryShowRequestInExternal(project, request);
      if (!success) {
        showInBuiltin.add(request);
      }
    }

    if (!showInBuiltin.isEmpty()) {
      DiffManagerEx.getInstance().showDiffBuiltin(project, new SimpleDiffRequestChain(showInBuiltin), hints);
    }
  }

  private static boolean tryShowRequestInExternal(@Nullable Project project, @NotNull DiffRequest request)
    throws IOException, ExecutionException {
    if (!canShow(request)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    ExternalDiffSettings.ExternalTool externalTool = StreamEx.of(contents)
      .map(content -> content.getContentType())
      .nonNull()
      .map(fileType -> ExternalDiffSettings.findDiffTool(fileType))
      .nonNull()
      .findFirst().orElse(null);
    if (externalTool == null) return false;

    showRequest(project, request, externalTool);
    return true;
  }

  @NotNull
  @RequiresBackgroundThread
  private static List<DiffRequestProducer> loadProducersFromChain(@Nullable Project project, @NotNull DiffRequestChain chain) {
    ListSelection<? extends DiffRequestProducer> listSelection;
    if (chain instanceof AsyncDiffRequestChain) {
      listSelection = ((AsyncDiffRequestChain)chain).loadRequestsInBackground();
    }
    else {
      listSelection = ListSelection.createAt(chain.getRequests(), chain.getIndex());
    }

    if (listSelection.isEmpty()) return Collections.emptyList();

    // We do not show all changes, as it might be an 'implicit selection' from 'getSelectedOrAll()' calls.
    // TODO: introduce key in DiffUserDataKeys to differentiate these use cases
    DiffRequestProducer producerToShow = listSelection.getList().get(listSelection.getSelectedIndex());
    return Collections.singletonList(producerToShow);
  }

  @NotNull
  private static List<DiffRequest> collectRequests(@Nullable Project project,
                                                   @NotNull List<? extends DiffRequestProducer> producers,
                                                   @NotNull ProgressIndicator indicator) {
    List<DiffRequest> requests = new ArrayList<>();

    UserDataHolderBase context = new UserDataHolderBase();
    List<DiffRequestProducer> errorRequests = new ArrayList<>();

    for (DiffRequestProducer producer : producers) {
      try {
        requests.add(producer.process(context, indicator));
      }
      catch (DiffRequestProducerException e) {
        LOG.warn(e);
        errorRequests.add(producer);
      }
    }

    if (!errorRequests.isEmpty()) {
      HtmlBuilder message = new HtmlBuilder()
        .appendWithSeparators(HtmlChunk.br(), ContainerUtil.map(errorRequests, producer -> HtmlChunk.text(producer.getName())));
      new Notification("Diff Changes Loading Error", DiffBundle.message("can.t.load.some.changes"), message.toString(),
                       NotificationType.ERROR).notify(project);
    }

    return requests;
  }

  private static <T> T computeWithModalProgress(@Nullable Project project,
                                                @NotNull @NlsContexts.DialogTitle String title,
                                                @NotNull ThrowableConvertor<? super ProgressIndicator, T, ? extends Exception> computable)
    throws Exception {
    return ProgressManager.getInstance().run(new Task.WithResult<T, Exception>(project, title, true) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws Exception {
        return computable.convert(indicator);
      }
    });
  }

  public static void showRequest(@Nullable Project project,
                                 @NotNull DiffRequest request,
                                 @NotNull ExternalDiffSettings.ExternalTool externalDiffTool) throws ExecutionException, IOException {
    request.onAssigned(true);
    try {
      List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
      List<String> titles = ((ContentDiffRequest)request).getContentTitles();
      ExternalDiffToolUtil.execute(project, externalDiffTool, contents, titles, request.getTitle());
    }
    finally {
      request.onAssigned(false);
    }
  }

  public static boolean canShow(@NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;
    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 2 && contents.size() != 3) return false;
    for (DiffContent content : contents) {
      if (!ExternalDiffToolUtil.canCreateFile(content)) return false;
    }
    return true;
  }
}
