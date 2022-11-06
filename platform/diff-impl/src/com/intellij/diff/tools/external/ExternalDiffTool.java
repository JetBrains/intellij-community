// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffNotificationIdsHolder;
import com.intellij.diff.chains.*;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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

    return JBIterable.from(diffProducers)
             .map(ExternalDiffTool::getFileType)
             .unique()
             .map(fileType -> ExternalDiffSettings.findDiffTool(fileType))
             .filter(Conditions.notNull())
             .first() != null;
  }

  @NotNull
  private static FileType getFileType(@NotNull DiffRequestProducer producer) {
    FileType contentType = producer.getContentType();
    if (contentType != null) return contentType;

    String filePath = producer.getName();
    return FileTypeManager.getInstance().getFileTypeByFileName(filePath);
  }

  public static boolean checkNotTooManyRequests(@Nullable Project project, @NotNull List<? extends DiffRequestProducer> diffProducers) {
    if (diffProducers.size() <= Registry.intValue("diff.external.tool.file.limit")) return true;
    new Notification("Diff Changes Loading Error",
                     DiffBundle.message("can.t.show.diff.in.external.tool.too.many.files", diffProducers.size()),
                     NotificationType.WARNING)
      .setDisplayId(DiffNotificationIdsHolder.EXTERNAL_TOO_MANY_SELECTED)
      .notify(project);
    return false;
  }

  @Nullable
  private static ExternalDiffSettings.ExternalTool getExternalToolFor(@NotNull ContentDiffRequest request) {
    ExternalDiffSettings.ExternalTool diffTool = JBIterable.from(request.getContents())
      .map(content -> content.getContentType())
      .filter(Conditions.notNull())
      .unique()
      .sort(Comparator.comparing(fileType -> fileType != UnknownFileType.INSTANCE ? -1 : 1))
      .map(fileType -> ExternalDiffSettings.findDiffTool(fileType))
      .filter(Conditions.notNull())
      .first();
    if (diffTool != null) return diffTool;

    if (isDefault()) {
      return ExternalDiffSettings.findDefaultDiffTool();
    }
    return null;
  }

  public static boolean showIfNeeded(@Nullable Project project,
                                     @NotNull DiffRequestChain chain,
                                     @NotNull DiffDialogHints hints) {
    return show(project, hints, indicator -> {
      List<? extends DiffRequestProducer> producers = loadProducersFromChain(chain);
      if (!wantShowExternalToolFor(producers)) return null;
      if (!checkNotTooManyRequests(project, producers)) return null;
      return collectRequests(project, producers, indicator);
    });
  }

  public static boolean showIfNeeded(@Nullable Project project,
                                     @NotNull List<? extends DiffRequestProducer> requestProducers,
                                     @NotNull DiffDialogHints hints) {
    return show(project, hints, indicator -> {
      if (!wantShowExternalToolFor(requestProducers)) return null;
      if (!checkNotTooManyRequests(project, requestProducers)) return null;
      return collectRequests(project, requestProducers, indicator);
    });
  }

  private static boolean show(@Nullable Project project,
                              @NotNull DiffDialogHints hints,
                              @NotNull ThrowableConvertor<? super ProgressIndicator, List<DiffRequest>, ? extends Exception> requestsProducer) {
    try {
      List<DiffRequest> requests = computeWithModalProgress(project,
                                                            DiffBundle.message("progress.title.loading.requests"),
                                                            requestsProducer);
      if (requests == null) return false;

      showRequests(project, requests, hints);
      return true;
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (Throwable e) {
      LOG.warn(e);
      Messages.showErrorDialog(project, e.getMessage(), DiffBundle.message("can.t.show.diff.in.external.tool"));
    }
    return false;
  }

  @RequiresEdt
  private static void showRequests(@Nullable Project project,
                                   @NotNull List<DiffRequest> requests,
                                   @NotNull DiffDialogHints hints) throws IOException {
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

  private static boolean tryShowRequestInExternal(@Nullable Project project, @NotNull DiffRequest request) throws IOException {
    if (!canShow(request)) return false;

    ExternalDiffSettings.ExternalTool externalTool = getExternalToolFor(((ContentDiffRequest)request));
    if (externalTool == null) return false;

    showRequest(project, request, externalTool);
    return true;
  }

  @NotNull
  @RequiresBackgroundThread
  private static List<? extends DiffRequestProducer> loadProducersFromChain(@NotNull DiffRequestChain chain) {
    ListSelection<? extends DiffRequestProducer> listSelection;
    if (chain instanceof AsyncDiffRequestChain) {
      listSelection = ((AsyncDiffRequestChain)chain).loadRequestsInBackground();
    }
    else if (chain instanceof DiffRequestSelectionChain) {
      listSelection = ((DiffRequestSelectionChain)chain).getListSelection();
    }
    else {
      listSelection = ListSelection.createAt(chain.getRequests(), chain.getIndex());
    }
    return listSelection.getExplicitSelection();
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
      new Notification("Diff Changes Loading Error",
                       DiffBundle.message("can.t.load.some.changes"),
                       message.toString(),
                       NotificationType.ERROR)
        .setDisplayId(DiffNotificationIdsHolder.EXTERNAL_CANT_LOAD_CHANGES)
        .notify(project);
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
                                 @NotNull ExternalDiffSettings.ExternalTool externalDiffTool) throws IOException {
    request.onAssigned(true);
    try {
      List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
      List<String> titles = ((ContentDiffRequest)request).getContentTitles();
      ExternalDiffToolUtil.executeDiff(project, externalDiffTool, contents, titles, request.getTitle());
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
